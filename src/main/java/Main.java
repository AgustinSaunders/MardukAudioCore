import utils.Player;

import javax.sound.sampled.*;

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