package com.music.bitchord.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The pink the app was drawn in before the accent was a setting, and the one it starts on. */
internal val DesktopAccentDefault = Color(0xFFFA586A)

/** One colour the accent can be set to. */
internal class DesktopAccentColour(val key: String, val label: String, val colour: Color)

/**
 * The accent the whole window is drawn in — buttons, switches, a liked star, the selected sidebar
 * row.
 *
 * A plain [Color] is read once and never changes, so the value sits in snapshot state and this
 * reads through to it: every existing `DesktopAccent` read keeps working, and recolours itself when
 * the choice changes. Read in composition or at draw, as the constant was.
 */
internal val DesktopAccent: Color get() = DesktopAccentSettings.current

/**
 * The accent setting: a colour picked from [DesktopAccentSettings.colours], or [DesktopAccentSettings.SONG]
 * to take it from the playing cover.
 */
internal object DesktopAccentSettings {

    /** The accent comes from the playing song's cover rather than from [colours]. */
    const val SONG = "song"

    val colours = listOf(
        DesktopAccentColour("pink", "Pink", DesktopAccentDefault),
        DesktopAccentColour("orange", "Orange", Color(0xFFFF9F0A)),
        DesktopAccentColour("yellow", "Yellow", Color(0xFFFFD60A)),
        DesktopAccentColour("green", "Green", Color(0xFF30D158)),
        DesktopAccentColour("teal", "Teal", Color(0xFF64D2FF)),
        DesktopAccentColour("blue", "Blue", Color(0xFF0A84FF)),
        DesktopAccentColour("purple", "Purple", Color(0xFFBF5AF2)),
    )

    private val _choice = MutableStateFlow(
        DesktopPersistence().string(KEY_ACCENT, colours.first().key),
    )

    /** What Settings writes: one of the colour keys, or [SONG]. */
    val choice: StateFlow<String> = _choice

    /** The colour every `DesktopAccent` read resolves to. */
    var current: Color by mutableStateOf(colourFor(_choice.value))
        private set

    fun setChoice(key: String) {
        val choice = if (key == SONG) SONG else colours.firstOrNull { it.key == key }?.key ?: colours.first().key
        DesktopPersistence().saveString(KEY_ACCENT, choice)
        _choice.value = choice
        // Following the song takes its colour from the cover, not from here: the one showing stays
        // until that cover has been read, rather than flashing back to pink for a frame.
        if (choice != SONG) current = colourFor(choice)
    }

    /** The accent for a stored choice, pink when the key is one this build no longer offers. */
    fun colourFor(key: String): Color =
        colours.firstOrNull { it.key == key }?.colour ?: colours.first().colour

    internal fun showSongColour(colour: Color) {
        if (_choice.value == SONG) current = colour
    }

    internal const val KEY_ACCENT = "accent_colour"
}

/**
 * Keeps [DesktopAccentSettings.current] on the choice: the chosen colour, or the playing cover's
 * own. Called once at the top of the window, above the theme that reads it.
 */
@Composable
internal fun DesktopAccentSource(artworkUrl: String?) {
    val choice by DesktopAccentSettings.choice.collectAsState()
    val palette = rememberDesktopArtworkPalette(artworkUrl.takeIf { choice == DesktopAccentSettings.SONG })
    LaunchedEffect(choice, palette) {
        // A cover still being read — or a sleeve with nothing but grey on it — answers the neutral
        // fallback. The accent it had is left alone then, so following the song never flickers
        // through grey between tracks.
        if (choice == DesktopAccentSettings.SONG && palette != DesktopArtworkPalette.Default) {
            DesktopAccentSettings.showSongColour(palette.secondary.asSongAccent())
        }
    }
}

/**
 * A cover's colour made fit to be an accent. A sleeve can be washed out or almost white, and a pale
 * accent under white button text cannot be read: the hue is kept, the saturation is floored so the
 * colour still reads as a colour, and the result is held at the brightness the pink sits at.
 */
private fun Color.asSongAccent(): Color {
    val high = maxOf(red, green, blue)
    val low = minOf(red, green, blue)
    val delta = high - low
    // No hue at all — a black-and-white or concrete-grey sleeve — leaves nothing to follow.
    if (high <= 0f || delta / high < 0.12f) return DesktopAccentDefault
    val hue = when {
        high == red -> 60f * (((green - blue) / delta) % 6f)
        high == green -> 60f * (((blue - red) / delta) + 2f)
        else -> 60f * (((red - green) / delta) + 4f)
    }
    val accent = Color.hsv(
        hue = if (hue < 0f) hue + 360f else hue,
        saturation = (delta / high).coerceAtLeast(0.45f),
        value = high.coerceIn(0.62f, 0.86f),
    )
    // Yellow and other light hues clear the value floor and still sit too bright under white ink.
    var fitted = accent
    var guard = 0
    while (fitted.luminance() > 0.34f && guard++ < 10) {
        fitted = Color(fitted.red * 0.88f, fitted.green * 0.88f, fitted.blue * 0.88f)
    }
    return fitted
}
