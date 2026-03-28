package processors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GainProcessor implements AudioProcessor {
    private static final Logger logger = LoggerFactory.getLogger(GainProcessor.class);

    private volatile float targetGain;
    private float currentGain;
    private static final float FADE_SPEED = 0.000075f;

    public GainProcessor(float initialGain) {
        this.targetGain = initialGain;
        this.currentGain = initialGain;
        logger.debug("GainProcessor initialized with gain: {}%", initialGain * 100);
    }

    public void setGain(float gain) {
        this.targetGain = Math.max(0.0f, Math.min(1.0f, gain));
        if (Math.abs(this.targetGain - this.currentGain) > 0.01f) {
            logger.debug("Gain target establish at: {}%", this.targetGain * 100);
        }
    }

    public float getGain() {
        return targetGain;
    }

    public void process(float[] samples, int sampleCount) {
        for (int i = 0; i < sampleCount; i++) {
            if (currentGain < targetGain) {
                currentGain = Math.min(targetGain, currentGain + FADE_SPEED);
            } else if (currentGain > targetGain) {
                currentGain = Math.max(targetGain, currentGain - FADE_SPEED);
            }

            samples[i] *= currentGain;
        }
    }

    @Override
    public void process(float[] samples) {
        process(samples, samples.length);
    }

    public boolean isFinished() {
        return Math.abs(currentGain - targetGain) < 0.0001f;
    }
}
