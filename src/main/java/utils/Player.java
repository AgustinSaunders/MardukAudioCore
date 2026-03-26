package utils;

import engine.AudioEngineFactory;
import engine.FormatAudioEngine;
import engine.Mp3AudioEngine;
import engine.WavAudioEngine;
import processors.GainProcessor;

import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.File;
import java.io.IOException;
import java.util.Scanner;

public class Player {

    private FileLoader fileLoader = new FileLoader();
    private File file = fileLoader.getFile();
    private GainProcessor gainProcessor = new GainProcessor(0.5f);

    public void start() {
        try (FormatAudioEngine audioEngine = AudioEngineFactory.createEngine(gainProcessor, file);
             Scanner scanner = new Scanner(System.in)) {

            String response = "";
            System.out.println("Cargando archivo: " + file.getName());

            while (!response.equalsIgnoreCase("Q")) {
                System.out.println("\n[P] Play/Pause | [S] Stop | [+] Vol Up | [-] Vol Down | [Q] Quit");
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