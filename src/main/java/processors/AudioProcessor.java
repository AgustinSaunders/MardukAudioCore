package processors;

import exceptions.AudioProcessingException;
import utils.AudioUtils;

/**
 * Interface defining the contract for audio signal processors.
 *
 * This interface establishes a standard abstraction for audio processing components that
 * manipulate audio samples in real-time. Any audio processor implementation (gain control,
 * equalization, reverb, compression, etc.) must implement this interface to integrate
 * into the Marduk Audio Core processing pipeline.
 *
 * <p><strong>Core Concept:</strong>
 * An AudioProcessor takes a block of audio samples, applies a specific audio transformation,
 * and returns the modified samples. Samples are represented as normalized 32-bit floating-point
 * values in the range [-1.0, 1.0], where:
 * <ul>
 *   <li><b>-1.0:</b> Maximum negative amplitude</li>
 *   <li><b>0.0:</b> Silence</li>
 *   <li><b>1.0:</b> Maximum positive amplitude</li>
 * </ul>
 *
 * <p><strong>Supported Audio Processors:</strong>
 * <ul>
 *   <li>{@link GainProcessor} - Volume control with smooth fade transitions</li>
 *   <li>EqualizerProcessor - Frequency-based tone shaping (future implementation)</li>
 *   <li>CompressorProcessor - Dynamic range compression (future implementation)</li>
 *   <li>ReverbProcessor - Spatial audio effects (future implementation)</li>
 *   <li>NoiseGateProcessor - Noise reduction (future implementation)</li>
 *   <li>Custom processors - User-defined audio processing chains</li>
 * </ul>
 *
 * <p><strong>Processing Pipeline Architecture:</strong>
 * <pre>
 * Audio File
 *     ↓
 * FFmpeg Decoder (float 32-bit samples)
 *     ↓
 * AudioProcessor Chain:
 *     ├─ GainProcessor (volume control)
 *     ├─ EqualizerProcessor (tone shaping)
 *     ├─ CompressorProcessor (dynamic range)
 *     └─ CustomProcessor (user-defined effects)
 *     ↓
 * AudioUtils.floatsToBytes (convert to 16-bit PCM)
 *     ↓
 * SourceDataLine (system audio output)
 * </pre>
 *
 * <p><strong>Implementation Requirements:</strong>
 * <ul>
 *   <li>Samples must be processed in-place (modifications directly to the input array)</li>
 *   <li>Output samples must remain within [-1.0, 1.0] range (implement clamping if needed)</li>
 *   <li>Processors should be real-time safe and low-latency</li>
 *   <li>Thread-safe implementation is recommended for concurrent access</li>
 *   <li>No blocking operations should be performed during processing</li>
 * </ul>
 *
 * <p><strong>Performance Considerations:</strong>
 * <ul>
 *   <li>Typical block size: 1024-4096 samples per process() call</li>
 *   <li>Sample rate: 44.1 kHz (typical), 48 kHz (professional audio)</li>
 *   <li>Call frequency: ~44-21 times per second depending on block size</li>
 *   <li>Processing must complete within milliseconds to maintain real-time performance</li>
 * </ul>
 *
 * <p><strong>Usage Example - Single Processor:</strong>
 * <pre>{@code
 * // Create a gain processor
 * AudioProcessor gainProcessor = new GainProcessor(0.8f);
 *
 * // Process audio samples
 * float[] audioSamples = new float[1024];
 * // ... fill audioSamples with decoded audio data ...
 *
 * // Apply the processor
 * gainProcessor.process(audioSamples);
 *
 * // audioSamples now contains processed audio
 * }</pre>
 *
 * <p><strong>Usage Example - Processing Chain:</strong>
 * <pre>{@code
 * // Create multiple processors
 * List<AudioProcessor> processingChain = new ArrayList<>();
 * processingChain.add(new GainProcessor(0.8f));           // Volume control
 * processingChain.add(new EqualizerProcessor());          // Tone shaping
 * processingChain.add(new CompressorProcessor(4.0f));    // Dynamic range control
 *
 * // Apply all processors in sequence
 * float[] audioSamples = new float[1024];
 * // ... fill audioSamples ...
 *
 * for (AudioProcessor processor : processingChain) {
 *     processor.process(audioSamples);
 * }
 *
 * // audioSamples now contains audio processed by all filters
 * }</pre>
 *
 * <p><strong>Audio Sample Format:</strong>
 * <pre>
 * Sample Type:        32-bit IEEE floating-point (float)
 * Sample Range:       -1.0 to 1.0 (normalized)
 * Channels:           2 (stereo, interleaved: LRLRLR...)
 * Sample Rate:        44100 Hz (or other standard rates)
 * Encoding:           Linear PCM (uncompressed)
 *
 * Example array for 1 sample (stereo):
 * samples[0] = left channel sample
 * samples[1] = right channel sample
 *
 * Example array for 2 samples (stereo):
 * samples[0] = left channel sample 1
 * samples[1] = right channel sample 1
 * samples[2] = left channel sample 2
 * samples[3] = right channel sample 2
 * </pre>
 *
 * <p><strong>Common Implementation Pattern:</strong>
 * <pre>{@code
 * public class CustomAudioProcessor implements AudioProcessor {
 *
 *     @Override
 *     public void process(float[] samples) {
 *         for (int i = 0; i < samples.length; i++) {
 *             // Apply transformation to each sample
 *             samples[i] = applySomeEffect(samples[i]);
 *
 *             // Ensure output stays within valid range
 *             samples[i] = Math.max(-1.0f, Math.min(1.0f, samples[i]));
 *         }
 *     }
 *
 *     private float applySomeEffect(float sample) {
 *         // Your processing logic here
 *         return sample * someMultiplier;
 *     }
 * }
 * }</pre>
 *
 * <p><strong>Best Practices for Implementation:</strong>
 * <ul>
 *   <li><b>In-Place Processing:</b> Modify samples directly to save memory and bandwidth</li>
 *   <li><b>Bounds Checking:</b> Use Math.max() and Math.min() to clamp values to [-1.0, 1.0]</li>
 *   <li><b>Avoid Allocation:</b> Don't allocate new arrays during processing</li>
 *   <li><b>Exception Handling:</b> Catch exceptions to prevent playback interruption</li>
 *   <li><b>Logging:</b> Log configuration changes but not per-sample operations</li>
 *   <li><b>Thread Safety:</b> Use synchronized access for shared state if needed</li>
 * </ul>
 *
 * @author Agustin Saunders
 * @version 1.0
 * @since 1.0
 * @see GainProcessor
 * @see engine.FFmpegAudioEngine
 */
