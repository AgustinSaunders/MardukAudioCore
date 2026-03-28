package utils;

import engine.*;
import processors.GainProcessor;

import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.File;
import java.io.IOException;
import java.util.Scanner;

public class Player {

    private FileLoader fileLoader = new FileLoader();
    private String filePath = fileLoader.getFilePath();
    private GainProcessor gainProcessor = new GainProcessor(0.5f);

    public void start() {
        try (FormatAudioEngine audioEngine = new FFmpegAudioEngine(gainProcessor, filePath);
             Scanner scanner = new Scanner(System.in)) {

            String response = "";
//            System.out.println("Cargando archivo: " + file.getName());

            while (!response.equalsIgnoreCase("Q")) {
                System.out.println("\n[P] Play/Pause | [S] Stop | [+] Vol Up | [-] Vol Down | [L] Jump/Leap | [Q] Quit");
                System.out.printf("Volumen actual: %.0f%%\n", audioEngine.getVolume() * 100);
                System.out.print(">> ");

                response = scanner.next().toUpperCase();

                switch (response) {
                    case "P" -> {
                        if (audioEngine.isPaused()) {
                            audioEngine.resume();
                            System.out.println("Resumiendo...");
                        } else if (audioEngine.isPlaying()) {
                            audioEngine.pause();
                            System.out.println("Pausado.");
                        } else {
                            audioEngine.play();
                            System.out.println("Reproduciendo...");
                        }
                    }
                    case "S" -> {
                        audioEngine.stop();
                        System.out.println("Reproducción detenida.");
                    }
                    case "+" -> {
                        float newVol = Math.min(1.0f, audioEngine.getVolume() + 0.1f);
                        audioEngine.setVolume(newVol);
                    }
                    case "-" -> {
                        float newVol = Math.max(0.0f, audioEngine.getVolume() - 0.1f);
                        audioEngine.setVolume(newVol);
                    }
                    case "L" -> {
                        double totalDuration = audioEngine.getDuration();
                        int minutes = (int) totalDuration / 60;
                        int seconds = (int) totalDuration % 60;

                        System.out.printf("Duración total del archivo: %02d:%02d (%.2f segundos)\n",
                                minutes, seconds, totalDuration);
                        System.out.print("¿A qué segundo quieres saltar?: ");

                        if (scanner.hasNextDouble()) {
                            double target = scanner.nextDouble();
                            if (target >= 0 && target <= totalDuration) {
                                audioEngine.seek(target);
                            } else {
                                System.out.println("Error: El tiempo debe estar entre 0 y " + totalDuration);
                            }
                        } else {
                            System.out.println("Entrada inválida.");
                            scanner.next();
                        }
                    }
                    case "Q" -> System.out.println("Saliendo...");
                    default -> System.out.println("Opción no válida.");
                }

            }
        } catch (Exception e) {
            System.err.println("Error en el sistema de audio: " + e.getMessage());
            e.printStackTrace();
        } finally {
            System.out.println("¡Adiós!");
        }
    }
}