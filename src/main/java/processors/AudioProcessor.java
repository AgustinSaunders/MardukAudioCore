package processors;

public interface AudioProcessor {
    /**
     * Process a block of audio samples.
     *
     * @param samples Array of samples in float format float (-1.0 a 1.0)
     */
    void process(float[] samples);
}