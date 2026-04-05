package engine;

import exceptions.*;

/**
 * Interface defining the contract for audio format engines.
 *
 * This interface specifies the standard operations and controls that any audio playback engine
 * must implement. It provides a common abstraction for different audio format implementations,
 * allowing interchangeable audio engines regardless of the underlying codec or format used.
 *
 * <p><strong>Core Responsibilities:</strong>
 * <ul>
 *   <li>Manage the complete audio playback lifecycle (play, pause, resume, stop)</li>
 *   <li>Control audio volume/gain in real-time</li>
 *   <li>Navigate through audio files (seeking)</li>
 *   <li>Report playback state and file information</li>
 *   <li>Release audio resources properly (AutoCloseable)</li>
 * </ul>
 *
 * <p><strong>Playback State Machine:</strong>
 * <pre>
 *                    ┌────────────────┐
 *                    │   STOPPED      │ (initial state)
 *                    └────────┬───────┘
 *                             │ play()
 *                             ↓
 *                    ┌────────────────┐
 *                ┌───│   PLAYING      │◄────────┐
 *                │   └────────┬───────┘         │
 *                │            │ pause()      resume()
 *                │            ↓                 │
 *                │   ┌────────────────┐         │
 *                │   │   PAUSED       │─────────┘
 *                │   └────────────────┘
 *                │
 *                │ stop() [from any state]
 *                │
 *                ↓
 *        ┌────────────────┐
 *        │   STOPPED      │
 *        └────────────────┘
 * </pre>
 *
 * <p><strong>Typical Usage Flow:</strong>
 * <pre>{@code
 * try (FormatAudioEngine engine = new FFmpegAudioEngine(gainProcessor, "song.mp3")) {
 *     // Start playback
 *     engine.play();
 *
 *     // Adjust volume while playing
 *     engine.setVolume(0.8f);  // 80% volume
 *
 *     // Pause and resume
 *     engine.pause();
 *     Thread.sleep(2000);
 *     engine.resume();
 *
 *     // Jump to a specific position
 *     engine.seek(30.0);  // Seek to 30 seconds
 *
 *     // Query playback state
 *     if (engine.isPlaying() && !engine.isPaused()) {
 *         System.out.println("Audio is playing");
 *     }
 *
 * } catch (Exception e) {
 *     System.err.println("Playback error: " + e.getMessage());
 * }
 * // Resources are automatically closed
 * }</pre>
 *
 * <p><strong>Implementations:</strong>
 * <ul>
 *   <li>{@link FFmpegAudioEngine} - Primary implementation using FFmpeg for multi-format support</li>
 * </ul>
 *
 * <p><strong>Thread Safety:</strong>
 * Implementations of this interface should ensure thread-safe operations, particularly
 * for seeking and volume control, which may be called from the UI thread while audio
 * playback occurs on a separate audio thread.
 *
 * @author Agustin Saunders
 * @version 1.0
 * @since 1.0
 * @see FFmpegAudioEngine
 * @see AutoCloseable
 */
public interface FormatAudioEngine extends AutoCloseable {

    /**
     * Starts audio playback from the beginning of the file.
     *
     * <p>This method initializes the audio engine and begins playing the audio file.
     * If playback is already active, the implementation may stop the current playback
     * before starting anew, or throw an exception depending on the implementation.
     *
     * <p><strong>State Transition:</strong>
     * STOPPED → PLAYING (or PLAYING → STOPPED → PLAYING if already playing)
     *
     * <p><strong>Async Behavior:</strong>
     * This method typically returns immediately and starts playback on a separate thread,
     * allowing the calling thread to continue execution.
     *
     * <p><strong>Exception Handling:</strong>
     * Various exceptions may be thrown if:
     * <ul>
     *   <li>The audio file cannot be found or opened</li>
     *   <li>The audio format is not supported</li>
     *   <li>The audio device is not available</li>
     *   <li>Insufficient system resources are available</li>
     * </ul>
     *
     * @throws AudioFileException if the audio file cannot be loaded or accessed
     * @throws AudioPlaybackException if the playback system fails to initialize
     * @throws AudioDecodingException if audio decoding cannot be set up
     * @throws AudioException for other audio-related errors
     *
     * @see #pause()
     * @see #resume()
     * @see #stop()
     * @see #isPlaying()
     */
    void play() throws AudioException;

    /**
     * Pauses the current audio playback.
     *
     * <p>This method temporarily stops audio playback while maintaining the current
     * playback position. The playback can be resumed from the pause point using
     * {@link #resume()}. Pausing has no effect if playback is not currently active.
     *
     * <p><strong>State Transition:</strong>
     * PLAYING → PAUSED (no change if already paused or stopped)
     *
     * <p><strong>Audio Behavior:</strong>
     * <ul>
     *   <li>Audio output is stopped</li>
     *   <li>Playback position is preserved</li>
     *   <li>Internal state is maintained for resuming</li>
     * </ul>
     *
     * <p><strong>Note:</strong>
     * This method does not throw exceptions and returns immediately.
     *
     * @see #resume()
     * @see #play()
     * @see #isPaused()
     */
    void pause();

