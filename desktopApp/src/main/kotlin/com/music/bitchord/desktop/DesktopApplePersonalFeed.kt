package com.music.bitchord.desktop

import com.music.bitchord.data.model.ShelfItem
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * The rows Apple's home builds around one listener.
 *
 * Apple's own versions of these — *Top Picks for You*, *Featuring …*, *New Release* — are computed
 * against a signed-in Apple account and its catalogue, neither of which this app has: the public
 * endpoints answer with an empty shell and the personalised playlists are not catalogue items at
 * all. What can be had is the same idea from the account the listener *is* signed into — the
 * music source's recent listening — rendered with Apple's pages and Apple's artwork, so the row
 * reads as Apple Music's and opens through the same collection path as every other Apple tile.
 *
 * Everything here is a best effort with nothing to fail the page: a source that is not signed in,
 * an artist Apple has no Essentials list for, or a network that is down leaves the row short or
 * absent rather than breaking Home.
 */
internal object DesktopApplePersonalFeed {
    /**
     * The marker a shelf wears to say it *is* the site's tall (3:4) pick row.
     *
     * Apple's feed never names this lockup — the page chooses which of its own cover rows wears it.
     * A row built here is the picks row by construction, so it says so, and the page stops having
     * to guess. See `HomeShelf.feedLayout`.
     */
    const val PICKS_LAYOUT = "portraitLockup"

    /** The marker a shelf wears to say it is the row of moods. */
    const val MOODS_LAYOUT = "moodLockup"

    /**
     * How many of the recent artists a row is read from.
     *
     * Apple's own picks row shows four or five tiles across the page. Not every artist has an
     * Essentials list of their own, so the row is worked out from a wider head of the recent list
     * than it shows rather than ending up with two covers on it.
     */
    private const val ARTISTS = 6

    /** How many of those artists also get their newest record in the row. */
    private const val ARTISTS_WITH_NEW_RELEASES = 3

    private const val CACHE_TTL_MS = 3L * 60 * 60 * 1000
    private const val MAX_CACHED_KEYS = 96

    /**
     * Apple's mood stations, in the order its own *Find Your Mood* row runs them.
     *
     * The ids are shared by every storefront; what a storefront changes is the name, the line under
     * it and the page it opens, all three of which Apple hands back localized.
     */
    private val MOOD_STATIONS = listOf(
        "ra.q-MLzEBw",
        "ra.q-MKvEBw",
        "ra.q-MMLEBw",
        "ra.q-MI4G",
        "ra.q-MK3VCQ",
        "ra.q-MNDEBw",
        "ra.q-MO3UCQ",
    )

    private val cache = ConcurrentHashMap<String, Entry>()

    /** The clip a mood tile animates with, kept against the tile's own id. */
    private val motion = ConcurrentHashMap<String, String>()

    private class Entry(val hits: List<DesktopAppleMusicCanvas.PageHit>, val expiresAtMs: Long)

