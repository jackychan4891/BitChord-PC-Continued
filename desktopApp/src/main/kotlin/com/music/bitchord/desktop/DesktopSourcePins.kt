package com.music.bitchord.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.concurrent.ConcurrentHashMap

/**
 * The tracks the listener has named a source for, from the player's track-information panel.
 *
 * The walk that picks a stream normally ranks the sources and takes the first one that answers.
 * This is the exception to it, kept beside [DesktopOriginalVersion]'s opposite: a row the listener
 * wants served by one particular source — the Tidal copy rather than the Qobuz one, or YouTube's
 * own upload rather than a module's.
 *
 * It is a *request*, not a guarantee. A source that turns out to have no copy of the track must
 * not fail playback, so the registry tries the named source alone and, when that comes back
 * empty, walks the usual order — see [DesktopSourceRegistry.resolve].
 */
internal object DesktopSourcePins {

    /** Bumped whenever the map changes, and read by [of] so a panel recomposes on it. */
    private var revision by mutableStateOf(0)

    private val pins = ConcurrentHashMap<String, String>().apply {
        putAll(DesktopPersistence().sourcePins())
    }

    /** The source [videoId] was told to play from, or null for the usual ranking. */
    fun of(videoId: String): String? {
        @Suppress("UNUSED_EXPRESSION") revision
        return if (videoId.isBlank()) null else pins[videoId]
    }

    /** Serves [videoId] from [sourceId] while that source can. */
    fun pin(videoId: String, sourceId: String) {
        if (videoId.isBlank() || pins.put(videoId, sourceId) == sourceId) return
        revision++
        persist()
    }

    /** Back to whatever ranks highest. */
    fun clear(videoId: String) {
        if (pins.remove(videoId) == null) return
        revision++
        persist()
    }

    private fun persist() = DesktopPersistence().saveSourcePins(pins.toMap())
}
