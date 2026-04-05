package utils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import exceptions.AudioConversionException;

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
 * <p><strong>Exception Handling:</strong>
 * <ul>
 *   <li>{@link AudioConversionException} - For format conversion errors (null arrays, buffer size mismatches)</li>
 *   <li>{@link IllegalArgumentException} - For invalid parameter values (negative counts, unsupported bit depths)</li>
 * </ul>
 *
 * <p><strong>Byte Order Support:</strong>
 * <ul>
 *   <li>Little-Endian (default for most audio formats, e.g., WAV on Windows)</li>
 *   <li>Big-Endian (used in some audio formats, e.g., AIFF)</li>
 * </ul>
 *
 * <p><strong>Validation Features:</strong>
 * <ul>
 *   <li>Null array detection</li>
 *   <li>Buffer size validation</li>
 *   <li>Bit depth support verification</li>
 *   <li>Invalid value handling (NaN, Infinity)</li>
 *   <li>Audio clipping prevention via sample clamping</li>
 * </ul>
 *
 * <p><strong>Usage Example:</strong>
 * <pre>{@code
 * try {
 *     // Convert 4096 bytes of 16-bit little-endian audio to floats
 *     byte[] audioBytes = new byte[4096];
 *     float[] floatSamples = new float[2048];
 *     AudioUtils.bytesToFloats(audioBytes, floatSamples, 4096, 16, false);
 *     
 *     // Process samples and convert back
 *     byte[] outputBytes = new byte[4096];
 *     AudioUtils.floatsToBytes(floatSamples, outputBytes, 2048, 16, false);
 * } catch (AudioConversionException e) {
 *     logger.error("Conversion failed: {}", e.getMessage());
 * }
 * }</pre>
 *
 * @author Agustin Saunders
 * @version 1.0
 * @since 1.0
 * @see engine.FFmpegAudioEngine
 * @see processors.GainProcessor
 * @see exceptions.AudioConversionException
 */
public class AudioUtils {

    private static final Logger logger = LoggerFactory.getLogger(AudioUtils.class);

    /**
     * Supported bit depths for audio conversion.
     */
    private static final int[] SUPPORTED_BIT_DEPTHS = {16, 32};

    /**
     * Normalization factor for 16-bit signed PCM.
     */
    private static final float NORMALIZATION_16_BIT = 32768.0f;

    /**
     * Quantization factor for converting floats back to 16-bit signed PCM.
     */
    private static final float QUANTIZATION_16_BIT = 32767.0f;

