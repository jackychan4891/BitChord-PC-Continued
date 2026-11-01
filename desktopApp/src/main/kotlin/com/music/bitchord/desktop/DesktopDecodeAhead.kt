package com.music.bitchord.desktop

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** What [DesktopDecodeAhead] reads from: a decoder, or a stand-in for one in tests. */
internal interface DesktopSampleSource {
    /** The next block as interleaved floats, or null at the end. The array may be reused. */
    fun readSamples(): FloatArray?

    /** How much of the array [readSamples] returned is actually this block. */
    val sampleCount: Int

    fun seek(micros: Long): Boolean

    /**
     * Where the first block delivered after the last [seek] really begins, or null until one has
     * answered it. A seek asks for a position but the container's index point decides what is
     * actually handed over, so a caller that measures the deck from the ask is wrong by the
     * difference. A source with nothing to say about it leaves this null.
     */
    val landedUs: Long? get() = null

    fun close()
}

/**
 * Decodes a track a few seconds ahead of the speakers, on a thread of its own.
 *
 * The audio thread used to call the decoder directly, and the decoder reads straight from the
 * network: a YouTube stream stops for a whole 512 KiB range request every half-minute or so, and a
 * lossless stream waits on its socket whenever the connection hiccups. WASAPI holds only a fifth
 * of a second, so any read slower than that was heard as a gap of a few milliseconds. Here those
 * waits land on this thread while the audio thread plays what was decoded earlier.
 *
 * Only this thread touches [source] once constructed — seeks and the close are handed to it — so
 * the native decoder never sees two threads.
 */
