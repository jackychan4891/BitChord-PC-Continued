package com.music.bitchord.desktop

import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopArtworkSizeTest {
    @Test
    fun appleArtworkIsFetchedAtTheRequestedSize() {
        assertEquals(
            "https://is1-ssl.mzstatic.com/image/thumb/Music/abc/1000x1000bb.jpg",
            "https://is1-ssl.mzstatic.com/image/thumb/Music/abc/600x600bb.jpg".desktopArtworkAt(1000),
        )
    }

    @Test
    fun otherArtworkKeepsTheSharedSizeHint() {
        assertEquals(
            "https://lh3.googleusercontent.com/x=w480-h480-l90",
            "https://lh3.googleusercontent.com/x=w120-h120-l90".desktopArtworkAt(480),
        )
        assertEquals("https://example.com/600x600bb.jpg", "https://example.com/600x600bb.jpg".desktopArtworkAt(1000))
    }
}
