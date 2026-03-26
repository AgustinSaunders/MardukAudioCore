package processors;

public class GainProcessor implements AudioProcessor {

    private volatile float targetGain; // Lo que el usuario pide
    private float currentGain;         // Lo que se aplica en el momento (suave)
    private static final float FADE_SPEED = 0.000075f; // Ajusta para velocidad de rampa

    public GainProcessor(float initialGain) {
        this.targetGain = initialGain;
        this.currentGain = initialGain; // Al inicio coinciden
    }

    public void setGain(float gain) {
        this.targetGain = Math.max(0.0f, Math.min(1.0f, gain));
    }

    public float getGain() {
        return targetGain;
    }

    public void process(float[] samples, int sampleCount) {
        for (int i = 0; i < sampleCount; i++) {
            // 1. Acercar el volumen actual al objetivo poco a poco
            if (currentGain < targetGain) {
                currentGain = Math.min(targetGain, currentGain + FADE_SPEED);
            } else if (currentGain > targetGain) {
                currentGain = Math.max(targetGain, currentGain - FADE_SPEED);
            }

            // 2. Aplicar el volumen suavizado a la muestra
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
