import engine.AudioEngine32Bit;
import processors.GainProcessor;

public class Main {
    public static void main(String[] args) {
        GainProcessor gain = new GainProcessor(0.5f);
        AudioEngine32Bit engine = new AudioEngine32Bit(gain);

        // Run Engine in a separate Thread
        Thread audioThread = new Thread(() -> {
            try {
                engine.startEngine();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        audioThread.setPriority(Thread.MAX_PRIORITY);
        audioThread.start();

        System.out.println("Marduk Audio Core is running...");

    }
}
