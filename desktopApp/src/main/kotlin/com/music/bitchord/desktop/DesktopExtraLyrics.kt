package com.music.bitchord.desktop

import com.music.bitchord.data.lyrics.KaraokeLrc
import com.music.bitchord.data.lyrics.LrcLib
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.lyrics.ProviderLyrics
import com.music.bitchord.data.lyrics.lyricsJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Two lyric databases the shared catalogue does not carry, reached from here.
 *
 * They are kept out of `:shared` on purpose: that module is the phone's too, and a provider added
 * there changes what a released build asks over the network. `:shared` is frozen for this project,
 * so the desktop grows its own list instead — and the parsing is still the phone's, through
 * [KaraokeLrc], [LrcLib] and [ProviderLyrics], so a line means the same thing either way.
 */
internal object DesktopExtraLyrics {

    /** Apple Music's timing served as TTML straight, rather than converted to word runs. */
    const val BETTER_LYRICS_TTML = "BetterLyrics Syllable"

    /** NetEase Cloud Music's YRC: syllable and word timings at millisecond resolution. */
    const val NETEASE = "NetEase (YRC)"

    /** Declared beside the source it is a variant of, so the default order keeps them together. */
    internal val sources = listOf(
        DesktopLyricsClient.Source(
            name = BETTER_LYRICS_TTML,
            detail = "Apple Music syllable-level timing, straight from the TTML",
            wordSynced = true,
            after = "BetterLyrics",
        ),
        DesktopLyricsClient.Source(
            name = NETEASE,
            detail = "Syllable and word-by-word timing from NetEase's YRC",
            wordSynced = true,
            after = BETTER_LYRICS_TTML,
        ),
    )

    internal val names: Set<String> = sources.map { it.name }.toSet()

    /** The named provider's answer, or null for any failure — including one it never had. */
    suspend fun lyrics(
        name: String,
        title: String,
        artist: String,
        durationMs: Long,
        album: String?,
    ): List<LyricLine>? = when (name) {
        BETTER_LYRICS_TTML -> betterLyricsTtml(title, artist, durationMs, album)
        NETEASE -> netease(title, artist, durationMs)
        else -> null
    }

    private suspend fun betterLyricsTtml(
        title: String,
        artist: String,
        durationMs: Long,
        album: String?,
    ): List<LyricLine>? = withContext(Dispatchers.IO) {
        val url = "https://lyrics-api.boidu.dev/ttml/getLyrics".toHttpUrl().newBuilder()
            .addQueryParameter("s", title)
            .addQueryParameter("a", artist)
            .apply {
                val seconds = durationMs / 1000
                if (seconds > 0) addQueryParameter("d", seconds.toString())
                if (!album.isNullOrBlank()) addQueryParameter("al", album)
            }
            .build()
        get(url.toString(), null)?.let { ProviderLyrics.parse(it) }
    }

    /**
     * NetEase has no catalogue id to hand, so this is two calls: find the recording by name and
     * length, then ask that id for its words. The length is what picks between the versions a
     * search returns — the live take, the sped-up edit, the demo — and the one that runs to the
     * same number of seconds as the track playing is almost always the right one.
     */
    private suspend fun netease(title: String, artist: String, durationMs: Long): List<LyricLine>? =
        withContext(Dispatchers.IO) {
            val seconds = (durationMs / 1000).toInt()
            val id = searchSongId("$title $artist".trim(), seconds) ?: return@withContext null
            songLyrics(id)
        }

    private fun searchSongId(query: String, seconds: Int): Long? {
        val url = "http://music.163.com/api/search/get/".toHttpUrl().newBuilder()
            .addQueryParameter("s", query)
            .addQueryParameter("type", "1")
            .addQueryParameter("offset", "0")
            .addQueryParameter("limit", "5")
            .build()
        val body = get(
            url.toString(),
            mapOf(
                "User-Agent" to "NeteaseMusic/9.0.0 (iPhone; iOS 16.0; Scale/3.00)",
                "Referer" to "http://music.163.com/",
            ),
        ) ?: return null
        val songs = runCatching { lyricsJson.decodeFromString<SearchResponse>(body) }
            .getOrNull()?.result?.songs.orEmpty()
        if (songs.isEmpty()) return null
        return if (seconds > 0) {
            songs.filter { abs((it.duration / 1000) - seconds) <= SEARCH_DURATION_TOLERANCE_SECONDS }
                .minByOrNull { abs((it.duration / 1000) - seconds) } ?: songs.first()
        } else {
            songs.first()
        }.id
    }

    /**
     * `yv` asks for the YRC document, `lv` for the plain line-synced one. The karaoke track wins
     * when it exists; a song with no syllable data still has words, and whole-line timing is worth
     * having over nothing.
     */
    private fun songLyrics(songId: Long): List<LyricLine>? {
        val url = "https://music.163.com/api/song/lyric".toHttpUrl().newBuilder()
            .addQueryParameter("id", songId.toString())
            .addQueryParameter("lv", "1")
            .addQueryParameter("tv", "1")
            .addQueryParameter("rv", "1")
            .addQueryParameter("yv", "1")
            .build()
        val body = get(
            url.toString(),
            mapOf("User-Agent" to "Mozilla/5.0", "Referer" to "https://music.163.com"),
        ) ?: return null
        val response = runCatching { lyricsJson.decodeFromString<LyricResponse>(body) }.getOrNull() ?: return null
        response.yrc?.lyric?.trim()?.takeIf { it.isNotEmpty() }?.let { raw ->
            val parsed = KaraokeLrc.parse(raw)
            if (parsed.any(LyricLine::isWordSynced)) return parsed
        }
        response.lrc?.lyric?.trim()?.takeIf { it.isNotEmpty() }?.let { raw ->
            val parsed = LrcLib.parseLrc(raw)
            if (parsed.isNotEmpty()) return parsed
        }
        return null
    }

    /**
     * Its own client rather than the shared one: `lyricsGet` is internal to `:shared`, and NetEase
     * wants a phone's `User-Agent` and a `Referer` on the search, which no other provider here does.
     */
    private val http by lazy {
        OkHttpClient.Builder()
            .callTimeout(6, TimeUnit.SECONDS)
            .connectTimeout(3, TimeUnit.SECONDS)
            .build()
    }

    private fun get(url: String, headers: Map<String, String>?): String? = runCatching {
        val request = Request.Builder().url(url).apply {
            headers?.forEach { (name, value) -> header(name, value) }
        }.build()
        http.newCall(request).execute().use { response ->
            if (response.isSuccessful) response.body?.string() else null
        }
    }.getOrNull()

    private const val SEARCH_DURATION_TOLERANCE_SECONDS = 8

    @Serializable
    private data class SearchResponse(val result: SearchResult? = null) {
        @Serializable
        data class SearchResult(val songs: List<Song> = emptyList())

        @Serializable
        data class Song(val id: Long, val duration: Long = 0)
    }

    @Serializable
    private data class LyricResponse(val yrc: Document? = null, val lrc: Document? = null) {
        @Serializable
        data class Document(val lyric: String = "")
    }
}
