package utils;

import org.bytedeco.ffmpeg.avcodec.*;
import org.bytedeco.ffmpeg.avformat.*;
import org.bytedeco.ffmpeg.avutil.*;
import org.bytedeco.ffmpeg.swresample.*;
import org.bytedeco.javacpp.FloatPointer;
import org.bytedeco.javacpp.PointerPointer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import exceptions.*;

import static org.bytedeco.ffmpeg.avformat.AVFormatContext.AVFMT_FLAG_CUSTOM_IO;
import static org.bytedeco.ffmpeg.avformat.AVFormatContext.AVFMT_FLAG_GENPTS;
import static org.bytedeco.ffmpeg.global.avcodec.*;
import static org.bytedeco.ffmpeg.global.avformat.*;
import static org.bytedeco.ffmpeg.global.avutil.*;
import static org.bytedeco.ffmpeg.global.swresample.*;


/**
 * Audio Decoder based on FFmpeg.
 *
 * This class provides functionality for opening, decoding, and seeking within audio files using the FFmpeg
 * library via JavaCPP. It supports multiple audio formats, including MP3, WAV, FLAC, and others.
 *
 * <p>This class automatically manages FFmpeg contexts (format, codec, resampler)
 * and provides methods to read decoded audio samples in floating-point PCM format.
 *
 * <p><strong>Typical usage:</strong>
 * <pre>{@code
 * try (FFmpegAudioDecoder decoder = new FFmpegAudioDecoder()) {
 *     decoder.open("file.mp3");
 *     float[] samples = new float[4096];
 *     while (decoder.readNextSamples(samples) > 0) {
 *         // Process samples
 *     }
 * }
 * }</pre>
 *
 * @author Agustin Saunders
 * @version 1.0
 * @since 1.0
 * @see engine.FFmpegAudioEngine
 */