public interface AudioProcessor {
    /**
     * Processes a block of audio samples.
     *
     * <p>This method performs audio processing on a block of samples. The samples
     * represent stereo audio in interleaved format at a standard sample rate (typically 44.1 kHz).
     * Processing is done in-place, meaning the input array is directly modified.
     *
     * <p><strong>Sample Format:</strong>
     * <ul>
     *   <li><b>Type:</b> 32-bit IEEE floating-point (float)</li>
     *   <li><b>Range:</b> -1.0 to 1.0 (normalized)</li>
     *   <li><b>Channels:</b> 2 (stereo, interleaved)</li>
     *   <li><b>Interleaving:</b> [L0, R0, L1, R1, L2, R2, ...]</li>
     * </ul>
     *
     * <p>Where L = left channel sample and R = right channel sample.
     *
     * <p><strong>Processing Constraints:</strong>
     * <ul>
     *   <li>Samples must be processed in-place (no separate output array)</li>
     *   <li>Modified samples must remain within [-1.0, 1.0] range</li>
     *   <li>Out-of-range samples should be clamped, not clipped</li>
     *   <li>Processing must complete quickly (real-time safe)</li>
     *   <li>No blocking I/O or thread synchronization during processing</li>
     * </ul>
     *
     * <p><strong>Typical Block Sizes:</strong>
     * <pre>
     * Block Size      | Duration at 44.1 kHz | Use Case
     * ─────────────────────────────────────────────────────
     * 512 samples     | 11.6 ms              | Low-latency interactive
     * 1024 samples    | 23.2 ms              | Standard real-time
     * 2048 samples    | 46.4 ms              | High-performance batch
     * 4096 samples    | 92.8 ms              | Offline processing
     * </pre>
     *
     * <p><strong>Implementation Example - Gain Processor:</strong>
     * <pre>{@code
     * @Override
     * public void process(float[] samples) {
     *     for (int i = 0; i < samples.length; i++) {
     *         samples[i] *= gain;  // Apply gain multiplier
     *         // Clamp to prevent clipping
     *         samples[i] = Math.max(-1.0f, Math.min(1.0f, samples[i]));
     *     }
     * }
     * }</pre>
     *
     * <p><strong>Implementation Example - Equalizer Processor:</strong>
     * <pre>{@code
     * @Override
     * public void process(float[] samples) {
     *     for (int i = 0; i < samples.length; i++) {
     *         // Apply filter calculations
     *         samples[i] = applyFilterChain(samples[i]);
     *         // Clamp output
     *         samples[i] = Math.max(-1.0f, Math.min(1.0f, samples[i]));
     *     }
     * }
     * }</pre>
     *
     * <p><strong>Error Handling:</strong>
     * If an error occurs during processing:
     * <ul>
     *   <li>Log the error for debugging</li>
     *   <li>Return samples unchanged or in a safe state</li>
     *   <li>Do not propagate exceptions that would stop playback</li>
     *   <li>Gracefully degrade if possible (e.g., bypass processing)</li>
     * </ul>
     *
     * <p><strong>Performance Tips:</strong>
     * <ul>
     *   <li>Use primitive loops instead of iterators for better performance</li>
     *   <li>Minimize floating-point operations; use lookup tables if needed</li>
     *   <li>Avoid method calls in the inner loop</li>
     *   <li>Cache frequently accessed values</li>
     *   <li>Consider vectorization for batch operations</li>
     * </ul>
     *
     * @param samples array of audio samples in float format.
     *                Range: -1.0 to 1.0 (normalized PCM)
     *                Format: Stereo interleaved [L0, R0, L1, R1, ...]
     *                Length: Typically 1024-4096 samples per block
     *
     *                Samples are modified in-place by this method.
     *                The array is expected to contain valid floating-point numbers.
     *
     * @throws NullPointerException if the samples array is null
     *                              (implementation may or may not handle this)
     *
     * @see GainProcessor#process(float[])
     * @see AudioUtils#floatsToBytes(float[], byte[], int, int, boolean)
     */
    void process(float[] samples) throws AudioProcessingException;
}