    /**
     * Converts audio data from byte format to floating-point format.
     *
     * <p>This method reads audio samples from a byte buffer and converts them to
     * normalized floating-point values in the range [-1.0, 1.0].
     *
     * @param input         byte array containing raw audio data
     * @param output        float array where converted samples will be stored
     * @param bytesRead     number of bytes to read from input
     * @param bitDepth      bit depth of audio data (16 or 32)
     * @param isBigEndian   true for big-endian, false for little-endian
     *
     * @throws AudioConversionException if conversion parameters are invalid
     * @throws IllegalArgumentException if bytesRead is not multiple of (bitDepth / 8)
     */
    public static void bytesToFloats(byte[] input, float[] output, int bytesRead, int bitDepth, boolean isBigEndian) 
            throws AudioConversionException {
        try {
            // Validate arrays
            if (input == null) {
                logger.error("Input byte array is null");
                throw new AudioConversionException("Input byte array cannot be null");
            }
            if (output == null) {
                logger.error("Output float array is null");
                throw new AudioConversionException("Output float array cannot be null");
            }

            // Validate bit depth
            if (!isSupportedBitDepth(bitDepth)) {
                logger.error("Unsupported bit depth: {} (supported: 16, 32)", bitDepth);
                throw new AudioConversionException("Unsupported bit depth: " + bitDepth + " (supported: 16, 32)");
            }

            // Validate bytesRead
            if (bytesRead < 0) {
                logger.error("Invalid bytesRead: {} (must be >= 0)", bytesRead);
                throw new AudioConversionException("bytesRead cannot be negative: " + bytesRead);
            }
            if (bytesRead > input.length) {
                logger.error("bytesRead {} exceeds input array length {}", bytesRead, input.length);
                throw new AudioConversionException(
                    String.format("bytesRead (%d) exceeds input array length (%d)", bytesRead, input.length)
                );
            }

            // Validate bytesRead is multiple of sample size
            int bytesPerSample = bitDepth / 8;
            if (bytesRead % bytesPerSample != 0) {
                logger.error("bytesRead {} is not a multiple of {} (bitDepth: {})", bytesRead, bytesPerSample, bitDepth);
                throw new IllegalArgumentException(
                    String.format("bytesRead (%d) must be a multiple of %d for %d-bit audio", 
                        bytesRead, bytesPerSample, bitDepth)
                );
            }

            // Validate output capacity
            int sampleCount = bytesRead / bytesPerSample;
            if (output.length < sampleCount) {
                logger.error("Output array capacity {} is insufficient for {} samples", output.length, sampleCount);
                throw new AudioConversionException(
                    String.format("Output array too small: need %d samples, capacity is %d", sampleCount, output.length)
                );
            };

            // Perform conversion
            ByteBuffer bb = ByteBuffer.wrap(input, 0, bytesRead);
            bb.order(isBigEndian ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);

            for (int i = 0; i < sampleCount; i++) {
                try {
                    if (bitDepth == 32) {
                        output[i] = bb.getFloat();
                    } else {
                        output[i] = bb.getShort() / NORMALIZATION_16_BIT;
                    }
                } catch (Exception e) {
                    logger.error("Error converting sample at index {}: {}", i, e.getMessage());
                    throw new AudioConversionException("Error converting sample at index " + i, e);
                }
            }


        } catch (AudioConversionException | IllegalArgumentException e) {
            throw e;
        } catch (ArrayIndexOutOfBoundsException e) {
            logger.error("Array index out of bounds: {}", e.getMessage());
            throw new AudioConversionException("Array size mismatch during bytes-to-floats conversion", e);
        } catch (Exception e) {
            logger.error("Unexpected error during bytes-to-floats conversion: {}", e.getMessage());
            throw new AudioConversionException("Failed to convert bytes to floats", e);
        }
    }

