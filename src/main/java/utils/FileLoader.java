package utils;

import java.nio.file.Path;
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

    /**
     * Stores the absolute path to the audio file.
     *
     * <p>This field is initialized to an empty string and should be populated
     * with a valid file path before use. The path can be either:
     * "/home/user/music/song.mp3" or "C:\\Users\\Music\\song.mp3"</li>
     *
     */
    private String filePath = "";

    /**
     * Retrieves the currently stored audio file path.
     *
     * <p>This method returns the file path that was previously set or initialized.
     * The returned path can be either absolute or relative, depending on how it was set.
     *
     * <p><strong>Typical Usage:</strong>
     * Used by the audio engine to determine which file to open and play.
     *
     * @return the stored file path as a String. Returns an empty string ("") if no path
     *         has been set or configured.
     *
     * @see #getFileExtension()
     * @see FFmpegAudioEngine#FFmpegAudioEngine(processors.GainProcessor, String)
     */
    public String getFilePath(){
        return  filePath;
    }

    /**
     * Extracts and returns the file extension from the stored file path.
     *
     * <p>This method parses the file name portion of the file path and extracts the
     * file extension (the part after the last dot). The extension is case-sensitive
     * and returned exactly as it appears in the file name.
     *
     * <p><strong>Parsing Logic:</strong>
     * <ol>
     *   <li>Extracts only the file name from the full path (ignoring directory components)</li>
     *   <li>Finds the position of the last dot (.) in the file name</li>
     *   <li>If no dot is found or the dot is at position 0 (hidden file), returns empty string</li>
     *   <li>Otherwise, returns the substring after the last dot</li>
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
     * filePath = "/path/to/audiofile"
     * getFileExtension() → ""
     *
     * filePath = "/path/to/.hidden_file"
     * getFileExtension() → ""
     * </pre>
     *
     * <p><strong>Use Cases:</strong>
     * <ul>
     *   <li>Format detection for choosing the appropriate audio decoder</li>
     *   <li>File validation before attempting to open</li>
     *   <li>Logging and debugging audio processing</li>
     * </ul>
     *
     * @return the file extension without the leading dot, or an empty string if:
     *         <ul>
     *           <li>The file name has no dot (no extension)</li>
     *           <li>The dot is at position 0 (hidden file like ".bashrc")</li>
     *           <li>The file path is empty</li>
     *         </ul>
     *
     * @see #getFilePath()
     * @see FFmpegAudioDecoder#open(String)
     */
    public String getFileExtension() {
        String fileName = Path.of(filePath).getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');

        return (dotIndex == -1 || dotIndex == 0) ? "" : fileName.substring(dotIndex + 1);
    }
}
