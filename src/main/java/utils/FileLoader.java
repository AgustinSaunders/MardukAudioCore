package utils;

import java.nio.file.Path;
import java.io.File;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import exceptions.AudioFileException;
import engine.FFmpegAudioEngine;

/**
 * Utility class for loading and managing audio file paths.
 *
 * This class provides functionality for retrieving and parsing audio file information,
 * specifically handling file path management and extracting file extensions.
 * It serves as a simple but essential utility for the audio playback system to identify
 * and work with audio files.
 *
 * <p><strong>Responsibilities:</strong>
 * <ul>
 *   <li>Store the path to the audio file to be played</li>
 *   <li>Provide access to the stored file path</li>
 *   <li>Extract and return the file extension for format detection</li>
 * </ul>
 *
 * <p><strong>Supported Audio Formats:</strong>
 * The extension extraction is format-agnostic and works with any file type:
 * <ul>
 *   <li>MP3 (.mp3) - MPEG Audio Layer III</li>
 *   <li>WAV (.wav) - Waveform Audio File Format</li>
 *   <li>FLAC (.flac) - Free Lossless Audio Codec</li>
 *   <li>OGG (.ogg) - Ogg Vorbis</li>
 *   <li>AAC (.aac) - Advanced Audio Coding</li>
 *   <li>WMA (.wma) - Windows Media Audio</li>
 *   <li>And any other format supported by FFmpeg</li>
 * </ul>
 *
 * <p><strong>Extension Parsing Examples:</strong>
 * <pre>
 * "/path/to/audio.mp3"     → "mp3"
 * "/path/to/audio.WAV"     → "WAV" (case-preserved)
 * "/path/to/audio.tar.gz"  → "gz"
 * "/path/to/audio"         → "" (no extension)
 * "/path/to/.audio"        → "" (no extension, dot at start)
 * </pre>
 *
 * <p><strong>Usage Example:</strong>
 * <pre>{@code
 * FileLoader loader = new FileLoader();
 * String filePath = loader.getFilePath();  // Get the full path
 * String extension = loader.getFileExtension();  // Get file extension
 * }</pre>
 *
 * <p><strong>Note:</strong>
 * This class currently stores an empty string by default for the file path.
 * The path should be populated through an external configuration or initialization
 * method before use in production.
 *
 * @author Agustin Saunders
 * @version 1.0
 * @since 1.0
 * @see Player
 * @see engine.FFmpegAudioEngine
 */
public class FileLoader {

    private static final Logger logger = LoggerFactory.getLogger(FileLoader.class);

    /**
     * Supported audio formats that FFmpeg can decode.
     */
    private static final String[] SUPPORTED_FORMATS = {
            "mp3", "wav", "flac", "ogg", "aac", "wma", "m4a",
            "opus", "aiff", "ape", "alac", "dsd", "dsf", "mid",
            "midi", "wv", "oga", "spx", "ac3", "eac3"
    };

    /**
     * Stores the absolute or relative path to the audio file.
     *
     * <p>This field is initialized to an empty string and should be populated
     * with a valid file path before use. The path can be either:
     * <ul>
     *   <li><b>Absolute:</b> "/home/user/music/song.mp3" or "C:\\Users\\Music\\song.mp3"</li>
     *   <li><b>Relative:</b> "./audio/song.mp3" or "music/song.mp3"</li>
     * </ul>
     */
    private String filePath = "";