    /**
     * Resumes audio playback from the current pause point.
     *
     * <p>This method continues audio playback from where it was paused. Resume has
     * no effect if the audio is not paused. If playback is stopped, use {@link #play()}
     * instead to start from the beginning.
     *
     * <p><strong>State Transition:</strong>
     * PAUSED → PLAYING (no change if already playing or stopped)
     *
     * <p><strong>Audio Behavior:</strong>
     * <ul>
     *   <li>Audio output is restarted</li>
     *   <li>Playback resumes from the paused position</li>
     *   <li>Volume/gain settings are preserved</li>
     * </ul>
     *
     * <p><strong>Note:</strong>
     * This method does not throw exceptions and returns immediately.
     *
     * @see #pause()
     * @see #play()
     * @see #isPaused()
     */
    void resume();

    /**
     * Stops the current audio playback completely.
     *
     * <p>This method stops audio playback and resets the playback position to the
     * beginning of the file. After stopping, {@link #play()} must be called again
     * to resume playback from the start. Stopping has no effect if playback is not active.
     *
     * <p><strong>State Transition:</strong>
     * PLAYING or PAUSED → STOPPED
     *
     * <p><strong>Audio Behavior:</strong>
     * <ul>
     *   <li>Audio output is stopped immediately</li>
     *   <li>Playback position is reset to the beginning</li>
     *   <li>Audio buffers are flushed</li>
     *   <li>Gain may be reset or faded to prevent audio artifacts</li>
     * </ul>
     *
     * <p><strong>Note:</strong>
     * This method does not throw exceptions and returns immediately.
     *
     * @see #play()
     * @see #pause()
     * @see #isPlaying()
     */
    void stop() throws AudioProcessingException;

    /**
     * Sets the audio volume/gain for playback.
     *
     * <p>This method adjusts the playback volume in real-time. Volume changes should
     * be smooth and not cause audible clicks or pops in the audio output. Implementation
     * may use fade transitions to prevent audio artifacts.
     *
     * <p><strong>Volume Range:</strong>
     * <ul>
     *   <li><b>0.0f:</b> Complete silence (mute)</li>
     *   <li><b>0.5f:</b> 50% volume (half amplitude)</li>
     *   <li><b>1.0f:</b> Maximum volume (100%)</li>
     * </ul>
     *
     * <p><strong>Behavior for Out-of-Range Values:</strong>
     * Values outside [0.0, 1.0] are typically clamped:
     * <ul>
     *   <li>Values &lt; 0.0 are clamped to 0.0</li>
     *   <li>Values &gt; 1.0 are clamped to 1.0</li>
     * </ul>
     *
     * <p><strong>Thread Safety:</strong>
     * This method is typically safe to call from any thread while playback is active.
     *
     * @param volume the desired volume level as a float. Recommended range: [0.0, 1.0]
     *
     * @see #getVolume()
     * @see #play()
     */
    void setVolume(float volume) throws AudioProcessingException;

    /**
     * Gets the current audio volume/gain level.
     *
     * <p>This method returns the current volume setting. If a volume change was recently
     * initiated with {@link #setVolume(float)}, this method may return a value that is
     * transitioning to the new target (depending on fade implementation).
     *
     * <p><strong>Return Value Range:</strong>
     * <ul>
     *   <li>0.0f: Complete silence (mute)</li>
     *   <li>0.5f: 50% volume</li>
     *   <li>1.0f: Maximum volume (100%)</li>
     * </ul>
     *
     * @return the current volume level as a float in the range [0.0, 1.0]
     *
     * @see #setVolume(float)
     */
    float getVolume();

    /**
     * Closes the audio engine and releases all resources.
     *
     * <p>This method performs cleanup of all audio-related resources including:
     * <ul>
     *   <li>Stopping playback if active</li>
     *   <li>Closing audio output lines</li>
     *   <li>Releasing codec contexts</li>
     *   <li>Freeing memory buffers</li>
     *   <li>Terminating playback threads</li>
     * </ul>
     *
     * <p><strong>AutoCloseable Pattern:</strong>
     * As this interface extends {@link AutoCloseable}, the engine can be used in
     * try-with-resources statements for automatic resource cleanup:
     * <pre>{@code
     * try (FormatAudioEngine engine = new FFmpegAudioEngine(processor, file)) {
     *     engine.play();
     *     // Use engine...
     * } // close() is automatically called here
     * }</pre>
     *
     * <p><strong>Idempotent Behavior:</strong>
     * This method can be safely called multiple times without harm.
     * Calling close() on an already-closed engine should have no effect.
     *
     * @throws AudioResourceException if an error occurs during resource cleanup
     *
     * @see AutoCloseable
     */
    @Override
    void close() throws AudioResourceException;

