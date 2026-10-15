package com.music.bitchord.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DesktopAppleArtistArtTest {
    private val pageUrl = "https://music.apple.com/gb/artist/ariana-grande/412778295"
    private val image = "https://is1-ssl.mzstatic.com/image/thumb/CfahkqIV7d3iglSrEp6Pcg/{w}x{h}{c}.{f}"

    private fun parse(payload: String): DesktopAppleArtistArt? {
        val method = DesktopAppleArtistArtRepository::class.java
            .getDeclaredMethod("parse", String::class.java, String::class.java)
        method.isAccessible = true
        return method.invoke(DesktopAppleArtistArtRepository, "<script id=\"serialized-server-data\">$payload</script>", pageUrl) as DesktopAppleArtistArt?
    }

    @Test
    fun logoUsesTheWebHeaderAspectInsteadOfTheSourceAspect() {
        val art = assertNotNull(parse("""
            {"artistLogo":{"dictionary":{"url":"$image","width":2880,"height":482}},
             "artwork":{"dictionary":{"url":"$image","width":2000,"height":2000,"bgColor":"252524"}},
             "bio":"Artist biography"}
        """))
        assertEquals(image.replace("{w}", "1000").replace("{h}", "333").replace("{c}", "bb").replace("{f}", "png"), art.logoUrl)
        assertEquals(3f, art.logoAspect)
        assertEquals(0xFF252524.toInt(), art.background)
        assertEquals("Artist biography", art.bio)
        assertEquals(pageUrl, art.pageUrl)
    }

    @Test
    fun wideMotionUsesItsMatchingPreviewAndKeepsTheSquareFallback() {
        val art = assertNotNull(parse("""
            {"artistLogo":null,
             "artwork":{"dictionary":{"url":"$image","width":2000,"height":2000,"bgColor":"252524"}},
             "wideVideoArtwork":{"dictionary":{"motionArtistWide16x9":{
                "video":"$pageUrl?wide.m3u8",
                "previewFrame":{"url":"$image","width":1920,"height":1080,"bgColor":"090907"}}}},
             "videoArtwork":{"dictionary":{"motionArtistSquare1x1":{"video":"$pageUrl?square.m3u8"}}}}
        """))
        assertEquals("$pageUrl?wide.m3u8", art.wideVideoUrl)
        assertEquals("$pageUrl?square.m3u8", art.squareVideoUrl)
        assertEquals(image.replace("{w}", "1600").replace("{h}", "900").replace("{c}", "bb").replace("{f}", "jpg"), art.heroUrl)
        assertEquals(0xFF090907.toInt(), art.background)
        assertNull(art.logoUrl)
    }

    @Test
    fun artistWithoutMotionOrLogoStillHasAnArtworkHeader() {
        val art = assertNotNull(parse("""
            {"artistLogo":null,
             "artwork":{"dictionary":{"url":"$image","width":2000,"height":2000}}}
        """))
        assertNull(art.logoUrl)
        assertNull(art.wideVideoUrl)
        assertNull(art.squareVideoUrl)
        assertNotNull(art.portraitUrl)
        assertEquals(0xFF1F1F1F.toInt(), art.background)
    }

    @Test
    fun fullscreenMotionWinsOverTheAlternateWideCrop() {
        val art = assertNotNull(parse("""
            {"artistLogo":null,"wideVideoArtwork":{"dictionary":{
             "motionArtistWide16x9":{"video":"$pageUrl?wide.m3u8","previewFrame":{"url":"$image","width":1920,"height":1080}},
             "motionArtistFullscreen16x9":{"video":"$pageUrl?fullscreen.m3u8","previewFrame":{"url":"$image","width":1920,"height":1080,"bgColor":"090907"}}}}}
        """))
        assertEquals("$pageUrl?fullscreen.m3u8", art.wideVideoUrl)
        assertEquals(0xFF090907.toInt(), art.background)
        assertNull(art.portraitUrl)
    }

    @Test
    fun channelSuffixesAreRemovedWithoutDamagingUnicodeNames() {
        assertEquals("Ariana Grande", canonicalAppleArtistName("Ariana Grande - Topic"))
        assertEquals("ArianaGrande", canonicalAppleArtistName("ArianaGrandeVEVO"))
        assertEquals("宇多田ヒカル", canonicalAppleArtistName("宇多田ヒカル - Topic"))
    }

    @Test
    fun anIncidentalNullLogoDoesNotHideTheActualArtistHeader() {
        val art = assertNotNull(parse("""
            {"analytics":{"artistLogo":null},"header":{"artistLogo":{"dictionary":{"url":"$image","width":2880,"height":482}},
             "wideArtwork":{"dictionary":{"url":"$image","width":1920,"height":1080}}}}
        """))
        assertNotNull(art.logoUrl)
        assertNull(art.portraitUrl)
    }
}
