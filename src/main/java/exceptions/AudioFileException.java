package exceptions;

/**
 * Exception thrown when audio file operations fail.
 * 
 * This exception is raised when there are problems related to audio file handling, including:
 * <ul>
 *   <li>File not found or inaccessible</li>
 *   <li>Invalid file path or format</li>
 *   <li>File permission issues</li>
 *   <li>Corrupted or invalid audio file</li>
 *   <li>Unsupported audio format</li>
 * </ul>
 * 
 * <p><strong>Usage Example:</strong>
 * <pre>{@code
 * try {
 *     decoder.open("nonexistent.mp3");
 * } catch (AudioFileException e) {
 *     logger.error("Failed to load audio file: {}", e.getMessage());
 *     System.err.println("Please verify the file path and try again.");
 * }
 * }</pre>
 * 
 * @author Agustin Saunders
 * @version 1.0
 * @since 1.0
 * @see AudioException
 * @see AudioDecodingException
 */
public class AudioFileException extends AudioException {
    
    /**
     * Constructs a new AudioFileException with the specified detail message.
     * 
     * @param message the detail message explaining why the file operation failed
     */
    public AudioFileException(String message) {
        super(message);
    }
    
    /**
     * Constructs a new AudioFileException with the specified detail message and cause.
     * 
     * @param message the detail message explaining why the file operation failed
     * @param cause   the underlying cause (usually an IOException or FileNotFoundException)
     */
    public AudioFileException(String message, Throwable cause) {
        super(message, cause);
    }
    
    /**
     * Constructs a new AudioFileException with the specified cause.
     * 
     * @param cause the underlying cause of this exception
     */
    public AudioFileException(Throwable cause) {
        super(cause);
    }
}

