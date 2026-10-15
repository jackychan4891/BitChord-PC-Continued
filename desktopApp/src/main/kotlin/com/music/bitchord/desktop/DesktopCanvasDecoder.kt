package com.music.bitchord.desktop

import org.bytedeco.ffmpeg.avcodec.AVCodecContext
import org.bytedeco.ffmpeg.avcodec.AVPacket
import org.bytedeco.ffmpeg.avformat.AVFormatContext
import org.bytedeco.ffmpeg.avutil.AVDictionary
import org.bytedeco.ffmpeg.avutil.AVFrame
import org.bytedeco.ffmpeg.global.avcodec.AVDISCARD_ALL
import org.bytedeco.ffmpeg.global.avcodec.av_packet_alloc
import org.bytedeco.ffmpeg.global.avcodec.av_packet_free
import org.bytedeco.ffmpeg.global.avcodec.av_packet_unref
import org.bytedeco.ffmpeg.global.avcodec.avcodec_alloc_context3
import org.bytedeco.ffmpeg.global.avcodec.avcodec_find_decoder
import org.bytedeco.ffmpeg.global.avcodec.avcodec_flush_buffers
import org.bytedeco.ffmpeg.global.avcodec.avcodec_free_context
import org.bytedeco.ffmpeg.global.avcodec.avcodec_open2
import org.bytedeco.ffmpeg.global.avcodec.avcodec_parameters_to_context
import org.bytedeco.ffmpeg.global.avcodec.avcodec_receive_frame
import org.bytedeco.ffmpeg.global.avcodec.avcodec_send_packet
import org.bytedeco.ffmpeg.global.avformat.av_find_best_stream
import org.bytedeco.ffmpeg.global.avformat.av_read_frame
import org.bytedeco.ffmpeg.global.avformat.av_seek_frame
import org.bytedeco.ffmpeg.global.avformat.avformat_close_input
import org.bytedeco.ffmpeg.global.avformat.avformat_find_stream_info
import org.bytedeco.ffmpeg.global.avformat.AVSEEK_FLAG_BACKWARD
import org.bytedeco.ffmpeg.global.avformat.avformat_open_input
import org.bytedeco.ffmpeg.global.avutil.AVMEDIA_TYPE_VIDEO
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_BGRA
import org.bytedeco.ffmpeg.global.avutil.av_dict_set
import org.bytedeco.ffmpeg.global.avutil.av_frame_alloc
import org.bytedeco.ffmpeg.global.avutil.av_frame_free
import org.bytedeco.ffmpeg.global.avutil.av_image_fill_arrays
import org.bytedeco.ffmpeg.global.avutil.av_image_get_buffer_size
import org.bytedeco.ffmpeg.global.swscale.SWS_BILINEAR
import org.bytedeco.ffmpeg.global.swscale.sws_freeContext
import org.bytedeco.ffmpeg.global.swscale.sws_getContext
import org.bytedeco.ffmpeg.global.swscale.sws_scale
import org.bytedeco.ffmpeg.swscale.SwsContext
import org.bytedeco.javacpp.BytePointer
import org.bytedeco.javacpp.IntPointer
import org.bytedeco.javacpp.PointerPointer

/** The motion artwork, decoded here rather than played by a second media stack. */
internal class DesktopCanvasDecoder(private val maxEdge: Int = MAX_EDGE) {

    private var format: AVFormatContext? = null
    private var codec: AVCodecContext? = null
    private var packet: AVPacket? = null
    private var frame: AVFrame? = null
    private var scaler: SwsContext? = null
    private var buffer: BytePointer? = null
    private var view: java.nio.ByteBuffer? = null
    private var planes: PointerPointer<BytePointer>? = null
    private var strides: IntPointer? = null
    private var scalerFormat = -1
    private var sourceWidth = 0
    private var sourceHeight = 0
    private var streamIndex = -1

    /** Seconds per frame, from the stream's own rate; used to pace playback. */
    var frameIntervalMillis: Long = 40L
        private set

    /** The size frames are handed back at, after scaling. */
    var width: Int = 0
        private set

    var height: Int = 0
        private set

