package processors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import exceptions.AudioProcessingException;

/**
 * Audio gain processor that applies volume control with smooth fade transitions.
 *
 * This class implements the {@link AudioProcessor} interface to provide smooth, real-time
 * volume adjustment with automatic fade-in and fade-out effects. Instead of applying gain
 * changes immediately, it gradually transitions from the current gain to the target gain
 * value, preventing audible clicks and pops in the audio output.
 *
 * <p><strong>Key Features:</strong>
 * <ul>
 *   <li>Smooth gain transitions with configurable fade speed</li>
 *   <li>Thread-safe target gain setting using volatile field</li>
 *   <li>Real-time sample-by-sample gain application</li>
 *   <li>Automatic gain clamping to prevent distortion (0.0 to 1.0)</li>
 *   <li>Fade completion detection for synchronization</li>
 *   <li>Comprehensive logging for debugging and monitoring</li>
 * </ul>
 *
 * <p><strong>Fade Mechanism:</strong>
 * <pre>
 * Target Gain: 0.8 (80%)
 * Current Gain: 0.3 (30%)
 * Fade Speed: 0.000075 per sample
 *
 * Sample 1: current = min(0.8, 0.3 + 0.000075) = 0.300075
 * Sample 2: current = min(0.8, 0.300075 + 0.000075) = 0.30015
 * ...
 * Sample N: current = 0.8 (target reached)
 * </pre>
 *
 * <p><strong>Gain Range:</strong>
 * All gain values are automatically clamped to the range [0.0, 1.0]:
 * <ul>
 *   <li><b>0.0:</b> Complete silence (mute)</li>
 *   <li><b>0.5:</b> 50% volume (half amplitude)</li>
 *   <li><b>1.0:</b> 100% volume (maximum amplitude)</li>
 * </ul>
 *
 * <p><strong>Sample Processing Pipeline:</strong>
 * <pre>
 * Input Samples (float array)
 *     ↓
 * Check gain transition needed
 *     ↓
 * Update currentGain towards targetGain
 *     ↓
 * Multiply each sample by currentGain
 *     ↓
 * Output Samples (modified in-place)
 * </pre>
 *
 * <p><strong>Usage Example:</strong>
 * <pre>{@code
 * // Initialize with 50% volume
 * GainProcessor gainProcessor = new GainProcessor(0.5f);
 *
 * float[] audioSamples = new float[1024];
 * // ... fill audioSamples with audio data ...
 *
 * // Apply gain to all samples
 * gainProcessor.process(audioSamples);
 *
 * // Smoothly increase volume to 80% over multiple frames
 * gainProcessor.setGain(0.8f);
 *
 * // Process multiple frames to complete the fade
 * for (int frame = 0; frame < totalFrames; frame++) {
 *     gainProcessor.process(audioSamples);
 *     if (gainProcessor.isFinished()) {
 *         System.out.println("Fade completed");
 *         break;
 *     }
 * }
 * }</pre>
 *
 * <p><strong>Thread Safety:</strong>
 * The {@code targetGain} field is marked as volatile to ensure visibility of changes
 * across threads. This allows safe gain updates from the UI thread while audio processing
 * happens on a separate audio thread.
 *
 * @author Agustin Saunders
 * @version 1.0
 * @since 1.0
 * @see AudioProcessor
 * @see engine.FFmpegAudioEngine
 */
public class GainProcessor implements AudioProcessor {
    private static final Logger logger = LoggerFactory.getLogger(GainProcessor.class);

    /**
     * The target gain level that the processor is fading towards.
     *
     * <p>This field is marked as volatile to ensure thread-safe updates from different threads.
     * Changes to this value trigger a smooth fade transition from the current gain to this target.
     * The value is automatically clamped to the range [0.0, 1.0] by the {@link #setGain(float)} method.
     *
     * <p><strong>Typical Values:</strong>
     * <ul>
     *   <li>0.0f - Complete silence (mute)</li>
     *   <li>0.5f - 50% volume</li>
     *   <li>1.0f - Maximum volume (100%)</li>
     * </ul>
     *
     * @see #setGain(float)
     * @see #getGain()
     */
    private volatile float targetGain;

