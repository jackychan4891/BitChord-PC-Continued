package com.music.bitchord.desktop

import com.music.bitchord.data.lyrics.LyricsSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The source list, as the settings dialog stores it and the lookup reads it back. */
class DesktopLyricsSourceOrderTest {

    @Test
    fun theChainIsTheStoredOrderNarrowedToWhatIsTicked() {
        val order = listOf("BiniLyrics", "LRCLIB", "Genius")
        assertEquals(
            listOf("BiniLyrics", "Genius"),
            DesktopLyricsClient.enabledSources(order, setOf("Genius", "BiniLyrics")),
        )
    }

    @Test
    fun untickingEverythingLeavesNothingToAsk() {
        assertEquals(emptyList(), DesktopLyricsClient.enabledSources(listOf("LRCLIB"), emptySet()))
    }

    @Test
    fun theDesktopOffersThePhonesProvidersInThePhonesOrderPlusItsOwn() {
        // One provider list for both apps — the lookup is the phone's LyricsRepository — widened by
        // the two databases only the desktop reaches, which `:shared` has no name for.
        val names = DesktopLyricsClient.sources.map { it.name }
        assertEquals(LyricsSource.offered.map { it.label }, names.filter { lyricsSourceNamed(it) != null })
        // Each of the desktop's own is declared beside the source it is a variant of, so the
        // out-of-the-box order still reads as the phone's.
        assertEquals("BetterLyrics Syllable", names[names.indexOf("BetterLyrics") + 1])
        assertEquals("NetEase (YRC)", names[names.indexOf("BetterLyrics Syllable") + 1])
        // The unsynced fallback is last by definition.
        assertEquals("Genius", names.last())
    }

    @Test
    fun everySourceOfferedIsOneTheLookupCanActuallyAsk() {
        // The dialog is built from [sources]; a name in it with no provider behind it is a row that
        // silently does nothing when ticked.
        val order = DesktopLyricsClient.sources.map { it.name }
        assertEquals(order, DesktopLyricsClient.enabledSources(order, order.toSet()))
    }
}