    fun open(url: String, headers: Map<String, String> = emptyMap()): Result<Unit> = runCatching {
        DesktopAudioDecoder.ensureNetworkReady()
        val options = AVDictionary(null)
        if (headers.isNotEmpty()) {
            av_dict_set(
                options,
                "headers",
                headers.entries.joinToString("") { (name, value) -> "$name: $value\r\n" },
                0,
            )
        }
        av_dict_set(options, "rw_timeout", "15000000", 0)

        val source = if (url.substringBefore('?').endsWith(".m3u8", ignoreCase = true)) {
            val key = "$maxEdge|$url"
            synchronized(variants) { variants[key] } ?: canvasGet(url, headers)
                ?.let { selectCanvasVariant(url, it, maxEdge) }?.also { synchronized(variants) { variants[key] = it } } ?: url
        } else url
        val opened = AVFormatContext(null)
        check(avformat_open_input(opened, source, null, options) >= 0) { "could not open the clip" }
        format = opened
        check(avformat_find_stream_info(opened, null as AVDictionary?) >= 0) { "no stream info" }
        streamIndex = av_find_best_stream(
            opened, AVMEDIA_TYPE_VIDEO, -1, -1, null as org.bytedeco.ffmpeg.avcodec.AVCodec?, 0,
        )
        check(streamIndex >= 0) { "no video stream" }
        // An HLS master lists every rendition as its own stream, and FFmpeg's pick is the largest:
        // a 4K decode for a frame that is scaled down anyway. The smallest that still covers the
        // target is taken instead, and the rest are dropped so their segments are never fetched.
        val renditions = (0 until opened.nb_streams()).filter { i ->
            val p = opened.streams(i).codecpar()
            p.codec_type() == AVMEDIA_TYPE_VIDEO && p.width() > 0 && p.height() > 0
        }
        if (renditions.size > 1) {
            fun edge(i: Int) = opened.streams(i).codecpar().let { maxOf(it.width(), it.height()) }
            streamIndex = renditions.filter { edge(it) >= maxEdge }.minByOrNull(::edge)
                ?: renditions.maxBy(::edge)
            for (i in 0 until opened.nb_streams()) {
                if (i != streamIndex) opened.streams(i).discard(AVDISCARD_ALL)
            }
        }

        val stream = opened.streams(streamIndex)
        val parameters = stream.codecpar()
        val decoder = avcodec_find_decoder(parameters.codec_id()) ?: error("no decoder for this clip")
        val context = avcodec_alloc_context3(decoder)
        check(avcodec_parameters_to_context(context, parameters) >= 0) { "bad codec parameters" }
        check(avcodec_open2(context, decoder, null as AVDictionary?) >= 0) { "decoder refused to open" }
        codec = context

        sourceWidth = context.width()
        sourceHeight = context.height()
        check(sourceWidth > 0 && sourceHeight > 0) { "the clip has no size" }

        // Scaled down on the way out.
        val scale = minOf(1.0, maxEdge.toDouble() / maxOf(sourceWidth, sourceHeight))
        width = ((sourceWidth * scale).toInt() / 2) * 2
        height = ((sourceHeight * scale).toInt() / 2) * 2

        val rate = stream.avg_frame_rate()
        if (rate.num() > 0 && rate.den() > 0) {
            // Never faster than [MIN_INTERVAL_MS]: a sleeve does not need sixty frames a second,
            // and the cost of one is a bitmap.
            frameIntervalMillis = (1_000L * rate.den() / rate.num()).coerceIn(MIN_INTERVAL_MS, 200L)
        }

        // Wired by FFmpeg rather than by hand.
        val size = av_image_get_buffer_size(AV_PIX_FMT_BGRA, width, height, 1)
        buffer = BytePointer(size.toLong())
        view = buffer?.asByteBuffer()
        planes = PointerPointer<BytePointer>(4)
        strides = IntPointer(4L)
        check(
            av_image_fill_arrays(planes, strides, buffer, AV_PIX_FMT_BGRA, width, height, 1) >= 0,
        ) { "could not lay out the frame buffer" }
        packet = av_packet_alloc()
        frame = av_frame_alloc()
    }.onFailure { close() }

