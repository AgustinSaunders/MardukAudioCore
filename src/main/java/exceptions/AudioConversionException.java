package exceptions;

/**
 * Exception thrown when audio format conversion operations fail.
 * 
 * This exception is raised when conversions between different audio formats encounter errors, including:
 * <ul>
 *   <li>Invalid bit depth (unsupported sample format)</li>
 *   <li>Buffer size mismatches or insufficient capacity</li>
 *   <li>Invalid byte order configuration</li>
 *   <li>Sample rate conversion failures</li>
 *   <li>Channel count mismatches</li>
 *   <li>Invalid sample values or ranges</li>
 * </ul>
 * 
 * <p><strong>Usage Example:</strong>
 * <pre>{@code
 * try {
 *     AudioUtils.bytesToFloats(audioBytes, samples, length, bitDepth, isBigEndian);
 * } catch (AudioConversionException e) {
 *     logger.error("Conversion failed: {}", e.getMessage());
 *     // Handle conversion error
 * }
 * }</pre>
 * 
 * @author Agustin Saunders
 * @version 1.0
 * @since 1.0
 * @see exceptions.AudioProcessingException
 * @see utils.AudioUtils
 */
public class AudioConversionException extends AudioException {
    
    /**
     * Constructs a new AudioConversionException with the specified detail message.
     * 
     * @param message the detail message explaining what conversion operation failed
     */
    public AudioConversionException(String message) {
        super(message);
    }
    
    /**
     * Constructs a new AudioConversionException with the specified detail message and cause.
     * 
     * @param message the detail message explaining what conversion operation failed
     * @param cause   the underlying cause of the conversion failure
     */
    public AudioConversionException(String message, Throwable cause) {
        super(message, cause);
    }
    
    /**
     * Constructs a new AudioConversionException with the specified cause.
     * 
     * @param cause the underlying cause of this exception
     */
    public AudioConversionException(Throwable cause) {
        super(cause);
    }
}

