import utils.FileLoader;
import utils.Player;

import javax.sound.sampled.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Scanner;


public class Main {
    public static void main(String[] args) {
        Player player = new Player();

        // Run Engine in a separate Thread
        Thread audioThread = new Thread(() -> {
            try {
                player.start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        audioThread.setPriority(Thread.MAX_PRIORITY);
        audioThread.start();

        System.out.println("Marduk Audio Core is running...");
    }
}