public class FFmpegAudioDecoder implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(FFmpegAudioDecoder.class);

    private AVFormatContext formatContext;
    private AVCodecContext codecContext;
    private SwrContext swrContext;
    private int audioStreamIndex = -1;

    private AVPacket packet = av_packet_alloc();
    private AVFrame frame = av_frame_alloc();

    private volatile boolean isClosed = false;

    /**
     * Open an audio file and prepare the decoder.
     *
     * <p>This method performs the following steps:
     * <ul>
     *   <li>Opens the input container (format)</li>
     *   <li>Searches for the audio stream</li>
     *   <li>Configures the codec context</li>
     *   <li>Sets up the resampler to convert to stereo floating-point PCM</li>
     * </ul>
     *
     * @param filePath path of the audio file to open
     * @throws AudioFileException if the file couldn't be opened or accessed
     * @throws AudioDecodingException if the audio stream can't be found or codecs aren't available
     * @throws AudioException for other audio-related errors
     * @see #close()
     */
    public void open(String filePath) throws AudioException {

        logger.info("Opening audio file: {}", filePath);

        try {
            openInputContainer(filePath);
            findAudioStream();
            setupCodecContext();
            setupResampler();

            double duration = getDuration();
            logger.info("File loaded successfully. Duration: {} seconds", duration);
            
        } catch (AudioFileException e) {
            // Re-throw file exceptions as-is
            throw e;
        } catch (AudioDecodingException e) {
            // Re-throw decoding exceptions as-is
            throw e;
        } catch (Exception e) {
            // Wrap unexpected exceptions
            logger.error("Unexpected error while opening file: {}", e.getMessage());
            throw new AudioDecodingException("Failed to open and initialize audio decoder", e);
        }
    }

    /**
     * Open the input container and extract the stream info.
     *
     * @param filePath path of the audio file to open
     * @throws AudioFileException if the file couldn't be opened or stream info couldn't be extracted
     */
    private void openInputContainer(String filePath) throws AudioFileException {
        try {
            formatContext = avformat_alloc_context();
            formatContext.flags(formatContext.flags() | AVFMT_FLAG_GENPTS | AVFMT_FLAG_CUSTOM_IO);
            formatContext.seek2any(1);

            if (avformat_open_input(formatContext, filePath, null, null) < 0) {
                logger.error("Failed to open audio file: {}", filePath);
                throw new AudioFileException("Failed to open audio file: " + filePath);
            }

            logger.debug("Looking for stream info...");
            if (avformat_find_stream_info(formatContext, (AVDictionary) null) < 0) {
                logger.error("No stream information found in file: {}", filePath);
                throw new AudioFileException("No stream information found in file: " + filePath);
            }
        } catch (AudioFileException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error opening input container: {}", e.getMessage());
            throw new AudioFileException("Error reading audio file: " + filePath, e);
        }
    }

    /**
     * Search for the first audio stream in the container.
     *
     * @throws AudioDecodingException if no audio stream is found
     */
    private void findAudioStream() throws AudioDecodingException {
        try {
            for (int i = 0; i < formatContext.nb_streams(); i++) {
                if (formatContext.streams(i).codecpar().codec_type() == AVMEDIA_TYPE_AUDIO) {
                    audioStreamIndex = i;
                    logger.debug("Audio stream found at index: {}", audioStreamIndex);
                    return;
                }
            }
            
            logger.error("No audio stream found in the file");
            throw new AudioDecodingException("No audio stream found in the file");
        } catch (AudioDecodingException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error searching for audio stream: {}", e.getMessage());
            throw new AudioDecodingException("Error searching for audio stream", e);
        }
    }

    /**
     * Configure codec context for the audio stream found.
     *
     * @throws AudioDecodingException if the codec couldn't be found or opened
     */
    private void setupCodecContext() throws AudioDecodingException {
        try {
            AVCodec codec = avcodec_find_decoder(formatContext.streams(audioStreamIndex).codecpar().codec_id());
            
            if (codec == null) {
                logger.error("Codec not found for stream");
                throw new AudioDecodingException("Audio codec not found or not supported");
            }
            
            String codecName = codec.name().getString();
            logger.info("Codec detected: {} ({})", codecName,
                    formatContext.streams(audioStreamIndex).codecpar().codec_id());

            codecContext = avcodec_alloc_context3(codec);
            avcodec_parameters_to_context(codecContext, formatContext.streams(audioStreamIndex).codecpar());

            if (avcodec_open2(codecContext, codec, (AVDictionary) null) < 0) {
                logger.error("Failed to open codec: {}", codecName);
                throw new AudioDecodingException("Failed to open audio codec: " + codecName);
            }
        } catch (AudioDecodingException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error setting up codec context: {}", e.getMessage());
            throw new AudioDecodingException("Error configuring audio codec", e);
        }
    }

    /**
     * Configure the resampler to convert audio to PCM float stereo at 44100 Hz.
     *
     * @throws AudioDecodingException if the resampler couldn't be initialized
     */
    private void setupResampler() throws AudioDecodingException {
        try {
            logger.debug("Initializing resampler...");
            AVChannelLayout outChannelLayout = new AVChannelLayout();
            av_channel_layout_default(outChannelLayout, 2);

            swrContext = swr_alloc();
            swr_alloc_set_opts2(
                    swrContext,
                    outChannelLayout,
                    AV_SAMPLE_FMT_FLT,
                    44100,
                    codecContext.ch_layout(),
                    codecContext.sample_fmt(),
                    codecContext.sample_rate(),
                    0, null
            );

            if (swr_init(swrContext) < 0) {
                logger.error("Failed to initialize resampler");
                throw new AudioDecodingException("Failed to initialize audio resampler");
            }
            logger.debug("Resampler initialized successfully");
        } catch (AudioDecodingException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error setting up resampler: {}", e.getMessage());
            throw new AudioDecodingException("Error configuring audio resampler", e);
        }
    }

    /**
     * Reads the next audio samples from the file.
     *
     * <p>This method decodes the next audio frame and resamples it to stereo (2-channel)
     * floating-point PCM format. Samples are provided in interleaved format (LRLRLR...).
     *
     * <p>Returns -1 when the end of the file is reached.
     *
     * @param outputBuffer float array where decoded samples will be stored.
     *                     It must have a capacity of at least 2048 elements.
     * @return number of samples (in pairs) written to the buffer, or -1 if EOF is reached
     * @throws AudioDecodingException if an error occurs during decoding or resampling
     * @see #seek(double)
     */
    public int readNextSamples(float[] outputBuffer) throws AudioDecodingException {
        if (isClosed || codecContext == null) {
            logger.warn("Attempt to read samples on a closed or invalid decoder");
            return -1;
        }

        int ret;
        boolean flushing = false;

        try {
            while (true) {
                if (isClosed || codecContext == null) return -1;

                try {
                    ret = avcodec_receive_frame(codecContext, frame);
                } catch (Exception e) {
                    if (isClosed || codecContext == null) return -1;
                    logger.error("Error receiving frame from codec: {}", e.getMessage());
                    throw new AudioDecodingException("Error receiving audio frame from codec", e);
                }

                if (ret == 0) {
                    // Frame decoded successfully
                    int outCount = outputBuffer.length / 2;
                    FloatPointer fp = new FloatPointer(outputBuffer);
                    PointerPointer outPointers = new PointerPointer(1).put(fp);

                    int converted = swr_convert(
                            swrContext,
                            outPointers,
                            outCount,
                            frame.data(),
                            frame.nb_samples()
                    );

                    av_frame_unref(frame);

                    if (converted > 0) {
                        logger.trace("Converted {} samples to output buffer", converted);
                        fp.get(outputBuffer, 0, converted * 2);
                        return converted * 2;
                    }
                    continue;
                }

                if (flushing && ret == AVERROR_EOF) {
                    logger.debug("End of stream reached (EOF)");
                    int outCount = outputBuffer.length / 2;
                    FloatPointer fp = new FloatPointer(outputBuffer);
                    PointerPointer outPointers = new PointerPointer(1).put(fp);

                    int converted = swr_convert(
                            swrContext,
                            outPointers,
                            outCount,
                            null,
                            0
                    );

                    if (converted > 0) {
                        logger.debug("Flushed remaining {} samples from resampler", converted);
                        fp.get(outputBuffer, 0, converted * 2);
                        return converted * 2;
                    }
                    return -1;
                }
                if (!flushing && av_read_frame(formatContext, packet) < 0) {
                    logger.debug("No more packets in file, entering flushing mode");
                    avcodec_send_packet(codecContext, null);
                    flushing = true;
                    continue;
                }

                if (!flushing) {
                    if (packet.stream_index() == audioStreamIndex) {
                        int sendRet = avcodec_send_packet(codecContext, packet);
                        if (sendRet < 0 && sendRet != AVERROR_EAGAIN()) {
                            logger.error("Error sending packet to decoder: {}", sendRet);
                            throw new AudioDecodingException("Error sending audio packet to decoder");
                        }
                    }
                    av_packet_unref(packet);
                }
            }
        } catch (AudioDecodingException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error during sample reading: {}", e.getMessage());
            throw new AudioDecodingException("Error reading audio samples", e);
        }
    }

    /**
     * Seeks to a specific position in the audio file.
     *
     * <p>This method attempts to find the specified position using multiple strategies:
     * <ol>
     *   <li>Direct search using AVSEEK_FLAG_BACKWARD</li>
     *   <li>Search with AVSEEK_FLAG_ANY if the first strategy fails</li>
     *   <li>Global search in microseconds as a final fallback</li>
     * </ol>
     *
     * <p>After seeking, the codec buffers are flushed and the resampler is reinitialized.
     *
     * @param seconds position in seconds to seek to
     * @throws AudioSeekException if the seek operation fails
     * @see #readNextSamples(float[])
     */
    public void seek(double seconds) throws AudioSeekException {
        if (isClosed || formatContext == null || codecContext == null) {
            logger.warn("Attempt to seek on a closed or invalid decoder");
            throw new AudioSeekException("Cannot seek: audio decoder not properly initialized");
        }

        if (seconds < 0) {
            logger.error("Invalid seek position: {} (must be >= 0)", seconds);
            throw new AudioSeekException("Seek position cannot be negative: " + seconds);
        }

        logger.debug("Seek requested to: {} seconds", seconds);

        try {
            double timeBase = av_q2d(formatContext.streams(audioStreamIndex).time_base());
            long ts = Math.round(seconds / timeBase);

            if (ts < 0) ts = 0;
            logger.debug("Timestamp calculated: {} (timebase: {})", ts, timeBase);

            int ret = av_seek_frame(formatContext, audioStreamIndex, ts, AVSEEK_FLAG_BACKWARD);

            if (ret < 0) {
                logger.debug("Seek failed with AVSEEK_FLAG_BACKWARD, trying with AVSEEK_FLAG_ANY");
                ret = av_seek_frame(formatContext, audioStreamIndex, ts, AVSEEK_FLAG_BACKWARD | AVSEEK_FLAG_ANY);

                if (ret < 0) {
                    logger.debug("Seek failed again, trying global seek");
                    long targetMicroseconds = (long) (seconds * AV_TIME_BASE);
                    ret = av_seek_frame(formatContext, -1, targetMicroseconds, AVSEEK_FLAG_BACKWARD | AVSEEK_FLAG_ANY);
                    
                    if (ret < 0) {
                        logger.error("All seek strategies failed for position: {} seconds", seconds);
                        throw new AudioSeekException("Seek operation failed for position " + seconds + " seconds");
                    }
                }
            }

            logger.debug("Seek completed, flushing buffers");

        } catch (AudioSeekException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error during seek to {} seconds: {}", seconds, e.getMessage());
            throw new AudioSeekException("Seek operation failed for position " + seconds + " seconds", e);
        }

        // Flush codec buffers after successful seek
        if (codecContext != null && !isClosed) {
            try {
                avcodec_flush_buffers(codecContext);
                logger.debug("Codec flushed successfully");
            } catch (Exception e) {
                logger.error("Error flushing codec: {}", e.getMessage());
                throw new AudioSeekException("Error flushing audio codec after seek", e);
            }
        }

        // Reinitialize resampler after successful seek
        if (swrContext != null && !isClosed) {
            try {
                swr_init(swrContext);
                logger.debug("Resampler reinitialized successfully");
            } catch (Exception e) {
                logger.warn("Error while reinitializing resampler: {}", e.getMessage());
                throw new AudioSeekException("Error reinitializing audio resampler after seek", e);
            }
        }
    }

    /**
     * Obtain the total length of the audio file in seconds.
     *
     * @return Length in seconds, or 0.0 if context format is not available
     */
    public double getDuration() {
        if (formatContext == null) return 0.0;
        double duration = formatContext.duration() / (double) AV_TIME_BASE;
        logger.debug("Duration obtained: {} seconds", duration);
        return duration;
    }

    /**
     * Close the decoder and releases all FFmpeg resources.
     *
     * <p>After calling this method the other methods from this class can't be used.
     *
     * @throws AudioResourceException if an error occurs during resource cleanup
     * @see AutoCloseable#close()
     */
    @Override
    public void close() throws AudioResourceException {
        logger.debug("Closing FFmpegAudioDecoder");
        
        try {
            isClosed = true;

            codecContext = null;
            swrContext = null;
            formatContext = null;
            packet = null;
            frame = null;

            logger.info("FFmpegAudioDecoder closed successfully");
            
        } catch (Exception e) {
            logger.error("Error during decoder cleanup: {}", e.getMessage());
            throw new AudioResourceException("Failed to properly close audio decoder resources", e);
        }
    }
}