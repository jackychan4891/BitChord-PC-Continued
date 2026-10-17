package com.music.bitchord.desktop

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class DesktopRadioPlayerTest {
    private val unsupported = DesktopRadioStation("unknown", "Unavailable station", null, "")

    @Test
    fun pauseRetainsTheStationAndStopClearsIt() {
        val player = DesktopRadioPlayer()
        try {
            player.start(unsupported)
            player.pause()
            assertEquals(unsupported, player.state.value.station)
            assertFalse(player.state.value.isPlaying)
            assertFalse(player.state.value.isLoading)
            assertNull(player.state.value.error)
            player.stop()
            assertNull(player.state.value.station)
            player.play()
            assertNull(player.state.value.station)
        } finally {
            player.release()
        }
    }

    @Test
    fun failedStationCanBeResumedAndObsoleteErrorsCannotReplaceIt() = runBlocking {
        val player = DesktopRadioPlayer()
        try {
            repeat(20) { player.start(unsupported.copy(id = "obsolete-$it")) }
            player.start(unsupported)
            withTimeout(5_000) { player.state.first { it.station == unsupported && it.error != null } }
            player.pause()
            player.play()
            val failed = withTimeout(5_000) { player.state.first { it.error != null } }
            assertEquals(unsupported, failed.station)
            assertFalse(failed.isPlaying)
            assertFalse(failed.isLoading)
        } finally {
            player.release()
        }
    }

    @Test
    fun releaseClearsStateAndPreventsNewWorkers() {
        val player = DesktopRadioPlayer()
        player.start(unsupported)
        player.release()
        player.start(unsupported)
        player.play()
        assertNull(player.state.value.station)
        assertFalse(player.state.value.isPlaying)
        assertFalse(player.state.value.isLoading)
    }
}
