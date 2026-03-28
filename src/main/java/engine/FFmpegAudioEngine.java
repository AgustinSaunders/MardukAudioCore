package engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import processors.GainProcessor;
import utils.AudioUtils;
import utils.FFmpegAudioDecoder;

import javax.sound.sampled.*;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

public class FFmpegAudioEngine implements FormatAudioEngine {
    private static final Logger logger = LoggerFactory.getLogger(FFmpegAudioEngine.class);

    private final ReentrantLock decoderLock = new ReentrantLock();
    private FFmpegAudioDecoder decoder;
    private SourceDataLine outputLine;
    private GainProcessor gainProcessor;
    private String filePath;
    private AtomicBoolean isPlaying = new AtomicBoolean(false);
    private AtomicBoolean isPaused = new AtomicBoolean(false);
    private Thread playbackThread;

    // Buffer de floats para el procesamiento (32-bit Float Estéreo)
    private static final int SAMPLES_PER_READ = 1024;           // 1024 muestras de audio
    private static final int CHANNELS = 2;                       // Estéreo
    private static final int BIT_DEPTH_OUTPUT = 16;
    private static final int BYTES_PER_SAMPLE = 2;              // 16 bits = 2 bytes
    private static final int BUFFER_SIZE_FLOAT = SAMPLES_PER_READ * CHANNELS; // 2048 floats
    float[] floatBuffer = new float[BUFFER_SIZE_FLOAT];
    byte[] byteBuffer = new byte[BUFFER_SIZE_FLOAT * BYTES_PER_SAMPLE]; // 4096 bytes MAX

    public FFmpegAudioEngine(GainProcessor gainProcessor, String filePath) {
        this.gainProcessor = gainProcessor;
        this.filePath = filePath;
        logger.debug("FFmpegAudioEngine initialized for file: {}", filePath);
    }

    @Override
    public void play() throws Exception {

        logger.info("Start playing: {}", filePath);
        if (isPlaying.get()) {
            logger.debug("Playback already active, stoping...");
            internalStop();
        }

        decoder = new FFmpegAudioDecoder();
        decoder.open(filePath);

//        // El DAW siempre trabajará a 44100Hz, 32-bit Float, Estéreo
//        AudioFormat format = new AudioFormat(AudioFormat.Encoding.PCM_FLOAT, 44100, 32, 2, 8, 44100, false);
//        DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);

        // Salida compatible: 16 bits, Signed Integer (2 bytes por frame por canal = 4 bytes/frame)
        AudioFormat format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 44100, 16, 2, 4, 44100, false);
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);

        this.outputLine = (SourceDataLine) AudioSystem.getLine(info);
        // Using small buffer for low latency (32KB approx)
        this.outputLine.open(format, 32768);
        this.outputLine.start();

        logger.debug("Audio line open and started");

        isPlaying.set(true);
        isPaused.set(false);

        playbackThread = new Thread(this::playbackLoop);
        playbackThread.setPriority(Thread.MAX_PRIORITY);
        playbackThread.setName("AudioPlaybackThread");
        playbackThread.start();

        logger.info("AudioPlaybackThread started");
    }

    @Override
    public void pause() {
        if (isPlaying.get() && !isPaused.get()) {
            logger.info("Pausing playback");
            isPaused.set(true);
            if (outputLine != null) outputLine.stop();
        }
    }

    @Override
    public void resume() {
        if (isPlaying.get() && isPaused.get()) {
            logger.info("Continue playing");
            isPaused.set(false);
            if (outputLine != null) outputLine.start();
        }
    }

    @Override
    public void stop() {
        logger.info("Stoping playback");
        if (!isPlaying.get()) return;

        gainProcessor.setGain(0.0f);
        isPlaying.set(false);

        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        internalStop();
    }

    private void internalStop() {
        logger.debug("Executing internalStop");
        if (!isPlaying.get() && !isPaused.get() && decoder == null) return;

        SourceDataLine line = this.outputLine;

        this.outputLine = null;

        isPlaying.set(false);
        isPaused.set(false);

        if (line != null) {
            try {
                line.drain();
                line.stop();
                line.close();
                logger.debug("Outputline closed successfully");
            } catch (Exception e) {
                logger.error("Error closing outputLine: {}", e.getMessage());
            }
        }

        if (decoder != null) {
            try {
                decoder.close();
                logger.debug("Decoder closed");
            } catch (Exception e) {
                logger.error("Error closing decoder: {}", e.getMessage());
                e.printStackTrace();
            }
            decoder = null;
        }
    }

    private void playbackLoop() {
        logger.debug("Playback loop initialized");
        try {
            int samplesRead;
            int totalSamplesPlayed = 0;

            while (isPlaying.get()) {
                while (isPaused.get() && isPlaying.get()) {
                    Thread.sleep(10);
                }

                if (!isPlaying.get()) break;

                decoderLock.lock();
                try {
                    if (!isPlaying.get()) break;
                    samplesRead = decoder.readNextSamples(floatBuffer);
                } finally {
                    decoderLock.unlock();
                }

                if (samplesRead == -1) {
                    logger.debug("End of file reached");
                    break;}
                if (samplesRead == 0) continue;

                gainProcessor.process(floatBuffer, samplesRead);

                // Convert to 16 bits PCM (signed) for output
                AudioUtils.floatsToBytes(floatBuffer, byteBuffer, samplesRead, BIT_DEPTH_OUTPUT, false);

                int bytesToWrite = samplesRead * BYTES_PER_SAMPLE;
                outputLine.write(byteBuffer, 0, bytesToWrite);

                totalSamplesPlayed += samplesRead;
            }

            logger.info("Playback ended. Total samples: {}", totalSamplesPlayed);
            stop();
        } catch (Exception e) {
            logger.error("Error on playback loop: {}", e.getMessage());
            e.printStackTrace();
        } finally {
            internalStop();
        }
    }


    @Override
    public void setVolume(float volume) {
        if (gainProcessor != null) {
            gainProcessor.setGain(volume);
            logger.debug("Volume set at: {}%", volume * 100);
        }
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
        logger.info("Seek called to: {} seconds", seconds);
        if (decoder == null || outputLine == null) {
            logger.warn("Seek could not be done: decoder or outputLine is null");
            return;
        }

        try {
            isPaused.set(true);

            outputLine.stop();
            outputLine.flush();

            decoderLock.lock();
            try {
                decoder.seek(seconds);
            } finally {
                decoderLock.unlock();
            }

            Arrays.fill(floatBuffer, 0);

            isPaused.set(false);
            outputLine.start();

            logger.info("Seek completed succesfuly");

        } catch (Exception e) {
            logger.error("Error during seek: {}", e.getMessage());
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
        logger.debug("Closing FFmpegAudioEngine");
        internalStop();
        if (playbackThread != null && playbackThread.isAlive()) {
            playbackThread.interrupt();
            try {
                playbackThread.join(500);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        logger.info("FFmpegAudioEngine closed successfully");
    }
}