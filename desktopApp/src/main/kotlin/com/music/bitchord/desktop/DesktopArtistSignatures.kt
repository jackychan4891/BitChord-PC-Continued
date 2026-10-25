package com.music.bitchord.desktop

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.decodeToImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

/**
 * What a signature service is, if the listener has one.
 *
 * This used to point at a public endpoint baked into the build. It was being harvested, so the
 * address is now the listener's own: nothing is asked of anywhere until a link is typed into
 * Settings, and an empty field means no signatures at all rather than a fallback host.
 */
internal object DesktopSignatureSettings {

    const val KEY = "artist_signature_api"

    private val persistence = DesktopPersistence()
    private val _url = MutableStateFlow(signatureEndpoint(persistence.string(KEY, "")))

    val url: StateFlow<String> = _url

    fun setUrl(value: String) {
        val endpoint = signatureEndpoint(value)
        persistence.saveString(KEY, endpoint)
        _url.value = endpoint
        DesktopArtistSignatures.clear()
    }
}

/**
 * The first address in whatever was pasted, or nothing at all.
 *
 * A link usually arrives out of a chat message with its prose and blank lines still attached, and
 * `URI` takes an embedded newline as an argument it will not parse. Reading the field back through
 * this too means a value saved before this existed is repaired the next time the app opens.
 */
internal fun signatureEndpoint(raw: String): String =
    SIGNATURE_URL.find(raw)?.value?.trimEnd('/', '.', ',', ';', ':').orEmpty()

/**
 * Whitespace ends an address, and so does a backslash; braces stay, because `{artist}` is part
 * of one.
 *
 * A paste that lost its newlines on the way in carries them as `\n` text, which is not whitespace
 * but belongs to no host either, and a whole sentence of prose glued to the address behind it.
 */
private val SIGNATURE_URL = Regex("""https?://[^\s<>"'\\]+""")

/**
 * The request for one artist against [endpoint], with [encodedName] already percent-encoded.
 *
 * `{artist}` is the one contract: a service whose query is shaped differently is still addressable.
 * Otherwise the name is appended as `?artist=…&type=handwritten` — and to a bare host the path goes
 * back on first, because that is exactly how this request was built while the address was baked in,
 * so a pasted origin keeps answering the way it always did.
 */
internal fun signatureRequestUrl(endpoint: String, encodedName: String): String {
    val path = endpoint.substringAfter("://", "").substringBefore('?').trimEnd('/')
    return when {
        endpoint.contains("{artist}") -> endpoint.replace("{artist}", encodedName)
        path.contains('/').not() ->
            "${endpoint.trimEnd('/')}/v1/signatures?artist=$encodedName&type=handwritten"
        else -> endpoint + (if (endpoint.contains('?')) "&" else "?") + "artist=$encodedName&type=handwritten"
    }
}

/** A handwritten signature for an artist, from the signature service configured in Settings. */
internal object DesktopArtistSignatures {

    private const val USER_AGENT = "BitChord-Desktop/1.8 (https://github.com/jackychan4891/BitChord-PC-Continued)"
    private const val CACHE_SIZE = 64

    private class Entry(val image: ImageBitmap?, val time: Long = System.currentTimeMillis()) {
        /**
         * A miss is held for a minute rather than ten.
         *
         * A service that was briefly unreachable — or rate-limited by the very request that came
         * before — should not blank the artist's page for most of the session it was opened in.
         */
        fun reusable(): Boolean = image != null || System.currentTimeMillis() - time < 60_000L
    }

    private val cache = object : LinkedHashMap<String, Entry>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>) = size > CACHE_SIZE
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    /** A new link is a different service: what was read from the old one is no longer evidence. */
    fun clear() = synchronized(cache) { cache.clear() }

    suspend fun signatureFor(artistName: String): ImageBitmap? {
        val endpoint = DesktopSignatureSettings.url.value
        val name = canonicalAppleArtistName(artistName)
        val key = name.key()
        // The link is the listener's own now, so a page with no signature on it has to say which of
        // its half-dozen failure modes this is: nothing configured, a name worth asking about, the
        // service, the shape of its answer, or the image inside it.
        if (endpoint.isBlank() || key.isEmpty()) {
            DesktopTrackLog.log(
                if (endpoint.isBlank()) "Artist signature: nothing asked — no link in Settings"
                else "Artist signature: nothing asked — \"$artistName\" leaves no name to ask with",
            )
            return null
        }
        synchronized(cache) { cache[key] }?.takeIf { it.reusable() }?.let { cached ->
            if (cached.image == null) {
                DesktopTrackLog.log("Artist signature: \"$name\" is inside its minute of not asking again")
            }
            return cached.image
        }
        val image = withContext(Dispatchers.IO) {
            runCatching { lookup(endpoint, name) }
                .onFailure {
                    DesktopTrackLog.log("Artist signature: the lookup threw ${it.javaClass.simpleName} (${it.message})")
                }
                .getOrNull()
        }
        DesktopTrackLog.log(
            "Artist signature: \"$name\" -> ${image?.let { "${it.width}x${it.height}" } ?: "nothing"}",
        )
        synchronized(cache) { cache[key] = Entry(image) }
        return image
    }

    private fun lookup(endpoint: String, name: String): ImageBitmap? {
        val requestUrl = signatureRequestUrl(endpoint, URLEncoder.encode(name, StandardCharsets.UTF_8))
        val body = get(requestUrl, "service")?.toString(StandardCharsets.UTF_8) ?: return null
        val root = json.parseToJsonElement(body) as? JsonObject ?: run {
            DesktopTrackLog.log("Artist signature: the link answers with something that is not an object (${body.take(120)})")
            return null
        }
        if (root["signatures"] == null && root["matches"] != null) {
            DesktopTrackLog.log("Artist signature: the link does not know \"$name\"")
            return null
        }
        val asset = pickSignatureUrl(root) ?: run {
            DesktopTrackLog.log("Artist signature: nothing in the answer for \"$name\" can be drawn")
            return null
        }
        val encoded = get(asset, "asset") ?: return null
        return runCatching { encoded.decodeToImageBitmap() }
            .onFailure { DesktopTrackLog.log("Artist signature: the asset is not an image BitChord can decode (${it.javaClass.simpleName})") }
            .getOrNull()
    }

    private fun get(url: String, stage: String): ByteArray? {
        val response = runCatching {
            client.send(
                HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", USER_AGENT)
                    .GET().build(),
                HttpResponse.BodyHandlers.ofByteArray(),
            )
        }.getOrElse {
            DesktopTrackLog.log("Artist signature: the $stage request did not complete (${it.javaClass.simpleName}: ${it.message})")
            return null
        }
        if (response.statusCode() !in 200..299) {
            DesktopTrackLog.log(
                "Artist signature: the $stage answered ${response.statusCode()} for $url " +
                    "(${String(response.body(), StandardCharsets.UTF_8).take(120).replace('\n', ' ')})",
            )
            return null
        }
        return response.body()
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
