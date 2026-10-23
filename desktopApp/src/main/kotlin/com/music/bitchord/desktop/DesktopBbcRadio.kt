package com.music.bitchord.desktop

import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.ShelfItem
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/** One of the BBC's national stations, and the live stream it is played from. */
internal data class DesktopBbcStation(
    val service: String,
    val name: String,
    /** The two marks the tile is drawn with when the station has no artwork of its own. */
    val monogram: String,
    val streamUrl: String,
)

/** What a station is carrying at this moment. */
internal data class DesktopBbcTrack(
    val title: String,
    val artist: String?,
    val programme: String?,
    /** The record's sleeve, or the programme's own picture on a speech station. */
    val artworkUrl: String? = null,
) {
    /** One line for under a station's name: the performer leads when there is one. */
    val line: String get() = if (artist.isNullOrBlank()) title else "$artist — $title"
}

/** A programme in a station's day. */
internal data class DesktopBbcProgramme(
    val title: String,
    val summary: String?,
    val start: OffsetDateTime?,
    val end: OffsetDateTime?,
)

/**
 * The BBC's national stations: their live streams, what each is playing now, and the day's schedule.
 *
 * Every feed here is one the BBC publishes for the public — the worldwide (`ww`) live pools, which
 * need no account, token or region, and the RMS now-playing and broadcast feeds. Nothing is asked of
 * the UK-only pools, so a listener outside the United Kingdom hears exactly what the BBC intends an
 * outside listener to hear: the World Service and the worldwide mirrors of the national stations.
 *
 * The stream addresses are fixed rather than discovered. The pool number in each is an opaque
 * Akamai shard id that no public API maps, so it is carried per station, and every one below was
 * fetched and confirmed to answer with a live playlist. A station whose pool stops answering is left
 * out rather than guessed at.
 */
internal object DesktopBbcRadio {

    /** Marks a BBC station among the shelf items the radio page shows. */
    const val PREFIX = "bbc:"

    private const val STREAM_ROOT = "https://as-hls-ww-live.akamaized.net"
    private const val FEED_ROOT = "https://rms.api.bbc.co.uk/v2"
    private const val USER_AGENT = "BitChord-Desktop/1.8 (https://github.com/jackychan4891/BitChord-PC-Continued)"

    /** Now playing is refreshed on the feed's own cadence; a day's schedule changes far less. */
    private const val TRACK_TTL_MILLIS = 45_000L

    /**
     * The station being listened to is asked on a faster beat than the page's other stations, so a
     * record change shows up in the player within a few seconds rather than most of a minute later.
     */
    private const val PLAYING_TTL_MILLIS = 12_000L
    private const val SCHEDULE_TTL_MILLIS = 10 * 60_000L

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    val stations: List<DesktopBbcStation> = listOf(
        station("bbc_radio_one", "BBC Radio 1", "R1", "01505109"),
        station("bbc_radio_one_dance", "BBC Radio 1 Dance", "1D", "62063831"),
        station("bbc_1xtra", "BBC 1Xtra", "1X", "92079267"),
        station("bbc_radio_two", "BBC Radio 2", "R2", "74208725"),
        station("bbc_radio_three", "BBC Radio 3", "R3", "23461179"),
        station("bbc_radio_fourfm", "BBC Radio 4", "R4", "55057080"),
        station("bbc_radio_four_extra", "BBC Radio 4 Extra", "4X", "26173715"),
        station("bbc_radio_five_live", "BBC Radio 5 Live", "5L", "89021708"),
        station("bbc_6music", "BBC 6 Music", "6M", "81827798"),
        station("bbc_asian_network", "BBC Asian Network", "AN", "22108647"),
        station("bbc_world_service", "BBC World Service", "WS", "87948813"),
    )

    private fun station(service: String, name: String, monogram: String, pool: String) = DesktopBbcStation(
        service = service,
        name = name,
        monogram = monogram,
        streamUrl = "$STREAM_ROOT/pool_$pool/live/ww/$service/$service.isml/$service-audio%3d128000.norewind.m3u8",
    )

