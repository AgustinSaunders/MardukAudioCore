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

    private volatile boolean isClosed = false;

    public void open(String filePath) throws Exception {
        // 1. Abrir contenedor
        formatContext = avformat_alloc_context();

        formatContext.flags(formatContext.flags() | AVFMT_FLAG_GENPTS | AVFMT_FLAG_CUSTOM_IO);
        formatContext.seek2any(1);

        if (avformat_open_input(formatContext, filePath, null, null) < 0)
            throw new Exception("No se pudo abrir el archivo.");

        // IMPORTANTE: SIEMPRE llamar a avformat_find_stream_info()
        // Incluso para WAV, es necesario para calcular la duración correctamente
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

        // 3. Configurar Codec
        AVCodec codec = avcodec_find_decoder(formatContext.streams(audioStreamIndex).codecpar().codec_id());
        codecContext = avcodec_alloc_context3(codec);
        avcodec_parameters_to_context(codecContext, formatContext.streams(audioStreamIndex).codecpar());

        if (avcodec_open2(codecContext, codec, (AVDictionary) null) < 0)
            throw new Exception("No se pudo abrir el codec.");

        // 4. Configurar Resampler
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
            throw new Exception("No se pudo inicializar el resampler.");
    }

    public int readNextSamples(float[] outputBuffer) throws Exception {
        if (isClosed || codecContext == null) return -1;

        int ret;
        boolean flushing = false;

        while (true) {
            if (isClosed || codecContext == null) return -1;

            try {
                ret = avcodec_receive_frame(codecContext, frame);
            } catch (Exception e) {
                if (isClosed || codecContext == null) return -1;
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
                    fp.get(outputBuffer, 0, converted * 2);
                    return converted * 2;
                }
                continue;
            }

            if (flushing && ret == AVERROR_EOF) {
                // flush del resampler
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
                    fp.get(outputBuffer, 0, converted * 2);
                    return converted * 2;
                }
                return -1;
            }
            if (!flushing && av_read_frame(formatContext, packet) < 0) {
                avcodec_send_packet(codecContext, null);
                flushing = true;
                continue;
            }

            if (!flushing) {
                if (packet.stream_index() == audioStreamIndex) {
                    avcodec_send_packet(codecContext, packet);
                }
                av_packet_unref(packet);
            }
        }
    }

    public void seek(double seconds) throws Exception {
        if (isClosed || formatContext == null || codecContext == null) return;

        try {
            // Convertir segundos a timestamp en unidades de time_base
            double timeBase = av_q2d(formatContext.streams(audioStreamIndex).time_base());
            long ts = Math.round(seconds / timeBase);

            // Clamp ts a valores válidos
            if (ts < 0) ts = 0;

            System.out.println("Buscando en timestamp: " + ts + " (segundos: " + seconds + ", timebase: " + timeBase + ")");

            // Intentar seek en el stream de audio específico
            int ret = av_seek_frame(formatContext, audioStreamIndex, ts, AVSEEK_FLAG_BACKWARD);

            if (ret < 0) {
                System.err.println("Seek falló, intentando con AVSEEK_FLAG_ANY");
                // Si falla, intentar con AVSEEK_FLAG_ANY para buscar en cualquier frame
                ret = av_seek_frame(formatContext, audioStreamIndex, ts, AVSEEK_FLAG_BACKWARD | AVSEEK_FLAG_ANY);

                if (ret < 0) {
                    System.err.println("Seek falló nuevamente, intentando seek global");
                    // Último intento: buscar globalmente con microsegundos
                    long targetMicroseconds = (long) (seconds * AV_TIME_BASE);
                    av_seek_frame(formatContext, -1, targetMicroseconds, AVSEEK_FLAG_BACKWARD | AVSEEK_FLAG_ANY);
                }
            }

            System.out.println("Seek completado, flushing buffers...");

        } catch (Exception e) {
            if (isClosed) return;
            System.err.println("Error durante seek: " + e.getMessage());
            e.printStackTrace();
        }

        // CRÍTICO: Flush del codec para descartar frames en caché
        if (codecContext != null && !isClosed) {
            try {
                avcodec_flush_buffers(codecContext);
                System.out.println("Codec flushed");
            } catch (Exception e) {
                System.err.println("Error flushing codec: " + e.getMessage());
            }
        }

        // Reinicializar resampler después del seek
        if (swrContext != null && !isClosed) {
            try {
                swr_init(swrContext);
                System.out.println("Resampler reinitialized");
            } catch (Exception e) {
                System.err.println("Error reinitializing resampler: " + e.getMessage());
            }
        }
    }

    public double getDuration() {
        return formatContext.duration() / (double) AV_TIME_BASE;
    }


    @Override
    public void close() {
        isClosed = true;

        codecContext = null;
        swrContext = null;
        formatContext = null;
        packet = null;
        frame = null;
    }
}