    /**
     * Converts audio data from floating-point format to byte format.
     *
     * <p>This method reads normalized floating-point audio samples in the range [-1.0, 1.0]
     * and converts them to byte representation.
     *
     * @param input         float array containing normalized audio samples [-1.0, 1.0]
     * @param output        byte array where converted data will be stored
     * @param samplesCount  number of float samples to convert
     * @param bitDepth      bit depth for output data (16 or 32)
     * @param isBigEndian   true for big-endian, false for little-endian
     *
     * @throws AudioConversionException if conversion parameters are invalid
     * @throws IllegalArgumentException if samplesCount is negative
     */
    public static void floatsToBytes(float[] input, byte[] output, int samplesCount, int bitDepth, boolean isBigEndian) 
            throws AudioConversionException {
        try {
            // Validate arrays
            if (input == null) {
                logger.error("Input float array is null");
                throw new AudioConversionException("Input float array cannot be null");
            }
            if (output == null) {
                logger.error("Output byte array is null");
                throw new AudioConversionException("Output byte array cannot be null");
            }

            // Validate bit depth
            if (!isSupportedBitDepth(bitDepth)) {
                logger.error("Unsupported bit depth: {} (supported: 16, 32)", bitDepth);
                throw new AudioConversionException("Unsupported bit depth: " + bitDepth + " (supported: 16, 32)");
            }

            // Validate samplesCount
            if (samplesCount < 0) {
                logger.error("Invalid samplesCount: {} (must be >= 0)", samplesCount);
                throw new IllegalArgumentException("samplesCount cannot be negative: " + samplesCount);
            }
            if (samplesCount > input.length) {
                logger.error("samplesCount {} exceeds input array length {}", samplesCount, input.length);
                throw new AudioConversionException(
                    String.format("samplesCount (%d) exceeds input array length (%d)", samplesCount, input.length)
                );
            }

            // Validate output capacity
            int bytesPerSample = bitDepth / 8;
            int bytesRequired = samplesCount * bytesPerSample;
            if (output.length < bytesRequired) {
                logger.error("Output array capacity {} is insufficient for {} bytes", output.length, bytesRequired);
                throw new AudioConversionException(
                    String.format("Output array too small: need %d bytes, capacity is %d", bytesRequired, output.length)
                );
            }

            // Perform conversion
            ByteBuffer bb = ByteBuffer.wrap(output);
            bb.order(isBigEndian ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);

            for (int i = 0; i < samplesCount; i++) {
                try {
                    // Handle special float values
                    float sample = input[i];
                    if (Float.isNaN(sample)) {
                        logger.warn("NaN sample detected at index {}, replacing with 0.0", i);
                        sample = 0.0f;
                    }
                    if (Float.isInfinite(sample)) {
                        logger.warn("Infinite sample detected at index {}, clamping to ±1.0", i);
                        sample = sample > 0 ? 1.0f : -1.0f;
                    }

                    // Clamp to valid range
                    sample = Math.max(-1.0f, Math.min(1.0f, sample));

                    if (bitDepth == 32) {
                        bb.putFloat(sample);
                    } else {
                        bb.putShort((short) (sample * QUANTIZATION_16_BIT));
                    }
                } catch (Exception e) {
                    logger.error("Error converting sample at index {}: {}", i, e.getMessage());
                    throw new AudioConversionException("Error converting sample at index " + i, e);
                }
            }

        } catch (AudioConversionException | IllegalArgumentException e) {
            throw e;
        } catch (ArrayIndexOutOfBoundsException e) {
            logger.error("Array index out of bounds: {}", e.getMessage());
            throw new AudioConversionException("Array size mismatch during floats-to-bytes conversion", e);
        } catch (Exception e) {
            logger.error("Unexpected error during floats-to-bytes conversion: {}", e.getMessage());
            throw new AudioConversionException("Failed to convert floats to bytes", e);
        }
    }

    /**
     * Checks if the specified bit depth is supported.
     *
     * @param bitDepth the bit depth to check
     * @return true if supported (16 or 32), false otherwise
     */
    private static boolean isSupportedBitDepth(int bitDepth) {
        for (int supported : SUPPORTED_BIT_DEPTHS) {
            if (supported == bitDepth) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gets array of supported bit depths.
     *
     * @return array containing supported bit depths (16, 32)
     */
    public static int[] getSupportedBitDepths() {
        return SUPPORTED_BIT_DEPTHS.clone();
    }

    /**
     * Validates conversion parameters before performing conversion.
     *
     * @param inputLength length of input array
     * @param outputLength length of output array
     * @param dataSize amount of data to convert
     * @param bitDepth bit depth in use
     * @throws AudioConversionException if parameters are invalid
     */
    public static void validateConversionParameters(int inputLength, int outputLength, int dataSize, int bitDepth) 
            throws AudioConversionException {
        if (!isSupportedBitDepth(bitDepth)) {
            throw new AudioConversionException("Unsupported bit depth: " + bitDepth);
        }
        if (dataSize < 0) {
            throw new AudioConversionException("Data size cannot be negative: " + dataSize);
        }
        if (inputLength < dataSize) {
            throw new AudioConversionException("Input array too small for specified data size");
        }
        int bytesPerSample = bitDepth / 8;
        int outputNeeded = dataSize / bytesPerSample;
        if (outputLength < outputNeeded) {
            throw new AudioConversionException(
                String.format("Output array too small: need %d, have %d", outputNeeded, outputLength)
            );
        }
    }
}
