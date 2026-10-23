package com.music.bitchord.desktop

import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.ShelfItem
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The three ways a stream names what it is carrying, read off bytes rather than off a live server.
 *
 * Every fixture here is the shape a real broadcaster was caught sending: an ICY block spliced into
 * audio, the two `#EXTINF` styles in use, and a v2.4 text frame at the head of an HLS segment.
 */
class DesktopRadioNowPlayingTest {

    @Test
    fun icyBlocksInTheOrderTheyArrive() {
        val stream = icyStream(
            "StreamTitle='George Michael - Freedom 90';StreamUrl='';",
            null,
            "StreamTitle='Taylor Swift - Patient Zero';StreamUrl='';",
        )
        val titles = DesktopRadioNowPlaying.icyTitles(stream, ICY_AUDIO)
        assertEquals(listOf("George Michael - Freedom 90", null, "Taylor Swift - Patient Zero"), titles.take(3))
    }

    @Test
    fun anIcyBlockWithNothingInItSaysNothing() {
        assertNull(DesktopRadioNowPlaying.streamTitle("StreamTitle='';StreamUrl='';"))
        assertNull(DesktopRadioNowPlaying.streamTitle(""))
    }

    @Test
    fun anIcyBlockKeepsItsApostrophes() {
        assertEquals("Carmen - Act III En'tracte", DesktopRadioNowPlaying.streamTitle("StreamTitle='Carmen - Act III En&#39;tracte';"))
        assertEquals("Adele - Hello", DesktopRadioNowPlaying.streamTitle("StreamTitle='Adele - Hello';StreamUrl='';"))
    }

    @Test
    fun anExtinfLineNamingTheItem() {
        assertEquals("Hello - Adele", DesktopRadioNowPlaying.extInfTitle("#EXTINF:4.992,Hello - Adele"))
        assertNull(DesktopRadioNowPlaying.extInfTitle("#EXTINF:4.992,"))
    }

    /** Planet Radio's attributes are the other way round: `title=` is who, `artist=` is what. */
    @Test
    fun anExtinfLineWithAttributedFields() {
        assertEquals(
            "Katseye — Animal",
            DesktopRadioNowPlaying.extInfTitle(
                "#EXTINF:10.026,title=\"Katseye\",artist=\"Animal\"," +
                    "url=\"https://listenapi.planetradio.co.uk/api9.2/eventdata/421849916\"",
            ),
        )
    }

    @Test
    fun aSegmentTaggedWithTheRecordAndItsPerformer() {
        val segment = id3Segment("TPE1" to "Estelle", "TIT2" to "The Estelle Show")
        assertEquals("Estelle — The Estelle Show", DesktopRadioNowPlaying.id3Line(segment))
    }

    @Test
    fun aSegmentTaggedWithOnlyATitle() {
        assertEquals("East River Drive", DesktopRadioNowPlaying.id3Line(id3Segment("TIT2" to "East River Drive")))
    }

    /** Apple opens its segments with a `PRIV` frame, which is not a record and is not read as one. */
    @Test
    fun aFrameThatIsNotATitleIsWalkedPast() {
        val segment = id3Segment("PRIV" to "com.apple.radio.ping.jingle", "TIT2" to "Patient Zero")
        assertEquals("Patient Zero", DesktopRadioNowPlaying.id3Line(segment))
    }

    @Test
    fun aSegmentWithNoTagAtAllSaysNothing() {
        assertNull(DesktopRadioNowPlaying.id3Line(ByteArray(64) { (it % 17).toByte() }))
    }

    private companion object {
        private const val ICY_AUDIO = 32

        /** `metaint` bytes of audio, then a length byte and its pads of text, then the audio again. */
        private fun icyStream(vararg blocks: String?): ByteArray {
            val out = ByteArrayOutputStream()
            blocks.forEach { block ->
                repeat(ICY_AUDIO) { out.write(0xAA) }
                val text = (block ?: "").toByteArray(StandardCharsets.UTF_8)
                val pads = if (text.isEmpty()) 0 else (text.size + 15) / 16
                out.write(pads)
                if (pads > 0) {
                    out.write(text)
                    repeat(pads * 16 - text.size) { out.write(0) }
                }
            }
            return out.toByteArray()
        }

        /** A v2.4 tag: the header, then frames of id, four-byte size, two flag bytes, encoding, text. */
        private fun id3Segment(vararg frames: Pair<String, String>): ByteArray {
            val out = ByteArrayOutputStream()
            out.write("ID3".toByteArray(StandardCharsets.US_ASCII))
            out.write(byteArrayOf(4, 0, 0, 0, 0, 0))
            frames.forEach { (id, text) ->
                val body = text.toByteArray(StandardCharsets.UTF_8)
                val size = body.size + 2
                out.write(id.toByteArray(StandardCharsets.US_ASCII))
                out.write(
                    byteArrayOf(
                        (size shr 21).toByte(), (size shr 14).toByte(),
                        (size shr 7).toByte(), size.toByte(),
                    ),
                )
                out.write(byteArrayOf(0, 0))
                out.write(3)
                out.write(body)
                out.write(0)
            }
            return out.toByteArray()
        }
    }
}

/** The radio page's rows: only free stations, and the BBC inside the local broadcasters' own row. */
class DesktopRadioShelfTest {

    private fun item(id: String) =
        ShelfItem("Station", "", null, null, DesktopAppleBrowseClient.RADIO_PREFIX + "https://music.apple.com/gb/station/station/$id")

    @Test
    fun theBbcJoinsTheLocalBroadcastersRow() {
        val shelves = DesktopRadioClient.shelves(
            listOf(
                HomeShelf("Featured Radio", listOf(item("ra.978194965"))),
                HomeShelf("Local Broadcasters", listOf(item("ra.1345165422"))),
            ),
        )
        assertEquals(listOf("Featured Radio", "Local Broadcasters"), shelves.map { it.title })
        assertEquals(
            DesktopBbcRadio.stations.map { it.service } + "ra.1345165422",
            shelves.last().items.map { DesktopRadioClient.keyOf(it) },
        )
    }

    @Test
    fun aPageWithoutABroadcastersRowKeepsTheBbcAsItsOwn() {
        val shelves = DesktopRadioClient.shelves(listOf(HomeShelf("Featured Radio", listOf(item("ra.unknown")))))
        assertEquals("BBC Radio", shelves.first().title)
        assertEquals(DesktopBbcRadio.stations.size, shelves.first().items.size)
        assertEquals(1, shelves.size)
    }

    @Test
    fun onlyTheStationsThatStreamFreeAreKept() {
        assertEquals(0, DesktopRadioClient.freeShelves(listOf(HomeShelf("Shows", listOf(item("ra.unknown"))))).size)
    }
}