    /**
     * Retrieves the currently stored audio file path.
     *
     * <p>This method returns the file path that was previously set or initialized.
     * The returned path can be either absolute or relative, depending on how it was set.
     *
     * <p><strong>Validations performed:</strong>
     * <ul>
     *   <li>Checks if the path is not empty</li>
     *   <li>Checks if the file exists</li>
     *   <li>Checks if it's a regular file (not a directory)</li>
     * </ul>
     *
     * <p><strong>Typical Usage:</strong>
     * Used by the audio engine to determine which file to open and play.
     *
     * @return the stored file path as a String
     * @throws AudioFileException if path is empty, file doesn't exist, or is not a regular file
     *
     * @see #getFileExtension()
     * @see FFmpegAudioEngine#FFmpegAudioEngine(processors.GainProcessor, String)
     */
    public String getFilePath() throws AudioFileException {
        // Check if path is empty
        if (filePath == null || filePath.isEmpty()) {
            logger.error("File path is not set or is empty");
            throw new AudioFileException("Audio file path not configured or is empty");
        }

        try {
            // Check if file exists
            File file = new File(filePath);
            if (!file.exists()) {
                logger.error("Audio file not found: {}", filePath);
                throw new AudioFileException("Audio file not found: " + filePath);
            }

            // Check if it's a regular file (not directory)
            if (!file.isFile()) {
                logger.error("Path is not a regular file: {}", filePath);
                throw new AudioFileException("Path is not a file: " + filePath);
            }

            // Check file readability
            if (!file.canRead()) {
                logger.error("Audio file is not readable: {}", filePath);
                throw new AudioFileException("Audio file is not readable (permission denied): " + filePath);
            }

            logger.debug("File path validated successfully: {}", filePath);
            return filePath;

        } catch (AudioFileException e) {
            throw e;
        } catch (SecurityException e) {
            logger.error("Security exception accessing file: {}", e.getMessage());
            throw new AudioFileException("Security exception accessing audio file: " + filePath, e);
        } catch (Exception e) {
            logger.error("Unexpected error validating file path: {}", e.getMessage());
            throw new AudioFileException("Error validating audio file: " + filePath, e);
        }
    }

