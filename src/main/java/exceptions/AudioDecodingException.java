package exceptions;

/**
 * Exception thrown when audio decoding operations fail.
 * 
 * This exception is raised when FFmpeg fails to decode audio data, including:
 * <ul>
 *   <li>Codec not found or not available</li>
 *   <li>Failed to open codec context</li>
 *   <li>Audio stream not found in file</li>
 *   <li>Decoding packet or frame fails</li>
 *   <li>Resampling initialization fails</li>
 *   <li>Unsupported audio format or codec</li>
 * </ul>
 * 
 * <p><strong>Usage Example:</strong>
 * <pre>{@code
 * try {
 *     int samplesRead = decoder.readNextSamples(buffer);
 * } catch (AudioDecodingException e) {
 *     logger.error("Failed to decode audio data: {}", e.getMessage());
 *     // Attempt recovery or graceful degradation
 * }
 * }</pre>
 * 
 * @author Agustin Saunders
 * @version 1.0
 * @since 1.0
 * @see AudioException
 * @see AudioFileException
 */
public class AudioDecodingException extends AudioException {
    
    /**
     * Constructs a new AudioDecodingException with the specified detail message.
     * 
     * @param message the detail message explaining what decoding operation failed
     */
    public AudioDecodingException(String message) {
        super(message);
    }
    
    /**
     * Constructs a new AudioDecodingException with the specified detail message and cause.
     * 
     * @param message the detail message explaining what decoding operation failed
     * @param cause   the underlying cause of the decoding failure
     */
    public AudioDecodingException(String message, Throwable cause) {
        super(message, cause);
    }
    
    /**
     * Constructs a new AudioDecodingException with the specified cause.
     * 
     * @param cause the underlying cause of this exception
     */
    public AudioDecodingException(Throwable cause) {
        super(cause);
    }
}

