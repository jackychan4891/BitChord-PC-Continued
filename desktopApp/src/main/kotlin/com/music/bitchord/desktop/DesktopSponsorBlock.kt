package com.music.bitchord.desktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/** One non-music span to jump over, in milliseconds. */
internal data class SponsorSegment(val startMs: Long, val endMs: Long)

/**
 * SponsorBlock's non-music segments: the intro, outro and interlude a YouTube upload carries that
 * the catalogue recording does not. Fetched per video and skipped during playback.
 *
 * Segments are cached per video, including the "asked, found none" answer, so a replay does not
 * ask again; a request that failed is deliberately not cached, so the next track change retries.
 */
internal object DesktopSponsorBlock {

    private val persistence = DesktopPersistence()

    private val _enabled = MutableStateFlow(persistence.boolean(KEY_ENABLED, true))
    val enabled: StateFlow<Boolean> = _enabled

    fun setEnabled(value: Boolean) {
        _enabled.value = value
        persistence.saveBoolean(KEY_ENABLED, value)
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build()

    private val cache = ConcurrentHashMap<String, List<SponsorSegment>>()
    private val inFlight = ConcurrentHashMap.newKeySet<String>()

    fun segmentsFor(videoId: String): List<SponsorSegment> = cache[videoId].orEmpty()

    suspend fun load(videoId: String) {
        if (videoId.isBlank() || cache.containsKey(videoId)) return
        if (!inFlight.add(videoId)) return
        try {
            cache[videoId] = fetch(videoId)
        } catch (_: Exception) {
            // Not cached: the next track change asks again.
        } finally {
            inFlight.remove(videoId)
        }
    }

    private suspend fun fetch(videoId: String): List<SponsorSegment> = withContext(Dispatchers.IO) {
        val url = "$API?videoID=${URLEncoder.encode(videoId, "UTF-8")}" +
            "&categories=${URLEncoder.encode(CATEGORIES, "UTF-8")}"
        val request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(15))
            .header("Accept", "application/json")
            .GET()
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) emptyList() else parse(response.body())
    }

    private fun parse(body: String): List<SponsorSegment> = runCatching {
        val array = json.parseToJsonElement(body) as? JsonArray ?: return@runCatching emptyList()
        array.mapNotNull { entry ->
            val obj = entry.jsonObject
            val action = obj["actionType"]?.jsonPrimitive?.content ?: "skip"
            if (action != "skip") return@mapNotNull null
            val span = obj["segment"]?.jsonArray ?: return@mapNotNull null
            if (span.size < 2) return@mapNotNull null
            val start = span[0].jsonPrimitive.content.toDoubleOrNull() ?: return@mapNotNull null
            val end = span[1].jsonPrimitive.content.toDoubleOrNull() ?: return@mapNotNull null
            val startMs = (start * 1000).toLong()
            val endMs = (end * 1000).toLong()
            if (endMs - startMs < MIN_SEGMENT_MS) null else SponsorSegment(startMs, endMs)
        }.sortedBy { it.startMs }
    }.getOrDefault(emptyList())

    private const val API = "https://api.sponsor.ajay.app/api/skipSegments"
    private const val CATEGORIES = "[\"music_offtopic\"]"
    private const val KEY_ENABLED = "sponsorblock_enabled"
    private const val MIN_SEGMENT_MS = 1000L
}
