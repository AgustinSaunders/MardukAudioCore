package utils;

import java.io.File;
import java.nio.file.Path;

public class FileLoader {
//    WAV
//    String filePath = "/home/sondsky/Music/WavFiles/Moonlight 2.wav";
//    String filePath = "/home/sondsky/Music/WavFiles/sonican-upbeat-latin-guitar-30-seconds-478219.wav";
//    String filePath = "/home/sondsky/Music/WavFiles/31 - En la sangre como me gusta a mi.wav";
//    String filePath = "/home/sondsky/Music/WavFiles/Memories From War gpx.wav";

//    Mp3
//    String filePath = "/home/sondsky/Music/Mp3Files/04. STYX HELIX.mp3";
    String filePath = "/home/sondsky/Music/Mp3Files/03 - Godzilla.mp3";
//    String filePath = "/home/sondsky/Music/Mp3Files/05 No Te Olvide.mpp3";


    public String getFilePath(){
        return  filePath;
    }

    public String getFileExtension() {
        String fileName = Path.of(filePath).getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');

        return (dotIndex == -1 || dotIndex == 0) ? "" : fileName.substring(dotIndex + 1);
    }
}
