package utils;

import org.bytedeco.ffmpeg.avcodec.*;
import org.bytedeco.ffmpeg.avformat.*;
import org.bytedeco.ffmpeg.avutil.*;
import org.bytedeco.ffmpeg.swresample.*;
import org.bytedeco.javacpp.FloatPointer;
import org.bytedeco.javacpp.PointerPointer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.bytedeco.ffmpeg.avformat.AVFormatContext.AVFMT_FLAG_CUSTOM_IO;
import static org.bytedeco.ffmpeg.avformat.AVFormatContext.AVFMT_FLAG_GENPTS;
import static org.bytedeco.ffmpeg.global.avcodec.*;
import static org.bytedeco.ffmpeg.global.avformat.*;
import static org.bytedeco.ffmpeg.global.avutil.*;
import static org.bytedeco.ffmpeg.global.swresample.*;

public class FFmpegAudioDecoder implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(FFmpegAudioDecoder.class);

    private AVFormatContext formatContext;
    private AVCodecContext codecContext;
    private SwrContext swrContext;
    private int audioStreamIndex = -1;

    private AVPacket packet = av_packet_alloc();
    private AVFrame frame = av_frame_alloc();

    private volatile boolean isClosed = false;

    public void open(String filePath) throws Exception {

        logger.info("Opening audio file: {}", filePath);

        formatContext = avformat_alloc_context();

        formatContext.flags(formatContext.flags() | AVFMT_FLAG_GENPTS | AVFMT_FLAG_CUSTOM_IO);
        formatContext.seek2any(1);

        if (avformat_open_input(formatContext, filePath, null, null) < 0)
            throw new Exception("Failed to open file.");

        logger.debug("Looking for stream info...");
        if (avformat_find_stream_info(formatContext, (AVDictionary) null) < 0)
            throw new Exception("No stream info.");

        for (int i = 0; i < formatContext.nb_streams(); i++) {
            if (formatContext.streams(i).codecpar().codec_type() == AVMEDIA_TYPE_AUDIO) {
                audioStreamIndex = i;
                break;
            }
        }
        if (audioStreamIndex == -1) throw new Exception("Audio stream not found.");

        logger.debug("Audio stream found at index: {}", audioStreamIndex);

        AVCodec codec = avcodec_find_decoder(formatContext.streams(audioStreamIndex).codecpar().codec_id());
        String codecName = codec.name().getString();
        logger.info("Codec detected: {} ({})", codecName,
                formatContext.streams(audioStreamIndex).codecpar().codec_id());

        codecContext = avcodec_alloc_context3(codec);
        avcodec_parameters_to_context(codecContext, formatContext.streams(audioStreamIndex).codecpar());

        if (avcodec_open2(codecContext, codec, (AVDictionary) null) < 0)
            throw new Exception("Codec could not be opened.");

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

        if (swr_init(swrContext) < 0)
            throw new Exception("Resampler failed to initialize.");

        double duration = getDuration();
        logger.info("File loaded succesfuly. Duration: {} seconds", duration);
    }

    public int readNextSamples(float[] outputBuffer) throws Exception {
        if (isClosed || codecContext == null) {
            logger.warn("Try to readNextSample on a closed or invalid decoder");
            return -1;
        }

        int ret;
        boolean flushing = false;

        while (true) {
            if (isClosed || codecContext == null) return -1;

            try {
                ret = avcodec_receive_frame(codecContext, frame);
            } catch (Exception e) {
                if (isClosed || codecContext == null) return -1;
                logger.error("Error receiving frame from codec: {}", e.getMessage());
                throw e;
            }

            if (ret == 0) {
                // Frame decodificado exitosamente
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
                logger.info("End of stream reached (EOF)");
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
                    }
                }
                av_packet_unref(packet);
            }
        }
    }

    public void seek(double seconds) throws Exception {
        if (isClosed || formatContext == null || codecContext == null) {
            logger.warn("Try to seek on a closed closed or invalid decoder");
            return;
        }

        logger.debug("Seek solicitado a: {} segundos", seconds);

        try {

            double timeBase = av_q2d(formatContext.streams(audioStreamIndex).time_base());
            long ts = Math.round(seconds / timeBase);

            if (ts < 0) ts = 0;
            logger.debug("Timestamp calculated: {} (timebase: {})", ts, timeBase);

            int ret = av_seek_frame(formatContext, audioStreamIndex, ts, AVSEEK_FLAG_BACKWARD);

            if (ret < 0) {
                logger.warn("Seek failed with AVSEEK_FLAG_BACKWARD, trying with AVSEEK_FLAG_ANY");
                ret = av_seek_frame(formatContext, audioStreamIndex, ts, AVSEEK_FLAG_BACKWARD | AVSEEK_FLAG_ANY);

                if (ret < 0) {
                    logger.warn("Seek failed again, trying global seek");
                    long targetMicroseconds = (long) (seconds * AV_TIME_BASE);
                    av_seek_frame(formatContext, -1, targetMicroseconds, AVSEEK_FLAG_BACKWARD | AVSEEK_FLAG_ANY);
                }
            }

            logger.debug("Seek completed, flushing buffers");

        } catch (Exception e) {
            if (isClosed) return;
            logger.error("Error during seek: {}", e.getMessage());
            e.printStackTrace();
        }

        if (codecContext != null && !isClosed) {
            try {
                avcodec_flush_buffers(codecContext);
                logger.debug("Codec flushed");
            } catch (Exception e) {
                logger.error("Error flushing codec: {} ", e.getMessage());
                e.printStackTrace();
            }
        }


        if (swrContext != null && !isClosed) {
            try {
                swr_init(swrContext);
                logger.debug("Resampler reinitialized");
            } catch (Exception e) {
                logger.warn("Error while reinitializing resampler: {}", e.getMessage());
                e.printStackTrace();
            }
        }
    }

    public double getDuration() {
        if (formatContext == null) return 0.0;
        double duration = formatContext.duration() / (double) AV_TIME_BASE;
        logger.debug("Duration obtained: {} seconds", duration);
        return duration;
    }


    @Override
    public void close() {
        logger.debug("Closing FFmpegAudioDecoder");
        isClosed = true;

        codecContext = null;
        swrContext = null;
        formatContext = null;
        packet = null;
        frame = null;

        logger.info("FFmpegAudioDecoder closed successfully");
    }
}