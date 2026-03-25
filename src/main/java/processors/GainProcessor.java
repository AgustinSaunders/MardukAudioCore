package processors;

public class GainProcessor implements AudioProcessor {

    private volatile float gain;

    public GainProcessor(float initialGain) {
        this.gain =initialGain;
    }

    public void setGain(float gain) {
        this.gain = gain;
    }

    public float getGain(){
        return gain;
    }

    public void process(float[] samples, int sampleCount) {
        float currentGain = this.gain; // volumen actual (0.0 a 1.0)

        for (int i = 0; i < sampleCount; i++) {
            samples[i] = samples[i] * currentGain;
        }
    }

    @Override
    public void process(float[] samples) {

    }
}