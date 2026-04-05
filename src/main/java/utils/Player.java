package utils;

import engine.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import processors.GainProcessor;
import exceptions.*;

import java.util.Scanner;

/**
 * Interactive command-line audio player interface for Marduk Audio Core.
 *
 * This class provides a user-friendly interactive console interface for controlling audio playback.
 * It manages the lifecycle of the audio engine and handles user input for playback control,
 * volume adjustment, and seeking operations.
 *
 * <p><strong>Supported Commands:</strong>
 * <ul>
 *   <li><b>P</b> - Play/Pause/Resume: Toggles between playing and paused states</li>
 *   <li><b>S</b> - Stop: Stops audio playback completely and resets position to start</li>
 *   <li><b>+</b> - Volume Up: Increases volume by 10%</li>
 *   <li><b>-</b> - Volume Down: Decreases volume by 10%</li>
 *   <li><b>L</b> - Jump/Leap: Seeks to a specific position in seconds</li>
 *   <li><b>Q</b> - Quit: Closes the application and releases all resources</li>
 * </ul>
 *
 * <p><strong>Audio Format Support:</strong>
 * The player supports any audio format that FFmpeg can decode, including:
 * MP3, WAV, FLAC, OGG, AAC, WMA, and others.
 *
 * <p><strong>Features:</strong>
 * <ul>
 *   <li>Real-time volume control (0% to 100%)</li>
 *   <li>Precise seeking with file duration validation</li>
 *   <li>User-friendly menu with current volume display</li>
 *   <li>Comprehensive logging of all operations</li>
 *   <li>Graceful error handling and recovery</li>
 *   <li>Resource cleanup with try-with-resources</li>
 * </ul>
 *
 * <p><strong>Usage Example:</strong>
 * <pre>{@code
 * Player player = new Player();
 * player.start();  // Starts the interactive console interface
 * }</pre>
 *
 * <p><strong>Flow Diagram:</strong>
 * <pre>
 * start()
 *   ↓
 * Display Menu
 *   ↓
 * Get User Input
 *   ↓
 * Process Command
 *   ├─ P: Play/Pause/Resume
 *   ├─ S: Stop
 *   ├─ +/-: Adjust Volume
 *   ├─ L: Seek to Position
 *   └─ Q: Quit
 *   ↓
 * Repeat until Quit
 *   ↓
 * Close Resources
 * </pre>
 *
 * @author Agustin Saunders
 * @version 1.0
 * @since 1.0
 * @see engine.FormatAudioEngine
 * @see engine.FFmpegAudioEngine
 * @see processors.GainProcessor
 * @see utils.FileLoader
 */
public class Player {
    private static final Logger logger = LoggerFactory.getLogger(Player.class);

    /** File loader instance for retrieving the audio file path. */
    private FileLoader fileLoader;
    
    /** The absolute or relative path to the audio file to be played. */
    private String filePath;
    
    /** Gain processor for audio volume control, initialized at 50% (0.5f). */
    private GainProcessor gainProcessor = new GainProcessor(0.5f);

