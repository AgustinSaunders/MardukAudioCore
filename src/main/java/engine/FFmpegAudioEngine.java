package engine;

import processors.GainProcessor;
import utils.AudioUtils;
import utils.FFmpegAudioDecoder;

import javax.sound.sampled.*;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

public class FFmpegAudioEngine implements FormatAudioEngine {
    private FFmpegAudioDecoder decoder;
    private SourceDataLine outputLine;
    private GainProcessor gainProcessor;
    private String filePath;
    private AtomicBoolean isPlaying = new AtomicBoolean(false);
    private AtomicBoolean isPaused = new AtomicBoolean(false);
    private Thread playbackThread;

    // Buffer de floats para el procesamiento (32-bit Float Estéreo)
    private static final int SAMPLES_PER_READ = 1024;
    private static int BIT_DEPTH_OUTPUT = 16;
    private static int BYTES = 2;
    private static final int BUFFER_SIZE_FLOAT = SAMPLES_PER_READ * 2; // *2 por estéreo
    float[] floatBuffer = new float[BUFFER_SIZE_FLOAT];
    byte[] byteBuffer = new byte[BUFFER_SIZE_FLOAT * BYTES];

    public FFmpegAudioEngine(GainProcessor gainProcessor, String filePath) {
        this.gainProcessor = gainProcessor;
        this.filePath = filePath;
    }

    @Override
    public void play() throws Exception {
        if (isPlaying.get()) {
            internalStop();
        }

        // Inicializamos el decodificador nativo
        decoder = new FFmpegAudioDecoder();
        decoder.open(filePath);

//        // El DAW siempre trabajará a 44100Hz, 32-bit Float, Estéreo
//        AudioFormat format = new AudioFormat(AudioFormat.Encoding.PCM_FLOAT, 44100, 32, 2, 8, 44100, false);
//        DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);

        // Salida compatible: 16 bits, Signed Integer (2 bytes por frame por canal = 4 bytes/frame)
        AudioFormat format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 44100, 16, 2, 4, 44100, false);
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);

        this.outputLine = (SourceDataLine) AudioSystem.getLine(info);
        // Usamos un buffer pequeño para baja latencia (32KB aprox)
        this.outputLine.open(format, 32768);
        this.outputLine.start();

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
            if (outputLine != null) outputLine.stop();
        }
    }

    @Override
    public void resume() {
        if (isPlaying.get() && isPaused.get()) {
            isPaused.set(false);
            if (outputLine != null) outputLine.start();
        }
    }

    @Override
    public void stop() {
        if (!isPlaying.get()) return;

        gainProcessor.setGain(0.0f);

        new Thread(() -> {
            try {
                // Esperamos un poco más (400ms) porque FFmpeg y la línea tienen más latencia
                Thread.sleep(400);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                internalStop();
            }
        }).start();
    }

    private void internalStop() {
        if (!isPlaying.get() && !isPaused.get() && decoder == null) return;

        isPlaying.set(false);
        isPaused.set(false);

        if (outputLine != null) {
            try {
                outputLine.drain();
                outputLine.stop();
                outputLine.close();
            } catch (Exception e) {}
            outputLine = null;
        }

        if (decoder != null) {
            decoder.close();
            decoder = null;
        }

        System.gc();
    }

    private void playbackLoop() {
        try {

            int samplesRead;
            // decoder.readNextSamples debe devolver la cantidad de floats leídos
            while (isPlaying.get()) {

                while (isPaused.get() && isPlaying.get()) {
                    Thread.sleep(10);
                }

                if (!isPlaying.get()) break;

                samplesRead = decoder.readNextSamples(floatBuffer);
                if (samplesRead == -1) break;

                // 1. Procesar
                gainProcessor.process(floatBuffer, samplesRead);

                // 2. Convertir a 16 bits (porque tu línea es de 16 bits ahora)
                // Little Endian = false es lo más común en Linux
                AudioUtils.floatsToBytes(floatBuffer, byteBuffer, samplesRead, BIT_DEPTH_OUTPUT, false);

                // 3. Escribir exactamente la cantidad de bytes generados
                outputLine.write(byteBuffer, 0, samplesRead * BYTES);

            }
            stop();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            internalStop();
        }
    }

    @Override
    public void setVolume(float volume) {
        if (gainProcessor != null) gainProcessor.setGain(volume);
    }

    @Override
    public float getVolume() {
        return gainProcessor != null ? gainProcessor.getGain() : 0.0f;
    }

    @Override
    public boolean isPlaying() { return isPlaying.get(); }

    @Override
    public boolean isPaused() { return isPaused.get(); }

    @Override
    public void seek(double seconds) {
        if (decoder == null || outputLine == null) return;

        try {
            isPaused.set(true);

            outputLine.stop();
            outputLine.flush();

            decoder.seek(seconds);

            Arrays.fill(floatBuffer, 0);

            isPaused.set(false);
            outputLine.start();

        } catch (Exception e) {
            e.printStackTrace();
            isPaused.set(false);
        }
    }

    @Override
    public double getDuration() {
        return (decoder != null) ? decoder.getDuration() : 0.0;
    }

    @Override
    public void close() {
        internalStop();
        if (playbackThread != null && playbackThread.isAlive()) {
            playbackThread.interrupt();
            try { playbackThread.join(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
    }
}