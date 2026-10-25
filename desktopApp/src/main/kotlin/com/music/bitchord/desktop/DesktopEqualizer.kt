package com.music.bitchord.desktop

import com.music.bitchord.playback.EqCurve
import com.music.bitchord.playback.EqLayout
import com.music.bitchord.playback.FilterKind
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * The app's own equaliser: ten filter sections and a balance trim over the decoded stream.
 *
 * A port of Android's `EqualizerProcessor`, and it drives the same [EqCurve] the Android build
 * does — [EqLayout] and the curve maths live in `shared`, so the two targets cannot drift into
 * different sounds from the same settings.
 *
 * Floats rather than 16-bit PCM: the desktop engine mixes in float all the way to the sink, so
 * there is no quantisation on the way through and no clamp on the way out. Everything else is the
 * same — the topology-preserving state-variable filter with Cytomic's output mixes, chosen because
 * the trapezoidal form stays well behaved at a 60 Hz cutoff where a naive biquad loses precision.
 *
 * Every number here is a target. Dragging the tone puck re-aims them continuously, and stepping
 * coefficients per block is zipper noise, so gains, Qs, the make-up attenuation and the balance all
 * chase their targets across [GLIDE_FRAMES]-frame sub-blocks.
 */
internal class DesktopEqualizer {

    private class Tuning(val curve: EqCurve, val balance: Float) {
        companion object {
            val OFF = Tuning(EqCurve.FLAT, 0f)
        }
    }

    @Volatile
    private var target: Tuning = Tuning.OFF

    /** Carries the boost instead of the make-up attenuation — see [setLimiter]. */
    @Volatile
    private var limiter = false

    @Volatile
    private var limiterRestart = false

    private var limiterEnvelope = 0f
    private var limiterRelease = 0f

    /** The centre-channel canceller, and how much of it is applied, glided so toggling is silent. */
    @Volatile
    private var vocalRemoval = false

    private var vocalAmount = 0f

    private var channels = 0
    private var sampleRate = 0

    private val currentGainDb = FloatArray(EqLayout.SLOTS)
    private val currentQ = FloatArray(EqLayout.SLOTS) { 0.707f }
    private var currentPreampDb = 0f
    private var currentBalance = 0f

    private val coeffA1 = FloatArray(EqLayout.SLOTS)
    private val coeffA2 = FloatArray(EqLayout.SLOTS)
    private val coeffA3 = FloatArray(EqLayout.SLOTS)
    private val mixInput = FloatArray(EqLayout.SLOTS)
    private val mixBand = FloatArray(EqLayout.SLOTS)
    private val mixLow = FloatArray(EqLayout.SLOTS)

    private val activeSlots = IntArray(EqLayout.SLOTS)
    private val running = BooleanArray(EqLayout.SLOTS)

    /** Two integrator states per section, per channel. */
    private var state = FloatArray(0)

    /** Output trim per channel: make-up attenuation, and balance where stereo. */
    private var channelGain = FloatArray(0)

    /**
     * Aims the equaliser. Called from whoever owns the settings, never from the audio thread.
     *
     * `enabled = false` is a flat curve rather than a bypass flag, so switching it off glides down
     * to nothing instead of cutting the current curve out from under a playing track.
     */
    fun setTuning(enabled: Boolean, curve: EqCurve, balance: Float) {
        target = if (enabled) Tuning(curve, balance.coerceIn(-1f, 1f)) else Tuning.OFF
    }

    /**
     * Swaps the make-up attenuation for a peak limiter.
     *
     * The attenuation is how a boosted curve is kept inside full scale on a format with no headroom:
     * turning the EQ up makes everything else quieter. A limiter does the same job only when the
     * music actually peaks, so a boost stays a boost; in exchange it touches the signal, and that is
     * why it is a choice rather than the way it always works. With it on, the curve handed to
     * [setTuning] should carry no attenuation of its own.
     */
    fun setLimiter(enabled: Boolean) {
        if (enabled && !limiter) limiterRestart = true
        limiter = enabled
    }

