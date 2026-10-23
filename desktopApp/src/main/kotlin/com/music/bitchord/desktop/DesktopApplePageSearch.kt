package com.music.bitchord.desktop

import com.music.bitchord.data.model.BrowseItem
import com.music.bitchord.data.model.BrowseType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Apple's pages for the desktop's own search field.
 *
 * The music source answers with its songs, albums and playlists; Apple's editorial playlists only
 * exist on Apple, so they are asked for separately and merged into the same results. A client of
 * its own, rather than a change to the shared search backend, which stays exactly as the phone has
 * it.
 */
internal object DesktopApplePageSearch {

    private const val CACHE_MILLIS = 10 * 60 * 1000L

    private val cache = object : LinkedHashMap<String, Pair<Long, List<BrowseItem>>>(16, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, Pair<Long, List<BrowseItem>>>,
        ) = size > 16
    }

    /** Apple's playlists for [term], as rows the search page already knows how to draw. */
    suspend fun playlists(term: String): List<BrowseItem> = withContext(Dispatchers.IO) {
        val key = term.trim().lowercase()
        if (key.isBlank()) return@withContext emptyList()
        val now = System.currentTimeMillis()
        synchronized(cache) { cache[key] }?.let { (at, rows) ->
            if (now - at < CACHE_MILLIS) return@withContext rows
        }
        val rows = DesktopAppleMusicCanvas.searchPages(key, "playlists", limit = 12).map { hit ->
            BrowseItem(
                browseId = DesktopAppleBrowseClient.PAGE_PREFIX + hit.url,
                title = hit.title,
                subtitle = hit.subtitle,
                thumbnailUrl = hit.artworkUrl,
                type = BrowseType.PLAYLIST,
            )
        }
        // Cached even when empty: a term Apple has nothing for should not be asked again on
        // every keystroke's worth of searching.
        synchronized(cache) { cache[key] = now to rows }
        rows
    }

    /** The Apple page for [album] by [artist], for a track whose album cell carries no link. */
    suspend fun albumPage(album: String, artist: String): String? = withContext(Dispatchers.IO) {
        if (album.isBlank()) return@withContext null
        val hits = DesktopAppleMusicCanvas.searchPages("$artist $album".trim(), "albums", limit = 5)
        hits.firstOrNull { it.title.equals(album, ignoreCase = true) }?.url ?: hits.firstOrNull()?.url
    }
}