    /** The next frame as BGRA bytes, looping at the end. */
    fun nextFrame(into: ByteArray): Boolean {
        val container = format ?: return false
        val context = codec ?: return false
        val pkt = packet ?: return false
        val decoded = frame ?: return false
        var looped = false
        while (true) {
            if (avcodec_receive_frame(context, decoded) == 0) {
                val converter = scalerFor(decoded.format()) ?: return false
                sws_scale(converter, decoded.data(), decoded.linesize(), 0, sourceHeight, planes, strides)
                // Read through an explicit NIO view rewound each time rather than through the
                // pointer's own `get`, whose starting offset is its position.
                view?.let { pixels ->
                    pixels.rewind()
                    pixels.get(into, 0, minOf(into.size, width * height * 4))
                }
                return true
            }
            if (av_read_frame(container, pkt) < 0) {
                if (looped) return false
                looped = true
                av_seek_frame(container, streamIndex, 0, AVSEEK_FLAG_BACKWARD)
                avcodec_flush_buffers(context)
                continue
            }
            if (pkt.stream_index() == streamIndex) avcodec_send_packet(context, pkt)
            av_packet_unref(pkt)
        }
    }

    /** The converter for the format frames are actually arriving in. */
    private fun scalerFor(pixelFormat: Int): SwsContext? {
        scaler?.let { if (pixelFormat == scalerFormat) return it }
        if (pixelFormat < 0) return null
        scaler?.let { sws_freeContext(it) }
        // BGRA because that is the order Skia's N32 bitmaps are laid out in, so the scaled frame
        // can be handed over without a second pass.
        val built = sws_getContext(
            sourceWidth, sourceHeight, pixelFormat,
            width, height, AV_PIX_FMT_BGRA,
            SWS_BILINEAR, null, null, null as DoublePointer?,
        )
        if (built == null) {
            DesktopTrackLog.log("canvas: no converter for pixel format $pixelFormat")
            scaler = null
            return null
        }
        scaler = built
        scalerFormat = pixelFormat
        return built
    }

    fun close() {
        frame?.let { av_frame_free(it) }
        packet?.let { av_packet_free(it) }
        scaler?.let { sws_freeContext(it) }
        codec?.let { avcodec_free_context(it) }
        format?.let { avformat_close_input(it) }
        buffer?.deallocate()
        strides?.deallocate()
        frame = null
        packet = null
        scaler = null
        scalerFormat = -1
        codec = null
        format = null
        buffer = null
        view = null
        planes = null
        strides = null
        streamIndex = -1
        sourceWidth = 0
        sourceHeight = 0
    }

    internal companion object {
        private val variants = object : LinkedHashMap<String, String>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>) = size > 64
        }

        /** Plenty for a sleeve, and a quarter of the pixels of the source. */
        const val MAX_EDGE = 540

        /** Thirty frames a second is motion; sixty is just more bitmaps. */
        const val MIN_INTERVAL_MS = 33L
    }
}

internal fun selectCanvasVariant(masterUrl: String, manifest: String, maxEdge: Int): String? {
    data class Variant(val url: String, val edge: Int, val bandwidth: Long, val avc: Boolean)
    val lines = manifest.lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
    val variants = lines.mapIndexedNotNull { index, line ->
        if (!line.startsWith("#EXT-X-STREAM-INF:")) return@mapIndexedNotNull null
        val resolution = Regex("RESOLUTION=(\\d+)x(\\d+)").find(line) ?: return@mapIndexedNotNull null
        val path = lines.getOrNull(index + 1)?.takeUnless { it.startsWith('#') } ?: return@mapIndexedNotNull null
        val uri = runCatching { java.net.URI(masterUrl).resolve(path) }.getOrNull() ?: return@mapIndexedNotNull null
        if (uri.scheme !in setOf("https", "http") || uri.host == null || uri.userInfo != null) return@mapIndexedNotNull null
        val edge = maxOf(resolution.groupValues[1].toInt(), resolution.groupValues[2].toInt())
        val bandwidth = Regex("(?:^|,)BANDWIDTH=(\\d+)").find(line.substringAfter(':'))?.groupValues?.get(1)?.toLongOrNull() ?: Long.MAX_VALUE
        val avc = Regex("""CODECS="[^"]*avc1[^"]*"""").containsMatchIn(line)
        Variant(uri.toString(), edge, bandwidth, avc)
    }
    val supported = variants.filter { it.avc }.ifEmpty { variants }
    return (supported.filter { it.edge >= maxEdge }.minWithOrNull(compareBy<Variant> { it.edge }.thenBy { it.bandwidth })
        ?: supported.maxByOrNull { it.edge })?.url
}

private typealias DoublePointer = org.bytedeco.javacpp.DoublePointer
