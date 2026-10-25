package com.music.bitchord.desktop

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/**
 * What the radio page's stations are carrying right now.
 *
 * A live station says nothing about its programme in any feed a listener may ask for, and says it
 * *inside* the stream instead. So the record is taken off the stream itself, by whichever of the ways
 * a station happens to send it:
 *
 *  - an ID3 tag at the head of a media segment, which is how Apple puts a title on its live radio;
 *  - the words on an HLS `#EXTINF` line, where a playlist names the item it is about to play;
 *  - Shoutcast/ICY metadata, the `StreamTitle='Artist - Track'` block spliced into the audio every N
 *    bytes, which is what a station that serves plain mp3 rather than a playlist still sends.
 *
 * Each is a *read*, not a subscription: nothing here touches the stream the player is playing, and a
 * station nobody is looking at is not asked. A playlist address may carry a short-lived signature; it
 * is used once, held in memory, and never written down.
 */
internal object DesktopRadioNowPlaying {

    /** How stale a caption may get before the station is asked again. */
    private const val TTL_MILLIS = 60_000L

    /** The block straight after the headers is often still empty, so a few are read across. */
    private const val ICY_BLOCKS = 4

    private const val USER_AGENT = "BitChord-Desktop/1.8 (https://github.com/jackychan4891/BitChord-PC-Continued)"

    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(8))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    private val _lines = MutableStateFlow<Map<String, String>>(emptyMap())

    /** Station id to the one line drawn under its tile. */
    val lines: StateFlow<Map<String, String>> = _lines.asStateFlow()

    private val asked = ConcurrentHashMap<String, Long>()

    /**
     * Ask every station that has aged out, all at once. A station whose answer does not come keeps
     * showing what it said last rather than going blank, and is not re-asked until the ttl turns.
     */
    suspend fun refresh(stations: List<DesktopRadioStation>) {
        val now = System.currentTimeMillis()
        val due = stations.filter { now - (asked[it.id] ?: 0L) >= TTL_MILLIS }
        if (due.isEmpty()) return
        due.forEach { asked[it.id] = now }
        val read = withContext(Dispatchers.IO) {
            coroutineScope {
                due.map { station -> station.id to async { runCatching { readLine(station) }.getOrNull() } }
                    .mapNotNull { (id, deferred) -> deferred.await()?.let { id to it } }
            }
        }.toMap()
        if (read.isNotEmpty()) _lines.update { it + read }
    }

    private fun readLine(station: DesktopRadioStation): String? {
        val url = station.streamUrl
        if (url.contains("radiotime.com")) {
            // RadioTime answers with the address of the stream rather than the stream itself.
            val tuned = text(url)?.lineSequence()?.map(String::trim)?.firstOrNull { it.startsWith("http") }
            return tuned?.let(::streamLine)
        }
        return streamLine(url)
    }

    /** One playlist walk, or one ICY read, whichever that address is serving. */
    private fun streamLine(url: String): String? =
        if (url.contains("/hls") || url.contains("playlist") || url.endsWith(".m3u8")) hlsLine(url)
        else icyTitleOf(url) ?: hlsLine(url)

    // -------------------------------------------------------------------- HLS

    /**
     * Follow a live playlist down to what it is carrying.
     *
     * A master names a variant, a media playlist names its segments, and the item being played is
     * either written on the `#EXTINF` line above the segment or tagged at the head of the segment
     * itself. Three hops is the most any of these streams takes; a fourth would be a loop, and is
     * abandoned rather than followed.
     */
    private fun hlsLine(url: String): String? {
        var target = url
        repeat(3) {
            val text = text(target) ?: return null
            if (!text.contains("#EXTM3U")) {
                target = text.lineSequence().map(String::trim).firstOrNull { it.startsWith("http") } ?: return null
                return@repeat
            }
            val lines = text.lines().map(String::trim).filter(String::isNotEmpty)
            val extInf = lines.firstOrNull { it.startsWith("#EXTINF") }
            if (extInf != null) {
                extInfTitle(extInf)?.let { return it }
                return lines.firstOrNull { !it.startsWith("#") }?.let { segmentLine(absolute(target, it)) }
            }
            val next = lines.firstOrNull { !it.startsWith("#") } ?: return null
            target = absolute(target, next)
        }
        return null
    }

    /** The first segment of a stream carries the tag; its neighbours only repeat it. */
    private fun segmentLine(url: String): String? = read(url, emptyMap(), 32_000)?.bytes?.let(::id3Line)

    /** The `StreamTitle` of a Shoutcast stream, from the blocks spliced into its audio. */
    private fun icyTitleOf(url: String): String? {
        val headers = mapOf("Icy-MetaData" to "1")
        val metaint = read(url, headers, 16)?.headers?.get("icy-metaint")?.toIntOrNull() ?: return null
        if (metaint <= 0) return null
        // Every block is the audio between them, one length byte, and its own padded text.
        val body = read(url, headers, metaint * ICY_BLOCKS + ICY_BLOCKS * 336 + 64)?.bytes ?: return null
        return icyTitles(body, metaint).firstOrNull { it != null }
    }

    // ------------------------------------------------------------- the parsers

    /**
     * An `#EXTINF` line's own words.
     *
     * Two shapes are in use. The plain one names the item after the comma — "Hello - Adele" — and is
     * shown as it is written. The attributed one puts the performer in `title=` and the work in
     * `artist=`: the fields the platform's own feed carries the other way round, which is why they are
     * said back the way a listener would say them.
     */
    internal fun extInfTitle(line: String): String? {
        val local = line.substringAfter(',', "").trim().takeIf { it.isNotEmpty() } ?: return null
        if (!local.contains('=')) return local
        val attributes = Regex("""([\w-]+)="([^"]*)"""").findAll(local)
            .associate { it.groupValues[1].lowercase() to it.groupValues[2].trim() }
            .filterValues { it.isNotEmpty() }
        val performer = attributes["title"]
        val work = attributes["artist"]
        return if (performer != null && work != null) "$performer — $work" else performer ?: work
    }

    /**
     * The metadata blocks of an ICY stream, in the order they arrive.
     *
     * The stream says how often to look: `icy-metaint` bytes of audio, then one byte that says how
     * many sixteen-byte pads of text follow, then the audio again. A pad of zero means the station
     * had nothing to say this time, and only the length byte was sent — reading past it is what
     * turns the next block into noise.
     */
    internal fun icyTitles(bytes: ByteArray, metaint: Int): List<String?> {
        val out = ArrayList<String?>(ICY_BLOCKS)
        var at = metaint
        repeat(ICY_BLOCKS) {
            if (at < 0 || at >= bytes.size) return out
            val pad = (bytes[at].toInt() and 0xff) * 16
            val block = if (pad == 0 || at + 1 >= bytes.size) {
                ""
            } else {
                String(bytes, at + 1, minOf(pad, bytes.size - at - 1), StandardCharsets.UTF_8)
            }
            out += streamTitle(block)
            at += 1 + pad + metaint
        }
        return out
    }

    /** `StreamTitle='George Michael - Freedom 90'` out of a whole ICY metadata block. */
    internal fun streamTitle(block: String): String? =
        block.substringAfter("StreamTitle='", "").substringBefore("'").trim()
            .replace("&#39;", "'").replace("&amp;", "&").takeIf { it.isNotEmpty() }

    /**
     * The record tagged at the head of a media segment, as `performer — record`.
     *
     * A segment can carry more than one tag and the first is usually the encoder's own — Apple opens
     * with a `PRIV` frame that says nothing about the music — so a frame is only taken when it parses
     * as one, and the walk goes on past anything that does not.
     */
    internal fun id3Line(bytes: ByteArray): String? {
        val title = id3Text(bytes, "TIT2") ?: return null
        val artist = id3Text(bytes, "TPE1")
        return if (artist.isNullOrBlank()) title else "$artist — $title"
    }

    /** The text of the first frame named [id] that parses, anywhere in the window. */
    private fun id3Text(bytes: ByteArray, id: String): String? {
        val name = id.toByteArray(StandardCharsets.US_ASCII)
        var at = indexOf(bytes, name, 0)
        while (at >= 0) {
            frameText(bytes, at + name.size)?.let { return it }
            at = indexOf(bytes, name, at + name.size)
        }
        return null
    }

    /** A text frame read from just past its four-letter name: size, two flag bytes, encoding, text. */
    private fun frameText(bytes: ByteArray, at: Int): String? {
        if (at + 7 > bytes.size) return null
        if (bytes[at + 4].toInt() != 0 || bytes[at + 5].toInt() != 0) return null
        val size = frameSize(bytes, at)
        val encoding = bytes[at + 6].toInt() and 0xff
        if (size < 2 || encoding > 3) return null
        val from = at + 7
        val to = minOf(from + size - 1, bytes.size)
        if (to <= from) return null
        val charset: Charset = when (encoding) {
            0 -> StandardCharsets.ISO_8859_1
            2 -> Charsets.UTF_16LE
            3 -> Charsets.UTF_8
            else -> Charsets.UTF_16
        }
        return String(bytes, from, to - from, charset).trimEnd('\u0000', ' ').takeIf(::readsAsTitle)
    }

    /**
     * A frame's declared length. Both the sizes an ID3v2.3 and a v2.4 frame use are read here: they
     * agree whenever every byte is under 0x80, which is any tag small enough to be worth reading, and
     * a byte over that only ever means the older, plain one.
     */
    private fun frameSize(bytes: ByteArray, at: Int): Int {
        val a = bytes[at].toInt() and 0xff
        val b = bytes[at + 1].toInt() and 0xff
        val c = bytes[at + 2].toInt() and 0xff
        val d = bytes[at + 3].toInt() and 0xff
        return if ((a or b or c or d) < 0x80) (a shl 21) or (b shl 14) or (c shl 7) or d
        else (a shl 24) or (b shl 16) or (c shl 8) or d
    }

    /** A title is words and not the noise next to them; anything else is the wrong frame. */
    private fun readsAsTitle(text: String): Boolean =
        text.length >= 2 && text.any(Char::isLetterOrDigit) && text.none { it.code in 1..31 }

    private fun indexOf(bytes: ByteArray, pattern: ByteArray, from: Int): Int {
        outer@ for (i in from..bytes.size - pattern.size) {
            for (j in pattern.indices) if (bytes[i + j] != pattern[j]) continue@outer
            return i
        }
        return -1
    }

    // ------------------------------------------------------------ one HTTP call

    private class Read(val headers: Map<String, String>, val bytes: ByteArray)

    private fun text(url: String): String? =
        read(url, emptyMap(), 64_000)?.bytes?.toString(StandardCharsets.UTF_8)

    private fun read(url: String, headers: Map<String, String>, cap: Int): Read? = runCatching {
        val request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(12))
            .header("User-Agent", USER_AGENT)
            .header("Accept", "*/*")
            .also { builder -> headers.forEach { (name, value) -> builder.header(name, value) } }
            .GET().build()
        val response = client.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
            .get(15, TimeUnit.SECONDS)
        if (response.statusCode() !in 200..299) return null
        val sent = response.headers().map().entries
            .associate { (name, values) -> name.lowercase() to values.firstOrNull().orEmpty() }
        Read(sent, response.body().use { it.readNBytes(cap) })
    }.getOrNull()

    private fun absolute(base: String, ref: String): String =
        runCatching { URI.create(base).resolve(ref).toString() }.getOrDefault(ref)
}