    /**
     * The current gain level being applied to samples.
     *
     * <p>This field represents the actual gain currently in use. It differs from
     * {@code targetGain} during fade transitions. After each sample is processed,
     * this value moves incrementally towards {@code targetGain} by {@code FADE_SPEED}.
     *
     * @see #currentGain
     * @see #FADE_SPEED
     */
    private float currentGain;

    /**
     * The fade transition speed in gain units per sample.
     *
     * <p>This constant controls how quickly the gain transitions from the current value
     * to the target value. A smaller value results in a longer, smoother fade, while a
     * larger value results in a quicker transition.
     *
     * <p><strong>Fade Duration Calculation:</strong>
     * <pre>
     * Fade Duration = (targetGain - currentGain) / FADE_SPEED / sample_rate
     * Example: (0.8 - 0.0) / 0.000075 / 44100 = 0.24 seconds
     * </pre>
     *
     * <p>Current value: 0.000075 = 75 microseconds per sample (at 44.1kHz)
     */
    private static final float FADE_SPEED = 0.000075f;

    /**
     * Constructs a new GainProcessor with the specified initial gain level.
     *
     * <p>Both the current gain and target gain are initialized to the specified value,
     * ensuring that the first audio samples are processed with the exact gain level requested.
     *
     * <p>The gain value is automatically clamped to the range [0.0, 1.0] if a value outside
     * this range is provided.
     *
     * @param initialGain the initial gain level for the processor. Should be in the range [0.0, 1.0].
     *                    <ul>
     *                      <li>0.0f: Complete silence (mute)</li>
     *                      <li>0.5f: 50% volume</li>
     *                      <li>1.0f: Maximum volume</li>
     *                    </ul>
     * 
     * @throws AudioProcessingException if initial gain is NaN (Not a Number)
     *
     * @see #setGain(float)
     * @see #getGain()
     */
    public GainProcessor(float initialGain) throws AudioProcessingException {
        try {
            // Validate that initialGain is a valid number
            if (Float.isNaN(initialGain)) {
                logger.error("Invalid initial gain: NaN (Not a Number)");
                throw new AudioProcessingException("Initial gain cannot be NaN (Not a Number)");
            }
            
            if (Float.isInfinite(initialGain)) {
                logger.error("Invalid initial gain: Infinity");
                throw new AudioProcessingException("Initial gain cannot be infinite");
            }
            
            // Clamp to valid range [0.0, 1.0]
            this.targetGain = Math.max(0.0f, Math.min(1.0f, initialGain));
            this.currentGain = this.targetGain;
            
            logger.debug("GainProcessor initialized with gain: {}%", this.targetGain * 100);
            
        } catch (AudioProcessingException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error initializing GainProcessor: {}", e.getMessage());
            throw new AudioProcessingException("Failed to initialize GainProcessor", e);
        }
    }

    /**
     * Sets the target gain level for smooth transition.
     *
     * <p>This method updates the target gain value and initiates a smooth fade transition
     * from the current gain to the new target gain. The gain value is automatically clamped
     * to the range [0.0, 1.0] to prevent invalid values.
     *
     * <p><strong>Fade Behavior:</strong>
     * <ul>
     *   <li>If the new target is higher than current: fade-in occurs</li>
     *   <li>If the new target is lower than current: fade-out occurs</li>
     *   <li>If the difference is very small (&lt; 1%), no log message is produced</li>
     * </ul>
     *
     * <p><strong>Examples:</strong>
     * <pre>
     * gainProcessor.setGain(0.5f);  // Fade to 50% volume
     * gainProcessor.setGain(0.0f);  // Fade to silence (mute)
     * gainProcessor.setGain(1.5f);  // Clamped to 1.0f (100%)
     * gainProcessor.setGain(-0.2f); // Clamped to 0.0f (silence)
     * </pre>
     *
     * @param gain the target gain level. Will be clamped to the range [0.0, 1.0].
     *             <ul>
     *               <li>Values &lt; 0.0 are clamped to 0.0</li>
     *               <li>Values &gt; 1.0 are clamped to 1.0</li>
     *               <li>Values in [0.0, 1.0] are used as-is</li>
     *             </ul>
     * 
     * @throws AudioProcessingException if gain value is NaN or Infinite
     *
     * @see #getGain()
     * @see #process(float[], int)
     * @see #isFinished()
     */
    public void setGain(float gain) throws AudioProcessingException {
        try {
            // Validate that gain is a valid number
            if (Float.isNaN(gain)) {
                logger.error("Invalid gain value: NaN (Not a Number)");
                throw new AudioProcessingException("Gain value cannot be NaN (Not a Number)");
            }
            
            if (Float.isInfinite(gain)) {
                logger.error("Invalid gain value: Infinity");
                throw new AudioProcessingException("Gain value cannot be infinite");
            }
            
            // Clamp to valid range [0.0, 1.0]
            this.targetGain = Math.max(0.0f, Math.min(1.0f, gain));
            
            // Only log significant changes (> 1%)
            if (Math.abs(this.targetGain - this.currentGain) > 0.01f) {
                logger.debug("Gain target set to: {}%", this.targetGain * 100);
            }
            
        } catch (AudioProcessingException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error setting gain: {}", e.getMessage());
            throw new AudioProcessingException("Failed to set gain value", e);
        }
    }