    /**
     * Checks if the given file extension is a supported audio format.
     *
     * <p>This method validates against FFmpeg's supported audio formats.
     *
     * @param extension the file extension to check (case-insensitive)
     * @return true if the format is supported, false otherwise
     */
    private boolean isSupportedFormat(String extension) {
        if (extension == null || extension.isEmpty()) {
            return false;
        }

        String lowerExtension = extension.toLowerCase();
        for (String format : SUPPORTED_FORMATS) {
            if (format.equals(lowerExtension)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Sets the audio file path with validation.
     *
     * <p>This method sets the file path and performs basic validation to ensure
     * the path is not empty and the file exists.
     *
     * @param filePath the absolute or relative path to the audio file
     * @throws AudioFileException if the file path is empty, null, file doesn't exist,
     *                            or is not a regular file
     */
    public void setFilePath(String filePath) throws AudioFileException {
        if (filePath == null || filePath.isEmpty()) {
            logger.error("Cannot set empty or null file path");
            throw new AudioFileException("File path cannot be null or empty");
        }

        try {
            File file = new File(filePath);

            if (!file.exists()) {
                logger.error("File not found: {}", filePath);
                throw new AudioFileException("Audio file not found: " + filePath);
            }

            if (!file.isFile()) {
                logger.error("Path is not a file: {}", filePath);
                throw new AudioFileException("Path is not a regular file: " + filePath);
            }

            if (!file.canRead()) {
                logger.error("File is not readable: {}", filePath);
                throw new AudioFileException("Audio file is not readable (permission denied): " + filePath);
            }

            this.filePath = filePath;
            logger.info("Audio file path set successfully: {}", filePath);

        } catch (AudioFileException e) {
            throw e;
        } catch (SecurityException e) {
            logger.error("Security exception accessing file: {}", e.getMessage());
            throw new AudioFileException("Security exception accessing file: " + filePath, e);
        } catch (Exception e) {
            logger.error("Unexpected error setting file path: {}", e.getMessage());
            throw new AudioFileException("Error setting audio file path", e);
        }
    }

    /**
     * Gets the file size in bytes.
     *
     * @return the file size in bytes
     * @throws AudioFileException if file path is not set or file doesn't exist
     */
    public long getFileSize() throws AudioFileException {
        try {
            File file = new File(getFilePath());
            long size = file.length();
            logger.debug("File size: {} bytes", size);
            return size;
        } catch (AudioFileException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error getting file size: {}", e.getMessage());
            throw new AudioFileException("Error getting audio file size", e);
        }
    }

    /**
     * Checks if the file is a supported audio format.
     *
     * @return true if the file extension matches a supported audio format
     * @throws AudioFileException if file path is not set or is invalid
     */
    public boolean isValidAudioFile() throws AudioFileException {
        try {
            getFileExtension(); // This validates the format
            logger.debug("File is a valid audio format: {}", filePath);
            return true;
        } catch (AudioFileException e) {
            logger.warn("File is not a valid audio format: {}", filePath);
            throw e;
        }
    }

    /**
     * Gets a formatted string with file information for logging.
     *
     * @return formatted file information string
     * @throws AudioFileException if file path is not set or file doesn't exist
     */
    public String getFileInfo() throws AudioFileException {
        try {
            File file = new File(getFilePath());
            String extension = getFileExtension();
            long sizeKB = getFileSize() / 1024;
            return String.format("File: %s | Format: .%s | Size: %d KB",
                    file.getName(), extension, sizeKB);
        } catch (AudioFileException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error getting file info: {}", e.getMessage());
            throw new AudioFileException("Error getting file information", e);
        }
    }

    /**
     * Extracts and returns the file extension from the stored file path.
     *
     * <p>This method parses the file name portion of the file path and extracts the
     * file extension (the part after the last dot). The extension is case-insensitive
     * when checking against supported formats.
     *
     * <p><strong>Parsing Logic:</strong>
     * <ol>
     *   <li>Validates that file path is set and accessible</li>
     *   <li>Extracts only the file name from the full path (ignoring directory components)</li>
     *   <li>Finds the position of the last dot (.) in the file name</li>
     *   <li>If no dot is found or the dot is at position 0 (hidden file), throws exception</li>
     *   <li>Validates that the format is supported by FFmpeg</li>
     * </ol>
     *
     * <p><strong>Examples:</strong>
     * <pre>
     * filePath = "/home/user/music.mp3"
     * getFileExtension() → "mp3"
     *
     * filePath = "C:\\Music\\song.WAV"
     * getFileExtension() → "WAV"
     *
     * filePath = "/path/to/archive.tar.gz"
     * getFileExtension() → "gz"
     *
     * filePath = "/path/to/audiofile" (no extension)
     * getFileExtension() → throws AudioFileException
     *
     * filePath = "/path/to/.hidden_file"
     * getFileExtension() → throws AudioFileException
     * </pre>
     *
     * <p><strong>Use Cases:</strong>
     * <ul>
     *   <li>Format detection for choosing the appropriate audio decoder</li>
     *   <li>File validation before attempting to open</li>
     *   <li>Logging and debugging audio processing</li>
     * </ul>
     *
     * @return the file extension without the leading dot, in lowercase for consistency
     *
     * @throws AudioFileException if:
     *         <ul>
     *           <li>File path is empty or not set</li>
     *           <li>The file name has no extension</li>
     *           <li>The extension is not a recognized audio format</li>
     *           <li>File doesn't exist or is not accessible</li>
     *         </ul>
     *
     * @see #getFilePath()
     * @see #isSupportedFormat(String)
     * @see FFmpegAudioDecoder#open(String)
     */
    public String getFileExtension() throws AudioFileException {
        try {
            // Validate file exists and is accessible
            getFilePath();

            String fileName = Path.of(filePath).getFileName().toString();
            int dotIndex = fileName.lastIndexOf('.');

            // Check if extension exists
            if (dotIndex == -1 || dotIndex == 0) {
                logger.error("File has no extension or is a hidden file: {}", filePath);
                throw new AudioFileException("Audio file has no extension: " + filePath);
            }

            String extension = fileName.substring(dotIndex + 1).toLowerCase();

            // Validate if format is supported
            if (!isSupportedFormat(extension)) {
                logger.warn("Unsupported audio format: {}", extension);
                throw new AudioFileException("Unsupported audio format: ." + extension + 
                        " (File: " + filePath + ")");
            }

            logger.debug("File extension validated: {}", extension);
            return extension;

        } catch (AudioFileException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error extracting file extension: {}", e.getMessage());
            throw new AudioFileException("Error validating audio file format", e);
        }
    }
}
