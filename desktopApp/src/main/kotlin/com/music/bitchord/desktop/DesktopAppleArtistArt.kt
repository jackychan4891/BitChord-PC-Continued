package com.music.bitchord.desktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.Normalizer
import java.util.Locale

/**
 * The header Apple Music's artist page draws: a still, the looping clip that replaces it, the
 * artist's stylized name as a transparent logo, Apple's colours for the picture, and the bio.
 *
 * Colours are ARGB ints. [logoAspect] is the logo's width over its height.
 */
data class DesktopAppleArtistArt(
    val heroUrl: String,
    val portraitUrl: String?,
    val wideVideoUrl: String?,
    val squareVideoUrl: String?,
    val logoUrl: String?,
    val logoAspect: Float,
    val background: Int,
    val accent: Int,
    val keyColor: Int?,
    val bio: String?,
    val pageUrl: String,
)

/**
 * Finds [DesktopAppleArtistArt] from an artist's name: the iTunes search turns it into an Apple
 * artist page, whose hydration JSON carries the header. That JSON is not a published API, so any
 * surprise reads as "no art" and the YouTube header stays.
 */
internal object DesktopAppleArtistArtRepository {

    private const val CACHE_SIZE = 96
    private const val HERO_WIDTH = 1600
    private const val LOGO_WIDTH = 1000

    private class Entry(val art: DesktopAppleArtistArt?, val time: Long = System.currentTimeMillis()) {
        fun reusable(): Boolean = art != null || System.currentTimeMillis() - time < 5 * 60 * 1000L
    }

