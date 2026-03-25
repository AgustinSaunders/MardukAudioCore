package utils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class AudioUtils {

    public static void bytesToFloats(byte[] input, float[] output, int bytesRead, int bitDepth, boolean isBigEndian) {
        ByteBuffer bb = ByteBuffer.wrap(input, 0, bytesRead);
        bb.order(isBigEndian ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);

        int sampleCount = bytesRead / (bitDepth / 8);

        for (int i = 0; i < sampleCount; i++) {
            if (bitDepth == 32) {
                // 32-bit PCM_FLOAT: already comes in a -1.0 to 1.0 range
                output[i] = bb.getFloat();
            } else {
                // 16-bit PCM_SIGNED: convert from short (-32768 to 32767) to float
                output[i] = bb.getShort() / 32768.0f;
            }
        }
    }


    public static void floatsToBytes(float[] input, byte[] output, int samplesCount, int bitDepth, boolean isBigEndian) {
        ByteBuffer bb = ByteBuffer.wrap(output);
        bb.order(isBigEndian ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);

        for (int i = 0; i < samplesCount; i++) {
            float sample = Math.max(-1.0f, Math.min(1.0f, input[i]));

            if (bitDepth == 32) {
                bb.putFloat(sample);
            } else {
                bb.putShort((short) (sample * 32767.0f));
            }
        }
    }
}
