package exceptions;

/**
 * Base exception class for all audio-related errors in MardukAudioCore.
 * 
 * This is the root exception for the audio processing framework. All specific audio exceptions
 * should extend this class to maintain a consistent exception hierarchy and allow for catching
 * all audio-related errors with a single catch block.
 * 
 * <p><strong>Exception Hierarchy:</strong>
 * <pre>
 * Exception
 *     └── AudioException
 *         ├── AudioFileException
 *         ├── AudioDecodingException
 *         ├── AudioPlaybackException
 *         ├── AudioSeekException
 *         ├── AudioProcessingException
 *         └── AudioResourceException
 * </pre>
 * 
 * <p><strong>Usage Example:</strong>
 * <pre>{@code
 * try {
 *     engine.play();
 * } catch (AudioException e) {
 *     logger.error("Audio error occurred: {}", e.getMessage());
 *     // Handle all audio-related errors
 * }
 * }</pre>
 * 
 * @author Agustin Saunders
 * @version 1.0
 * @since 1.0
 * @see AudioFileException
 * @see AudioDecodingException
 * @see AudioPlaybackException
 */
public class AudioException extends Exception {
    
    /**
     * Constructs a new AudioException with the specified detail message.
     * 
     * @param message the detail message explaining the error
     */
    public AudioException(String message) {
        super(message);
    }
    
    /**
     * Constructs a new AudioException with the specified detail message and cause.
     * 
     * @param message the detail message explaining the error
     * @param cause   the underlying cause of this exception
     */
    public AudioException(String message, Throwable cause) {
        super(message, cause);
    }
    
    /**
     * Constructs a new AudioException with the specified cause.
     * 
     * @param cause the underlying cause of this exception
     */
    public AudioException(Throwable cause) {
        super(cause);
    }
}