    private val cache = object : LinkedHashMap<String, Entry>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>) = size > CACHE_SIZE
    }

    private val _updates = MutableStateFlow(0)
    val updates: StateFlow<Int> = _updates

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun cached(artistName: String): DesktopAppleArtistArt? =
        synchronized(cache) { cache[artistName.key()] }?.art

    fun isSettled(artistName: String): Boolean =
        synchronized(cache) { cache[artistName.key()]?.reusable() == true }

    suspend fun artFor(artistName: String): DesktopAppleArtistArt? {
        val key = artistName.key()
        if (key.isEmpty()) return null
        synchronized(cache) { cache[key] }?.let { if (it.reusable()) return it.art }
        val entry = withContext(Dispatchers.IO) {
            runCatching { lookup(canonicalAppleArtistName(artistName), key) }
                .onFailure { DesktopTrackLog.log("Apple artist: lookup failed for $artistName (${it.javaClass.simpleName})") }
                .getOrNull()
        } ?: return null
        synchronized(cache) { cache[key] = entry }
        _updates.value++
        return entry.art
    }

    private fun lookup(name: String, key: String): Entry? {
        val search = canvasGet(
            "https://itunes.apple.com/search?term=" +
                URLEncoder.encode(name, StandardCharsets.UTF_8) +
                "&entity=musicArtist&limit=8&country=gb",
        ) ?: return null
        val results = (json.parseToJsonElement(search) as? JsonObject)?.get("results") as? JsonArray
            ?: return null
        val page = results.asSequence()
            .mapNotNull { it as? JsonObject }
            .firstOrNull { it.text("artistName")?.key() == key }
            ?.let { hit ->
                hit.text("artistLinkUrl")?.substringBefore('?')?.takeIf { it.isNotBlank() }
                    ?: hit.text("artistId")?.let { "https://music.apple.com/gb/artist/$it" }
            }
            ?: return Entry(null)
        val html = canvasGet(page) ?: return null
        return parse(html, page)?.let(::Entry)
    }

    private fun parse(html: String, pageUrl: String): DesktopAppleArtistArt? {
        val marker = html.indexOf("id=\"serialized-server-data\"")
        if (marker < 0) return null
        val start = html.indexOf('>', marker) + 1
        val end = html.indexOf("</script>", start)
        if (start <= 0 || end < 0) return null
        val header = findHeader(json.parseToJsonElement(html.substring(start, end))) ?: return null

        val expectedId = pageUrl.substringBefore('?').substringAfterLast('/')
        val headerId = header.obj("contentDescriptor")?.obj("identifiers")?.text("storeAdamID")
        if (headerId != null && headerId != expectedId) return null
        val logo = header.dict("artistLogo")
        val wideStill = header.dict("wideArtwork")
        val portrait = header.dict("circleArtwork") ?: header.dict("artwork")
        val still = wideStill ?: portrait ?: header.dict("colorBackdropArtwork")
        val wideClip = header.dict("wideVideoArtwork")?.let {
            it.obj("motionArtistFullscreen16x9") ?: it.obj("motionArtistWide16x9")
        }
        val squareClip = header.dict("videoArtwork")?.obj("motionArtistSquare1x1")
        val wideVideo = wideClip?.text("video")?.takeIf { it.startsWith("http") }
        val squareVideo = squareClip?.text("video")?.takeIf { it.startsWith("http") }

        // A wide header wants the wide clip's own still when there is one; it matches the loop.
        val heroSource = wideClip?.obj("previewFrame")?.takeIf { wideVideo != null }
            ?: still
            ?: squareClip?.obj("previewFrame")
            ?: return null
        val portraitUrl = portrait?.takeIf { wideVideo == null && squareVideo == null && wideStill == null }?.image(380, "jpg")
        val heroUrl = heroSource.image(if (portraitUrl != null) 600 else HERO_WIDTH, "jpg") ?: return null

        val logoW = logo?.int("width") ?: 0
        val logoH = logo?.int("height") ?: 0
        val logoUrl = if (logo != null && logoW > 0 && logoH > 0) logo.image(LOGO_WIDTH, "png", aspect = 3f) else null

        val colours = heroSource
        val background = colours.text("bgColor")?.toArgb() ?: still?.text("bgColor")?.toArgb() ?: 0xFF1F1F1F.toInt()
        val accent = colours.text("textColor1")?.toArgb() ?: 0xFFFFFFFF.toInt()
        return DesktopAppleArtistArt(
            heroUrl = heroUrl,
            portraitUrl = portraitUrl,
            wideVideoUrl = wideVideo,
            squareVideoUrl = squareVideo,
            logoUrl = logoUrl,
            logoAspect = 3f,
            background = background,
            accent = accent,
            keyColor = header.text("keyColor")?.toArgb(),
            bio = header.text("bio")?.takeIf { it.isNotBlank() },
            pageUrl = pageUrl,
        )
    }

    private fun findHeader(node: JsonElement): JsonObject? {
        when (node) {
            is JsonObject -> {
                if (node.containsKey("artistLogo") &&
                    (node.dict("artwork") != null || node.dict("wideArtwork") != null || node.dict("circleArtwork") != null || node.dict("wideVideoArtwork") != null)
                ) return node
                for (child in node.values) findHeader(child)?.let { return it }
            }
            is JsonArray -> for (child in node) findHeader(child)?.let { return it }
            else -> Unit
        }
        return null
    }

    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

    private fun JsonObject.dict(key: String): JsonObject? = obj(key)?.obj("dictionary")

    private fun JsonObject.text(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.contentOrNull

    private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull

    private fun JsonObject.image(width: Int, format: String, aspect: Float? = null): String? {
        val template = text("url")?.takeIf { it.contains("{w}") } ?: return null
        val w = int("width")?.takeIf { it > 0 } ?: return null
        val h = int("height")?.takeIf { it > 0 } ?: return null
        val outW = minOf(width, w)
        val outH = if (aspect != null) (outW / aspect).toInt() else (outW.toLong() * h / w).toInt()
        return template
            .replace("{w}", outW.toString())
            .replace("{h}", outH.toString())
            .replace("{c}", "bb")
            .replace("{f}", format)
    }

    private fun String.toArgb(): Int? =
        takeIf { it.length == 6 }?.toIntOrNull(16)?.let { 0xFF000000.toInt() or it }

    private fun String.key(): String = Normalizer.normalize(canonicalAppleArtistName(this), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]"), "")
}

internal fun canonicalAppleArtistName(name: String): String = name
    .replace(Regex("""\s*(?:-\s*Topic|VEVO|\s+-\s+Official(?:\s+Channel)?)$""", RegexOption.IGNORE_CASE), "")
    .trim()
