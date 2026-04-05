package exceptions;

/**
 * Exception thrown when audio processing operations fail.
 * 
 * This exception is raised when audio processors encounter errors during sample processing, including:
 * <ul>
 *   <li>Audio processor initialization fails</li>
 *   <li>Invalid processor configuration</li>
 *   <li>Processing algorithm errors</li>
 *   <li>Buffer size mismatches</li>
 *   <li>Out-of-range audio values (clipping prevention)</li>
 *   <li>Custom processor errors</li>
 * </ul>
 * 
 * <p><strong>Usage Example:</strong>
 * <pre>{@code
 * try {
 *     processor.process(audioSamples);
 * } catch (AudioProcessingException e) {
 *     logger.error("Audio processing failed: {}", e.getMessage());
 *     // Skip this processor or bypass processing
 * }
 * }</pre>
 * 
 * @author Agustin Saunders
 * @version 1.0
 * @since 1.0
 * @see AudioException
 * @see processors.AudioProcessor
 */
public class AudioProcessingException extends AudioException {
    
    /**
     * Constructs a new AudioProcessingException with the specified detail message.
     * 
     * @param message the detail message explaining what processing operation failed
     */
    public AudioProcessingException(String message) {
        super(message);
    }
    
    /**
     * Constructs a new AudioProcessingException with the specified detail message and cause.
     * 
     * @param message the detail message explaining what processing operation failed
     * @param cause   the underlying cause of the processing failure
     */
    public AudioProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
    
    /**
     * Constructs a new AudioProcessingException with the specified cause.
     * 
     * @param cause the underlying cause of this exception
     */
    public AudioProcessingException(Throwable cause) {
        super(cause);
    }
}

