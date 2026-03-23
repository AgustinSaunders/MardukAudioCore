import processors.GainProcessor;
import utils.AudioUtils;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.DataLine;
import java.io.File;

public class AudioStreamPlayer implements Runnable {
    private final String filePath;
    private final GainProcessor gainProcessor;
    private volatile boolean running = false;

    public AudioStreamPlayer(String filePath, GainProcessor gainProcessor) {
        this.filePath = filePath;
        this.gainProcessor = gainProcessor;
    }

    @Override
    public void run() {
        try (AudioInputStream ais = AudioSystem.getAudioInputStream(new File(filePath))) {
            AudioFormat format = ais.getFormat();
            SourceDataLine line = (SourceDataLine) AudioSystem.getLine(new DataLine.Info(SourceDataLine.class, format));

            line.open(format);
            line.start();
            this.running = true;

            byte[] byteBuffer = new byte[4096];
            float[] floatBuffer = new float[2048]; // La mitad porque cada float son 2 bytes en 16-bit

            int bytesRead;
            while (running && (bytesRead = ais.read(byteBuffer)) != -1) {
                int samplesCount = bytesRead / 2;

                // 1. Convertir a Float (Formato DAW profesional)
                AudioUtils.bytesToFloats(byteBuffer, floatBuffer, bytesRead, format.isBigEndian());

                // 2. Procesar (Aquí podrías tener una lista de efectos)
                gainProcessor.process(floatBuffer);

                // 3. Convertir de vuelta a Byte para la tarjeta de sonido
                AudioUtils.floatsToBytes(floatBuffer, byteBuffer, samplesCount, format.isBigEndian());

                line.write(byteBuffer, 0, bytesRead);
            }
            line.drain();
            line.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}