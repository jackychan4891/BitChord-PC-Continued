package com.music.bitchord.desktop

import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.ShelfItem
import java.net.URI

/**
 * Apple Music's own live stations.
 *
 * The radio page used to reach past them: Apple lists local broadcasters beside its stations and each
 * of those was wired to its own commercial stream, with a shelf of BBC national stations folded in on
 * top. Every one of those left the app's radio page, since the point is to listen to Apple Music.
 */
internal object DesktopRadioClient {
    private val appleStations = mapOf(
        "ra.978194965" to "s345732",
        "ra.1498155548" to "s345724",
        "ra.1498157166" to "s345725",
        "ra.1740613864" to "s345727",
        "ra.1740613859" to "s345726",
        "ra.1740614260" to "s345733",
    )

    /** Whether a shelf row is a live station that this player can start. */
    fun isStation(item: ShelfItem): Boolean =
        item.browseId?.startsWith(DesktopAppleBrowseClient.RADIO_PREFIX) == true

    fun station(item: ShelfItem): DesktopRadioStation? {
        val browseId = item.browseId ?: return null
        if (!browseId.startsWith(DesktopAppleBrowseClient.RADIO_PREFIX)) return null
        val url = browseId.removePrefix(DesktopAppleBrowseClient.RADIO_PREFIX)
        val valid = runCatching {
            val uri = URI(url)
            uri.scheme == "https" && uri.host == "music.apple.com" && uri.userInfo == null && uri.port == -1
        }.getOrDefault(false)
        if (!valid) return null
        val id = URI(url).path.substringAfterLast('/')
        val tuneId = appleStations[id] ?: return null
        return DesktopRadioStation(
            id,
            item.title,
            item.thumbnailUrl,
            "https://opml.radiotime.com/tune.ashx?id=$tuneId&formats=hls",
            "hls",
        )
    }

    /** Only the stations that stream free in-app; everything else on Apple's radio page needs a subscription. */
    fun freeShelves(shelves: List<HomeShelf>): List<HomeShelf> = shelves
        .map { shelf -> shelf.copy(items = shelf.items.filter { station(it) != null }) }
        .filter { it.items.isNotEmpty() }

    /** What a station hides under: its own id, which a page reload keeps. */
    fun keyOf(item: ShelfItem): String = station(item)?.id ?: item.browseId.orEmpty()

    /** The page with the stations the listener has hidden left off, and any row that emptied removed. */
    fun visible(shelves: List<HomeShelf>): List<HomeShelf> = shelves
        .map { shelf -> shelf.copy(items = shelf.items.filterNot { DesktopRadioVisibility.isHidden(keyOf(it)) }) }
        .filter { it.items.isNotEmpty() }

    fun resolveStream(station: DesktopRadioStation): Result<DesktopStream> = runCatching {
        val tuneId = appleStations[station.id] ?: error("This station needs the Apple Music player")
        val body = canvasGet("https://opml.radiotime.com/tune.ashx?id=$tuneId&formats=hls")
            ?: error("Could not connect to the live station")
        DesktopStream(parseTuneResponse(station.id, body), transport = "hls")
    }

    internal fun parseTuneResponse(stationId: String, body: String): String {
        val expectedPath = "/3p-tune-in/tune_in/${stationId.removePrefix("ra.")}-ts/index-ts.m3u8"
        return body.lineSequence().map(String::trim).firstOrNull { line ->
            runCatching {
                val uri = URI(line)
                uri.scheme == "https" && uri.host == "itsliveradio.apple.com" &&
                    uri.userInfo == null && uri.port == -1 && uri.path == expectedPath
            }.getOrDefault(false)
        } ?: error("No public Apple radio stream is currently available")
    }
}
