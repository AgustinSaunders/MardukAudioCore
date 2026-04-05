package exceptions;

/**
 * Exception thrown when audio resource management fails.
 * 
 * This exception is raised when problems occur with managing audio resources, including:
 * <ul>
 *   <li>Failed to allocate audio buffers or contexts</li>
 *   <li>Failed to release audio resources</li>
 *   <li>Resource already closed or freed</li>
 *   <li>Thread resource management failures</li>
 *   <li>Memory allocation failures</li>
 *   <li>FFmpeg context allocation/deallocation errors</li>
 * </ul>
 * 
 * <p><strong>Usage Example:</strong>
 * <pre>{@code
 * try {
 *     engine.close();
 * } catch (AudioResourceException e) {
 *     logger.error("Failed to release audio resources: {}", e.getMessage());
 *     // Attempt alternative cleanup or continue
 * }
 * }</pre>
 * 
 * @author Agustin Saunders
 * @version 1.0
 * @since 1.0
 * @see AudioException
 * @see AudioPlaybackException
 */
public class AudioResourceException extends AudioException {
    
    /**
     * Constructs a new AudioResourceException with the specified detail message.
     * 
     * @param message the detail message explaining what resource operation failed
     */
    public AudioResourceException(String message) {
        super(message);
    }
    
    /**
     * Constructs a new AudioResourceException with the specified detail message and cause.
     * 
     * @param message the detail message explaining what resource operation failed
     * @param cause   the underlying cause of the resource failure
     */
    public AudioResourceException(String message, Throwable cause) {
        super(message, cause);
    }
    
    /**
     * Constructs a new AudioResourceException with the specified cause.
     * 
     * @param cause the underlying cause of this exception
     */
    public AudioResourceException(Throwable cause) {
        super(cause);
    }
}

