package utils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Utility class for audio format conversions between bytes and floating-point samples.
 *
 * This class provides static methods for converting audio data between byte representation
 * (typically used for I/O and storage) and floating-point representation (used for audio
 * processing and manipulation). It supports multiple bit depths (16-bit and 32-bit PCM)
 * and handles both big-endian and little-endian byte ordering.
 *
 * <p><strong>Audio Format Support:</strong>
 * <ul>
 *   <li><b>16-bit PCM_SIGNED:</b> Range -32768 to 32767 (converted to -1.0 to 1.0)</li>
 *   <li><b>32-bit PCM_FLOAT:</b> Range -1.0 to 1.0 (native floating-point)</li>
 * </ul>
 *
 * <p><strong>Byte Order Support:</strong>
 * <ul>
 *   <li>Little-Endian (default for most audio formats, e.g., WAV on Windows)</li>
 *   <li>Big-Endian (used in some audio formats, e.g., AIFF)</li>
 * </ul>
 *
 * <p><strong>Conversion Pipeline:</strong>
 * <pre>
 * Audio File (bytes)
 *     ↓ (bytesToFloats)
 * Float Samples [-1.0 to 1.0]
 *     ↓ (processing/gain control)
 * Float Samples (processed)
 *     ↓ (floatsToBytes)
 * Audio Output (bytes)
 * </pre>
 *
 * <p><strong>Usage Examples:</strong>
 * <pre>{@code
 * // Convert bytes to floats (16-bit little-endian)
 * byte[] audioBytes = new byte[4096];
 * float[] audioSamples = new float[2048];
 * AudioUtils.bytesToFloats(audioBytes, audioSamples, 4096, 16, false);
 *
 * // Process the audio samples (e.g., apply gain)
 * for (int i = 0; i < audioSamples.length; i++) {
 *     audioSamples[i] *= 0.5f; // Reduce volume by 50%
 * }
 *
 * // Convert floats back to bytes (16-bit little-endian)
 * byte[] outputBytes = new byte[4096];
 * AudioUtils.floatsToBytes(audioSamples, outputBytes, 2048, 16, false);
 * }</pre>
 *
 * <p><strong>Important Notes:</strong>
 * <ul>
 *   <li>Float samples are expected to be in the range [-1.0, 1.0]</li>
 *   <li>Out-of-range float samples are automatically clamped to [-1.0, 1.0] before conversion</li>
 *   <li>This prevents audio clipping and data loss during conversion to PCM</li>
 *   <li>Buffer sizes must be adequate to hold the converted data</li>
 * </ul>
 *
 * @author Agustin Saunders
 * @version 1.0
 * @since 1.0
 * @see engine.FFmpegAudioEngine
 * @see processors.GainProcessor
 */
public class AudioUtils {

    /**
     * Converts audio data from byte format to floating-point format.
     *
     * <p>This method reads audio samples from a byte buffer and converts them to
     * normalized floating-point values in the range [-1.0, 1.0]. It supports multiple
     * bit depths and byte orders to accommodate different audio formats.
     *
     * <p><strong>Conversion Details:</strong>
     * <ul>
     *   <li><b>16-bit PCM_SIGNED:</b>
     *     <ul>
     *       <li>Input range: -32768 to 32767 (signed short)</li>
     *       <li>Output range: -1.0 to 1.0 (float)</li>
     *       <li>Formula: output = input / 32768.0f</li>
     *     </ul>
     *   </li>
     *   <li><b>32-bit PCM_FLOAT:</b>
     *     <ul>
     *       <li>Input range: -1.0 to 1.0 (already normalized)</li>
     *       <li>Output range: -1.0 to 1.0 (float)</li>
     *       <li>No conversion needed, direct copy</li>
     *     </ul>
     *   </li>
     * </ul>
     *
     * <p><strong>Buffer Size Calculation:</strong>
     * The output buffer must have a capacity of at least:
     * <pre>
     * capacity = bytesRead / (bitDepth / 8)
     * </pre>
     *
     * For example:
     * <ul>
     *   <li>1024 bytes at 16-bit = 512 float samples</li>
     *   <li>4096 bytes at 16-bit = 2048 float samples</li>
     *   <li>4096 bytes at 32-bit = 1024 float samples</li>
     * </ul>
     *
     * @param input         byte array containing raw audio data. The first {@code bytesRead}
     *                      bytes will be read from this array.
     * @param output        float array where the converted samples will be stored. Must have
     *                      sufficient capacity to hold all converted samples.
     * @param bytesRead     the number of bytes to read from the input array. This value
     *                      must be a multiple of (bitDepth / 8).
     * @param bitDepth      the bit depth of the audio data. Supported values: 16 or 32
     * @param isBigEndian   true if the byte data is in big-endian byte order (most significant
     *                      byte first), false for little-endian (least significant byte first)
     *
     * @throws ArrayIndexOutOfBoundsException if the output buffer is too small to hold all
     *                                        converted samples
     * @throws IllegalArgumentException if bytesRead is not a multiple of (bitDepth / 8)
     *
     * @see #floatsToBytes(float[], byte[], int, int, boolean)
     */
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

