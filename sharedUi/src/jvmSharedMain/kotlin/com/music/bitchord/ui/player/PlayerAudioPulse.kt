package com.music.bitchord.ui.player

/**
 * The player's own loudness, handed from the audio thread to the backdrop.
 *
 * The engine writes two already-smoothed numbers per output block — the whole
 * mix's level and its low end — and the mesh reads them on the frame clock.
 * Plain volatiles rather than snapshot state: these writes happen on the audio
 * thread, and a snapshot write there would schedule work on the UI thread once
 * per block for a number the backdrop only needs once per frame.
 *
 * Both are 0..1 with the music at a normal mastering level sitting around the
 * middle of the range, so a quiet passage still visibly lets the field down.
 */
object PlayerAudioPulse {

    /** The mix's loudness, smoothed: the field's breathing. */
    @Volatile
    var level: Float = 0f
        private set

    /** The mix's low end, smoothed and quicker to rise: the kick. */
    @Volatile
    var bass: Float = 0f
        private set

    fun publish(level: Float, bass: Float) {
        this.level = level.coerceIn(0f, 1f)
        this.bass = bass.coerceIn(0f, 1f)
    }

    fun silence() = publish(0f, 0f)
}
