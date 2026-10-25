package com.music.bitchord.desktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.Locale

/**
 * What Genius prints under a song — the paragraph about what the song means, not its lyrics.
 *
 * There is no key in this: the web client's own `search/multi` answers anonymously, and the song's
 * page carries the description in its HTML. Both sit behind Cloudflare, which answers a plain
 * User-Agent and challenges one claiming to be a browser, for the reason the shared lyrics scraper
 * records. A miss is a miss rather than an error: plenty of songs have no description.
 */
internal object DesktopGenius {

    private const val USER_AGENT = "BitChord-Desktop/1.8 (https://github.com/jackychan4891/BitChord-PC-Continued)"
    private const val CACHE_SIZE = 64

    /** Long enough for a whole description, short enough that the panel stays a panel. */
    private const val MAX_CHARS = 900

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(8))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    // Nulls are kept: the panel is reopened on the same track, and a song Genius has nothing for
    // should not cost another two round trips every time it is.
    private val cache = object : LinkedHashMap<String, String?>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String?>) = size > CACHE_SIZE
    }

    /** The song's own description, or null when Genius does not have one. Never throws. */
    suspend fun about(title: String, artist: String): String? {
        if (title.isBlank() || artist.isBlank()) return null
        val key = "${canonical(artist)}|${canonical(title)}"
        synchronized(cache) { if (cache.containsKey(key)) return cache[key] }
        val found = withContext(Dispatchers.IO) {
            runCatching {
                val page = search(title, artist) ?: return@runCatching null
                description(page)
            }
                .onFailure { DesktopTrackLog.log("About track: Genius lookup failed (${it.javaClass.simpleName})") }
                .getOrNull()
        }
        synchronized(cache) { cache[key] = found }
        return found
    }

    private fun search(title: String, artist: String): String? {
        val query = URLEncoder.encode("$artist $title", StandardCharsets.UTF_8)
        val body = get("https://genius.com/api/search/multi?q=$query") ?: return null
        val sections = runCatching {
            json.parseToJsonElement(body).jsonObject["response"]?.jsonObject?.get("sections")?.jsonArray
        }.getOrNull() ?: return null
        val candidates = sections.mapNotNull { section ->
            val record = section as? JsonObject ?: return@mapNotNull null
            if (record["type"]?.jsonPrimitive?.contentOrNull != "song") return@mapNotNull null
            record["hits"]?.jsonArray?.mapNotNull { (it as? JsonObject)?.get("result") as? JsonObject }
        }.firstOrNull { it.isNotEmpty() } ?: return null
        val want = canonical(title)
        // The search is already artist-and-title, so its top hit is usually right; an exact title
        // still outranks it, because a live version or a cover is the thing that gets served first.
        val best = candidates.firstOrNull { canonical(it.text("title").orEmpty()) == want }
            ?: candidates.first()
        return best.text("url")?.takeIf { it.startsWith("https://") }
    }

    private fun description(page: String): String? {
        val html = get(page) ?: return null
        val block = Jsoup.parse(html, page).let { document ->
            document.selectFirst("div[data-testid=song-description]")
                ?: document.selectFirst("[class*=SongBioPreview-desktop__Container]")
        } ?: return null
        val paragraphs = block.select("p").map { it.text().trim() }.filter { it.isNotBlank() }
        val text = (paragraphs.ifEmpty { listOf(block.text().trim()) }
            .joinToString("\n\n")
            // A clipped teaser keeps its ellipsis, and the sentence it cut is not worth having back.
            .removeSuffix("…")
            .removeSuffix("...")
            .trim())
        if (text.isBlank()) return null
        if (text.length <= MAX_CHARS) return text
        val cut = text.take(MAX_CHARS)
        return cut.substringBeforeLast(' ').trim() + "…"
    }

    private fun get(url: String): String? {
        val request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(12))
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/json;q=0.9")
            .GET().build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        return if (response.statusCode() in 200..299) response.body() else null
    }

    private fun JsonObject.text(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    /** Lower-case and punctuation-free, for comparing a search hit with what was asked for. */
    private fun canonical(value: String): String = value.lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]"), "")
}
