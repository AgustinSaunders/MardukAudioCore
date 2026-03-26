package utils;

import java.io.File;
import java.nio.file.Path;

public class FileLoader {

    String filePath = "/home/sondsky/Music/WavFiles/Moonlight 2.wav";
//    String filePath = "/home/sondsky/Music/Mp3Files/04. STYX HELIX.mp3";
    File file = new File(filePath);

    public File getFile(){
        return  file;
    }

    public String getFileExtension() {
        String fileName = Path.of(filePath).getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');

        return (dotIndex == -1 || dotIndex == 0) ? "" : fileName.substring(dotIndex + 1);
    }
}
