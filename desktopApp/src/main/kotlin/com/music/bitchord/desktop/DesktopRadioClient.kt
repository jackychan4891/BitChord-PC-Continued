package com.music.bitchord.desktop

import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.ShelfItem
import java.net.URI

internal object DesktopRadioClient {
    private val appleStations = mapOf(
        "ra.978194965" to "s345732",
        "ra.1498155548" to "s345724",
        "ra.1498157166" to "s345725",
        "ra.1740613864" to "s345727",
        "ra.1740613859" to "s345726",
        "ra.1740614260" to "s345733",
    )
    private val broadcasters = mapOf(
        "ra.1345165422" to DesktopStream("https://media-ssl.musicradio.com/CapitalUK"),
        "ra.1445918457" to DesktopStream("https://stream.planetradio.co.uk/ki1/hi/hls/?player=BMUK_HomePod", transport = "hls"),
        "ra.1461581216" to DesktopStream("https://media-ssl.musicradio.com/HeartUKMP3"),
        "ra.1445918670" to DesktopStream("https://stream.planetradio.co.uk/ki2/hi/hls/?player=BMUK_HomePod", transport = "hls"),
        "ra.1461555238" to DesktopStream("https://media-ssl.musicradio.com/SmoothUKMP3"),
        "ra.1534224138" to DesktopStream("https://media-ssl.musicradio.com/CapitalDance"),
        "ra.1345167712" to DesktopStream("https://media-ssl.musicradio.com/ClassicFM"),
        "ra.1345166934" to DesktopStream("http://hls.musicradio.com/CapitalXTRANational/playlist.m3u8", transport = "hls"),
        "ra.1445918839" to DesktopStream("https://stream.planetradio.co.uk/mag/hi/hls/?player=BMUK_HomePod", transport = "hls"),
    )

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
        val stream = broadcasters[id] ?: appleStations[id]?.let {
            DesktopStream("https://opml.radiotime.com/tune.ashx?id=$it&formats=hls", transport = "hls")
        } ?: return null
        return DesktopRadioStation(id, item.title, item.thumbnailUrl, stream.url, stream.transport)
    }

    /** Only the stations that stream free in-app; everything else on Apple's radio page needs a subscription. */
    fun freeShelves(shelves: List<HomeShelf>): List<HomeShelf> = shelves
        .map { shelf -> shelf.copy(items = shelf.items.filter { station(it) != null }) }
        .filter { it.items.isNotEmpty() }

    fun resolveStream(station: DesktopRadioStation): Result<DesktopStream> = runCatching {
        broadcasters[station.id]?.let { return@runCatching it }
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
