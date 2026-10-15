package com.music.bitchord.desktop

import com.music.bitchord.data.NerdStats
import java.util.Locale

/**
 * The optional audio line shown on BitChord's Discord activity card — the desktop
 * twin of Android's `discordAudioQualityLine`, reading the same figures off the
 * engine's measured [DesktopStreamFormat] rather than off Media3's.
 *
 * Only premium formats earn a line. The values are what the decoder measured, never
 * what a source advertised, so a lossy fallback cannot keep calling itself Lossless.
 */
internal fun desktopDiscordAudioQualityLine(format: DesktopStreamFormat?): String? {
    format ?: return null

    val quality = when {
        format.isDolbyAtmos -> "◗◖ Dolby Atmos"
        format.isHiRes -> "Hi-Res Lossless"
        format.isLossless -> "Lossless"
        else -> return null
    }

    return buildList {
        add(quality)
        format.codecLabel?.let(::add)
        format.kbps?.takeIf { it > 0 }?.let { add("$it kbps") }
        format.bitDepth?.takeIf { it > 0 }?.let { add("$it-bit") }
        format.sampleRateHz?.takeIf { it > 0 }?.let {
            val khz = String.format(Locale.ROOT, "%.1f", it / 1000f).removeSuffix(".0")
            add(NerdStats.megahertzLabel(it) ?: "$khz kHz")
        }
        format.channels?.takeIf { it > 0 }?.let {
            add(
                when (it) {
                    1 -> "Mono"
                    2 -> "Stereo"
                    else -> "$it ch"
                },
            )
        }
    }.joinToString(" · ").take(DISCORD_TEXT_LIMIT).trimEnd()
}

/** Discord caps each activity text field at 128 characters. */
private const val DISCORD_TEXT_LIMIT = 128
