package com.music.bitchord.desktop

import com.music.bitchord.data.lyrics.LrcLib
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.lyrics.LyricsRepository
import com.music.bitchord.data.lyrics.LyricsSource
import com.music.bitchord.data.lyrics.withBackgroundVocals
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.durationMillis

/** A track's lyrics and which provider they came from, by its label. */
data class DesktopLyrics(val source: String, val lines: List<LyricLine>) {
    val plainText: String get() = lines.joinToString("\n") { it.text }
    val wordSynced: Boolean get() = lines.any(LyricLine::isWordSynced)
}

/**
 * The desktop's lyrics lookup.
 *
 * The providers, the race between them and the parsing are the phone's own
 * [LyricsRepository], shared from `:shared`. What stays here is the desktop's
 * settings — which providers are ticked, in what order, stored by label — and
 * the one source only a desktop has: the words inside a local file.
 */
object DesktopLyricsClient {
    private const val CACHE_TTL_MS = 12L * 60L * 60L * 1_000L

    /** Keys the settings screen writes and this reads. */
    internal const val KEY_SYNCED_LYRICS = "synced_lyrics"
    internal const val KEY_PRIORITIZE_SYLLABLES = "prioritize_syllable_lyrics"
    internal const val KEY_LYRICS_BLUR = "lyrics_blur"
    internal const val KEY_LYRICS_SOURCES = "lyrics_sources"

    /**
     * Which sources have ever been put in front of the listener; see
     * [DesktopPersistence.lyricsEnabledSources].
     */
    internal const val KEY_LYRICS_SOURCES_SEEN = "lyrics_sources_seen"
    internal const val KEY_LYRICS_ORDER = "lyrics_source_order"

    /** One lyric database, as the settings screen needs to describe it. */
    internal data class Source(
        val name: String,
        val detail: String,
        val wordSynced: Boolean,
        /** The source this one is a variant of; only the desktop's own set it. */
        val after: String? = null,
    )

    /**
     * Every provider out of the box: the phone's, with the two the desktop reaches itself
     * slotted beside the source each of them is a variant of.
     */
    internal val sources: List<Source> = run {
        val out = LyricsSource.offered.map { Source(it.label, it.detail, it.wordSynced) }.toMutableList()
        DesktopExtraLyrics.sources.forEach { extra ->
            val anchor = extra.after?.let { wanted -> out.indexOfFirst { it.name == wanted } } ?: -1
            out.add(anchor + 1, extra)
        }
        out
    }

    /**
     * A saved order brought up to date with this build's list: anything gone drops out, anything
     * new lands beside the source it was declared after. The phone's rule for the same job,
     * widened to cover providers a [LyricsSource] has no name for.
     */
    internal fun orderFor(saved: List<String>): List<String> {
        val known = sources.map { it.name }
        val out = saved.filter { it in known }.distinct().toMutableList()
        known.forEachIndexed { index, name ->
            if (name in out) return@forEachIndexed
            val before = known.subList(0, index).lastOrNull { it in out }
            out.add(before?.let { out.indexOf(it) + 1 } ?: 0, name)
        }
        return out
    }

    /** The sources that will actually be asked, in the order they are asked. */
    internal fun enabledSources(order: List<String>, enabled: Set<String>): List<String> =
        order.filter { it in enabled }

    private class Cached(val createdAt: Long, val result: Result<DesktopLyrics>)

    /**
     * The last few hundred lookups.
     *
     * A full set of words is tens of kilobytes of strings and a long session touches thousands of
     * tracks, so an unbounded map here quietly holds a chunk of the heap; the tracks worth a second
     * lookup are the ones near the front, which is exactly what an access-ordered map keeps.
     */
    private const val CACHE_ENTRIES = 300