    /**
     * Pulls the centre of the stereo field out of the mix.
     *
     * A lead vocal is usually mixed dead centre, so subtracting the middle (the signal both
     * channels share) from both leaves the sides: the vocal drops away, along with anything else
     * that was panned centre. It is a cancellation, not a separator — a centred bass or kick goes
     * with it — which is why it is an explicit switch rather than something always running.
     */
    fun setVocalRemoval(enabled: Boolean) {
        vocalRemoval = enabled
    }

    /** Sizes the state for a stream. Snaps rather than glides: there is nothing to be continuous with. */
    fun configure(channels: Int, sampleRate: Int) {
        if (channels == this.channels && sampleRate == this.sampleRate) return
        this.channels = channels.coerceAtLeast(1)
        this.sampleRate = sampleRate
        state = FloatArray(this.channels * EqLayout.SLOTS * 2)
        channelGain = FloatArray(this.channels) { 1f }
        limiterRelease = exp(-1f / (LIMITER_RELEASE_MS / 1_000f * sampleRate.coerceAtLeast(1)))
        limiterEnvelope = 0f
        vocalAmount = if (vocalRemoval) 1f else 0f
        running.fill(false)
        snapToTarget()
    }

    /** Forgets what the filters were ringing with, for a seek or a fresh source. */
    fun reset() {
        state.fill(0f)
        running.fill(false)
        limiterEnvelope = 0f
        vocalAmount = if (vocalRemoval) 1f else 0f
        snapToTarget()
    }

    /** Equalises [count] interleaved samples of [block], in place. */
    fun process(block: FloatArray, count: Int) {
        if (count == 0 || channels == 0 || sampleRate == 0) return
        if (limiterRestart) {
            limiterEnvelope = 0f
            limiterRestart = false
        }
        val tuning = target
        val limiting = limiter
        // Flat *and* settled there: an equaliser that has just been switched off is still gliding
        // down, and cutting that glide short is the click it exists to avoid. A limiter still has
        // a job to do over a flat curve, and so has a vocal canceller on its way out.
        if (isFlat(tuning) && isSettled(tuning) && !limiting && vocalAmount < VOCAL_SETTLED) return

        var index = 0
        while (index < count) {
            val remaining = count - index
            val block_ = min(remaining, GLIDE_FRAMES * channels)
            glideTowards(tuning)
            val active = prepareSections()
            prepareChannelGains()

            var at = index
            val end = index + block_
            while (at < end) {
                val frameStart = at
                var peak = 0f
                for (channel in 0 until channels) {
                    if (at >= end) break
                    var sample = block[at]
                    for (slot in 0 until active) {
                        sample = section(activeSlots[slot], channel, sample)
                    }
                    sample *= channelGain[channel]
                    val magnitude = abs(sample)
                    if (magnitude > peak) peak = magnitude
                    block[at] = sample
                    at++
                }
                if (channels == 2 && at == frameStart + 2) {
                    val target = if (vocalRemoval) 1f else 0f
                    vocalAmount += (target - vocalAmount) * VOCAL_GLIDE
                    if (vocalAmount > VOCAL_SETTLED) {
                        val middle = (block[frameStart] + block[frameStart + 1]) * 0.5f * vocalAmount
                        block[frameStart] -= middle
                        block[frameStart + 1] -= middle
                        // Cancelling can raise one side above either input, so the limiter's peak
                        // is measured again after it rather than from the pre-cancel samples.
                        peak = max(abs(block[frameStart]), abs(block[frameStart + 1]))
                    }
                }
                if (limiting) limit(block, frameStart, at, peak)
            }
            flushDenormals(active)
            index = end
        }
    }

    /**
     * A linked peak limiter, after the output trim.
     *
     * Instant attack, so a boosted band cannot overshoot the ceiling; exponential release, the shape
     * a gain reduction should return in. One envelope for the whole frame rather than one per
     * channel: a limiter that pulled each side down by its own peak would swing the stereo image on
     * every transient.
     */
    private fun limit(block: FloatArray, from: Int, to: Int, peak: Float) {
        limiterEnvelope = max(peak, limiterEnvelope * limiterRelease)
        if (limiterEnvelope <= LIMITER_CEILING) return
        val gain = LIMITER_CEILING / limiterEnvelope
        for (index in from until to) block[index] *= gain
    }