    /**
     * Constructor that initializes the Player with a specified file path.
     *
     * @param audioFilePath the absolute or relative path to the audio file
     * @throws AudioFileException if the file path is invalid or file doesn't exist
     */
    public Player(String audioFilePath) throws AudioFileException, AudioProcessingException {
        this.fileLoader = new FileLoader();
        try {
            fileLoader.setFilePath(audioFilePath);
            this.filePath = fileLoader.getFilePath();
            logger.info("Player initialized with file: {}", filePath);
        } catch (AudioFileException e) {
            logger.error("Failed to initialize player: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Default constructor (uses default file path from FileLoader).
     * 
     * @deprecated Use {@link #Player(String)} with explicit file path instead
     */
    @Deprecated
    public Player() throws AudioProcessingException, AudioFileException {
        this.fileLoader = new FileLoader();
        this.filePath = fileLoader.getFilePath();
        logger.warn("Player initialized with default (empty) file path");
    }

    /**
     * Starts the interactive audio player console interface.
     *
     * <p>This method initializes the audio engine with the loaded file and enters
     * an interactive loop where the user can control playback via keyboard commands.
     *
     * <p><strong>Resource Management:</strong>
     * Uses try-with-resources to ensure proper cleanup of:
     * <ul>
     *   <li>Audio engine (FormatAudioEngine) - closes all audio resources</li>
     *   <li>Scanner - closes the standard input stream</li>
     * </ul>
     *
     * <p><strong>User Interaction Loop:</strong>
     * The method continuously:
     * <ol>
     *   <li>Displays the command menu</li>
     *   <li>Shows the current volume level</li>
     *   <li>Waits for user input (case-insensitive)</li>
     *   <li>Processes the command and performs the corresponding action</li>
     *   <li>Logs all operations for debugging and monitoring</li>
     * </ol>
     *
     * <p>The loop terminates when the user enters 'Q' (Quit) or an error occurs.
     *
     * <p><strong>Exception Handling:</strong>
     * Any exceptions during audio playback or user interaction are caught, logged,
     * and a stack trace is printed for debugging purposes.
     *
     * @see #handlePlayPauseCommand(FormatAudioEngine)
     * @see #handleStopCommand(FormatAudioEngine)
     * @see #handleVolumeUpCommand(FormatAudioEngine)
     * @see #handleVolumeDownCommand(FormatAudioEngine)
     * @see #handleSeekCommand(FormatAudioEngine, Scanner)
     */
    public void start() {
        logger.info("========== MARDUK AUDIO CORE STARTED ==========");
        
        // Validate file path first
        try {
            if (filePath == null || filePath.isEmpty()) {
                throw new AudioFileException("No audio file path configured. Use Player(filePath) constructor.");
            }
            logger.info("Audio file: {}", fileLoader.getFileInfo());
        } catch (AudioFileException e) {
            logger.error("File validation error: {}", e.getMessage());
            System.err.println("❌ ERROR: " + e.getMessage());
            return;
        }
        
        try (FormatAudioEngine audioEngine = new FFmpegAudioEngine(gainProcessor, filePath);
             Scanner scanner = new Scanner(System.in)) {

            String response = "";

            while (!response.equalsIgnoreCase("Q")) {
                System.out.println("\n[P] Play/Pause | [S] Stop | [+] Vol Up | [-] Vol Down | [L] Jump/Leap | [Q] Quit");
                System.out.printf("Volume currently at: %.0f%%\n", audioEngine.getVolume() * 100);
                System.out.print(">> ");

                response = scanner.next().toUpperCase();

                try {
                    switch (response) {
                        case "P" -> handlePlayPauseCommand(audioEngine);
                        case "S" -> handleStopCommand(audioEngine);
                        case "+" -> handleVolumeUpCommand(audioEngine);
                        case "-" -> handleVolumeDownCommand(audioEngine);
                        case "L" -> handleSeekCommand(audioEngine, scanner);
                        case "Q" -> System.out.println("Closing Marduk Audio Core...");
                        default -> System.out.println("Invalid option.");
                    }
                } catch (AudioException e) {
                    handleAudioException(e);
                } catch (Exception e) {
                    logger.error("Unexpected error: {}", e.getMessage(), e);
                    System.err.println("An unexpected error occurred: " + e.getMessage());
                }
            }
        } catch (AudioException e) {
            logger.error("Audio system error: {}", e.getMessage());
            System.err.println("ERROR: An audio system error occurred.");
        } catch (Exception e) {
            logger.error("Fatal error in the audio system: {}", e.getMessage(), e);
            System.err.println("FATAL ERROR: The application encountered an unexpected error.");
            e.printStackTrace();
        } finally {
            System.out.println("Goodbye!");
            logger.info("========== MARDUK AUDIO CORE CLOSED ==========");
        }
    }

    /**
     * Command: P - Play/Pause/Resume
     * Toggles between playing and paused states, or starts playback if stopped.
     */
    private void handlePlayPauseCommand(FormatAudioEngine audioEngine) throws AudioException {
        try {
            if (audioEngine.isPaused()) {
                audioEngine.resume();
                logger.info("Resuming playback...");
            } else if (audioEngine.isPlaying()) {
                audioEngine.pause();
                logger.info("Paused");
            } else {
                audioEngine.play();
                logger.info("Playing...");
            }
        } catch (AudioPlaybackException e) {
            logger.error("Playback control error: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error during playback control: {}", e.getMessage());
            throw new AudioPlaybackException("Failed to control playback", e);
        }
    }

    /**
     * Command: S - Stop
     * Stops audio playback completely and resets the position to the beginning.
     */
    private void handleStopCommand(FormatAudioEngine audioEngine) throws AudioProcessingException {
        audioEngine.stop();
        logger.info("Playback stopped");
    }

    /**
     * Command: + - Volume Up
     * Increases the volume by 10% (capped at 100%).
     */
    private void handleVolumeUpCommand(FormatAudioEngine audioEngine) throws AudioProcessingException {
        float newVol = Math.min(1.0f, audioEngine.getVolume() + 0.1f);
        audioEngine.setVolume(newVol);
        logger.info("Volume increased to: {}%", newVol * 100);
    }

    /**
     * Command: - - Volume Down
     * Decreases the volume by 10% (capped at 0%).
     */
    private void handleVolumeDownCommand(FormatAudioEngine audioEngine) throws AudioProcessingException {
        float newVol = Math.max(0.0f, audioEngine.getVolume() - 0.1f);
        audioEngine.setVolume(newVol);
        logger.info("Volume diminished to: {}%", newVol * 100);
    }

    /**
     * Command: L - Jump/Leap (Seek)
     * Displays the total file duration and prompts the user to seek to a specific position.
     * The seek position is validated against the file duration before execution.
     */
    private void handleSeekCommand(FormatAudioEngine audioEngine, Scanner scanner) throws AudioException {
        try {
            double totalDuration = audioEngine.getDuration();
            
            if (totalDuration <= 0) {
                logger.warn("Cannot seek: invalid file duration");
                System.out.println("Error: Cannot determine file duration.");
                return;
            }
            
            int minutes = (int) totalDuration / 60;
            int seconds = (int) totalDuration % 60;

            System.out.printf("Total file duration: %02d:%02d (%.2f seconds)\n",
                    minutes, seconds, totalDuration);
            System.out.print("Which second do you want to jump to?: ");

            if (scanner.hasNextDouble()) {
                double target = scanner.nextDouble();
                
                if (target < 0 || target > totalDuration) {
                    logger.warn("Invalid seek position: {} (valid range: 0-{})", target, totalDuration);
                    System.out.printf("Error: Target time must be between 0 and %.2f seconds%n", totalDuration);
                    throw new AudioSeekException("Seek position out of bounds: " + target);
                }
                
                try {
                    audioEngine.seek(target);
                    logger.info("Successfully seeked to: {} seconds", target);
                } catch (AudioSeekException e) {
                    logger.error("Seek operation failed: {}", e.getMessage());
                    System.err.println("Error: Could not seek to the requested position.");
                    throw e;
                }
            } else {
                logger.warn("Invalid user input for seek command");
                System.out.println("Invalid input: Please enter a numeric value.");
                scanner.next(); // Consume the invalid input
            }
        } catch (AudioSeekException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error during seek operation: {}", e.getMessage());
            throw new AudioSeekException("Seek operation failed", e);
        }
    }

    private void printMenu(FormatAudioEngine audioEngine) {
        System.out.println("\n[P] Play/Pause | [S] Stop | [+] Vol Up | [-] Vol Down | [L] Jump | [Q] Quit");
        System.out.printf("Volume: %.0f%%\n>> ", audioEngine.getVolume() * 100);
    }

    /**
     * Handles audio exceptions with appropriate user feedback.
     * 
     * <p>This method logs the exception details and provides user-friendly
     * error messages based on the specific exception type.
     * 
     * @param e the audio exception to handle
     */
    private void handleAudioException(AudioException e) {
        switch (e) {
            case AudioFileException audioFileException -> {
                logger.error("File error: {}", e.getMessage());
                System.err.println("❌ File Error: " + e.getMessage());
            }
            case AudioDecodingException audioDecodingException -> {
                logger.error("Decoding error: {}", e.getMessage());
                System.err.println("❌ Decoding Error: The audio file format may not be supported.");
            }
            case AudioPlaybackException audioPlaybackException -> {
                logger.error("Playback error: {}", e.getMessage());
                System.err.println("❌ Playback Error: Check your audio device.");
            }
            case AudioSeekException audioSeekException -> {
                logger.error("Seek error: {}", e.getMessage());
                System.err.println("❌ Seek Error: Could not seek to the requested position.");
            }
            case AudioProcessingException audioProcessingException -> {
                logger.error("Processing error: {}", e.getMessage());
                System.err.println("❌ Processing Error: Audio processing failed.");
            }
            case AudioResourceException audioResourceException -> {
                logger.error("Resource error: {}", e.getMessage());
                System.err.println("❌ Resource Error: Audio resource management failed.");
            }
            default -> {
                logger.error("Unknown audio error: {}", e.getMessage());
                System.err.println("❌ Unknown Error: " + e.getMessage());
            }
        }
    }
}

