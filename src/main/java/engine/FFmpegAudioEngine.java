package engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import processors.GainProcessor;
import utils.AudioUtils;
import utils.FFmpegAudioDecoder;
import exceptions.*;

import javax.sound.sampled.*;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * FFmpeg-based audio playback engine with advanced audio processing capabilities.
 *
 * This class provides a high-performance audio playback engine that leverages FFmpeg for decoding
 * multiple audio formats (MP3, WAV, FLAC, OGG, etc.) and supports playback control features such as
 * play, pause, resume, stop, seeking, and volume adjustment.
 *
 * <p><strong>Key Features:</strong>
 * <ul>
 *   <li>Multi-format audio decoding using FFmpeg</li>
 *   <li>Real-time audio processing with gain control</li>
 *   <li>Playback control: play, pause, resume, stop</li>
 *   <li>Precision seeking within audio files</li>
 *   <li>Thread-safe operations with ReentrantLock</li>
 *   <li>Low-latency audio output (32KB buffer)</li>
 *   <li>16-bit PCM stereo output at 44.1kHz</li>
 * </ul>
 *
 * <p><strong>Audio Processing Pipeline:</strong>
 * <pre>
 * FFmpeg Decoder (float 32-bit)
 *     ↓
 * GainProcessor (volume control)
 *     ↓
 * AudioUtils.floatsToBytes (16-bit PCM conversion)
 *     ↓
 * SourceDataLine (system audio output)
 * </pre>
 *
 * <p><strong>Thread Safety:</strong>
 * The engine uses a {@link ReentrantLock} to protect critical sections where the decoder
 * is accessed. The playback runs on a separate high-priority thread ({@code AudioPlaybackThread}).
 *
 * <p><strong>Usage Example:</strong>
 * <pre>{@code
 * GainProcessor gainProcessor = new GainProcessor();
 * FFmpegAudioEngine engine = new FFmpegAudioEngine(gainProcessor, "audio.mp3");
 *
 * try {
 *     engine.play();
 *     Thread.sleep(5000); // Play for 5 seconds
 *     engine.pause();
 *     Thread.sleep(2000);
 *     engine.resume();
 *     engine.seek(30.0); // Seek to 30 seconds
 *     engine.setVolume(0.8f); // 80% volume
 * } finally {
 *     engine.close();
 * }
 * }</pre>
 *
 * @author Agustin Saunders
 * @version 1.0
 * @since 1.0
 * @see FormatAudioEngine
 * @see utils.FFmpegAudioDecoder
 * @see processors.GainProcessor
 */
public class FFmpegAudioEngine implements FormatAudioEngine {
    private static final Logger logger = LoggerFactory.getLogger(FFmpegAudioEngine.class);

    private final ReentrantLock decoderLock = new ReentrantLock();
    private FFmpegAudioDecoder decoder;
    private SourceDataLine outputLine;
    private GainProcessor gainProcessor;
    private String filePath;
    private AtomicBoolean isPlaying = new AtomicBoolean(false);
    private AtomicBoolean isPaused = new AtomicBoolean(false);
    private Thread playbackThread;

    // Floats buffer for processing (32-bit Float Stereo)
    private static final int SAMPLES_PER_READ = 1024;           // 1024 muestras de audio
    private static final int CHANNELS = 2;                       // Estéreo
    private static final int BIT_DEPTH_OUTPUT = 16;
    private static final int BYTES_PER_SAMPLE = 2;              // 16 bits = 2 bytes
    private static final int BUFFER_SIZE_FLOAT = SAMPLES_PER_READ * CHANNELS; // 2048 floats

    /** Buffer for storing decoded float samples (32-bit). Capacity: 2048 samples (stereo). */
    float[] floatBuffer = new float[BUFFER_SIZE_FLOAT];

    /** Buffer for storing PCM bytes after conversion. Capacity: 4096 bytes (16-bit stereo). */
    byte[] byteBuffer = new byte[BUFFER_SIZE_FLOAT * BYTES_PER_SAMPLE]; // 4096 bytes MAX

    /**
     * Constructs a new FFmpegAudioEngine with the specified gain processor and file path.
     *
     * @param gainProcessor the {@link GainProcessor} to be used for volume control
     * @param filePath      the absolute or relative path to the audio file to be played
     * @throws NullPointerException if gainProcessor or filePath is null
     */
    public FFmpegAudioEngine(GainProcessor gainProcessor, String filePath) {
        this.gainProcessor = gainProcessor;
        this.filePath = filePath;
        logger.debug("FFmpegAudioEngine initialized for file: {}", filePath);
    }