internal class DesktopDecodeAhead(
    private val source: DesktopSampleSource,
    val outputFormat: DesktopPcmFormat,
    val durationUs: Long?,
    val measuredFormat: DesktopStreamFormat?,
    /** How far ahead to decode, in samples across all channels. */
    private val aheadSamples: Int = outputFormat.sampleRate * outputFormat.channels * AHEAD_SECONDS,
    name: String = "BitChord-Decode",
) {

    private val lock = ReentrantLock()
    /** Signalled when there is something for the audio thread to take. */
    private val ready = lock.newCondition()
    /** Signalled when the worker has room, a seek, or a close to act on. */
    private val wake = lock.newCondition()

    private val queue = ArrayDeque<FloatArray>()
    /** The song position each queued block starts at, kept index-for-index with [queue]. */
    private val queueStarts = ArrayDeque<Long>()
    /** The end of the last block appended, so an unstamped block still gets a start. */
    private var queueEndUs = Long.MIN_VALUE / 4
    private var queuedSamples = 0
    private var ended = false
    private var closed = false
    private var pendingSeekUs: Long? = null
    /** Bumped by every seek, so a block decoded before it is never played after it. */
    private var generation = 0
    /** Whether a block has been handed out since the last seek; a wait before the first is a load. */
    private var started = false

    /** How much of the array [readSamples] returned is actually this block. */
    var sampleCount: Int = 0
        private set

    /**
     * Where the first block since the last [seek] really begins, as the decoder says, or null while
     * it has not answered. Read from the queue, so it is the deck's own start rather than the ask.
     */
    var landedUs: Long? = null
        private set

    /** Where in the song the block last handed to the caller begins. */
    var headUs: Long? = null
        private set

    private val worker = Thread(::decodeLoop, name).apply {
        isDaemon = true
        // Above the UI, below the audio thread itself.
        priority = Thread.NORM_PRIORITY + 1
        start()
    }

    /** The next decoded block, waiting for the decoder only if it has fallen behind. */
    fun readSamples(): FloatArray? = lock.withLock {
        if (queue.isEmpty() && !ended && !closed) {
            val waitedFrom = System.nanoTime()
            while (queue.isEmpty() && !ended && !closed) ready.await()
            val waitedMs = (System.nanoTime() - waitedFrom) / 1_000_000
            if (started && waitedMs >= STARVED_LOG_MS) {
                DesktopTrackLog.log("decode-ahead ran dry: the audio thread waited ${waitedMs}ms for the decoder")
            }
        }
        val block = queue.removeFirstOrNull()
        val blockStart = queueStarts.removeFirstOrNull()
        if (blockStart != null) headUs = blockStart
        if (block == null) {
            sampleCount = 0
            return null
        }
        queuedSamples -= block.size
        started = true
        wake.signal()
        sampleCount = block.size
        block
    }

    /** Moves to [micros]; whatever was decoded ahead of the old position is thrown away. */
    fun seek(micros: Long): Boolean {
        lock.withLock {
            generation++
            queue.clear()
            queueStarts.clear()
            queuedSamples = 0
            ended = false
            started = false
            landedUs = null
            headUs = null
            queueEndUs = Long.MIN_VALUE / 4
            pendingSeekUs = micros
            wake.signal()
        }
        return true
    }

    /** Stops decoding; the decoder itself is closed by the worker, once any read in flight returns. */
    fun close() {
        lock.withLock {
            closed = true
            queue.clear()
            queuedSamples = 0
            wake.signal()
            ready.signalAll()
        }
    }

    private fun decodeLoop() {
        try {
            while (true) {
                var seekTo: Long? = null
                val decodingFor = lock.withLock {
                    while (!closed && pendingSeekUs == null && (ended || queuedSamples >= aheadSamples)) {
                        wake.await()
                    }
                    if (closed) return
                    seekTo = pendingSeekUs
                    pendingSeekUs = null
                    generation
                }
                val micros = seekTo
                if (micros != null) {
                    if (!source.seek(micros)) DesktopTrackLog.log("decode-ahead: seek to ${micros / 1_000}ms failed")
                    continue
                }

                val decoded = try {
                    source.readSamples()?.copyOf(source.sampleCount)
                } catch (failure: Exception) {
                    DesktopTrackLog.log("decode-ahead: decoder failed, ending the track: ${failure.message}")
                    null
                }
                // Only this thread reads the decoder, so its answer about where this block landed is
                // taken here and handed to the audio thread through the same lock as the block.
                val landedFromSource = source.landedUs
                lock.withLock {
                    // A seek came in while this block was being read; it belongs to the old position.
                    if (decodingFor != generation) return@withLock
                    // Close came in while this block was being read. Committing it would leave the
                    // queue non-empty after close, and the caller's very next readSamples() — which
                    // the contract says is null — would return this block instead. Seen on Windows
                    // CI, where scheduling lets the caller's close win this race consistently;
                    // Linux's timing hides it. The block dies with the closed reader.
                    if (closed) return@withLock
                    if (landedFromSource != null) landedUs = landedFromSource
                    if (decoded == null) {
                        ended = true
                    } else if (decoded.isNotEmpty()) {
                        queue.addLast(decoded)
                        // A block's start: the decoder's own timestamp for the first block after a
                        // seek, and otherwise the end of the one before it. A block is always
                        // recorded, so the starts stay index-for-index with the queue; a missing
                        // one would leave the two deques one block apart for good, and the
                        // reported head would then sit permanently behind the audio.
                        val perChannel = decoded.size / outputFormat.channels.coerceAtLeast(1)
                        val blockUs = perChannel * 1_000_000L /
                            outputFormat.sampleRate.coerceAtLeast(1)
                        val start = when {
                            queueStarts.isNotEmpty() -> queueEndUs
                            landedFromSource != null -> landedFromSource
                            else -> 0L
                        }
                        val end = start + blockUs
                        queueEndUs = end
                        queueStarts.addLast(start)
                        queuedSamples += decoded.size
                    }
                    ready.signalAll()
                }
            }
        } catch (_: InterruptedException) {
            // Closing the app; the decoder goes with the process.
        } finally {
            runCatching { source.close() }
        }
    }

    private companion object {
        /** Long enough to ride out a slow range request or a reconnect. */
        const val AHEAD_SECONDS = 8

        /** Waits shorter than this are ordinary scheduling, not a network stall worth logging. */
        const val STARVED_LOG_MS = 20L
    }
}
