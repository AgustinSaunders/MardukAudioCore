package engine;

import processors.GainProcessor;

import java.io.File;

public class AudioEngineFactory {

    public static FormatAudioEngine createEngine(GainProcessor gainProcessor, File file) {
        String fileName = file.getName().toLowerCase();

        if (fileName.endsWith(".wav")) {
            return new WavAudioEngine(gainProcessor, file);
        } else if (fileName.endsWith(".mp3")) {
            return new Mp3AudioEngine(gainProcessor, file);
        } else {
            throw new IllegalArgumentException("Formato de archivo no soportado: " + fileName);
        }
    }
}