    /**
     * Converts audio data from floating-point format to byte format.
     *
     * <p>This method reads normalized floating-point audio samples in the range [-1.0, 1.0]
     * and converts them to byte representation. It supports multiple bit depths and byte orders
     * to produce audio data compatible with various audio output formats.
     *
     * <p><strong>Conversion Details:</strong>
     * <ul>
     *   <li><b>16-bit PCM_SIGNED:</b>
     *     <ul>
     *       <li>Input range: -1.0 to 1.0 (float, recommended)</li>
     *       <li>Output range: -32768 to 32767 (signed short)</li>
     *       <li>Formula: output = (short)(input * 32767.0f)</li>
     *     </ul>
     *   </li>
     *   <li><b>32-bit PCM_FLOAT:</b>
     *     <ul>
     *       <li>Input range: -1.0 to 1.0 (float)</li>
     *       <li>Output range: -1.0 to 1.0 (float in bytes)</li>
     *       <li>Direct byte representation of float</li>
     *     </ul>
     *   </li>
     * </ul>
     *
     * <p><strong>Audio Clipping Prevention:</strong>
     * Input values outside the range [-1.0, 1.0] are automatically clamped to prevent
     * digital clipping and audio distortion:
     * <pre>{@code
     * float sample = Math.max(-1.0f, Math.min(1.0f, input[i]));
     * }</pre>
     *
     * <p><strong>Buffer Size Calculation:</strong>
     * The output buffer must have a capacity of at least:
     * <pre>
     * capacity = samplesCount * (bitDepth / 8)
     * </pre>
     *
     * For example:
     * <ul>
     *   <li>512 samples at 16-bit = 1024 bytes</li>
     *   <li>2048 samples at 16-bit = 4096 bytes</li>
     *   <li>1024 samples at 32-bit = 4096 bytes</li>
     * </ul>
     *
     * @param input         float array containing normalized audio samples in the range [-1.0, 1.0].
     *                      Values outside this range will be clamped.
     * @param output        byte array where the converted audio data will be stored. Must have
     *                      sufficient capacity to hold all converted bytes.
     * @param samplesCount  the number of float samples to convert. This is NOT the number of
     *                      bytes, but the number of individual audio samples.
     * @param bitDepth      the target bit depth for the output audio data. Supported values: 16 or 32
     * @param isBigEndian   true if the output should be in big-endian byte order (most significant
     *                      byte first), false for little-endian (least significant byte first)
     *
     * @throws ArrayIndexOutOfBoundsException if the output buffer is too small to hold all
     *                                        converted bytes
     *
     * @see #bytesToFloats(byte[], float[], int, int, boolean)
     *
     * @example {@code
     * // Convert 2048 16-bit stereo samples to bytes (little-endian)
     * float[] samples = new float[2048];
     * byte[] output = new byte[4096]; // 2048 * 2 bytes
     * AudioUtils.floatsToBytes(samples, output, 2048, 16, false);
     * }
     */
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
