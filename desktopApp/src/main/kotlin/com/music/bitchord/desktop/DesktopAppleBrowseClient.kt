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
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.net.URI
import java.security.MessageDigest

internal object DesktopAppleBrowseClient {
    const val PAGE_PREFIX = "apple-page:"
    const val SONG_PREFIX = "apple-song:"
    const val RADIO_PREFIX = "apple-radio:"
    /**
     * Covers are asked for at twice the widest tile a shelf draws. A 600px copy is over twice the
     * bytes of a 400px one, and a page is a hundred of them; surfaces that want more say so.
     */
    private const val ARTWORK_PX = 400
    private const val NEW_URL = "https://music.apple.com/gb/new"

    /**
     * Apple republishes its charts, playlists and new releases on the day boundary, so a kept page
     * is worth reading only until the next midnight GMT — and is fetched again, once, past it.
     */
    private const val DAY_MILLIS = 24 * 60 * 60 * 1000L

    private fun dayStartUtc(now: Long): Long = now - now % DAY_MILLIS

    private val json = Json { ignoreUnknownKeys = true }
    private val cache = object : LinkedHashMap<String, Pair<Long, List<HomeShelf>>>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, List<HomeShelf>>>) = size > 32
    }
    private val resolved = object : LinkedHashMap<String, Song>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Song>) = size > 128
    }

    /**
     * The rest of a track cell's columns: the release it sits on and its length.
     *
     * Apple's own tables read Song · Artist · Album · Time, and both extra columns come from the
     * page's track lockup rather than from anything the player needs, so they are kept beside the
     * shelf item instead of being pressed into it.
     */
    data class TrackDetail(val album: String?, val albumUrl: String?, val durationMs: Long?)

    private val details = object : LinkedHashMap<String, TrackDetail>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, TrackDetail>) = size > 1024
    }

    fun trackDetail(item: ShelfItem): TrackDetail? = synchronized(details) { details[item.browseId.orEmpty()] }

    private fun rememberDetail(browseId: String, album: String?, albumUrl: String?, durationMs: Long?) {
        if (album == null && albumUrl == null && durationMs == null) return
        synchronized(details) { details[browseId] = TrackDetail(album, albumUrl, durationMs) }
    }

    /** Apple writes link URLs both absolute and relative to the storefront. */
    private fun absolutePageUrl(url: String): String? =
        (if (url.startsWith("/")) "https://music.apple.com$url" else url).takeIf(::validPageUrl)

    suspend fun newPage(): Result<List<HomeShelf>> = page(NEW_URL, "New")

    suspend fun page(url: String, title: String): Result<List<HomeShelf>> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val kept = synchronized(cache) { cache[url] } ?: readDisk(url)?.also { entry ->
            synchronized(cache) { cache[url] = entry }
        }
        if (kept != null && kept.first >= dayStartUtc(now)) return@withContext Result.success(kept.second)
        try {
            check(validPageUrl(url)) { "Invalid Apple Music page" }
            val html = canvasGet(url) ?: error("Could not load Apple Music")
            val shelves = parse(html, title)
            check(shelves.isNotEmpty()) { "No music found on this Apple Music page" }
            synchronized(cache) { cache[url] = now to shelves }
            writeDisk(url, shelves)
            Result.success(shelves)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            // Yesterday's page still plays; a fetch that fails on the day boundary is not worth
            // an error screen in front of it.
            if (kept != null) Result.success(kept.second) else Result.failure(failure)
        }
    }

    // ── What the last visit kept, on disk ─────────────────────────────────────────
    //
    // An album, playlist or set list is a megabyte of HTML parsed into a dozen shelves, and the
    // same collection gets opened again across restarts. The parsed shelves are small, so they are
    // written back as JSON and read on the next launch instead of fetched again.

    private val pagesDirectory: Path by lazy {
        val state = System.getenv("XDG_CACHE_HOME")?.takeIf(String::isNotBlank)
            ?: "${System.getProperty("user.home")}/.cache"
        Path.of(state, "bitchord", "apple-pages").also { runCatching { Files.createDirectories(it) } }
    }

    private fun pageFile(url: String): Path? = runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(url.toByteArray(StandardCharsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
        // v3: the shelf's own row count joined the cache format. A file from before it would draw
        // today's two-row shelves in yesterday's single row until the day's page was fetched again.
        pagesDirectory.resolve("$digest.v3.json")
    }.getOrNull()

    private fun readDisk(url: String): Pair<Long, List<HomeShelf>>? = runCatching {
        val file = pageFile(url) ?: return@runCatching null
        val stamp = Files.getLastModifiedTime(file).toMillis()
        val root = json.parseToJsonElement(Files.readString(file, StandardCharsets.UTF_8)) as? JsonArray
        val shelves = root.orEmpty().mapNotNull { element ->
            val shelf = element as? JsonObject ?: return@mapNotNull null
            val items = shelf.array("items").mapNotNull { row ->
                val entry = row as? JsonObject ?: return@mapNotNull null
                val browseId = entry.text("browseId").orEmpty()
                rememberDetail(
                    browseId,
                    entry.text("album"),
                    entry.text("albumUrl"),
                    (entry["durationMs"] as? JsonPrimitive)?.longOrNull,
                )
                ShelfItem(
                    title = entry.text("title").orEmpty(),
                    subtitle = entry.text("subtitle").orEmpty(),
                    thumbnailUrl = entry.text("thumbnailUrl"),
                    videoId = entry.text("videoId"),
                    browseId = browseId,
                )
            }
            if (items.isEmpty()) return@mapNotNull null
            HomeShelf(
                title = shelf.text("title").orEmpty(),
                items = items,
                subtitle = shelf.text("subtitle").orEmpty(),
                moreBrowseId = shelf.text("moreBrowseId"),
                layout = shelf.text("layout").orEmpty(),
                rows = shelf.text("rows")?.toIntOrNull() ?: 1,
            )
        }
        if (shelves.isEmpty()) null else stamp to shelves
    }.getOrNull()

    private fun writeDisk(url: String, shelves: List<HomeShelf>) {
        val file = pageFile(url) ?: return
        runCatching {
            val root = buildJsonArray {
                shelves.forEach { shelf ->
                    add(
                        buildJsonObject {
                            put("title", shelf.title)
                            put("subtitle", shelf.subtitle)
                            put("rows", shelf.rows)
                            shelf.moreBrowseId?.let { put("moreBrowseId", it) }
                            if (shelf.layout.isNotBlank()) put("layout", shelf.layout)
                            putJsonArray("items") {
                                shelf.items.forEach { item ->
                                    addJsonObject {
                                        put("title", item.title)
                                        put("subtitle", item.subtitle)
                                        item.thumbnailUrl?.let { put("thumbnailUrl", it) }
                                        item.videoId?.let { put("videoId", it) }
                                        item.browseId?.let { put("browseId", it) }
                                        val detail = trackDetail(item)
                                        detail?.album?.let { put("album", it) }
                                        detail?.albumUrl?.let { put("albumUrl", it) }
                                        detail?.durationMs?.let { put("durationMs", it) }
                                    }
                                }
                            }
                        },
                    )
                }
            }
            Files.writeString(file, root.toString(), StandardCharsets.UTF_8)
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
            val layout = section.text("itemKind")
            if (layout !in setOf("flowcaseLockup", "squareLockup", "trackLockup", "liveRadioGridLockup", "horizontalLockup", "verticalVideoLockup")) return@mapNotNull null
            val heading = section.obj("header")?.obj("item")?.obj("titleLink")
            val title = heading?.text("title") ?: pageTitle
            val items = section.array("items").mapNotNull { parseItem(it as? JsonObject, layout == "liveRadioGridLockup") }
                .distinctBy { it.browseId }
            if (items.isEmpty()) return@mapNotNull null
            HomeShelf(
                title = title,
                items = items,
                moreBrowseId = heading?.text("url")?.takeIf(::validPageUrl)?.let { PAGE_PREFIX + it },
                layout = layout.orEmpty(),
                // The shelf's own depth, which Apple states per section: a run of songs is four
                // rows, "Updated Playlists" two, most cover rows one.
                rows = section.obj("presentation")?.obj("layout")?.text("numberOfRows")?.toIntOrNull() ?: 1,
            )
        }
    }

    private fun parseItem(item: JsonObject?, liveGrid: Boolean): ShelfItem? {
        item ?: return null
        val descriptor = item.obj("contentDescriptor") ?: return null
        val kind = descriptor.text("kind")
        // Videos, archived shows and subscriber stations only play in Apple's own player.
        if (kind !in setOf("song", "album", "playlist", "radioStation", "curator")) return null
        if (kind == "radioStation" && !liveGrid && item.obj("radioStation")?.text("isLiveRadio") != "true") return null
        val url = descriptor.text("url")?.takeIf(::validPageUrl) ?: return null
        val title = item.text("title") ?: item.links("titleLinks").firstOrNull() ?: item.text("accessibilityLabel") ?: return null
        // A track cell's artist is a link on some pages and a plain name on others — an album's own
        // list gives only `artistName`, and would otherwise show no artist at all.
        val artist = item.links("subtitleLinks").joinToString(", ").ifBlank {
            item.text("subtitle").orEmpty().ifBlank { item.text("artistName").orEmpty() }
        }
        val prefix = when (kind) {
            "song" -> SONG_PREFIX
            "radioStation" -> RADIO_PREFIX
            else -> PAGE_PREFIX
        }
        val browseId = prefix + url
        val caption = if (prefix == RADIO_PREFIX) "Live Radio · Free" else artist
        val artwork = item.obj("artwork")?.obj("dictionary")?.text("url")
            ?.replace("{w}", "$ARTWORK_PX")?.replace("{h}", "$ARTWORK_PX")?.replace("{c}", "bb")?.replace("{f}", "jpg")
        // The album the cell names is also a link on most pages, which is what makes the Album
        // column openable rather than only readable.
        val albumUrl = item.array("tertiaryLinks")
            .mapNotNull { (it as? JsonObject)?.text("url") }
            .mapNotNull(::absolutePageUrl)
            .firstOrNull()
        rememberDetail(
            browseId,
            item.names("tertiaryLinks").joinToString(", ").ifBlank { null },
            albumUrl,
            (item["duration"] as? JsonPrimitive)?.longOrNull,
        )
        return ShelfItem(title, caption, artwork, null, browseId)
    }

    private fun validPageUrl(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme == "https" && uri.host == "music.apple.com" && uri.userInfo == null && uri.port == -1
    }.getOrDefault(false)

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
    private fun JsonObject.array(key: String): List<JsonElement> = (this[key] as? JsonArray).orEmpty()
    private fun JsonObject.links(key: String): List<String> = array(key).mapNotNull { (it as? JsonObject)?.text("title") }

    /** Names that Apple writes either as bare strings or as link objects, depending on the page. */
    private fun JsonObject.names(key: String): List<String> = array(key).mapNotNull { row ->
        when (row) {
            is JsonPrimitive -> row.contentOrNull
            is JsonObject -> row.text("title")
            else -> null
        }
    }
}

