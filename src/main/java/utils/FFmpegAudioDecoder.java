package utils;

import org.bytedeco.ffmpeg.avcodec.*;
import org.bytedeco.ffmpeg.avformat.*;
import org.bytedeco.ffmpeg.avutil.*;
import org.bytedeco.ffmpeg.swresample.*;
import org.bytedeco.javacpp.FloatPointer;
import org.bytedeco.javacpp.PointerPointer;

import static org.bytedeco.ffmpeg.avformat.AVFormatContext.AVFMT_FLAG_CUSTOM_IO;
import static org.bytedeco.ffmpeg.avformat.AVFormatContext.AVFMT_FLAG_GENPTS;
import static org.bytedeco.ffmpeg.global.avcodec.*;
import static org.bytedeco.ffmpeg.global.avformat.*;
import static org.bytedeco.ffmpeg.global.avutil.*;
import static org.bytedeco.ffmpeg.global.swresample.*;

public class FFmpegAudioDecoder implements AutoCloseable {
    private AVFormatContext formatContext;
    private AVCodecContext codecContext;
    private SwrContext swrContext;
    private int audioStreamIndex = -1;

    private AVPacket packet = av_packet_alloc();
    private AVFrame frame = av_frame_alloc();

    public void open(String filePath) throws Exception {
        // 1. Abrir contenedor
        formatContext = avformat_alloc_context();

        formatContext.flags(formatContext.flags() | AVFMT_FLAG_GENPTS | AVFMT_FLAG_CUSTOM_IO);
        formatContext.seek2any(1);

        if (avformat_open_input(formatContext, filePath, null, null) < 0)
            throw new Exception("No se pudo abrir el archivo.");

        if (avformat_find_stream_info(formatContext, (AVDictionary) null) < 0)
            throw new Exception("Sin info de stream.");

        // 2. Buscar stream de audio
        for (int i = 0; i < formatContext.nb_streams(); i++) {
            if (formatContext.streams(i).codecpar().codec_type() == AVMEDIA_TYPE_AUDIO) {
                audioStreamIndex = i;
                break;
            }
        }
        if (audioStreamIndex == -1) throw new Exception("No se encontró stream de audio.");

        // 3. Configurar Codec (AHORA codecContext deja de ser null)
        AVCodec codec = avcodec_find_decoder(formatContext.streams(audioStreamIndex).codecpar().codec_id());
        codecContext = avcodec_alloc_context3(codec);
        avcodec_parameters_to_context(codecContext, formatContext.streams(audioStreamIndex).codecpar());

        if (avcodec_open2(codecContext, codec, (AVDictionary) null) < 0)
            throw new Exception("No se pudo abrir el codec.");

        // 4. Configurar Resampler (Swr) con la API moderna
        AVChannelLayout outChannelLayout = new AVChannelLayout();
        av_channel_layout_default(outChannelLayout, 2); // Estéreo

        swrContext = swr_alloc();
        swr_alloc_set_opts2(
                swrContext,
                outChannelLayout,          // Salida: Layout (estéreo)
                AV_SAMPLE_FMT_FLT,         // Salida: Formato (32-bit Float)
                44100,                     // Salida: Sample Rate
                codecContext.ch_layout(),  // Entrada: Layout original
                codecContext.sample_fmt(), // Entrada: Formato original
                codecContext.sample_rate(),// Entrada: Rate original
                0, null
        );

        if (swr_init(swrContext) < 0)
            throw new Exception("No se pudo inicializar el resampler.");
    }

    public synchronized int readNextSamples(float[] outputBuffer) throws Exception {
        int ret;
        while (true) {
            ret = avcodec_receive_frame(codecContext, frame);

            if (ret == 0) {
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

                if (converted > 0) {
                    fp.get(outputBuffer, 0, converted * 2);
                }

                av_frame_unref(frame);
                if (converted <= 0) continue;
                return converted * 2;
            }

            if (av_read_frame(formatContext, packet) < 0) return -1;

            if (packet.stream_index() == audioStreamIndex) {
                avcodec_send_packet(codecContext, packet);
            }
            av_packet_unref(packet);
        }
    }

    public synchronized void seek(double seconds) throws Exception {
        if (formatContext == null) return;

        long targetMicroseconds = (long) (seconds * AV_TIME_BASE);

        // Usamos AVSEEK_FLAG_ANY para que el salto sea instantáneo por posición de byte
        int flags = AVSEEK_FLAG_BACKWARD | AVSEEK_FLAG_ANY;

        if (av_seek_frame(formatContext, -1, targetMicroseconds, flags) < 0) {
            // Plan B: Intentar con el stream de audio si el global falla
            long ts = (long) (seconds / av_q2d(formatContext.streams(audioStreamIndex).time_base()));
            av_seek_frame(formatContext, audioStreamIndex, ts, flags);
        }

        if (codecContext != null) {
            avcodec_flush_buffers(codecContext);
        }
    }

    public double getDuration() {
        return formatContext.duration() / (double) AV_TIME_BASE;
    }

    @Override
    public void close() {
        if (packet != null) av_packet_free(packet);
        if (frame != null) av_frame_free(frame);
        if (swrContext != null) swr_free(swrContext);
        if (codecContext != null) avcodec_free_context(codecContext);
        if (formatContext != null) avformat_close_input(formatContext);
    }
}