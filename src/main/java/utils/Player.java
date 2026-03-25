package utils;

import engine.FormatAudioEngine;
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
    // Supongamos que GainProcessor recibe el volumen inicial
    private GainProcessor gainProcessor = new GainProcessor(0.5f);

    public void start() {
        // El bloque try-with-resources cerrará automáticamente audioEngine y scanner
        try (FormatAudioEngine audioEngine = new WavAudioEngine(gainProcessor, file);
             Scanner scanner = new Scanner(System.in)) {

            String response = "";
            System.out.println("Cargando archivo: " + file.getName());

            while (!response.equals("Q")) {
                System.out.println("\n--- Controles ---");
                System.out.println("P = Play / Pause / Resume");
                System.out.println("S = Stop (Detener completamente)");
                System.out.println("Q = Quit");
                System.out.print("Tu elección: ");

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
                    case "Q" -> {
                        // No es estrictamente necesario llamar a close() aquí
                        // porque el try-with-resources lo hará al salir del bloque.
                        System.out.println("Cerrando motor...");
                    }
                    default -> System.out.println("Opción inválida.");
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