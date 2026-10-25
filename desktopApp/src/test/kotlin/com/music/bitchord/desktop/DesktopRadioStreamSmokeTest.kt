package com.music.bitchord.desktop

import com.music.bitchord.data.model.ShelfItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DesktopRadioStreamSmokeTest {
    private fun station(id: String) = assertNotNull(DesktopRadioClient.station(
        ShelfItem(id, "", null, null, DesktopAppleBrowseClient.RADIO_PREFIX + "https://music.apple.com/gb/station/station/$id"),
    ))

    private fun decode(id: String) {
        val stream = DesktopRadioClient.resolveStream(station(id)).getOrThrow()
        val decoder = DesktopAudioDecoder()
        try {
            decoder.open(stream.url, requested = DesktopPcmFormat(44100, 2, 2), transport = stream.transport).getOrThrow()
            val bytes = assertNotNull(decoder.read(), "No decoded audio for $id")
            assertTrue(bytes.isNotEmpty(), "Empty decoded audio for $id")
            println("Radio decoded: $id (${bytes.size} PCM bytes)")
        } finally {
            decoder.close()
        }
    }

    @Test
    fun sixAppleLiveStationsDecodePublicAudio() {
        if (System.getenv("BITCHORD_RADIO_SMOKE") != "1") return
        listOf("ra.978194965", "ra.1498155548", "ra.1498157166", "ra.1740613864", "ra.1740613859", "ra.1740614260").forEach(::decode)
    }

    @Test
    fun mutedOutputStartsPausesReconnectsAndStops() = runBlocking {
        if (System.getenv("BITCHORD_RADIO_OUTPUT_SMOKE") != "1") return@runBlocking
        val player = DesktopRadioPlayer()
        try {
            player.setVolume(0f)
            player.start(station("ra.978194965"))
            val first = withTimeout(120_000) { player.state.first { it.isPlaying || it.error != null } }
            assertTrue(first.isPlaying, first.error)
            player.pause()
            assertTrue(!player.state.value.isPlaying && !player.state.value.isLoading)
            player.play()
            val resumed = withTimeout(120_000) { player.state.first { it.isPlaying || it.error != null } }
            assertTrue(resumed.isPlaying, resumed.error)
            player.stop()
            assertTrue(player.state.value.station == null)
        } finally {
            player.release()
        }
    }
}
