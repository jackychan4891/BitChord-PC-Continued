package com.music.bitchord.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopAppleBrowseClientTest {
    private val room = "https://music.apple.com/gb/room/6820752875"
    private val album = "https://music.apple.com/gb/album/nicole-kidman/6792883860"

    private fun parse(sections: String) = DesktopAppleBrowseClient.parse(
        """<script id="serialized-server-data">{"data":[{"data":{"sections":[$sections]}}]}</script>""", "Trending",
    )

    private fun track(index: Int) = """{"title":"Song $index","subtitleLinks":[{"title":"Artist"}],
        "contentDescriptor":{"kind":"song","url":"$album?i=$index"}}"""

    @Test
    fun theWholeTrendingRoomIsKeptInAppleOrder() {
        val tracks = (1..100).joinToString(",") { track(it) }
        val shelf = parse("""{"itemKind":"trackLockup","items":[$tracks]}""").single()
        assertEquals("Trending", shelf.title)
        assertEquals((1..100).map { "Song $it" }, shelf.items.map { it.title })
        assertTrue(shelf.items.all { it.isAppleSong() })
        assertTrue(shelf.items.all { it.videoId == null })
    }

    @Test
    fun sectionLinksOpenTheFullRoomAndSupplyTrendingCategories() {
        val shelf = parse("""{"itemKind":"trackLockup","header":{"item":{"titleLink":{"title":"Trending Songs","url":"$room"}}},
            "items":[${track(1)},${track(2)}]}""").single()
        assertEquals(DesktopAppleBrowseClient.PAGE_PREFIX + room, shelf.moreBrowseId)
        val category = DesktopAppleBrowseClient.categories(listOf(shelf)).single()
        assertEquals("Trending", category.title)
        assertEquals(shelf.moreBrowseId, category.browseId)
    }

    @Test
    fun chartPlaylistsKeepTheirTitlesArtworkAndPageIdentity() {
        val image = "https://is1-ssl.mzstatic.com/image/thumb/CfahkqIV7d3iglSrEp6Pcg/{w}x{h}{c}.{f}"
        val shelf = parse("""{"itemKind":"squareLockup","header":{"item":{"titleLink":{"title":"Daily Top 100","url":"$room"}}},
            "items":[{"titleLinks":[{"title":"Top 100: UK"}],"subtitleLinks":[{"title":"Apple Music"}],
            "artwork":{"dictionary":{"url":"$image"}},"contentDescriptor":{"kind":"playlist","url":"$room"}}]}""").single()
        assertEquals("Top 100: UK", shelf.items.single().title)
        assertEquals(image.replace("{w}", "400").replace("{h}", "400").replace("{c}", "bb").replace("{f}", "jpg"), shelf.items.single().thumbnailUrl)
        assertEquals("Charts", DesktopAppleBrowseClient.categories(listOf(shelf)).single().title)
    }

    @Test
    fun onlyFreeLiveStationsSurviveFromTheRadioPage() {
        val live = "https://music.apple.com/gb/station/apple-music-1/ra.978194965"
        val episode = "https://music.apple.com/gb/station/interview/ra.1"
        val video = "https://music.apple.com/gb/post/2"
        val shelves = parse("""{"itemKind":"liveRadioGridLockup","items":[
            {"accessibilityLabel":"Apple Music 1","contentDescriptor":{"kind":"radioStation","url":"$live"}}]},
            {"itemKind":"horizontalLockup","items":[{"title":"Interview","radioStation":{"isRadioEpisode":true,"isLiveRadio":false},
            "contentDescriptor":{"kind":"radioStation","url":"$episode"}}]},
            {"itemKind":"verticalVideoLockup","items":[{"titleLinks":[{"title":"Watch Interview"}],
            "contentDescriptor":{"kind":"artistUploadedVideo","url":"$video"}}]}""")
        assertEquals(1, shelves.size)
        assertEquals(DesktopAppleBrowseClient.RADIO_PREFIX + live, shelves[0].items.single().browseId)
        assertEquals("Apple Music 1", shelves[0].items.single().title)
        assertTrue(shelves.flatMap { it.items }.none { it.isAppleSong() })
    }

    @Test
    fun localBroadcastersAreLiveAndKeepTheFullDirectoryLink() {
        val station = "https://music.apple.com/gb/station/capital/ra.1345165422"
        val shelf = parse("""{"itemKind":"squareLockup","header":{"item":{"titleLink":{"title":"Local Broadcasters","url":"$room"}}},
            "items":[{"titleLinks":[{"title":"Capital"}],"radioStation":{"isLiveRadio":true,"isBroadcastRadio":true},
            "contentDescriptor":{"kind":"radioStation","url":"$station"}}]}""").single()
        assertEquals(DesktopAppleBrowseClient.RADIO_PREFIX + station, shelf.items.single().browseId)
        assertEquals(DesktopAppleBrowseClient.PAGE_PREFIX + room, shelf.moreBrowseId)
    }

    @Test
    fun subscriberStationsAreDropped() {
        val shelves = parse("""{"itemKind":"squareLockup","items":[{"title":"Genre station",
            "radioStation":{"isLiveRadio":false,"isRadioEpisode":false},
            "contentDescriptor":{"kind":"radioStation","url":"$room"}}]}""")
        assertTrue(shelves.isEmpty())
    }

    @Test
    fun analyticsDuplicatesAndUntrustedPageUrlsAreNotMusicItems() {
        val shelf = parse("""{"itemKind":"trackLockup","items":[${track(1)},${track(1)},
            {"title":"Bad","contentDescriptor":{"kind":"song","url":"file:///private"}}]},
            {"itemKind":"spacer","items":[${track(2)}]}""").single()
        assertEquals(1, shelf.items.size)
        assertNull(shelf.moreBrowseId)
    }
}
