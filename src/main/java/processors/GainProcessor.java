package processors;

public class GainProcessor implements AudioProcessor {
    private volatile float gain = 1.0f;

    public void setGain(float gain) {
        this.gain = gain;
    }

    @Override
    public void process(float[] samples) {
        for (int i = 0; i < samples.length; i++) {
            samples[i] *= gain;

            // Hard Clipping/limiter
            if (samples[i] > 1.0f) samples[i] = 1.0f;
            if (samples[i] < -1.0f) samples[i] = -1.0f;
        }
    }
}