    fun byBrowseId(browseId: String?): DesktopBbcStation? =
        browseId?.takeIf { it.startsWith(PREFIX) }
            ?.let { id -> stations.firstOrNull { it.service == id.removePrefix(PREFIX) } }

    fun byId(service: String?): DesktopBbcStation? =
        service?.let { wanted -> stations.firstOrNull { it.service == wanted } }

    /** The row the radio page lists this station as, and the tiles' click route plays. */
    fun shelfItem(station: DesktopBbcStation) = ShelfItem(
        title = station.name,
        subtitle = "",
        thumbnailUrl = null,
        videoId = null,
        browseId = PREFIX + station.service,
    )

    /** The BBC's shelf on the radio page, ahead of whatever the music source serves. */
    fun shelf() = HomeShelf(title = "BBC Radio", items = stations.map(::shelfItem))

    // What each station is carrying, published rather than pulled: a tile is drawn on every recomposition
    // and the player's radio line is drawn while a stream is already running, so neither may wait on the
    // network. [refreshAll] is the only thing that fetches, and the page it is polled from owns the timer.
    private val _tracks = MutableStateFlow<Map<String, DesktopBbcTrack>>(emptyMap())
    val tracks: StateFlow<Map<String, DesktopBbcTrack>> = _tracks.asStateFlow()

    /** When a station was last asked, whether or not the answer came back — a dead feed is not re-asked per tick. */
    private val seen = ConcurrentHashMap<String, Long>()
    private val schedules = ConcurrentHashMap<String, Cached<List<DesktopBbcProgramme>>>()

    private class Cached<T>(val value: T, val at: Long = System.currentTimeMillis()) {
        fun fresh(millis: Long) = System.currentTimeMillis() - at < millis
    }

    /** The station's last known programme, or nothing until the first read lands. */
    fun nowPlaying(station: DesktopBbcStation): DesktopBbcTrack? = _tracks.value[station.service]

    /** Ask for every station that has aged out, in one batch. Those inside the feed's own cadence are left alone. */
    suspend fun refreshAll() = refresh(stations, TRACK_TTL_MILLIS)

    /** The one station in the player, asked more often than the page's others. */
    suspend fun refreshPlaying(station: DesktopBbcStation) = refresh(listOf(station), PLAYING_TTL_MILLIS)

    private suspend fun refresh(which: List<DesktopBbcStation>, ttl: Long) {
        val now = System.currentTimeMillis()
        val due = which.filter { now - (seen[it.service] ?: 0L) >= ttl }
        if (due.isEmpty()) return
        due.forEach { seen[it.service] = now }
        val read = withContext(Dispatchers.IO) {
            due.mapNotNull { station ->
                runCatching { readNowPlaying(station) }.getOrNull()?.let { station.service to it }
            }
        }.toMap()
        if (read.isNotEmpty()) _tracks.update { it + read }
    }

    suspend fun schedule(station: DesktopBbcStation): List<DesktopBbcProgramme> {
        schedules[station.service]?.takeIf { it.fresh(SCHEDULE_TTL_MILLIS) }?.let { return it.value }
        val read = withContext(Dispatchers.IO) { runCatching { readSchedule(station) }.getOrDefault(emptyList()) }
        if (read.isNotEmpty()) schedules[station.service] = Cached(read)
        return read
    }

