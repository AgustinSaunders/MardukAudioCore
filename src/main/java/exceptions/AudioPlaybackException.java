package exceptions;

/**
 * Exception thrown when audio playback operations fail.
 * 
 * This exception is raised when problems occur during audio playback, including:
 * <ul>
 *   <li>Audio device/line not available</li>
 *   <li>Failed to open or start audio output line</li>
 *   <li>Audio format not supported by system</li>
 *   <li>Playback thread error or crash</li>
 *   <li>Buffer underrun or audio data loss</li>
 *   <li>System audio resources exhausted</li>
 * </ul>
 * 
 * <p><strong>Usage Example:</strong>
 * <pre>{@code
 * try {
 *     engine.play();
 * } catch (AudioPlaybackException e) {
 *     logger.error("Playback failed: {}", e.getMessage());
 *     System.err.println("Check audio device connection and system settings.");
 * }
 * }</pre>
 * 
 * @author Agustin Saunders
 * @version 1.0
 * @since 1.0
 * @see AudioException
 * @see AudioFileException
 */
public class AudioPlaybackException extends AudioException {
    
    /**
     * Constructs a new AudioPlaybackException with the specified detail message.
     * 
     * @param message the detail message explaining what playback operation failed
     */
    public AudioPlaybackException(String message) {
        super(message);
    }
    
    /**
     * Constructs a new AudioPlaybackException with the specified detail message and cause.
     * 
     * @param message the detail message explaining what playback operation failed
     * @param cause   the underlying cause of the playback failure
     */
    public AudioPlaybackException(String message, Throwable cause) {
        super(message, cause);
    }
    
    /**
     * Constructs a new AudioPlaybackException with the specified cause.
     * 
     * @param cause the underlying cause of this exception
     */
    public AudioPlaybackException(Throwable cause) {
        super(cause);
    }
}

