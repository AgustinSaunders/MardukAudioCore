package utils;

import java.io.File;
import java.nio.file.Path;

public class FileLoader {

    String filePath = "/home/sondsky/Music/WavFiles/Moonlight 2.wav";
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
