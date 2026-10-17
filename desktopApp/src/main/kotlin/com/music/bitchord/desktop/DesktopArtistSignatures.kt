package com.music.bitchord.desktop

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.decodeToImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.text.Normalizer
import java.time.Duration
import java.util.Locale

/** A handwritten signature for an artist, from the artist-signature API. */
internal object DesktopArtistSignatures {

    private const val BASE = "https://proud-cascade-1654.de.deplexo.com"
    private const val USER_AGENT = "BitChord-Desktop/1.8 (https://github.com/jackychan4891/BitChord-PC-Continued)"
    private const val CACHE_SIZE = 64

    private class Entry(val image: ImageBitmap?, val time: Long = System.currentTimeMillis()) {
        fun reusable(): Boolean = image != null || System.currentTimeMillis() - time < 10 * 60 * 1000L
    }

    private val cache = object : LinkedHashMap<String, Entry>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>) = size > CACHE_SIZE
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    suspend fun signatureFor(artistName: String): ImageBitmap? {
        val name = canonicalAppleArtistName(artistName)
        val key = name.key()
        if (key.isEmpty()) return null
        synchronized(cache) { cache[key] }?.let { if (it.reusable()) return it.image }
        val image = withContext(Dispatchers.IO) {
            runCatching { lookup(name) }
                .onFailure { DesktopTrackLog.log("Artist signature: lookup failed (${it.javaClass.simpleName})") }
                .getOrNull()
        }
        synchronized(cache) { cache[key] = Entry(image) }
        return image
    }

    private fun lookup(name: String): ImageBitmap? {
        val body = get("$BASE/v1/signatures?artist=" + URLEncoder.encode(name, StandardCharsets.UTF_8) + "&type=handwritten")
            ?.toString(StandardCharsets.UTF_8) ?: return null
        val root = json.parseToJsonElement(body) as? JsonObject ?: return null
        val url = pickSignatureUrl(root) ?: return null
        return get(url)?.decodeToImageBitmap()
    }

    private fun get(url: String): ByteArray? {
        val request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(15))
            .header("User-Agent", USER_AGENT)
            .GET().build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofByteArray())
        return if (response.statusCode() in 200..299) response.body() else null
    }

    private fun String.key(): String = Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]"), "")
}

/**
 * Chooses the best transparent signature in an API response and returns a URL for a raster of it.
 * JPEGs are skipped: they carry a paper background, and the header tints the ink white.
 */
internal fun pickSignatureUrl(root: JsonObject): String? {
    val signatures = root["signatures"] as? JsonArray ?: return null
    return signatures.asSequence()
        .mapNotNull { it as? JsonObject }
        .mapNotNull { signature ->
            val asset = signature["asset"] as? JsonObject ?: return@mapNotNull null
            val url = asset.text("url")?.takeIf { it.startsWith("https://") } ?: return@mapNotNull null
            val format = asset.text("format")?.lowercase(Locale.ROOT)
            val formatScore = when (format) { "svg" -> 2; "png" -> 1; else -> return@mapNotNull null }
            val licenseScore = when ((signature["license"] as? JsonObject)?.text("status")) {
                "known" -> 2; "requires_review" -> 1; else -> 0
            }
            val verification = when (signature.text("verification")) {
                "artist_verified" -> 3; "verified" -> 2; "unverified" -> 1; else -> 0
            }
            val width = asset.int("width") ?: 0
            val height = asset.int("height") ?: 0
            val panoramic = if (height > 0 && width.toFloat() / height >= 1.4f) 1 else 0
            val score = licenseScore * 1000 + formatScore * 100 + verification * 10 + panoramic
            val raster = if (format == "svg") wikimediaSvgRaster(url) ?: return@mapNotNull null else url.substringBefore('?')
            Triple(score, width.toLong() * height, raster)
        }
        .sortedWith(compareByDescending<Triple<Int, Long, String>> { it.first }.thenByDescending { it.second })
        .firstOrNull()?.third
}

/** Wikimedia renders SVGs to PNG thumbnails, but only at its standard widths. */
internal fun wikimediaSvgRaster(url: String): String? {
    val path = url.substringBefore('?')
    val prefix = "https://upload.wikimedia.org/wikipedia/commons/"
    if (!path.startsWith(prefix) || path.startsWith(prefix + "thumb/")) return null
    val file = path.substringAfterLast('/')
    return prefix + "thumb/" + path.removePrefix(prefix) + "/960px-" + file + ".png"
}

private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull
