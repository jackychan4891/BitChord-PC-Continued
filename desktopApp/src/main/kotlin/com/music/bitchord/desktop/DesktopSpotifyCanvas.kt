package com.music.bitchord.desktop

import com.music.bitchord.data.canvas.SpotifyCanvasQuery
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Spotify's Canvas: the short looping clip shown behind a track in their own app.
 *
 * Needs the listener's `sp_dc` cookie, which is the one thing here that identifies an account —
 * see [DesktopSpotifyToken] for how a bearer is minted from it, and why that is the fragile part
 * on a desktop with no embedded browser.
 */
internal object DesktopSpotifyCanvas {

    private const val SEARCH_URL = "https://api.spotify.com/v1/search"
    private const val ALBUM_TRACKS_URL = "https://api.spotify.com/v1/albums"
    private const val CANVAS_URL = "https://spclient.wg.spotify.com/canvaz-cache/v0/canvases"

    /** spclient gates this path to Spotify's own apps by user agent; the web player's is turned
     * away, so this wears a mobile client's instead. */
    private const val SPOTIFY_APP_UA = "Spotify/9.0.34.593 iOS/18.4 (iPhone15,3)"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val CANVAS_URL_REGEX = Regex("""https://[^"'\s\x00-\x1F]+\.cnvs\.mp4""")

    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    private data class TrackHit(val uri: String, val title: String, val artist: String, val album: String?)

    fun search(title: String, artist: String, album: String?): DesktopCanvasArtwork? {
        val token = DesktopSpotifyToken.accessToken() ?: return null
        val hit = searchTrack(title, artist, album, token) ?: return null
        val canvasUrl = fetchCanvasUrl(hit.uri, token) ?: return null
        return DesktopCanvasArtwork(
            url = canvasUrl,
            title = hit.title,
            artist = hit.artist,
            album = hit.album,
            source = DesktopCanvasSource.SPOTIFY,
        )
    }

    /** A release's canvas, read off its first track — Spotify hangs Canvas off tracks, not
     * releases, so there is no album-level lookup to make directly. */
    fun searchAlbum(album: String, artist: String): DesktopCanvasArtwork? {
        val token = DesktopSpotifyToken.accessToken() ?: return null
        val items = get(
            url("$SEARCH_URL", listOf("q" to "$album $artist", "type" to "album", "limit" to "10")),
            token,
        )?.get("albums")?.jsonObject?.get("items")?.jsonArray ?: return null

        for (item in items) {
            val record = item as? JsonObject ?: continue
            val recordTitle = record.text("name") ?: continue
            val artists = record["artists"]?.jsonArray
                ?.mapNotNull { it.jsonObject.text("name") }
                .orEmpty()
            if (!canvasMatches(recordTitle, artists, album, artist)) continue
            val albumId = record.text("id") ?: continue
            val trackUri = firstTrackUri(albumId, token) ?: continue
            val canvasUrl = fetchCanvasUrl(trackUri, token) ?: continue
            return DesktopCanvasArtwork(
                url = canvasUrl,
                title = recordTitle,
                artist = artists.joinToString(", ").ifBlank { null },
                album = recordTitle,
                source = DesktopCanvasSource.SPOTIFY,
            )
        }
        return null
    }

    private fun searchTrack(title: String, artist: String, album: String?, token: String): TrackHit? {
        val query = listOfNotNull(title, artist, album).joinToString(" ")
        val items = get(url(SEARCH_URL, listOf("q" to query, "type" to "track", "limit" to "10")), token)
            ?.get("tracks")?.jsonObject?.get("items")?.jsonArray
            ?: return null
        for (item in items) {
            val track = item as? JsonObject ?: continue
            val trackTitle = track.text("name") ?: continue
            val artists = track["artists"]?.jsonArray
                ?.mapNotNull { it.jsonObject.text("name") }
                .orEmpty()
            if (!canvasMatches(trackTitle, artists, title, artist)) continue
            val uri = track.text("uri") ?: continue
            return TrackHit(
                uri = uri,
                title = trackTitle,
                artist = artists.joinToString(", ").ifBlank { artist },
                album = track["album"]?.jsonObject?.text("name"),
            )
        }
        return null
    }

    private fun firstTrackUri(albumId: String, token: String): String? =
        get(url("$ALBUM_TRACKS_URL/$albumId/tracks", listOf("limit" to "1")), token)
            ?.get("items")?.jsonArray?.firstOrNull()?.jsonObject?.text("uri")

    // ── Pathfinder: the `canvas` GraphQL query ───────────────────────────

    /** The live `canvas` query hash, read off the web player's own scripts. */
    private val queryHashes = SpotifyCanvasQuery.QueryHashes(fetch = { url -> canvasGet(url) })

    /** The track's canvas, asked for the way the current web player does; `canvaz-cache` is the
     * fallback when the query fails or answers with nothing playable. */
    private fun fetchCanvasUrl(trackUri: String, token: String): String? =
        when (val answer = fetchCanvasViaPathfinder(trackUri, token)) {
            is SpotifyCanvasQuery.Answer.Found -> answer.url
            is SpotifyCanvasQuery.Answer.NoCanvas -> {
                DesktopTrackLog.log("canvas: Pathfinder has no playable canvas (${answer.detail}); trying canvaz-cache")
                fetchCanvasViaCanvaz(trackUri, token)
            }
            is SpotifyCanvasQuery.Answer.Failed -> {
                DesktopTrackLog.log("canvas: Spotify's canvas query failed (${answer.reason}); trying canvaz-cache")
                fetchCanvasViaCanvaz(trackUri, token)
            }
        }

    private fun fetchCanvasViaPathfinder(trackUri: String, token: String, isRetry: Boolean = false): SpotifyCanvasQuery.Answer {
        val hash = queryHashes.canvasHash(forceRefresh = isRetry)
        val response = runCatching {
            val builder = HttpRequest.newBuilder(URI.create(SpotifyCanvasQuery.ENDPOINT))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("Accept-Language", "en")
                .header("App-platform", "WebPlayer")
                .header("User-Agent", CANVAS_UA)
            authHeaders(token).forEach { (name, value) -> builder.header(name, value) }
            http.send(
                builder.POST(HttpRequest.BodyPublishers.ofString(SpotifyCanvasQuery.requestBody(trackUri, hash))).build(),
                HttpResponse.BodyHandlers.ofString(),
            )
        }.getOrElse { return SpotifyCanvasQuery.Answer.Failed("request threw: ${it.message}") }
        if (response.statusCode() !in 200..299) return SpotifyCanvasQuery.Answer.Failed("http ${response.statusCode()}")

        val answer = SpotifyCanvasQuery.parse(response.body())
        // A rebuilt web player retires the old hash: look it up again once.
        if (answer is SpotifyCanvasQuery.Answer.Failed && answer.staleHash && !isRetry) {
            return fetchCanvasViaPathfinder(trackUri, token, isRetry = true)
        }
        return answer
    }

    // ── canvaz-cache: protobuf in, protobuf out (fallback) ───────────────

    internal data class CanvasHit(val url: String, val trackUri: String?)

    private fun fetchCanvasViaCanvaz(trackUri: String, token: String): String? {
        val bytes = runCatching {
            val builder = HttpRequest.newBuilder(URI.create(CANVAS_URL))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/protobuf")
                .header("Content-Type", "application/protobuf")
                .header("Accept-Language", "en")
                .header("User-Agent", SPOTIFY_APP_UA)
            authHeaders(token).forEach { (name, value) -> builder.header(name, value) }
            val response = http.send(
                builder.POST(HttpRequest.BodyPublishers.ofByteArray(encodeCanvasRequest(trackUri))).build(),
                HttpResponse.BodyHandlers.ofByteArray(),
            )
            if (response.statusCode() in 200..299) response.body() else null
        }.getOrNull() ?: return null

        // The structured parse first — it can tell this track's own clip apart from another one
        // bundled into the same response. The regex is the fallback, needing only a *.cnvs.mp4 URL
        // to be sitting in the bytes as plain text.
        val hits = decodeCanvasResponse(bytes)
        hits.firstOrNull { it.trackUri == trackUri }?.url?.let { return it }
        hits.firstOrNull()?.url?.let { return it }
        return CANVAS_URL_REGEX.find(String(bytes, Charsets.ISO_8859_1))?.value
    }

    /** `CanvasRequest { repeated Track tracks = 1; Track { string track_uri = 1; } }` */
    internal fun encodeCanvasRequest(trackUri: String): ByteArray {
        val track = ByteArrayOutputStream().apply { writeLengthDelimited(1, trackUri.toByteArray()) }
        return ByteArrayOutputStream().apply { writeLengthDelimited(1, track.toByteArray()) }.toByteArray()
    }

    /**
     * `CanvasResponse { repeated Canvas canvases = 1; }`, `Canvas { id = 1; canvas_url = 2;
     * track_uri = 5; }` — only the fields this needs are read.
     */
    internal fun decodeCanvasResponse(bytes: ByteArray): List<CanvasHit> = runCatching {
        val hits = mutableListOf<CanvasHit>()
        val reader = ProtoReader(bytes)
        while (reader.hasMore()) {
            val tag = reader.readTag() ?: break
            if (tag shr 3 == 1 && tag and 7 == 2) {
                decodeCanvas(reader.readBytes())?.let(hits::add)
            } else {
                reader.skip(tag)
            }
        }
        hits
    }.getOrElse { emptyList() }

    private fun decodeCanvas(bytes: ByteArray): CanvasHit? = runCatching {
        var url: String? = null
        var trackUri: String? = null
        val reader = ProtoReader(bytes)
        while (reader.hasMore()) {
            val tag = reader.readTag() ?: break
            when {
                tag shr 3 == 2 && tag and 7 == 2 -> url = String(reader.readBytes(), StandardCharsets.UTF_8)
                tag shr 3 == 5 && tag and 7 == 2 -> trackUri = String(reader.readBytes(), StandardCharsets.UTF_8)
                else -> reader.skip(tag)
            }
        }
        url?.let { CanvasHit(it, trackUri) }
    }.getOrNull()

    // ── Plumbing ─────────────────────────────────────────────────────────

    /** The client token on top of the bearer: both endpoints turn away a bearer-only request with
     * a 429 that reads exactly like rate limiting. Omitted rather than fatal when minting fails. */
    private fun authHeaders(token: String): Map<String, String> = buildMap {
        put("Authorization", "Bearer $token")
        DesktopSpotifyToken.clientToken()?.let { put("Client-Token", it) }
    }

    private fun get(url: String, token: String): JsonObject? =
        canvasGet(url, authHeaders(token))
            ?.let { runCatching { json.parseToJsonElement(it).jsonObject }.getOrNull() }

    private fun url(base: String, params: List<Pair<String, String>>): String =
        params.joinToString("&", prefix = "$base?") { (name, value) ->
            "$name=${URLEncoder.encode(value, StandardCharsets.UTF_8)}"
        }

    /** A string field, or null when Spotify put something other than a string there. */
    private fun JsonObject.text(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.contentOrNull
}

// ── A protobuf reader, small enough not to be worth a dependency ────────────

private class ProtoReader(private val bytes: ByteArray) {
    private var at = 0

    fun hasMore(): Boolean = at < bytes.size

    fun readTag(): Int? = if (hasMore()) readVarint().toInt() else null

    fun readBytes(): ByteArray {
        val length = readVarint().toInt()
        val end = (at + length).coerceAtMost(bytes.size)
        val slice = bytes.copyOfRange(at, end)
        at = end
        return slice
    }

    fun skip(tag: Int) {
        when (tag and 7) {
            0 -> readVarint()
            1 -> at += 8
            2 -> readBytes()
            5 -> at += 4
            else -> at = bytes.size
        }
    }

    private fun readVarint(): Long {
        var result = 0L
        var shift = 0
        while (at < bytes.size && shift < 64) {
            val byte = bytes[at++].toInt()
            result = result or ((byte and 0x7F).toLong() shl shift)
            if (byte and 0x80 == 0) break
            shift += 7
        }
        return result
    }
}

private fun ByteArrayOutputStream.writeLengthDelimited(field: Int, payload: ByteArray) {
    writeVarint((field shl 3 or 2).toLong())
    writeVarint(payload.size.toLong())
    write(payload)
}

private fun ByteArrayOutputStream.writeVarint(value: Long) {
    var remaining = value
    while (true) {
        if (remaining and 0x7FL.inv() == 0L) {
            write(remaining.toInt())
            return
        }
        write((remaining and 0x7F or 0x80).toInt())
        remaining = remaining ushr 7
    }
}

/**
 * The bearer Spotify's own web player mints for itself.
 *
 * Android loads the real player in an offscreen WebView and reads the token it mints, because a
 * request signed here is answered with a token the downstream endpoints then refuse. A desktop
 * build has no embedded browser to do that in, so this takes the only route left: the web player's
 * own `/api/token`, signed with the TOTP it derives from a secret published in its bundle. When
 * Spotify declines, the canvas chain simply falls through to its other three sources.
 */
/**
 * Whether the web player has handed out a bearer. Held as three states rather than a yes/no,
 * because a cookie sitting in the settings is not evidence of a working Canvas: only a token is.
 */
internal enum class DesktopCanvasTokenState { UNTESTED, WORKING, REFUSED }

internal object DesktopSpotifyToken {

    private const val TOKEN_URL = "https://open.spotify.com/api/token"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val _state = MutableStateFlow(DesktopCanvasTokenState.UNTESTED)

    /** What the last mint did, for the Settings row to report. */
    val state: StateFlow<DesktopCanvasTokenState> = _state

    @Volatile private var cachedAccessToken: String? = null
    @Volatile private var accessTokenExpiresAtMs = 0L
    @Volatile private var cachedClientId: String? = null
    @Volatile private var cachedClientToken: String? = null
    @Volatile private var clientTokenExpiresAtMs = 0L
    @Volatile private var retryAfterMs = 0L
    @Volatile private var session: Pair<String, String>? = null

    /** The listener's `sp_dc` cookie. Nothing here works without it. */
    fun cookie(): String = DesktopPersistence().string(KEY_SPDC).trim()

    fun setCookie(value: String) {
        DesktopPersistence().saveString(KEY_SPDC, value.trim())
        cachedAccessToken = null
        accessTokenExpiresAtMs = 0L
        retryAfterMs = 0L
        _state.value = DesktopCanvasTokenState.UNTESTED
    }

    @Synchronized
    fun accessToken(): String? {
        val cookie = cookie().ifBlank { return null }
        val now = System.currentTimeMillis()
        cachedAccessToken?.let { if (now < accessTokenExpiresAtMs - 30_000) return it }
        if (now < retryAfterMs) return null

        // The shipped table, so a token costs one request. The bundle is only read down here once
        // that has been overtaken — never on the way in, because it is several megabytes of
        // JavaScript and this runs inline ahead of a canvas the listener is waiting on.
        var entry: Pair<Int, IntArray>? = TOTP_SECRETS.maxByOrNull { it.key }?.let { it.key to it.value }
        var body = mint(cookie, entry, now / 1000)
        if (body?.contains(TOTP_RETIRED) == true) {
            DesktopTrackLog.log("canvas: Spotify has retired this build's TOTP version; reading the web player's own")
            entry = DesktopSpotifyTotp.entry() ?: entry
            body = mint(cookie, entry, System.currentTimeMillis() / 1000)
        }
        val root = body?.let { runCatching { json.parseToJsonElement(it).jsonObject }.getOrNull() }
        val token = root?.get("accessToken")?.jsonPrimitive?.contentOrNull
        if (token.isNullOrBlank()) {
            // Backed off rather than retried per track: if Spotify has changed the shape, asking on
            // every skip fixes nothing and makes every canvas lookup pay for the round trip.
            retryAfterMs = now + RETRY_MS
            // Which half broke is the difference between a dead cookie and a moved endpoint, and
            // the Settings row can only say one of them.
            _state.value = DesktopCanvasTokenState.REFUSED
            DesktopTrackLog.log(
                if (body == null) {
                    "canvas: Spotify would not answer the token request"
                } else {
                    // What Spotify says no with is the whole diagnosis: `totpVerExpired` is a build
                    // overtaken above, `Unauthorized request` is a cookie that has died, and the
                    // Settings row can only say one of them. The answer carries nothing of the
                    // listener's, so it is safe to keep.
                    "canvas: Spotify refused the token request: ${body.take(MAX_LOGGED_BODY)}"
                },
            )
            return null
        }
        _state.value = DesktopCanvasTokenState.WORKING
        cachedAccessToken = token
        accessTokenExpiresAtMs = root["accessTokenExpirationTimestampMs"]
            ?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: (now + 3_600_000)
        cachedClientId = root["clientId"]?.jsonPrimitive?.contentOrNull
        return token
    }

