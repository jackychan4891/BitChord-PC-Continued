package com.music.bitchord.desktop

import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.MoodGenre
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.net.URI

internal object DesktopAppleBrowseClient {
    const val PAGE_PREFIX = "apple-page:"
    const val SONG_PREFIX = "apple-song:"
    private const val NEW_URL = "https://music.apple.com/gb/new"
    private const val CACHE_MILLIS = 30 * 60 * 1000L
    private val json = Json { ignoreUnknownKeys = true }
    private val cache = object : LinkedHashMap<String, Pair<Long, List<HomeShelf>>>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, List<HomeShelf>>>) = size > 32
    }
    private val resolved = object : LinkedHashMap<String, Song>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Song>) = size > 128
    }

    suspend fun newPage(): Result<List<HomeShelf>> = page(NEW_URL, "New")

    suspend fun page(url: String, title: String): Result<List<HomeShelf>> = withContext(Dispatchers.IO) {
        try {
            check(validPageUrl(url)) { "Invalid Apple Music page" }
            synchronized(cache) {
                cache[url]?.let { (time, shelves) ->
                    if (System.currentTimeMillis() - time < CACHE_MILLIS) return@withContext Result.success(shelves)
                }
            }
            val html = canvasGet(url) ?: error("Could not load Apple Music")
            val shelves = parse(html, title)
            check(shelves.isNotEmpty()) { "No music found on this Apple Music page" }
            synchronized(cache) { cache[url] = System.currentTimeMillis() to shelves }
            Result.success(shelves)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Result.failure(failure)
        }
    }

    fun categories(shelves: List<HomeShelf>): List<MoodGenre> = shelves.filter { it.isAppleChartShelf() }
        .mapNotNull { shelf ->
            val id = shelf.moreBrowseId ?: return@mapNotNull null
            MoodGenre(
                title = when (shelf.title) {
                    "Daily Top 100" -> "Charts"
                    "Trending Songs" -> "Trending"
                    else -> shelf.title
                },
                browseId = id,
                params = null,
                thumbnailUrl = shelf.items.firstOrNull()?.thumbnailUrl,
            )
        }

    suspend fun resolveSong(item: ShelfItem): Result<Song> {
        val id = item.browseId.orEmpty()
        synchronized(resolved) { resolved[id]?.let { return Result.success(it) } }
        return DesktopSearchClient.search("${item.title} ${item.subtitle}").mapCatching { rows ->
            val songs = rows.mapNotNull { row ->
                when (row) {
                    is SearchResult.Track -> row.song
                    is SearchResult.TopTrack -> row.song
                    else -> null
                }
            }
            val song = songs.firstOrNull {
                DesktopCanvasArtwork(url = "", title = it.title.cleanedForCanvas(), artist = it.artist.cleanedForCanvas())
                    .matches(item.title.cleanedForCanvas(), item.subtitle.cleanedForCanvas(), null)
            } ?: error("${item.title} by ${item.subtitle} is not available from the current music source")
            val withArtwork = song.copy(thumbnailUrl = item.thumbnailUrl ?: song.thumbnailUrl)
            synchronized(resolved) { resolved[id] = withArtwork }
            withArtwork
        }
    }

    internal fun parse(html: String, pageTitle: String): List<HomeShelf> {
        val body = Regex("""<script\b[^>]*id=["']serialized-server-data["'][^>]*>([\s\S]*?)</script>""")
            .find(html)?.groupValues?.get(1) ?: return emptyList()
        val root = json.parseToJsonElement(body) as? JsonObject ?: return emptyList()
        val page = root.array("data").mapNotNull { (it as? JsonObject)?.obj("data") }
            .firstOrNull { it["sections"] is JsonArray } ?: return emptyList()
        return page.array("sections").mapNotNull { element ->
            val section = element as? JsonObject ?: return@mapNotNull null
            if (section.text("itemKind") !in setOf("flowcaseLockup", "squareLockup", "trackLockup")) return@mapNotNull null
            val heading = section.obj("header")?.obj("item")?.obj("titleLink")
            val title = heading?.text("title") ?: pageTitle
            val items = section.array("items").mapNotNull { parseItem(it as? JsonObject) }
                .distinctBy { it.browseId }
            if (items.isEmpty()) return@mapNotNull null
            HomeShelf(
                title = title,
                items = items,
                moreBrowseId = heading?.text("url")?.takeIf(::validPageUrl)?.let { PAGE_PREFIX + it },
            )
        }
    }

    private fun parseItem(item: JsonObject?): ShelfItem? {
        item ?: return null
        val descriptor = item.obj("contentDescriptor") ?: return null
        val kind = descriptor.text("kind")
        if (kind !in setOf("song", "album", "playlist")) return null
        val url = descriptor.text("url")?.takeIf(::validPageUrl) ?: return null
        val title = item.text("title") ?: item.links("titleLinks").firstOrNull() ?: return null
        val artist = item.links("subtitleLinks").joinToString(", ").ifBlank { item.text("subtitle").orEmpty() }
        val artwork = item.obj("artwork")?.obj("dictionary")?.text("url")
            ?.replace("{w}", "400")?.replace("{h}", "400")?.replace("{c}", "bb")?.replace("{f}", "jpg")
        return ShelfItem(title, artist, artwork, null, (if (kind == "song") SONG_PREFIX else PAGE_PREFIX) + url)
    }

    private fun validPageUrl(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme == "https" && uri.host == "music.apple.com" && uri.userInfo == null && uri.port == -1
    }.getOrDefault(false)

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
    private fun JsonObject.array(key: String): List<JsonElement> = (this[key] as? JsonArray).orEmpty()
    private fun JsonObject.links(key: String): List<String> = array(key).mapNotNull { (it as? JsonObject)?.text("title") }
}

internal fun ShelfItem.isAppleSong(): Boolean = browseId?.startsWith(DesktopAppleBrowseClient.SONG_PREFIX) == true
internal fun HomeShelf.isAppleChartShelf(): Boolean = title.contains("trend", true) || title.contains("chart", true) ||
    title.contains("top 100", true) || title.contains("viral", true)
