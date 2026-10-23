package com.music.bitchord.desktop

import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.ShelfItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopRadioClientTest {
    private fun item(id: String, prefix: String = DesktopAppleBrowseClient.RADIO_PREFIX) =
        ShelfItem("Station", "", null, null, prefix + "https://music.apple.com/gb/station/station/$id")

    @Test
    fun allSixAppleLiveStationsHaveFreshPublicResolvers() {
        listOf("ra.978194965", "ra.1498155548", "ra.1498157166", "ra.1740613864", "ra.1740613859", "ra.1740614260").forEach { id ->
            val station = assertNotNull(DesktopRadioClient.station(item(id)))
            assertEquals(id, station.id)
            assertEquals("hls", station.transport)
            assertEquals("opml.radiotime.com", java.net.URI(station.streamUrl).host)
        }
    }

    @Test
    fun allNineBroadcastersHaveDirectStreams() {
        listOf("ra.1345165422", "ra.1445918457", "ra.1461581216", "ra.1445918670", "ra.1461555238",
            "ra.1534224138", "ra.1345167712", "ra.1345166934", "ra.1445918839").forEach { id ->
            assertNotNull(DesktopRadioClient.station(item(id)))
        }
    }

    @Test
    fun pagesAndUnknownStationsNeverBecomeLiveStreams() {
        assertNull(DesktopRadioClient.station(item("ra.978194965", DesktopAppleBrowseClient.PAGE_PREFIX)))
        assertNull(DesktopRadioClient.station(item("ra.unknown")))
    }

    @Test
    fun radioPageKeepsOnlyFreeStations() {
        val shelves = DesktopRadioClient.freeShelves(listOf(
            HomeShelf("Live", listOf(item("ra.978194965"), item("ra.unknown"))),
            HomeShelf("Shows", listOf(item("ra.1", DesktopAppleBrowseClient.PAGE_PREFIX))),
        ))
        assertEquals(1, shelves.size)
        assertEquals(1, shelves.single().items.size)
    }

    @Test
    fun bbcStationsCarryTheirOwnWorldwidePool() {
        DesktopBbcRadio.stations.forEach { station ->
            // The `ww` pools are the ones the BBC publishes for listeners outside the United
            // Kingdom; a station that ever pointed anywhere else would be reaching for the UK-only
            // ones, which this app has no business asking for.
            assertTrue(station.streamUrl.contains("/live/ww/"), station.streamUrl)
            assertTrue(DesktopRadioClient.isStation(DesktopBbcRadio.shelfItem(station)))
            val live = assertNotNull(DesktopRadioClient.station(DesktopBbcRadio.shelfItem(station)))
            assertEquals(station.service, live.id)
            assertEquals("hls", live.transport)
            assertEquals(station.streamUrl, DesktopRadioClient.resolveStream(live).getOrThrow().url)
        }
    }

    @Test
    fun publicResolverPreservesTheCompleteTemporarySignature() {
        val url = "https://itsliveradio.apple.com/3p-tune-in/tune_in/978194965-ts/index-ts.m3u8?accessKey=test%2Bsignature%3D"
        assertEquals(url, DesktopRadioClient.parseTuneResponse("ra.978194965", "$url\n"))
    }

    @Test
    fun resolverRejectsOtherHostsSchemesCredentialsPortsAndStationIds() {
        val path = "/3p-tune-in/tune_in/978194965-ts/index-ts.m3u8"
        listOf("http://itsliveradio.apple.com$path", "https://example.invalid$path",
            "https://itsliveradio.apple.com.example.invalid$path", "https://user@itsliveradio.apple.com$path",
            "https://itsliveradio.apple.com:443$path", "https://itsliveradio.apple.com/other.m3u8").forEach {
            assertFailsWith<IllegalStateException> { DesktopRadioClient.parseTuneResponse("ra.978194965", it) }
        }
        assertFailsWith<IllegalStateException> {
            DesktopRadioClient.parseTuneResponse("ra.1498155548", "https://itsliveradio.apple.com$path")
        }
    }

    @Test
    fun stationsOnlyAcceptAppleHttpsPages() {
        listOf("file:///private", "http://music.apple.com/gb/radio", "https://music.apple.com.example.invalid/gb/radio",
            "https://user@music.apple.com/gb/radio", "https://music.apple.com:443/gb/radio").forEach { url ->
            assertNull(DesktopRadioClient.station(item("ra.1").copy(browseId = DesktopAppleBrowseClient.RADIO_PREFIX + "$url/ra.978194965")))
        }
    }
}