    /**
     * The second header these endpoints demand alongside the bearer. Best-effort: a caller with no
     * client token sends the bearer alone and takes whatever the endpoint does with that.
     */
    @Synchronized
    fun clientToken(): String? {
        val now = System.currentTimeMillis()
        cachedClientToken?.let { if (now < clientTokenExpiresAtMs - 30_000) return it }
        val clientId = cachedClientId ?: return null
        val (clientVersion, deviceId) = session() ?: return null

        val payload = buildJsonObject {
            putJsonObject("client_data") {
                put("client_version", clientVersion)
                put("client_id", clientId)
                putJsonObject("js_sdk_data") {
                    put("device_brand", "unknown")
                    put("device_model", "unknown")
                    put("os", "linux")
                    put("os_version", System.getProperty("os.version").orEmpty())
                    put("device_id", deviceId)
                    put("device_type", "computer")
                }
            }
        }
        val body = runCatching {
            val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
            val response = client.send(
                HttpRequest.newBuilder(URI.create("https://clienttoken.spotify.com/v1/clienttoken"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Accept", "application/json")
                    // The bare header, deliberately: clienttoken 400s on a charset suffix.
                    .header("Content-Type", "application/json")
                    .header("User-Agent", CANVAS_UA)
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                    .build(),
                HttpResponse.BodyHandlers.ofString(),
            )
            if (response.statusCode() in 200..299) response.body() else null
        }.getOrNull() ?: return null

        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        if (root["response_type"]?.jsonPrimitive?.contentOrNull != "RESPONSE_GRANTED_TOKEN_RESPONSE") return null
        val granted = root["granted_token"]?.jsonObject ?: return null
        val token = granted["token"]?.jsonPrimitive?.contentOrNull ?: return null
        val ttl = granted["expires_after_seconds"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 3600L
        cachedClientToken = token
        clientTokenExpiresAtMs = now + ttl * 1000
        return token
    }

    /** The web player's build version, read off its own page, plus a device id to call ourselves. */
    private fun session(): Pair<String, String>? {
        session?.let { return it }
        val html = canvasGet("https://open.spotify.com") ?: return null
        val configB64 = Regex("""<script id="appServerConfig" type="text/plain">([^<]+)</script>""")
            .find(html)?.groupValues?.get(1) ?: return null
        val clientVersion = runCatching {
            val decoded = String(Base64.getDecoder().decode(configB64), StandardCharsets.UTF_8)
            json.parseToJsonElement(decoded).jsonObject["clientVersion"]?.jsonPrimitive?.contentOrNull
        }.getOrNull() ?: return null
        return (clientVersion to UUID.randomUUID().toString()).also { session = it }
    }

    /**
     * The six digits the web player sends with a token request.
     *
     * Standard TOTP over SHA-1, thirty-second steps, from a secret the bundle carries obfuscated as
     * a byte array XORed against its own index.
     */
    internal fun totp(secret: IntArray, epochSeconds: Long): String? = runCatching {
        val cleaned = secret.mapIndexed { index, value -> value xor (index % 33 + 9) }
            .joinToString("")
            .toByteArray(StandardCharsets.UTF_8)
        val counter = epochSeconds / 30
        val message = ByteArray(8) { index -> (counter ushr (56 - index * 8)).toByte() }
        val mac = Mac.getInstance("HmacSHA1").apply { init(SecretKeySpec(cleaned, "HmacSHA1")) }
        val hash = mac.doFinal(message)
        val offset = hash[hash.size - 1].toInt() and 0x0F
        val binary = ((hash[offset].toInt() and 0x7F) shl 24) or
            ((hash[offset + 1].toInt() and 0xFF) shl 16) or
            ((hash[offset + 2].toInt() and 0xFF) shl 8) or
            (hash[offset + 3].toInt() and 0xFF)
        "%06d".format(binary % 1_000_000)
    }.getOrNull()

    /**
     * One token request, answered with the body whatever the status.
     *
     * A refusal is read rather than discarded because the reason Spotify says no is in it, and
     * `totpVerExpired` is the difference between a cookie that has died and a version that has been
     * retired — only one of which a rebuild of the table can fix.
     */
    private fun mint(cookie: String, entry: Pair<Int, IntArray>?, epochSeconds: Long): String? {
        val code = entry?.let { totp(it.second, epochSeconds) }
        val query = buildList {
            add("reason" to "init")
            add("productType" to "web-player")
            if (code != null) {
                add("totp" to code)
                add("totpServer" to code)
                add("totpVer" to entry.first.toString())
            }
        }.joinToString("&") { (name, value) -> "$name=${URLEncoder.encode(value, StandardCharsets.UTF_8)}" }
        return canvasFetch(
            "$TOKEN_URL?$query",
            mapOf(
                "Cookie" to "sp_dc=$cookie",
                "Referer" to "https://open.spotify.com/",
                "App-Platform" to "WebPlayer",
            ),
        )?.second
    }

    /**
     * The secrets the web player has shipped, newest last.
     *
     * Shipped as a table so a token request costs one round trip rather than a page and a bundle.
     * Each entry is the secret with its bytes folded by `index % 33 + 9`, which is the same fold
     * [DesktopSpotifyTotp] applies to what it reads, so the two are interchangeable.
     *
     * Spotify retires a version on a date it publishes and answers an old one with `totpVerExpired`.
     * Version 12 was retired in October 2025 and took the canvas chain down with it; these are the
     * three still standing as of this writing, and the scrape is what catches the next bump.
     */
    internal val TOTP_SECRETS: Map<Int, IntArray> = mapOf(
        59 to intArrayOf(114, 99, 68, 74, 99, 53, 59, 109, 45, 35, 67, 82, 76, 93, 71, 78, 38, 47, 96, 57, 104, 47, 43, 125, 108, 28, 12, 114, 21, 78, 99, 96),
        60 to intArrayOf(70, 103, 78, 119, 87, 79, 33, 90, 79, 48, 41, 36, 83, 81, 75, 77, 99, 37, 64, 92, 74, 73),
        61 to intArrayOf(37, 61, 36, 38, 75, 38, 45, 98, 93, 88, 33, 123, 109, 119, 92, 84, 71, 124, 48, 89, 44, 102, 105, 112, 97, 108),
    )

    private const val RETRY_MS = 30L * 60 * 1000

    /** A refusal is logged so the reason is readable, but an HTML error page is not a log line. */
    private const val MAX_LOGGED_BODY = 220

    internal const val KEY_SPDC = "spotify_spdc_token"
}

private const val TOTP_RETIRED = "totpVerExpired"

/**
 * The web player's own TOTP table, read off its scripts.
 *
 * Spotify folds the secret into a byte array with `index % 33 + 9` and retires a version on a date
 * it publishes — the one this build shipped with was retired in October 2025, which took the canvas
 * chain down with it. [DesktopSpotifyToken]'s table is the fast path and this is what happens when
 * the build has been overtaken, so it is asked for only then: the caller is already waiting on a
 * token, and this is several megabytes of JavaScript.
 */
internal object DesktopSpotifyTotp {

    /** Newest first, which is how the bundle declares them. */
    private val SECRET_DECL =
        Regex("""secret:('(?:[^'\\]|\\.)*'|"(?:[^"\\]|\\.)*"),version:(\d+)""")

    /** The highest version the bundle still ships, or null when the player cannot be read. */
    fun entry(): Pair<Int, IntArray>? {
        val html = canvasGet("https://open.spotify.com/") ?: return null
        for (script in SpotifyCanvasQuery.webPlayerScripts(html).take(MAX_SCRIPTS)) {
            val body = canvasGet(script) ?: continue
            entries(body)?.let { return it }
        }
        return null
    }

    /** The newest `{secret:…,version:…}` declared in one piece of the player's JavaScript. */
    internal fun entries(bundle: String): Pair<Int, IntArray>? =
        SECRET_DECL.findAll(bundle)
            .mapNotNull { match ->
                val version = match.groupValues[2].toIntOrNull() ?: return@mapNotNull null
                version to unquote(match.groupValues[1]).folded()
            }
            .maxByOrNull { it.first }

    /** A JavaScript string literal, taken back to the characters it stands for. */
    private fun unquote(literal: String): String = literal.substring(1, literal.length - 1)
        .replace(Regex("""\\(.)""")) { match -> match.groupValues[1] }

    /** The same fold the bundle applies, so a scraped entry and a shipped one are interchangeable. */
    private fun String.folded(): IntArray =
        mapIndexed { index, char -> char.code xor (index % 33 + 9) }.toIntArray()

    private const val MAX_SCRIPTS = 8
}
