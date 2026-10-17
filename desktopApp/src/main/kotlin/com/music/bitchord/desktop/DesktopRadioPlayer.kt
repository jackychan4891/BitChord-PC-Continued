package com.music.bitchord.desktop

import java.util.concurrent.Executors
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class DesktopRadioStation(
    val id: String,
    val title: String,
    val artworkUrl: String?,
    val streamUrl: String,
    val transport: String? = null,
)

internal data class DesktopRadioPlaybackState(
    val station: DesktopRadioStation? = null,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
)

internal class DesktopRadioPlayer {
    private val lock = Any()
    private val mutableState = MutableStateFlow(DesktopRadioPlaybackState())
    val state: StateFlow<DesktopRadioPlaybackState> = mutableState.asStateFlow()
    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "desktop-radio").apply { isDaemon = true }
    }
    private var current: Session? = null
    private var released = false
    @Volatile private var gain = 1f

    fun start(station: DesktopRadioStation) = synchronized(lock) {
        if (released) return@synchronized
        cancelLocked()
        val session = Session()
        current = session
        mutableState.value = DesktopRadioPlaybackState(station, isLoading = true)
        executor.execute { runSession(session, station) }
    }

    fun play() = synchronized(lock) {
        if (!released && current == null) mutableState.value.station?.let { start(it) }
    }

    fun pause() = synchronized(lock) {
        cancelLocked()
        mutableState.value = mutableState.value.copy(isPlaying = false, isLoading = false, error = null)
    }

    fun stop() = synchronized(lock) {
        cancelLocked()
        mutableState.value = DesktopRadioPlaybackState()
    }

    fun setVolume(volume: Float) {
        gain = if (volume.isNaN()) 0f else volume.coerceIn(0f, 1f)
    }

    fun release() = synchronized(lock) {
        released = true
        cancelLocked()
        mutableState.value = DesktopRadioPlaybackState()
        executor.shutdown()
    }

    private fun cancelLocked() {
        current?.let {
            it.cancelled = true
            runCatching { it.line?.close() }
        }
        current = null
    }

    private fun runSession(session: Session, station: DesktopRadioStation) {
        if (session.cancelled) return
        val decoder = DesktopAudioDecoder()
        var line: SourceDataLine? = null
        var failure: String? = null
        try {
            if (session.cancelled) return
            val stream = DesktopRadioClient.resolveStream(station).getOrThrow()
            if (session.cancelled) return
            val opened = decoder.open(
                stream.url,
                requested = DesktopPcmFormat(44100, 2, 2),
                transport = stream.transport,
            )
            if (session.cancelled) return
            opened.getOrThrow()
            val pcm = decoder.outputFormat
            val format = AudioFormat(pcm.sampleRate.toFloat(), 16, pcm.channels, true, false)
            if (session.cancelled) return
            val output = AudioSystem.getSourceDataLine(format)
            line = output
            if (session.cancelled) return
            output.open(format)
            if (session.cancelled) return
            synchronized(lock) {
                if (session.cancelled || current !== session) return
                session.line = output
                output.start()
            }
            if (session.cancelled) return
            while (!session.cancelled) {
                val bytes = decoder.read()
                if (session.cancelled) return
                if (bytes == null) error("The live stream disconnected. Press Play to reconnect.")
                if (bytes.isEmpty()) continue
                scale(bytes)
                synchronized(lock) {
                    if (session.cancelled || current !== session) return
                    if (mutableState.value.isLoading) {
                        mutableState.value = mutableState.value.copy(isPlaying = true, isLoading = false)
                    }
                }
                var offset = 0
                while (offset < bytes.size) {
                    if (session.cancelled) return
                    val written = output.write(bytes, offset, bytes.size - offset)
                    if (session.cancelled) return
                    check(written > 0 && written <= bytes.size - offset) { "Radio audio output stopped" }
                    offset += written
                }
            }
        } catch (error: Throwable) {
            failure = error.message ?: "Radio playback failed"
        } finally {
            runCatching { line?.close() }
            // Only the worker closes the decoder, so cancellation cannot destroy an active native read.
            val closed = runCatching { decoder.close() }
            if (failure == null) failure = closed.exceptionOrNull()?.let { it.message ?: "Radio cleanup failed" }
            synchronized(lock) {
                if (!session.cancelled && current === session) {
                    current = null
                    mutableState.value = mutableState.value.copy(
                        isPlaying = false, isLoading = false, error = failure,
                    )
                }
            }
        }
    }

    private fun scale(bytes: ByteArray) {
        val volume = gain
        if (volume == 1f) return
        if (volume == 0f) {
            bytes.fill(0)
            return
        }
        for (index in 0 until bytes.size - 1 step 2) {
            val sample = (bytes[index].toInt() and 0xff) or (bytes[index + 1].toInt() shl 8)
            val scaled = (sample * volume).toInt().coerceIn(-32768, 32767)
            bytes[index] = scaled.toByte()
            bytes[index + 1] = (scaled shr 8).toByte()
        }
    }

    private class Session {
        @Volatile var cancelled = false
        var line: SourceDataLine? = null
    }
}