    private val cache = object : LinkedHashMap<String, Cached>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Cached>) = size > CACHE_ENTRIES
    }

    /**
     * The track's lyrics from the configured providers — or, with [only], from that one provider,
     * which is how the player's provider picker asks for a specific source's answer.
     *
     * [onSourceStarted] and [onSourceResult] report each provider's race as it runs, so the song
     * menu's provider drawer can show what each one turned up rather than only the winner. They
     * fire off the IO dispatcher and only for a lookup that actually reaches the network — a cache
     * hit or a local file's embedded words return at once and report nothing.
     */
    suspend fun lookup(
        song: Song,
        durationMs: Long = song.durationMillis(),
        only: String? = null,
        onSourceStarted: ((LyricsSource) -> Unit)? = null,
        onSourceResult: ((LyricsSource, Boolean) -> Unit)? = null,
    ): Result<DesktopLyrics> {
        val persistence = DesktopPersistence()
        // Off in Settings means no lookup at all rather than a lookup nobody sees.
        if (!persistence.boolean(KEY_SYNCED_LYRICS, true)) {
            return Result.failure(IllegalStateException("Synced lyrics are switched off"))
        }
        val configured = enabledSources(persistence.lyricsSourceOrder(), persistence.lyricsEnabledSources())
        val asked = only?.let { listOf(it) } ?: configured
        // Every source unticked is a state the listener can reach, and it means the same thing as
        // the feature being off.
        if (asked.isEmpty()) return Result.failure(IllegalStateException("No lyrics sources are enabled"))
        val sharedAsked = asked.mapNotNull(::lyricsSourceNamed)

        // The title and artist are part of the key, not just the video id: the
        // player's manual search asks the same track's providers under a typed
        // query, and a cache keyed only on the id would hand back the earlier
        // automatic answer and look as though the search had done nothing.
        val cacheKey = "${song.videoId}|$durationMs|${song.title}|${song.artist}|${only.orEmpty()}"
        // Reads reorder the map, so the access is guarded rather than the map being concurrent.
        synchronized(cache) { cache[cacheKey] }
            ?.takeIf { System.currentTimeMillis() - it.createdAt < CACHE_TTL_MS }?.let { return it.result }

        // A downloaded or local file carries its own words; asking servers for a string already
        // on disk is a round trip, and it is why a download showed nothing offline.
        val embedded = if (only == null) embedded(song) else null
        val result = if (embedded != null) {
            Result.success(DesktopLyrics("Downloaded", embedded))
        } else {
            val found = if (sharedAsked.isEmpty()) null else LyricsRepository.lyrics(
                videoId = song.videoId,
                title = song.title,
                artist = song.artist,
                durationMs = durationMs,
                album = song.albumName,
                sources = sharedAsked.toSet(),
                order = sharedAsked,
                prioritizeSyllableSync = persistence.boolean(KEY_PRIORITIZE_SYLLABLES, false),
                onSourceStarted = onSourceStarted,
                onSourceResult = { source, result -> onSourceResult?.invoke(source, result != null) },
            )
            var answer = found?.let { DesktopLyrics(it.source.label, it.lines) }
            // The desktop's own providers are asked only when one of them sits ahead of whatever
            // the shared race settled on. Asking them always would put two more round trips in
            // front of every track change to win a song the top of the list already had.
            var beat = answer?.let { asked.indexOf(it.source) }?.takeIf { it >= 0 } ?: asked.size
            for (index in 0 until beat) {
                val name = asked[index]
                if (name !in DesktopExtraLyrics.names) continue
                val lines = DesktopExtraLyrics.lyrics(name, song.title, song.artist, durationMs, song.albumName)
                if (lines != null) {
                    answer = DesktopLyrics(name, lines)
                    break
                }
            }
            answer?.let { Result.success(it) } ?: Result.failure(IllegalStateException("Lyrics unavailable"))
        }
        synchronized(cache) { cache[cacheKey] = Cached(System.currentTimeMillis(), result) }
        return result
    }

    /** The words inside the file this track plays from, when it plays from one. */
    private fun embedded(song: Song): List<LyricLine>? {
        val path = song.localPath?.let { runCatching { java.nio.file.Paths.get(it) }.getOrNull() }
            ?: song.localUri?.let { runCatching { java.nio.file.Paths.get(java.net.URI(it)) }.getOrNull() }
            ?: return null
        val raw = DesktopEmbeddedLyrics.read(path) ?: return null
        // The same last pass the network sources get, as on the phone.
        return LrcLib.parseLrc(raw).takeIf { lines -> lines.any { it.text.isNotBlank() } }?.withBackgroundVocals()
    }
}