    /**
     * Starts audio playback from the beginning of the file.
     *
     * <p>This method performs the following operations:
     * <ul>
     *   <li>Stops any existing playback</li>
     *   <li>Creates and opens a new FFmpeg decoder</li>
     *   <li>Initializes the audio output line (SourceDataLine)</li>
     *   <li>Starts a high-priority playback thread</li>
     * </ul>
     *
     * <p>The playback runs asynchronously on a separate thread. Audio data is continuously
     * decoded, processed for gain, converted to PCM bytes, and written to the system audio output.
     *
     * @throws AudioFileException if the audio file cannot be opened or accessed
     * @throws AudioDecodingException if audio decoding cannot be initialized
     * @throws AudioPlaybackException if the playback system fails to initialize
     * @throws AudioException for other audio-related errors
     * @see #pause()
     * @see #resume()
     * @see #stop()
     */
    @Override
    public void play() throws AudioException {

        logger.info("Start playing: {}", filePath);
        if (isPlaying.get()) {
            logger.debug("Playback already active, stopping...");
            internalStop();
        }

        try {
            decoder = new FFmpegAudioDecoder();
            
            try {
                decoder.open(filePath);
            } catch (Exception e) {
                logger.error("Failed to open audio file: {}", e.getMessage());
                throw new AudioFileException("Failed to open audio file: " + filePath, e);
            }

            // Output compatible: 16 bits, Signed Integer (2 bytes per frame per channel = 4 bytes/frame)
            AudioFormat format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 44100, 16, 2, 4, 44100, false);
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);

            try {
                this.outputLine = (SourceDataLine) AudioSystem.getLine(info);
                if (outputLine == null) {
                    throw new AudioPlaybackException("No audio output line available");
                }
                // Using small buffer for low latency (32KB approx)
                this.outputLine.open(format, 32768);
                this.outputLine.start();
            } catch (LineUnavailableException e) {
                logger.error("Audio output line not available: {}", e.getMessage());
                throw new AudioPlaybackException("Audio device not available or already in use", e);
            }

            logger.debug("Audio line open and started");

            isPlaying.set(true);
            isPaused.set(false);

            playbackThread = new Thread(this::playbackLoop);
            playbackThread.setPriority(Thread.MAX_PRIORITY);
            playbackThread.setName("AudioPlaybackThread");
            playbackThread.start();

