package utils;

import engine.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import processors.GainProcessor;

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
    private FileLoader fileLoader = new FileLoader();

    /** The absolute or relative path to the audio file to be played. */
    private String filePath = fileLoader.getFilePath();

    /** Gain processor for audio volume control, initialized at 50% (0.5f). */
    private GainProcessor gainProcessor = new GainProcessor(0.5f);

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
        logger.info("File: {}", filePath);
        try (FormatAudioEngine audioEngine = new FFmpegAudioEngine(gainProcessor, filePath);
             Scanner scanner = new Scanner(System.in)) {

            String response = "";

            while (!response.equalsIgnoreCase("Q")) {
                System.out.println("\n[P] Play/Pause | [S] Stop | [+] Vol Up | [-] Vol Down | [L] Jump/Leap | [Q] Quit");
                System.out.printf("Volume currently at: %.0f%%\n", audioEngine.getVolume() * 100);
                System.out.print(">> ");

                response = scanner.next().toUpperCase();

                switch (response) {
                    case "P" -> handlePlayPauseCommand(audioEngine);
                    case "S" -> handleStopCommand(audioEngine);
                    case "+" -> handleVolumeUpCommand(audioEngine);
                    case "-" -> handleVolumeDownCommand(audioEngine);
                    case "L" -> handleSeekCommand(audioEngine, scanner);
                    /**
                     * Command: Q - Quit
                     * Exits the application. The try-with-resources block ensures all resources are released.
                     */
                    case "Q" -> System.out.println("Closing Marduk Audio Core...");
                    default -> System.out.println("Invalid option.");
                }

            }
        } catch (Exception e) {
            logger.error("Error in the audio system: {}", e.getMessage());
            e.printStackTrace();
        } finally {
            System.out.println("Goodbye!");
            logger.info("========== MARDUK AUDIO CORE CERRADO ==========");
        }
    }

    /**
     * Command: P - Play/Pause/Resume
     * Toggles between playing and paused states, or starts playback if stopped.
     */
    private void handlePlayPauseCommand(FormatAudioEngine audioEngine) throws Exception {
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
    }

    /**
     * Command: S - Stop
     * Stops audio playback completely and resets the position to the beginning.
     */
    private void handleStopCommand(FormatAudioEngine audioEngine) {
        audioEngine.stop();
        logger.info("Playback stopped");
    }

    /**
     * Command: + - Volume Up
     * Increases the volume by 10% (capped at 100%).
     */
    private void handleVolumeUpCommand(FormatAudioEngine audioEngine) {
        float newVol = Math.min(1.0f, audioEngine.getVolume() + 0.1f);
        audioEngine.setVolume(newVol);
        logger.info("Volume increased to: {}%", newVol * 100);
    }

    /**
     * Command: - - Volume Down
     * Decreases the volume by 10% (capped at 0%).
     */
    private void handleVolumeDownCommand(FormatAudioEngine audioEngine) {
        float newVol = Math.max(0.0f, audioEngine.getVolume() - 0.1f);
        audioEngine.setVolume(newVol);
        logger.info("Volume diminished to: {}%", newVol * 100);
    }

    /**
     * Command: L - Jump/Leap (Seek)
     * Displays the total file duration and prompts the user to seek to a specific position.
     * The seek position is validated against the file duration before execution.
     */
    private void handleSeekCommand(FormatAudioEngine audioEngine, Scanner scanner) {
        double totalDuration = audioEngine.getDuration();
        int minutes = (int) totalDuration / 60;
        int seconds = (int) totalDuration % 60;

        System.out.printf("Total file duration: %02d:%02d (%.2f seconds)\n",
                minutes, seconds, totalDuration);
        System.out.print("Which second do you want to jump to?: ");

        if (scanner.hasNextDouble()) {
            double target = scanner.nextDouble();
            if (target >= 0 && target <= totalDuration) {
                logger.info("Jumping to: {} seconds", target);
                audioEngine.seek(target);
            } else {
                System.out.println("Error: target time must be between 0 y " + totalDuration);
                logger.warn("Invalid seek solicited: {} (range: 0-{})", target, totalDuration);
            }
        } else {
            System.out.println("Invalid answer.");
            scanner.next();
        }
    }

    private void printMenu(FormatAudioEngine audioEngine) {
        System.out.println("\n[P] Play/Pause | [S] Stop | [+] Vol Up | [-] Vol Down | [L] Jump | [Q] Quit");
        System.out.printf("Volume: %.0f%%\n>> ", audioEngine.getVolume() * 100);
    }

}