    /**
     * Gets the current target gain level.
     *
     * <p>This method returns the target gain value that was set by {@link #setGain(float)}.
     * Note that during a fade transition, this value may differ from the actual gain being
     * applied to samples (which is stored in {@code currentGain}). Use {@link #isFinished()}
     * to check if the fade transition has completed.
     *
     * @return the target gain level in the range [0.0, 1.0]
     *         <ul>
     *           <li>0.0f: Complete silence</li>
     *           <li>0.5f: 50% volume</li>
     *           <li>1.0f: Maximum volume</li>
     *         </ul>
     *
     * @see #setGain(float)
     * @see #isFinished()
     */
    public float getGain() {
        return targetGain;
    }

    /**
     * Processes a batch of audio samples with gain applied and smooth fade transitions.
     *
     * <p>This method applies the current gain level to each sample in the input array,
     * and gradually transitions the gain towards the target value. This creates smooth
     * fade-in and fade-out effects that prevent audio artifacts.
     *
     * <p><strong>Processing Steps:</strong>
     * <ol>
     *   <li>For each sample in the array:</li>
     *   <li>Update currentGain towards targetGain by FADE_SPEED</li>
     *   <li>Multiply the sample by the updated currentGain</li>
     *   <li>Store the modified sample back in the array</li>
     * </ol>
     *
     * <p><strong>Fade Transition Logic:</strong>
     * <pre>{@code
     * if (currentGain < targetGain) {
     *     currentGain = Math.min(targetGain, currentGain + FADE_SPEED);
     * } else if (currentGain > targetGain) {
     *     currentGain = Math.max(targetGain, currentGain - FADE_SPEED);
     * }
     * samples[i] *= currentGain;
     * }</pre>
     *
     * <p><strong>Important Notes:</strong>
     * <ul>
     *   <li>Samples are modified in-place (no separate output array)</li>
     *   <li>The fade transition can span multiple process() calls</li>
     *   <li>Use {@link #isFinished()} to detect when fade completes</li>
     *   <li>Typically called once per audio frame (~1024 samples)</li>
     * </ul>
     *
     * <p><strong>Usage Example:</strong>
     * <pre>{@code
     * float[] audioSamples = new float[1024];
     * // ... fill audioSamples ...
     *
     * gainProcessor.process(audioSamples, 1024);
     *
     * // audioSamples now contains gain-adjusted values
     * }</pre>
     *
     * @param samples      array of audio samples to process. Values should be in the range [-1.0, 1.0].
     *                     Samples are modified in-place.
     * @param sampleCount  the number of samples to process from the array. Must be &lt;= samples.length
     *
     * @throws AudioProcessingException if samples array is null, sampleCount is invalid,
     *                                  or array size doesn't match sampleCount
     * @throws ArrayIndexOutOfBoundsException if sampleCount is greater than samples.length
     *
     * @see #process(float[])
     * @see #setGain(float)
     * @see #isFinished()
     */
    public void process(float[] samples, int sampleCount) throws AudioProcessingException {
        try {
            // Validate input array
            if (samples == null) {
                logger.error("Audio samples array is null");
                throw new AudioProcessingException("Audio samples array cannot be null");
            }
            
            // Validate sample count
            if (sampleCount < 0) {
                logger.error("Invalid sample count: {} (must be >= 0)", sampleCount);
                throw new AudioProcessingException("Sample count cannot be negative: " + sampleCount);
            }
            
            // Validate array bounds
            if (sampleCount > samples.length) {
                logger.error("Sample count {} exceeds array length {}", sampleCount, samples.length);
                throw new AudioProcessingException(
                    String.format("Sample count (%d) exceeds array length (%d)", sampleCount, samples.length)
                );
            }
            
            // Process samples with gain
            for (int i = 0; i < sampleCount; i++) {
                // Validate individual sample value
                if (Float.isNaN(samples[i])) {
                    logger.warn("NaN sample detected at index {}, skipping", i);
                    continue;
                }
                
                if (Float.isInfinite(samples[i])) {
                    logger.warn("Infinite sample detected at index {}, clamping to [-1.0, 1.0]", i);
                    samples[i] = samples[i] > 0 ? 1.0f : -1.0f;
                }
                
                // Update gain towards target
                if (currentGain < targetGain) {
                    currentGain = Math.min(targetGain, currentGain + FADE_SPEED);
                } else if (currentGain > targetGain) {
                    currentGain = Math.max(targetGain, currentGain - FADE_SPEED);
                }
                
                // Apply gain to sample
                samples[i] *= currentGain;
                
                // Ensure output stays in valid range
                samples[i] = Math.max(-1.0f, Math.min(1.0f, samples[i]));
            }
            
            logger.trace("Processed {} samples with gain", sampleCount);
            
        } catch (AudioProcessingException e) {
            throw e;
        } catch (ArrayIndexOutOfBoundsException e) {
            logger.error("Array index out of bounds while processing: {}", e.getMessage());
            throw new AudioProcessingException("Array size mismatch during gain processing", e);
        } catch (Exception e) {
            logger.error("Unexpected error processing samples: {}", e.getMessage());
            throw new AudioProcessingException("Failed to process audio samples", e);
        }
    }