    /**
     * The segment the station is in — a record for a music station, the programme's own title for a
     * speech one. RMS answers `music` segments with the performer in `primary` and the record in
     * `secondary`; anything else it lists has no record to name, so the day's on-air broadcast is
     * asked for instead, and that title is what a listener would call the programme.
     */
    private fun readNowPlaying(station: DesktopBbcStation): DesktopBbcTrack? {
        val segments = get("$FEED_ROOT/services/${station.service}/segments/latest")
            ?.let { json.parseToJsonElement(it) as? JsonObject }
        val items = (segments?.get("data") as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        // The flag is the feed's own answer. Without it the segment furthest along the broadcast is
        // the safest guess; taking the first item instead can land on a record that has just ended.
        val current = items.firstOrNull { it.obj("offset")?.boolean("now_playing") == true }
            ?: items.maxByOrNull { it.obj("offset")?.int("end") ?: 0 }
        if (current != null) {
            val titles = current.obj("titles")
            val title = titles.string("secondary") ?: titles.string("primary")
            val artwork = artworkUrl(current.string("image_url"))
            if (current.string("segment_type") == "music" && title != null) {
                return DesktopBbcTrack(
                    title = title,
                    artist = titles.string("primary")?.takeIf { it != title },
                    programme = titles.string("entity_title"),
                    artworkUrl = artwork,
                )
            }
            // A speech segment names the programme itself, and the picture for it lives on the
            // day's on-air broadcast rather than on the segment.
            val programme = titles.string("primary") ?: titles.string("entity_title") ?: current.string("title")
            if (programme != null) {
                return DesktopBbcTrack(programme, null, programme, readOnAir(station)?.programmeArtwork())
            }
        }
        val broadcast = readOnAir(station)
        val names = broadcast?.obj("programme")?.obj("titles")
        val title = names.string("display_title") ?: names.string("primary_title") ?: names.string("primary")
        return title?.let { DesktopBbcTrack(it, null, it, broadcast.programmeArtwork()) }
    }

    /** The broadcast on air now, for the programme's own title and picture. */
    private fun readOnAir(station: DesktopBbcStation): JsonObject? =
        get("$FEED_ROOT/broadcasts/latest?service=${station.service}&on_air=now")
            ?.let { json.parseToJsonElement(it) as? JsonObject }
            ?.let { (it["data"] as? JsonArray)?.firstOrNull() as? JsonObject }

    private fun JsonObject?.programmeArtwork(): String? =
        artworkUrl((this?.obj("programme")?.get("images") as? JsonArray)
            ?.asSequence()?.mapNotNull { (it as? JsonObject)?.string("url") }?.firstOrNull())

    /** `{recipe}` is the BBC's size slot; 400 is the size the app's covers are drawn at. */
    private fun artworkUrl(raw: String?): String? =
        raw?.replace("{recipe}", "400x400")?.takeIf { it.startsWith("https://") }

    /** The station's day, in the order it runs. */
    private fun readSchedule(station: DesktopBbcStation): List<DesktopBbcProgramme> {
        val day = LocalDate.now(ZoneOffset.UTC)
        // Asked for the whole day at once: the feed's own page is thirty long, which stops short of
        // an afternoon on a speech station.
        val body = get("$FEED_ROOT/broadcasts?service=${station.service}&date=$day&limit=200")
            ?.let { json.parseToJsonElement(it) as? JsonObject } ?: return emptyList()
        return (body["data"] as? JsonArray).orEmpty()
            .mapNotNull { element ->
                val broadcast = element as? JsonObject
                val programme = broadcast?.obj("programme") ?: return@mapNotNull null
                val titles = programme.obj("titles")
                val title = titles.string("display_title") ?: titles.string("primary_title")
                    ?: titles.string("primary") ?: return@mapNotNull null
                DesktopBbcProgramme(
                    title = title,
                    summary = programme.obj("synopses").string("short"),
                    start = broadcast.string("start").asTime(),
                    end = broadcast.string("end").asTime(),
                )
            }
            .sortedBy { it.start }
    }

    private fun get(url: String): String? {
        val request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(15))
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .GET().build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        return if (response.statusCode() in 200..299) response.body() else null
    }

    private fun JsonObject?.obj(key: String): JsonObject? = this?.get(key) as? JsonObject

    private fun JsonObject?.string(key: String): String? =
        (this?.get(key) as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotBlank)

    private fun JsonObject?.boolean(key: String): Boolean? =
        (this?.get(key) as? JsonPrimitive)?.booleanOrNull

    private fun JsonObject?.int(key: String): Int? =
        (this?.get(key) as? JsonPrimitive)?.intOrNull

    private fun String?.asTime(): OffsetDateTime? =
        this?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() }
}
