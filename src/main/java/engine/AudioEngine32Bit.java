package engine;

import processors.GainProcessor;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.AudioSystem;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * High-performance 32-bit Float Audio Engine for Linux DAW.
 */
public class AudioEngine32Bit {

    private static final int SAMPLE_RATE = 44100;
    private static final int BIT_DEPTH = 32;
    private static final int CHANNELS = 2; // Stereo
    private static final int BUFFER_SIZE_FRAMES = 512; // Typical latency for DAW

    private SourceDataLine outputLine;
    private boolean running = false;

    private final GainProcessor gainProcessor;

    public AudioEngine32Bit(GainProcessor gainProcessor) {
        this.gainProcessor = gainProcessor;
    }

    public void startEngine() throws LineUnavailableException {
            // 1. Setup Audio Format (PCM_FLOAT is the industry standard for internal processing)
            AudioFormat format = new AudioFormat(
                    AudioFormat.Encoding.PCM_FLOAT,
                    SAMPLE_RATE,
                    BIT_DEPTH,
                    CHANNELS,
                    CHANNELS * (BIT_DEPTH / 8),
                    SAMPLE_RATE,
                    false // Little Endian (Required for most Linux Audio Backends)
            );

            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
            outputLine = (SourceDataLine) AudioSystem.getLine(info);

            outputLine.open(format);
            outputLine.start();

            this.running = true;
            runAudioLoop();

            System.out.println("Audio Engine started at 32-bit Float / 44.1kHz");
    }

    private void runAudioLoop() {

        float[] dspBuffer = new float[BUFFER_SIZE_FRAMES * CHANNELS];
        byte[] outputBuffer = new byte[dspBuffer.length * 4]; // 4 bytes per float

        while (running) {
            // Generate or fetch raw audio
            generateTestTone(dspBuffer);

            gainProcessor.process(dspBuffer);

            //  Fast Float-to-Byte conversion
            ByteBuffer.wrap(outputBuffer)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .asFloatBuffer()
                    .put(dspBuffer);

            // Push to Audio Driver (ALSA/JACK/Pulse)
            outputLine.write(outputBuffer, 0, outputBuffer.length);
        }
    }

    private void generateTestTone(float[] buffer) {
        for (int i = 0; i < buffer.length; i++) {
            buffer[i] = (float) Math.sin(i * 0.1);
        }
    }

    public void stop () {
        this.running = false;
        if (outputLine != null) {
            outputLine.drain();
            outputLine.close();
        }
    }

}

