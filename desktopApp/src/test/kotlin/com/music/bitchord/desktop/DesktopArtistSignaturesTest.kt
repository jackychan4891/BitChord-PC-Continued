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
}