    /**
     * Processes all samples in the provided array with gain applied.
     *
     * <p>This is a convenience method that processes the entire array by delegating
     * to {@link #process(float[], int)} with the array length.
     *
     * <p><strong>Equivalent To:</strong>
     * <pre>{@code
     * process(samples, samples.length);
     * }</pre>
     *
     * @param samples array of audio samples to process. All elements will be processed.
     *                Samples are modified in-place.
     *
     * @throws AudioProcessingException if samples array is null or processing fails
     * @throws NullPointerException if samples array is null
     *
     * @see #process(float[], int)
     * @see AudioProcessor#process(float[])
     */
    @Override
    public void process(float[] samples) throws AudioProcessingException {
        try {
            if (samples == null) {
                logger.error("Audio samples array is null");
                throw new AudioProcessingException("Audio samples array cannot be null");
            }
            
            process(samples, samples.length);
            
        } catch (AudioProcessingException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error in process(float[]): {}", e.getMessage());
            throw new AudioProcessingException("Failed to process audio samples", e);
        }
    }

    /**
     * Checks whether the gain fade transition has completed.
     *
     * <p>This method detects when the current gain has reached (or nearly reached) the
     * target gain value. It uses a small tolerance threshold (0.0001f) to account for
     * floating-point precision limitations.
     *
     * <p><strong>Tolerance Threshold:</strong>
     * The fade is considered finished when the absolute difference between current and
     * target gain is less than 0.0001 (0.01%). This prevents unnecessary processing
     * when the gain values are effectively equal.
     *
     * <p><strong>Return Behavior:</strong>
     * <ul>
     *   <li>Returns {@code true}: When currentGain ≈ targetGain (fade complete)</li>
     *   <li>Returns {@code false}: When currentGain is still transitioning (fade in progress)</li>
     * </ul>
     *
     * <p><strong>Usage Example:</strong>
     * <pre>{@code
     * gainProcessor.setGain(0.8f); // Change to 80% volume
     *
     * while (!gainProcessor.isFinished()) {
     *     gainProcessor.process(audioSamples);
     *     // Fade is still in progress
     * }
     *
     * System.out.println("Fade completed!");
     * }</pre>
     *
     * @return {@code true} if the fade transition is complete or not needed, {@code false} if
     *         the current gain is still transitioning towards the target gain
     *
     * @see #setGain(float)
     * @see #process(float[], int)
     */
    public boolean isFinished() {
        return Math.abs(currentGain - targetGain) < 0.0001f;
    }
}