internal fun ShelfItem.isAppleSong(): Boolean = browseId?.startsWith(DesktopAppleBrowseClient.SONG_PREFIX) == true

/** Apple's live-radio grid. The Radio page is where a station belongs, not the New one. */
internal fun HomeShelf.isAppleRadioShelf(): Boolean =
    items.isNotEmpty() &&
        items.all { it.browseId?.startsWith(DesktopAppleBrowseClient.RADIO_PREFIX) == true }

/** A song either way it arrives: Apple's own chart track, or the music source's. */
internal fun ShelfItem.isSongLine(): Boolean = isAppleSong() || (!videoId.isNullOrBlank() && browseId.isNullOrBlank())
internal fun HomeShelf.isAppleChartShelf(): Boolean = title.contains("trend", true) || title.contains("chart", true) ||
    title.contains("top 100", true) || title.contains("viral", true)

/** An Apple editorial playlist, opened as a page of its own. */
internal fun ShelfItem.isApplePlaylistTile(): Boolean =
    browseId?.startsWith(DesktopAppleBrowseClient.PAGE_PREFIX) == true && browseId.orEmpty().contains("/playlist/")

/**
 * A shelf of Apple's own playlists — the row the home page takes over from the
 * music source, whose mixes and subscriber lists are not ours to show.
 */
internal fun HomeShelf.isApplePlaylistShelf(): Boolean =
    !isAppleChartShelf() && items.size >= 6 && items.count { it.isApplePlaylistTile() } * 2 >= items.size

/** The equivalent row from the music source, which Apple's playlists replace. */
internal fun HomeShelf.isSourcePlaylistShelf(): Boolean =
    items.size >= 4 && items.count { it.browseId?.startsWith("VL") == true } * 2 >= items.size