    /**
     * Checks if audio is currently playing.
     *
     * <p>This method returns true if audio playback is active, regardless of whether
     * the playback is currently paused or not. To distinguish between active playing
     * and paused states, combine this method with {@link #isPaused()}.
     *
     * <p><strong>Return Value Logic:</strong>
     * <pre>
     * isPlaying() = true  &amp;&amp; isPaused() = false  → Audio is actively playing
     * isPlaying() = true  &amp;&amp; isPaused() = true   → Audio is paused
     * isPlaying() = false &amp;&amp; isPaused() = false  → Audio is stopped
     * </pre>
     *
     * @return {@code true} if audio playback is active (playing or paused),
     *         {@code false} if playback is stopped
     *
     * @see #isPaused()
     * @see #play()
     * @see #stop()
     */
    boolean isPlaying();

    /**
     * Checks if audio playback is currently paused.
     *
     * <p>This method returns true only when playback has been paused using
     * {@link #pause()}. It returns false when playback is stopped or actively playing.
     *
     * <p><strong>Return Value Logic:</strong>
     * <pre>
     * isPaused() = false &amp;&amp; isPlaying() = true   → Audio is actively playing
     * isPaused() = true  &amp;&amp; isPlaying() = true   → Audio is paused
     * isPaused() = false &amp;&amp; isPlaying() = false  → Audio is stopped
     * </pre>
     *
     * @return {@code true} if audio playback is paused, {@code false} if playback is
     *         actively playing or stopped
     *
     * @see #isPlaying()
     * @see #pause()
     * @see #resume()
     */
    boolean isPaused();

    /**
     * Seeks to a specific position in the audio file.
     *
     * <p>This method navigates the playback position to a specific point in time within
     * the audio file. The seek operation may take time depending on the audio format and
     * implementation. During seeking, playback is typically paused and resumed after the
     * seek completes.
     *
     * <p><strong>Seek Behavior:</strong>
     * <ul>
     *   <li>Playback is paused during the seek operation</li>
     *   <li>Audio buffers are flushed to prevent artifacts</li>
     *   <li>Playback resumes from the new position if it was playing</li>
     *   <li>Playback remains paused if it was already paused</li>
     * </ul>
     *
     * <p><strong>Performance Characteristics:</strong>
     * <ul>
     *   <li>MP3 files: Fast seeking (indexed format)</li>
     *   <li>WAV files: Slower seeking (linear format, may take 20-30 seconds)</li>
     *   <li>Other formats: Depends on codec and container structure</li>
     * </ul>
     *
     * <p><strong>Valid Range:</strong>
     * The seek position should be between 0 and the file duration. Use {@link #getDuration()}
     * to obtain the total file length. Seeking beyond the file duration may result in
     * end-of-file or undefined behavior.
     *
     * <p><strong>Error Handling:</strong>
     * Seek failures are handled gracefully and logged. The engine remains in a usable state
     * even if a seek operation fails.
     *
     * @param seconds the target seek position in seconds from the start of the file.
     *                Must be &gt;= 0 and &lt;= file duration.
     * 
     * @throws AudioSeekException if the seek operation fails or position is invalid
     *
     * @see #getDuration()
     * @see #play()
     * @see #pause()
     */
    void seek(double seconds) throws AudioSeekException;

    /**
     * Gets the total duration of the audio file in seconds.
     *
     * <p>This method returns the total length of the audio file being played.
     * This value is useful for:
     * <ul>
     *   <li>Validating seek positions before seeking</li>
     *   <li>Displaying total playback time in the UI</li>
     *   <li>Calculating progress percentage</li>
     *   <li>Determining when playback has naturally reached the end</li>
     * </ul>
     *
     * <p><strong>Return Value:</strong>
     * <ul>
     *   <li>Positive value: Total duration in seconds</li>
     *   <li>0.0: If the file is not loaded or duration cannot be determined</li>
     * </ul>
     *
     * <p><strong>Precision:</strong>
     * The returned value is a double-precision floating-point number, allowing
     * precision to milliseconds or better.
     *
     * <p><strong>Example Usage:</strong>
     * <pre>{@code
     * double totalDuration = engine.getDuration();
     * int minutes = (int) totalDuration / 60;
     * int seconds = (int) totalDuration % 60;
     * System.out.printf("Duration: %02d:%02d\n", minutes, seconds);
     * }</pre>
     *
     * @return the total duration of the audio file in seconds, or 0.0 if the duration
     *         cannot be determined
     *
     * @see #seek(double)
     * @see #play()
     */
    double getDuration();
}