            logger.info("AudioPlaybackThread started");
            
        } catch (AudioException e) {
            // Re-throw audio exceptions as-is
            internalStop();
            throw e;
        } catch (Exception e) {
            // Wrap unexpected exceptions
            logger.error("Unexpected error during playback initialization: {}", e.getMessage());
            internalStop();
            throw new AudioPlaybackException("Failed to initialize playback", e);
        }
    }

    /**
     * Pauses the current audio playback.
     *
     * <p>The audio output line is stopped, but the playback position is maintained.
     * Call {@link #resume()} to continue playback from the pause point.
     *
     * <p>This method has no effect if playback is not active or already paused.
     *
     * @see #resume()
     * @see #stop()
     */
    @Override
    public void pause() {
        if (isPlaying.get() && !isPaused.get()) {
            logger.info("Pausing playback");
            isPaused.set(true);
            if (outputLine != null) outputLine.stop();
        }
    }

    /**
     * Resumes audio playback from the current pause point.
     *
     * <p>This method restarts the audio output line to continue playing from where
     * playback was paused.
     *
     * <p>This method has no effect if playback is not active or not paused.
     *
     * @see #pause()
     * @see #play()
     */
    @Override
    public void resume() {
        if (isPlaying.get() && isPaused.get()) {
            logger.info("Continue playing");
            isPaused.set(false);
            if (outputLine != null) outputLine.start();
        }
    }

    /**
     * Stops the current audio playback completely.
     *
     * <p>This method performs the following operations:
     * <ul>
     *   <li>Sets gain to 0 to silence audio</li>
     *   <li>Sleeps for 50ms to allow sound to fade</li>
     *   <li>Calls {@link #internalStop()} to release resources</li>
     * </ul>
     *
     * <p>The playback state is reset and the audio position is reset to the beginning.
     * After calling this method, call {@link #play()} to start playback again from the beginning.
     *
     * @see #pause()
     * @see #play()
     */
    @Override
    public void stop() throws AudioProcessingException {
        logger.info("Stoping playback");
        if (!isPlaying.get()) return;

        gainProcessor.setGain(0.0f);
        isPlaying.set(false);

        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        internalStop();
    }

    /**
     * Internal method that releases all audio resources and stops the playback thread.
     *
     * <p>This method is called by {@link #stop()} and the playback loop's finally block
     * to ensure proper cleanup of:
     * <ul>
     *   <li>SourceDataLine (drain, stop, close)</li>
     *   <li>FFmpeg decoder</li>
     *   <li>Audio buffers</li>
     * </ul>
     *
     * <p>If the decoder or output line are already null, this method returns early.
     *
     * @see #stop()
     */
    private void internalStop() {
        logger.debug("Executing internalStop");
        if (!isPlaying.get() && !isPaused.get() && decoder == null) return;

        SourceDataLine line = this.outputLine;

        this.outputLine = null;

        isPlaying.set(false);
        isPaused.set(false);

        if (line != null) {
            try {
                line.drain();
                line.stop();
                line.close();
                logger.debug("Outputline closed successfully");
            } catch (Exception e) {
                logger.error("Error closing outputLine: {}", e.getMessage());
            }
        }

        if (decoder != null) {
            try {
                decoder.close();
                logger.debug("Decoder closed");
            } catch (Exception e) {
                logger.error("Error closing decoder: {}", e.getMessage());
                e.printStackTrace();
            }
            decoder = null;
        }
    }

    /**
     * Main playback loop running on a separate high-priority thread.
     *
     * <p>This method continuously:
     * <ul>
     *   <li>Waits if playback is paused</li>
     *   <li>Reads decoded audio samples from the FFmpeg decoder</li>
     *   <li>Applies gain processing (volume control)</li>
     *   <li>Converts 32-bit float samples to 16-bit PCM bytes</li>
     *   <li>Writes audio data to the system audio output line</li>
     * </ul>
     *
     * <p>The loop terminates when end-of-file is reached or playback is stopped.
     * Any exceptions during playback are caught, logged, and handled gracefully.
     *
     * @see #play()
     * @see #pause()
     * @see #resume()
     */
    private void playbackLoop() {
        logger.debug("Playback loop initialized");
        try {
            int samplesRead;
            int totalSamplesPlayed = 0;

            while (isPlaying.get()) {
                while (isPaused.get() && isPlaying.get()) {
                    Thread.sleep(10);
                }

                if (!isPlaying.get()) break;

                decoderLock.lock();
                try {
                    if (!isPlaying.get()) break;
                    samplesRead = decoder.readNextSamples(floatBuffer);
                } finally {
                    decoderLock.unlock();
                }

                if (samplesRead == -1) {
                    logger.debug("End of file reached");
                    break;}
                if (samplesRead == 0) continue;

                gainProcessor.process(floatBuffer, samplesRead);

                // Convert to 16 bits PCM (signed) for output
                AudioUtils.floatsToBytes(floatBuffer, byteBuffer, samplesRead, BIT_DEPTH_OUTPUT, false);

                int bytesToWrite = samplesRead * BYTES_PER_SAMPLE;
                outputLine.write(byteBuffer, 0, bytesToWrite);

                totalSamplesPlayed += samplesRead;
            }

            logger.info("Playback ended. Total samples: {}", totalSamplesPlayed);
            stop();
        } catch (Exception e) {
            logger.error("Error on playback loop: {}", e.getMessage());
            e.printStackTrace();
        } finally {
            internalStop();
        }
    }

    /**
     * Sets the audio volume/gain for playback.
     *
     * @param volume the volume level as a float between 0.0f (silent) and 1.0f (maximum).
     *               Values outside this range are clamped by the gain processor.
     * @see #getVolume()
     */
    @Override
    public void setVolume(float volume) throws AudioProcessingException {
        if (gainProcessor != null) {
            gainProcessor.setGain(volume);
            logger.debug("Volume set at: {}%", volume * 100);
        }
    }

    /**
     * Gets the current audio volume/gain level.
     *
     * @return the current volume as a float between 0.0f and 1.0f, or 0.0f if the gain processor is null
     * @see #setVolume(float)
     */
    @Override
    public float getVolume() {
        return gainProcessor != null ? gainProcessor.getGain() : 0.0f;
    }

    /**
     * Checks if audio is currently playing.
     *
     * <p>This method returns true if playback is active, regardless of whether
     * the playback is paused or not.
     *
     * @return true if playback is active, false otherwise
     * @see #play()
     * @see #stop()
     */
    @Override
    public boolean isPlaying() { return isPlaying.get(); }

    /**
     * Checks if audio playback is currently paused.
     *
     * @return true if playback is paused, false otherwise
     * @see #pause()
     * @see #resume()
     */
    @Override
    public boolean isPaused() { return isPaused.get(); }

    /**
     * Seeks to a specific position in the audio file.
     *
     * <p>This method performs the following operations:
     * <ul>
     *   <li>Temporarily pauses playback</li>
     *   <li>Flushes the output buffer</li>
     *   <li>Calls seek on the decoder (thread-safe with lock)</li>
     *   <li>Clears the float buffer to prevent audio artifacts</li>
     *   <li>Resumes playback from the new position</li>
     * </ul>
     *
     * @param seconds the seek position in seconds from the start of the file
     * @throws AudioSeekException if seek fails or decoder is not initialized
     * @see #getDuration()
     * @see FFmpegAudioDecoder#seek(double)
     */
    @Override
    public void seek(double seconds) throws AudioSeekException {
        logger.info("Seek called to: {} seconds", seconds);
        
        if (decoder == null || outputLine == null) {
            logger.error("Cannot seek: decoder or outputLine is null");
            throw new AudioSeekException("Cannot seek: audio engine not properly initialized");
        }

        if (seconds < 0) {
            logger.error("Invalid seek position: {} (must be >= 0)", seconds);
            throw new AudioSeekException("Seek position cannot be negative: " + seconds);
        }

        try {
            isPaused.set(true);

            outputLine.stop();
            outputLine.flush();

            decoderLock.lock();
            try {
                decoder.seek(seconds);
            } finally {
                decoderLock.unlock();
            }

            Arrays.fill(floatBuffer, 0);

            isPaused.set(false);
            outputLine.start();

            logger.info("Seek completed successfully");

        } catch (AudioSeekException e) {
            // Re-throw audio seek exceptions
            isPaused.set(false);
            throw e;
        } catch (Exception e) {
            logger.error("Error during seek to {} seconds: {}", seconds, e.getMessage());
            isPaused.set(false);
            throw new AudioSeekException("Seek operation failed for position " + seconds + " seconds", e);
        }
    }

    /**
     * Gets the total duration of the audio file in seconds.
     *
     * @return the duration in seconds, or 0.0 if the decoder is not initialized
     * @see #seek(double)
     */
    @Override
    public double getDuration() {
        return (decoder != null) ? decoder.getDuration() : 0.0;
    }

    /**
     * Closes the audio engine and releases all resources.
     *
     * <p>This method performs the following cleanup operations:
     * <ul>
     *   <li>Stops audio playback via {@link #internalStop()}</li>
     *   <li>Interrupts the playback thread</li>
     *   <li>Waits up to 500ms for the playback thread to terminate</li>
     * </ul>
     *
     * <p>After calling this method, the engine should not be used. A new instance
     * should be created to play another audio file.
     *
     * @throws AudioResourceException if an error occurs during resource cleanup
     * @see #play()
     * @see #stop()
     */
    @Override
    public void close() throws AudioResourceException {
        logger.debug("Closing FFmpegAudioEngine");
        
        try {
            internalStop();
            
            if (playbackThread != null && playbackThread.isAlive()) {
                playbackThread.interrupt();
                try {
                    playbackThread.join(500);
                    if (playbackThread.isAlive()) {
                        logger.warn("Playback thread did not terminate within timeout");
                    }
                } catch (InterruptedException e) {
                    logger.warn("Interrupted while waiting for playback thread to terminate");
                    Thread.currentThread().interrupt();
                    throw new AudioResourceException("Thread interruption during close", e);
                }
            }
            
            logger.info("FFmpegAudioEngine closed successfully");
            
        } catch (AudioResourceException e) {
            // Re-throw audio resource exceptions
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error during close: {}", e.getMessage());
            throw new AudioResourceException("Failed to properly close audio engine resources", e);
        }
    }
}