package exceptions;

/**
 * Exception thrown when audio seeking operations fail.
 * 
 * This exception is raised when seeking within an audio file encounters errors, including:
 * <ul>
 *   <li>Seek position out of file bounds</li>
 *   <li>Invalid seek timestamp</li>
 *   <li>FFmpeg seek operation failure</li>
 *   <li>Decoder not initialized or already closed</li>
 *   <li>Seek not supported by format</li>
 *   <li>Buffer flush failure during seek</li>
 * </ul>
 * 
 * <p><strong>Seek Position Validation:</strong>
 * Always validate seek positions against file duration:
 * <pre>{@code
 * double duration = engine.getDuration();
 * if (seekPosition >= 0 && seekPosition <= duration) {
 *     engine.seek(seekPosition);
 * } else {
 *     throw new AudioSeekException("Seek position out of bounds");
 * }
 * }</pre>
 * 
 * @author Agustin Saunders
 * @version 1.0
 * @since 1.0
 * @see AudioException
 * @see AudioDecodingException
 */
public class AudioSeekException extends AudioException {
    
    /**
     * Constructs a new AudioSeekException with the specified detail message.
     * 
     * @param message the detail message explaining why the seek operation failed
     */
    public AudioSeekException(String message) {
        super(message);
    }
    
    /**
     * Constructs a new AudioSeekException with the specified detail message and cause.
     * 
     * @param message the detail message explaining why the seek operation failed
     * @param cause   the underlying cause of the seek failure
     */
    public AudioSeekException(String message, Throwable cause) {
        super(message, cause);
    }
    
    /**
     * Constructs a new AudioSeekException with the specified cause.
     * 
     * @param cause the underlying cause of this exception
     */
    public AudioSeekException(Throwable cause) {
        super(cause);
    }
}