    // ---- Gliding -----------------------------------------------------------

    private fun snapToTarget() {
        val tuning = target
        tuning.curve.gainsDb.copyInto(currentGainDb)
        tuning.curve.qs.copyInto(currentQ)
        currentPreampDb = tuning.curve.preampDb
        currentBalance = tuning.balance
    }

    private fun glideTowards(tuning: Tuning) {
        for (slot in 0 until EqLayout.SLOTS) {
            // Decibels, not linear gain: it is the perceptual unit, and it puts the resting point
            // at zero so a band crossing from cut to boost passes through flat.
            currentGainDb[slot] = linearGlide(currentGainDb[slot], tuning.curve.gainsDb[slot])
            // Q the other way round — a bandwidth halves and doubles, it does not step.
            currentQ[slot] = geometricGlide(currentQ[slot], tuning.curve.qs[slot])
        }
        currentPreampDb = linearGlide(currentPreampDb, tuning.curve.preampDb)
        currentBalance = linearGlide(currentBalance, tuning.balance)
    }

    private fun linearGlide(current: Float, target: Float): Float =
        current + (target - current) * GLIDE_RATE

    private fun geometricGlide(current: Float, target: Float): Float {
        val from = ln(current.coerceAtLeast(MIN_Q))
        val to = ln(target.coerceAtLeast(MIN_Q))
        return exp(from + (to - from) * GLIDE_RATE)
    }

    private fun isFlat(tuning: Tuning): Boolean =
        abs(tuning.balance) < SETTLED_BALANCE &&
            abs(tuning.curve.preampDb) < SETTLED_DB &&
            tuning.curve.gainsDb.all { abs(it) < SETTLED_DB }

    private fun isSettled(tuning: Tuning): Boolean {
        if (abs(currentBalance - tuning.balance) >= SETTLED_BALANCE) return false
        if (abs(currentPreampDb - tuning.curve.preampDb) >= SETTLED_DB) return false
        for (slot in 0 until EqLayout.SLOTS) {
            if (abs(currentGainDb[slot] - tuning.curve.gainsDb[slot]) >= SETTLED_DB) return false
        }
        return true
    }

    // ---- Coefficients ------------------------------------------------------

    /**
     * Which sections are doing anything, with their coefficients brought up to date.
     *
     * A section at 0 dB is arithmetically a wire, so skipping it costs nothing and saves the whole
     * cascade while the other tab's half of [EqLayout] sits idle. Its state is cleared on the way
     * out rather than left stale.
     */
    private fun prepareSections(): Int {
        var active = 0
        for (slot in 0 until EqLayout.SLOTS) {
            if (abs(currentGainDb[slot]) >= SETTLED_DB) {
                updateCoefficients(slot)
                activeSlots[active++] = slot
                running[slot] = true
            } else if (running[slot]) {
                clearState(slot)
                running[slot] = false
            }
        }
        return active
    }

    private fun updateCoefficients(slot: Int) {
        val spec = EqLayout.slots[slot]
        val a = 10f.pow(currentGainDb[slot] / 40f)
        val q = currentQ[slot].coerceAtLeast(MIN_Q)
        val base = tan(PI * usableFrequency(spec.frequencyHz) / sampleRate).toFloat()
        val g: Float
        val k: Float
        when (spec.kind) {
            FilterKind.BELL -> {
                g = base
                k = 1f / (q * a)
                mixInput[slot] = 1f
                mixBand[slot] = k * (a * a - 1f)
                mixLow[slot] = 0f
            }
            FilterKind.LOW_SHELF -> {
                g = base / sqrt(a)
                k = 1f / q
                mixInput[slot] = 1f
                mixBand[slot] = k * (a - 1f)
                mixLow[slot] = a * a - 1f
            }
            FilterKind.HIGH_SHELF -> {
                g = base * sqrt(a)
                k = 1f / q
                mixInput[slot] = a * a
                mixBand[slot] = k * (1f - a) * a
                mixLow[slot] = 1f - a * a
            }
        }
        val d = 1f / (1f + g * (g + k))
        coeffA1[slot] = d
        coeffA2[slot] = g * d
        coeffA3[slot] = g * (g * d)
    }

