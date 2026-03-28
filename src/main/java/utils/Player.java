package utils;

import engine.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import processors.GainProcessor;

import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.File;
import java.io.IOException;
import java.util.Scanner;

public class Player {
    private static final Logger logger = LoggerFactory.getLogger(Player.class);

    private FileLoader fileLoader = new FileLoader();
    private String filePath = fileLoader.getFilePath();
    private GainProcessor gainProcessor = new GainProcessor(0.5f);

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
                    case "P" -> {
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
                    case "S" -> {
                        audioEngine.stop();
                        logger.info("Playback stopped");
                    }
                    case "+" -> {
                        float newVol = Math.min(1.0f, audioEngine.getVolume() + 0.1f);
                        audioEngine.setVolume(newVol);
                        logger.info("Volume aumented to: {}%", newVol * 100);
                    }
                    case "-" -> {
                        float newVol = Math.max(0.0f, audioEngine.getVolume() - 0.1f);
                        audioEngine.setVolume(newVol);
                        logger.info("Volume diminished to: {}%", newVol * 100);
                    }
                    case "L" -> {
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
}