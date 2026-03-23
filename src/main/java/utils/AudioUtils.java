package utils;

public class AudioUtils {
    // Convert bytes (16-bit PCM) to floats (-1.0 a 1.0)
    public static void bytesToFloats(byte[] input, float[] output, int bytesRead, boolean isBigEndian) {
        for (int i = 0, j = 0; i < bytesRead; i += 2, j++) {
            int sample = isBigEndian
                    ? (input[i] << 8) | (input[i + 1] & 0xFF)
                    : (input[i + 1] << 8) | (input[i] & 0xFF);
            output[j] = sample / 32768.0f;
        }
    }

    // Convert floats (-1.0 a 1.0) back to bytes (16-bit PCM)
    public static void floatsToBytes(float[] input, byte[] output, int samplesCount, boolean isBigEndian) {
        for (int i = 0; i < samplesCount; i++) {
            short s = (short) (input[i] * 32767.0f);
            if (isBigEndian) {
                output[i * 2] = (byte) (s >> 8);
                output[i * 2 + 1] = (byte) (s & 0xFF);
            } else {
                output[i * 2] = (byte) (s & 0xFF);
                output[i * 2 + 1] = (byte) (s >> 8);
            }
        }
    }
}