    /**
     * The tiles for the picks row, for the artists the listener has been playing.
     *
     * Apple alternates an artist's Essentials list with that artist's newest release, which is both
     * how the site reads and why the row never looks like one long list of the same thing.
     */
    suspend fun picks(artists: List<String>): List<ShelfItem> {
        val names = artists
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.containsVariousArtists() }
            .distinctBy { it.lowercase(Locale.ROOT) }
            .take(ARTISTS)
        if (names.isEmpty()) return emptyList()
        val withAlbums = names.take(ARTISTS_WITH_NEW_RELEASES)
        val essentials: List<DesktopAppleMusicCanvas.PageHit?>
        val newest: List<DesktopAppleMusicCanvas.PageHit?>
        coroutineScope {
            val essentialJobs = names.map { async(Dispatchers.IO) { essential(it) } }
            val albumJobs = withAlbums.map { async(Dispatchers.IO) { newestRelease(it) } }
            essentials = essentialJobs.map { runCatching { it.await() }.getOrNull() }
            newest = albumJobs.map { runCatching { it.await() }.getOrNull() }
        }
        return buildList {
            names.forEachIndexed { index, name ->
                essentials[index]?.let { add(tile(it, DesktopStrings["d_made_for_you", "Made for You"])) }
                if (index < newest.size) {
                    newest[index]?.let { add(tile(it, DesktopStrings["d_new_release", "New Release"])) }
                }
            }
        }.distinctBy { it.browseId }
    }

    /** The tiles for the mood row: Apple's own mood stations, in the listener's storefront. */
    suspend fun moods(): List<ShelfItem> {
        val stations = withContext(Dispatchers.IO) {
            runCatching { DesktopAppleMusicCanvas.moodStations(MOOD_STATIONS) }.getOrDefault(emptyList())
        }
        val items = stations.map { station ->
            val browseId = DesktopAppleBrowseClient.MOOD_PREFIX + station.url
            // A station record carries the motion artwork its tile loops through, so the cover that
            // moves comes from the same read as the name and the blurb under it.
            station.motionUrl?.let { motion[browseId] = it }
            ShelfItem(
                title = station.title,
                subtitle = station.blurb,
                thumbnailUrl = station.artworkUrl,
                videoId = null,
                browseId = browseId,
            )
        }
        // The list behind each tile, read before the row is shown so that clicking one plays
        // straight away instead of waiting on a search. The seven are independent, so they go out
        // together.
        coroutineScope { items.map { async(Dispatchers.IO) { runCatching { curated(it.title) } } } }
        return items
    }

    /** The motion artwork a mood tile loops through, if Apple has any for it. */
    fun motionFor(item: ShelfItem): String? = item.browseId?.let(motion::get)

    /**
     * The list a mood tile plays.
     *
     * A station streams only to Apple's subscribers, and nothing about it is a playlist the app
     * could open, so the tile answers with Apple's own curated list for that mood — the shelf the
     * station is made of — and plays it the way any other Apple row is played.
     */
    suspend fun playlistFor(item: ShelfItem): ShelfItem? {
        val pick = curated(item.title) ?: return null
        return ShelfItem(
            title = item.title,
            subtitle = pick.subtitle,
            thumbnailUrl = pick.artworkUrl,
            videoId = null,
            browseId = DesktopAppleBrowseClient.PAGE_PREFIX + pick.url,
        )
    }

    /**
     * Apple's own list for a mood, or null when the storefront has none.
     *
     * Only a list actually curated for that mood counts. Apple answers a one-word search with every
     * third-party list that uses the word, and a tile that plays someone else's idea of *Focus* is
     * worse than one that plays nothing.
     */
    private suspend fun curated(mood: String): DesktopAppleMusicCanvas.PageHit? {
        val name = mood.trim()
        if (name.isBlank()) return null
        val hits = withContext(Dispatchers.IO) {
            cached("mood:$name") { DesktopAppleMusicCanvas.searchPages(name, "playlists", 10) }
        }
        // Apple's own curation first: a mood named for the storefront's word for *Focus* is a
        // different thing from whatever a third party filed under the same word.
        val apple = hits.filter { it.subtitle.startsWith("Apple Music", ignoreCase = true) }
        val wanted = "apple music ${name.lowercase(Locale.ROOT)}"
        return apple.firstOrNull { it.subtitle.trim().lowercase(Locale.ROOT) == wanted }
            ?: apple.firstOrNull { it.title.contains(name, ignoreCase = true) }
            ?: apple.firstOrNull()
            ?: hits.firstOrNull { it.title.contains(name, ignoreCase = true) }
    }

    // ── Lookups ──────────────────────────────────────────────────────────

    /**
     * An artist's *Essentials* list, as Apple titles it; the nearest thing to a made-for-you mix.
     *
     * Only a list actually named for the artist counts. Apple answers "… Essentials" for a
     * catalogue of one-off artists with whatever editorial playlist has that word in it — a
     * Christmas round-up in October — and a tile that lies about who it was made for is worse than
     * a row with one fewer cover in it.
     */
    private fun essential(artist: String): DesktopAppleMusicCanvas.PageHit? {
        val hits = cached("pl:$artist") { DesktopAppleMusicCanvas.searchPages("$artist Essentials", "playlists", 8) }
        val wanted = artist.lowercase(Locale.ROOT).trim()
        return hits.firstOrNull { it.title.lowercase(Locale.ROOT).startsWith("$wanted essentials") }
    }

    /** The most recent record an artist has put out, which is what Apple's *New Release* cell is. */
    private fun newestRelease(artist: String): DesktopAppleMusicCanvas.PageHit? {
        val hits = cached("al:$artist") { DesktopAppleMusicCanvas.searchPages(artist, "albums", 12) }
        // Apple's search ranks by popularity, not recency, and answers a well-known name with other
        // people's tribute and cover records; the artist line is what says otherwise.
        val own = hits.filter { it.subtitle.isNotBlank() && it.subtitle.matchesArtist(artist) }
        val of = own.ifEmpty { hits }.filterNot { DesktopAppleMusicCanvas.isCompilation(it.title) }
        return of.filter { it.releaseDate.isNotBlank() }.maxByOrNull { it.releaseDate } ?: of.firstOrNull()
    }

    private fun tile(
        hit: DesktopAppleMusicCanvas.PageHit,
        kicker: String,
    ): ShelfItem = ShelfItem(
        title = hit.title,
        subtitle = kicker,
        thumbnailUrl = hit.artworkUrl,
        videoId = null,
        // The same `apple-page:` id every other Apple tile carries, so opening one and playing from
        // it travel the paths the rest of the feed already has.
        browseId = DesktopAppleBrowseClient.PAGE_PREFIX + hit.url,
    )

    // ── Cache ────────────────────────────────────────────────────────────

    /**
     * Answers from Apple once per window per term.
     *
     * Home is revisited constantly and these reads are search calls on the public catalog; a
     * failure is cached as nothing rather than a miss, so a token that is briefly unavailable is
     * retried on the next visit instead of being asked four times at once.
     */
    private fun cached(key: String, load: () -> List<DesktopAppleMusicCanvas.PageHit>): List<DesktopAppleMusicCanvas.PageHit> {
        val now = System.currentTimeMillis()
        cache[key]?.takeIf { it.expiresAtMs > now }?.let { return it.hits }
        val hits = load()
        if (cache.size > MAX_CACHED_KEYS) cache.clear()
        if (hits.isNotEmpty()) cache[key] = Entry(hits, now + CACHE_TTL_MS)
        return hits
    }

    // ── Names ────────────────────────────────────────────────────────────

    /** Whether a row is a credited artist or the source's catch-all for a compilation. */
    private fun String.containsVariousArtists(): Boolean =
        this == "Various Artists" || lowercase(Locale.ROOT) == "various artists"

    /** Whether two ways of writing one artist name agree, in either direction. */
    private fun String.matchesArtist(artist: String): Boolean {
        val left = trim().lowercase(Locale.ROOT)
        val right = artist.trim().lowercase(Locale.ROOT)
        return left == right || left.contains(right) || right.contains(left)
    }
}