    /** Highest centre the bilinear transform can still place without warping to infinity. */
    private fun usableFrequency(hz: Float): Float =
        hz.coerceIn(MIN_HZ, sampleRate * MAX_FREQUENCY_FRACTION)

    private fun prepareChannelGains() {
        val preamp = 10f.pow(currentPreampDb / 20f)
        if (channels == 2) {
            // Attenuate the far side rather than lift the near one: a balance that made things
            // louder would be a volume control with a side effect.
            channelGain[0] = preamp * min(1f, 1f - currentBalance)
            channelGain[1] = preamp * min(1f, 1f + currentBalance)
        } else {
            channelGain.fill(preamp)
        }
    }

    // ---- Filter ------------------------------------------------------------

    private fun section(slot: Int, channel: Int, input: Float): Float {
        val i = (channel * EqLayout.SLOTS + slot) * 2
        val ic1 = state[i]
        val ic2 = state[i + 1]
        val v3 = input - ic2
        val v1 = coeffA1[slot] * ic1 + coeffA2[slot] * v3
        val v2 = ic2 + coeffA2[slot] * ic1 + coeffA3[slot] * v3
        state[i] = 2f * v1 - ic1
        state[i + 1] = 2f * v2 - ic2
        return mixInput[slot] * input + mixBand[slot] * v1 + mixLow[slot] * v2
    }

    private fun clearState(slot: Int) {
        for (channel in 0 until channels) {
            val i = (channel * EqLayout.SLOTS + slot) * 2
            state[i] = 0f
            state[i + 1] = 0f
        }
    }

    /**
     * Zeroes integrator states that have decayed to nothing.
     *
     * A filter left ringing out under silence walks its state down towards denormal floats, and
     * denormal arithmetic is orders of magnitude slower on hardware that traps it. On an audio
     * thread with a buffer deadline that is a dropout, and a quiet passage is when it would happen.
     */
    private fun flushDenormals(active: Int) {
        for (index in 0 until active) {
            val slot = activeSlots[index]
            for (channel in 0 until channels) {
                val i = (channel * EqLayout.SLOTS + slot) * 2
                if (abs(state[i]) < DENORMAL_FLOOR) state[i] = 0f
                if (abs(state[i + 1]) < DENORMAL_FLOOR) state[i + 1] = 0f
            }
        }
    }

    internal companion object {
        /** Frames between coefficient updates. ~1.5 ms at 44.1 kHz. */
        const val GLIDE_FRAMES = 64

        /** Per-sub-block glide fraction. ~18 ms time constant — a fast drag still tracks. */
        private const val GLIDE_RATE = 0.08f

        /** Below this a band is doing nothing anyone can hear, so it counts as flat. */
        private const val SETTLED_DB = 0.01f

        private const val SETTLED_BALANCE = 0.0005f
        private const val MIN_Q = 0.05f
        private const val MIN_HZ = 10f

        /** Keeps `tan` away from its pole at Nyquist. */
        private const val MAX_FREQUENCY_FRACTION = 0.45f

        /** Just under full scale, so a format that clamps has nothing to clamp. */
        private const val LIMITER_CEILING = 0.98f

        /** How long the gain takes to come back after a peak — long enough not to pump. */
        private const val LIMITER_RELEASE_MS = 120f

        /** Per-frame glide for the vocal canceller — about 11 ms, so switching it is not a click. */
        private const val VOCAL_GLIDE = 0.002f

        /** Below this the canceller is doing nothing anyone can hear. */
        private const val VOCAL_SETTLED = 1e-4f

        private const val DENORMAL_FLOOR = 1e-12f
    }
}
