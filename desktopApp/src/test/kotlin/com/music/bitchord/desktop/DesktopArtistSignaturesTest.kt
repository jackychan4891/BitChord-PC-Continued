package com.music.bitchord.desktop

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DesktopArtistSignaturesTest {
    private fun parse(text: String) = Json.parseToJsonElement(text) as JsonObject

    @Test
    fun knownLicenceSvgWinsAndIsRasterised() {
        val root = parse(
            """{"signatures":[
              {"asset":{"url":"https://upload.wikimedia.org/wikipedia/commons/a/ab/X.png?utm=1","format":"png","width":900,"height":300},"license":{"status":"known"},"verification":"unverified"},
              {"asset":{"url":"https://upload.wikimedia.org/wikipedia/commons/5/51/X_Signature.svg?utm=1","format":"svg","width":1000,"height":300},"license":{"status":"known"},"verification":"unverified"},
              {"asset":{"url":"https://upload.wikimedia.org/wikipedia/commons/c/cd/X.jpg","format":"jpeg","width":2000,"height":600},"license":{"status":"known"},"verification":"verified"}
            ]}""",
        )
        assertEquals(
            "https://upload.wikimedia.org/wikipedia/commons/thumb/5/51/X_Signature.svg/960px-X_Signature.svg.png",
            pickSignatureUrl(root),
        )
    }

    @Test
    fun ambiguousOrJpegOnlyAnswersHaveNoSignature() {
        assertNull(pickSignatureUrl(parse("""{"matches":[{"name":"A"},{"name":"B"}]}""")))
        assertNull(
            pickSignatureUrl(parse("""{"signatures":[{"asset":{"url":"https://upload.wikimedia.org/wikipedia/commons/c/cd/X.jpg","format":"jpeg"}}]}""")),
        )
    }

    /** A link arrives out of a chat message with its prose still around it. */
    @Test
    fun onlyTheAddressOfAPasteIsKept() {
        assertEquals(
            "https://signatures.example.com",
            signatureEndpoint(
                """
                https://signatures.example.com

                Paste this API in Settings -> Artist Signatures
                by someone
                """.trimIndent(),
            ),
        )
        assertEquals("https://signatures.example.com/v1/signatures", signatureEndpoint("see https://signatures.example.com/v1/signatures."))
        assertEquals("https://signatures.example.com/s/{artist}", signatureEndpoint("https://signatures.example.com/s/{artist}"))
        assertEquals("", signatureEndpoint("nothing here at all"))
        // Flattened on the way in, so the break survives as `\n` text rather than as whitespace.
        assertEquals(
            "https://signatures.example.com",
            signatureEndpoint("""https://signatures.example.com\nPaste this API in Settings"""),
        )
    }

    @Test
    fun aBareHostGetsThePathTheOldRequestCarried() {
        assertEquals(
            "https://signatures.example.com/v1/signatures?artist=Coldplay&type=handwritten",
            signatureRequestUrl("https://signatures.example.com", "Coldplay"),
        )
        assertEquals(
            "https://signatures.example.com:8443/v1/signatures?artist=Coldplay&type=handwritten",
            signatureRequestUrl("https://signatures.example.com:8443/", "Coldplay"),
        )
        // A link that already carries its own path or query is left as its author wrote it.
        assertEquals(
            "https://signatures.example.com/api/sig?artist=Coldplay&type=handwritten",
            signatureRequestUrl("https://signatures.example.com/api/sig", "Coldplay"),
        )
        assertEquals(
            "https://signatures.example.com/api/sig?fmt=png&artist=Coldplay&type=handwritten",
            signatureRequestUrl("https://signatures.example.com/api/sig?fmt=png", "Coldplay"),
        )
        assertEquals(
            "https://signatures.example.com/s/Coldplay",
            signatureRequestUrl("https://signatures.example.com/s/{artist}", "Coldplay"),
        )
    }
}
