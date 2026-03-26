package engine;

import processors.GainProcessor;

import javax.sound.sampled.*;
import java.io.File;
import java.io.IOException;

public class Mp3AudioEngine implements FormatAudioEngine{
    private Clip audioClip;
    private FloatControl volumeControl;
    private boolean isPaused = false;
    private final File file;
    private final GainProcessor gainProcessor; // Nota: Clip no usa GainProcessor manualmente

    public Mp3AudioEngine(GainProcessor gainProcessor, File file) {
        this.gainProcessor = gainProcessor;
        this.file = file;
    }

    @Override
    public void play() throws Exception {
        if (audioClip != null) {
            close(); // Limpieza completa antes de abrir uno nuevo
        }

        AudioInputStream baseStream = AudioSystem.getAudioInputStream(file);
        AudioFormat baseFormat = baseStream.getFormat();

        // Obligamos a que se decodifique a PCM_SIGNED para que el Clip pueda leerlo
        AudioFormat decodedFormat = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                baseFormat.getSampleRate(),
                16,
                baseFormat.getChannels(),
                baseFormat.getChannels() * 2,
                baseFormat.getSampleRate(),
                false
        );

        // Esta línea es la que usa la librería que instalaste para convertir MP3 -> PCM
        AudioInputStream decodedStream = AudioSystem.getAudioInputStream(decodedFormat, baseStream);

        audioClip = AudioSystem.getClip();
        audioClip.open(decodedStream);

        if (audioClip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            volumeControl = (FloatControl) audioClip.getControl(FloatControl.Type.MASTER_GAIN);
            setVolume(gainProcessor.getGain());
        }

        audioClip.start();
        isPaused = false;
    }

    @Override
    public void pause() {
        if (audioClip != null && audioClip.isRunning()) {
            audioClip.stop();
            isPaused = true;
        }
    }

    @Override
    public void resume() {
        if (audioClip != null && isPaused) {
            audioClip.start();
            isPaused = false;
        }
    }

    @Override
    public void stop() {
        if (audioClip != null && audioClip.isRunning()) {
            // Creamos un hilo rápido para no bloquear la interfaz del usuario
            new Thread(() -> {
                try {
                    float vol = getVolume();
                    // Bajamos el volumen en 10 pasos (aprox 300ms)
                    while (vol > 0.01f) {
                        vol -= 0.1f;
                        setVolume(Math.max(0, vol));
                        Thread.sleep(30);
                    }

                    // Una vez en silencio, detenemos de verdad
                    audioClip.stop();
                    audioClip.setFramePosition(0);
                    isPaused = false;

                    // Restauramos el volumen original para la próxima vez
                    setVolume(gainProcessor.getGain());
                } catch (InterruptedException e) {
                    audioClip.stop();
                }
            }).start();
        }
    }

    @Override
    public void setVolume(float volume) {
        if (gainProcessor != null) gainProcessor.setGain(volume);

        if (volumeControl != null) {
            // 1. Guardamos el valor lineal (0.0 a 1.0) en nuestro procesador para recordarlo
            if (gainProcessor != null) {
                gainProcessor.setGain(Math.max(0.0f, Math.min(1.0f, volume)));
            }

            if (volumeControl != null) {
                // 2. Convertir lineal (0.0 - 1.0) a Decibelios
                // Usamos 0.0001 para evitar el logaritmo de cero (que es infinito)
                float dB = (float) (Math.log10(Math.max(0.0001, volume)) * 20.0);

                // 3. Limitar el valor entre los mínimos y máximos que soporta el hardware
                float min = volumeControl.getMinimum(); // Suele ser -80.0 dB (silencio)
                float max = volumeControl.getMaximum(); // Suele ser 6.0 dB (amplificación)

                volumeControl.setValue(Math.max(min, Math.min(max, dB)));
            }
        }
    }

    @Override
    public float getVolume() {
        return gainProcessor != null ? gainProcessor.getGain() : 0.0f;
    }

    @Override
    public boolean isPlaying() {
        return audioClip != null && audioClip.isRunning();
    }

    @Override
    public boolean isPaused() {
        return isPaused;
    }

    @Override
    public void close() {
        if (audioClip != null) {
            audioClip.stop();
            audioClip.close();
        }
    }
}
