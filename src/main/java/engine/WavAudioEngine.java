package engine;

import processors.GainProcessor;
import utils.AudioUtils;

import javax.sound.sampled.*;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

public class WavAudioEngine implements FormatAudioEngine {
    private AudioInputStream audioInputStream;
    private SourceDataLine outputLine;
    private GainProcessor gainProcessor;
    private File file;
    private AtomicBoolean isPlaying = new AtomicBoolean(false);
    private AtomicBoolean isPaused = new AtomicBoolean(false);
    private Thread playbackThread;

    private static final int BUFFER_SIZE = 4096;

    public WavAudioEngine(GainProcessor gainProcessor, File file) {
        this.gainProcessor = gainProcessor;
        this.file = file;
    }

    @Override
    public void play() throws Exception {
        if (isPlaying.get()) {
            stop();
        }

        this.audioInputStream = AudioSystem.getAudioInputStream(file);

        AudioFormat format = audioInputStream.getFormat();
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);

        this.outputLine = (SourceDataLine) AudioSystem.getLine(info);
        outputLine.open(format);
        outputLine.start();

        isPlaying.set(true);
        isPaused.set(false);

        playbackThread = new Thread(this::playbackLoop);
        playbackThread.setPriority(Thread.MAX_PRIORITY);
        playbackThread.start();
    }
    @Override
    public void pause() {
        if (isPlaying.get() && !isPaused.get()) {
            isPaused.set(true);
            if (outputLine != null) {
                outputLine.stop();
            }
        }
    }
    @Override
    public void resume() {
        if (isPlaying.get() && isPaused.get()) {
            isPaused.set(false);
            if (outputLine != null) {
                outputLine.start();
            }
        }
    }
    @Override
    public void stop() {
        isPlaying.set(false);
        isPaused.set(false);

        if (outputLine != null) {
            outputLine.drain();
            outputLine.close();
        }

        if (audioInputStream != null) {
            try {
                audioInputStream.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (playbackThread != null && playbackThread.isAlive()) {
            try {
                playbackThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
    @Override
    public void setVolume(float volume) {
        if (gainProcessor != null) {
            gainProcessor.setGain(Math.max(0.0f, Math.min(1.0f, volume)));
        }
    }
    @Override
    public float getVolume() {
        return gainProcessor != null ? gainProcessor.getGain() : 0.0f;
    }
    @Override
    public boolean isPlaying() {
        return isPlaying.get();
    }
    @Override
    public boolean isPaused() {
        return isPaused.get();
    }
    private void playbackLoop() {
        try {
            AudioFormat format = audioInputStream.getFormat();
            int bitDepth = format.getSampleSizeInBits();
            int bytesPerSample = bitDepth / 8;

            byte[] buffer = new byte[BUFFER_SIZE];
            // El floatBuffer debe ser del mismo tamaño proporcional
            float[] floatBuffer = new float[BUFFER_SIZE / bytesPerSample];

            int bytesRead;
            while (isPlaying.get() && (bytesRead = audioInputStream.read(buffer)) != -1) {

                while (isPaused.get() && isPlaying.get()) {
                    Thread.sleep(10);
                }

                if (!isPlaying.get()) {
                    break;
                }

                // 1. Calcular exactamente cuántas muestras hay en esta lectura
                int sampleCount = bytesRead / bytesPerSample;

                // 2. Convertir solo los bytes que se leyeron (bytesRead)
                AudioUtils.bytesToFloats(buffer, floatBuffer, bytesRead, bitDepth, format.isBigEndian());

                // 3. Procesar SOLO las muestras reales
                gainProcessor.process(floatBuffer, sampleCount);

                // 4. Convertir de vuelta SOLO las muestras procesadas
                AudioUtils.floatsToBytes(floatBuffer, buffer, sampleCount, bitDepth, format.isBigEndian());

                // 5. Escribir en la tarjeta de sonido exactamente lo que leímos
                outputLine.write(buffer, 0, bytesRead);
            }
            stop();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    @Override
    public void close() {
        // 1. Forzamos la detención del bucle de reproducción
        isPlaying.set(false);
        isPaused.set(false);

        // 2. Cerramos la línea de salida (la "manguera" de audio)
        if (outputLine != null) {
            try {
                outputLine.stop();
                outputLine.flush(); // Limpia datos residuales
                outputLine.close();
            } catch (Exception e) {
                // Ignorar si ya está cerrada
            }
        }

        // 3. Cerramos el archivo de audio
        if (audioInputStream != null) {
            try {
                audioInputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // 4. Esperamos a que el hilo termine para evitar hilos "zombies"
        if (playbackThread != null && playbackThread.isAlive()) {
            try {
                playbackThread.interrupt(); // Interrumpimos si está en sleep
                playbackThread.join(500);   // Esperamos medio segundo a que muera
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}

