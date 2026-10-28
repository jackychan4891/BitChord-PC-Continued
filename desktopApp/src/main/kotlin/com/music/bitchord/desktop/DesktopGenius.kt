package com.music.bitchord.desktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
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

    /** What the reader asked about: the words on the line, and the paragraph written over them. */
    data class LineMeaning(val text: String, val url: String)

    /** One annotated stretch of a song's lyrics: the annotation it opens, and the words it covers. */
    private class Referent(val id: String, val text: String)

    // The page behind a song is half a megabyte of HTML; parsed once per song and kept for the
    // next right-click on it, since a reader exploring a verse asks about line after line.
    private val referentCache = object : LinkedHashMap<String, List<Referent>?>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Referent>?>) = size > CACHE_SIZE
    }

    private val annotationCache = object : LinkedHashMap<String, String?>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String?>) = size > CACHE_SIZE
    }

    private val meaningCache = object : LinkedHashMap<String, LineMeaning?>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, LineMeaning?>) = size > CACHE_SIZE
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

    /**
     * What Genius's editors wrote over the stretch of lyrics [line] belongs to.
     *
     * This is the per-line annotation rather than [about]'s paragraph about the song: the page
     * marks each annotated fragment with a link whose id is the annotation, and the annotation
     * itself is a different endpoint. A line nobody has written about is answered null, which the
     * dialog says plainly rather than passing the song's blurb off as an explanation of the line.
     */
    suspend fun lineMeaning(title: String, artist: String, line: String): LineMeaning? {
        if (title.isBlank() || artist.isBlank() || line.isBlank()) return null
        val key = "${canonical(artist)}|${canonical(title)}|${canonical(line)}"
        synchronized(meaningCache) { if (meaningCache.containsKey(key)) return meaningCache[key] }
        val found = withContext(Dispatchers.IO) {
            runCatching {
                val page = search(title, artist) ?: return@runCatching null
                val referents = referents(page) ?: return@runCatching null
                val referent = bestReferent(referents, line) ?: return@runCatching null
                val body = annotation(referent.id) ?: return@runCatching null
                LineMeaning(body, page)
            }
                .onFailure { DesktopTrackLog.log("Line meaning: Genius lookup failed (${it.javaClass.simpleName})") }
                .getOrNull()
        }
        synchronized(meaningCache) { meaningCache[key] = found }
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

    /**
     * Every annotated fragment on a song's page, in reading order.
     *
     * Genius marks an annotated stretch by wrapping it in a link whose first path segment is the
     * annotation's id — `/1405348/Radiohead-creep/But-im-a-creep…` — so the lyrics markup is
     * enough; nothing has to be run to read the page.
     */
    private fun referents(page: String): List<Referent>? {
        synchronized(referentCache) { if (referentCache.containsKey(page)) return referentCache[page] }
        val html = get(page)
        val found = html?.let {
            runCatching {
                Jsoup.parse(it, page)
                    .select("div[data-lyrics-container=true] a[href], div.lyrics a[href]")
                    .mapNotNull { anchor ->
                        val id = anchor.attr("href").trim().trimStart('/').substringBefore('/')
                        if (id.isEmpty() || !id.all(Char::isDigit)) return@mapNotNull null
                        val text = anchor.text().trim()
                        if (text.isBlank()) null else Referent(id, text)
                    }
                    .distinctBy { referent -> referent.id to referent.text }
                    .takeIf { referents -> referents.isNotEmpty() }
            }.getOrNull()
        }
        synchronized(referentCache) { referentCache[page] = found }
        return found
    }

    /**
     * The annotated fragment the sung [line] falls inside.
     *
     * An annotation covers one stretch of the lyrics — often several lines — so the fragment is
     * what has to be found, not an equal string. The smallest fragment that holds the line is the
     * most specific answer; where the line is the longer of the two (a lyric service splitting a
     * fragment differently), the longest fragment it contains is used instead.
     */
    private fun bestReferent(referents: List<Referent>, line: String): Referent? {
        val want = canonical(line)
        if (want.isEmpty()) return null
        referents.firstOrNull { canonical(it.text) == want }?.let { return it }
        referents.filter { canonical(it.text).contains(want) }
            .minByOrNull { canonical(it.text).length }
            ?.let { return it }
        return referents.filter { want.contains(canonical(it.text)) }
            .maxByOrNull { canonical(it.text).length }
    }

    /** The annotation's own paragraph, as plain text. */
    private fun annotation(id: String): String? {
        synchronized(annotationCache) { if (annotationCache.containsKey(id)) return annotationCache[id] }
        val text = runCatching {
            val body = get("https://genius.com/api/annotations/$id") ?: return@runCatching null
            val annotation = json.parseToJsonElement(body).jsonObject["response"]?.jsonObject
                ?.get("annotation")?.jsonObject?.get("body")?.jsonObject ?: return@runCatching null
            // The API serves the body as rendered HTML on some annotations and as a node tree on
            // others; both are the same paragraph.
            val html = (annotation["html"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
            val raw = if (html != null) Jsoup.parseBodyFragment(html).text() else domText(annotation["dom"])
            raw.replace('\u00A0', ' ')
                .replace(Regex("[ \\t]+"), " ")
                .replace(Regex(" *\\n *"), "\n")
                .replace(Regex("\\n{3,}"), "\n\n")
                .trim()
                .ifBlank { null }
        }.getOrNull()
        synchronized(annotationCache) { annotationCache[id] = text }
        return text
    }

    /** The annotation API's node tree (`{tag, children: [...]}`) flattened into paragraphs. */
    private fun domText(dom: JsonElement?): String {
        if (dom == null) return ""
        val out = StringBuilder()
        fun walk(node: JsonElement?) {
            when (node) {
                is JsonPrimitive -> if (node.isString) out.append(node.content)
                is JsonObject -> {
                    val tag = node["tag"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    if (tag == "br") out.append('\n')
                    node["children"]?.jsonArray?.forEach { walk(it) }
                    if (tag == "p" || tag == "li") out.append("\n\n")
                }
                else -> Unit
            }
        }
        walk(dom)
        return out.toString()
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
