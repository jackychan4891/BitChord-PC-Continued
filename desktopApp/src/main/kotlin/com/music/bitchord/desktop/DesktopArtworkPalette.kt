package com.music.bitchord.desktop

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DesktopArtworkPalette(
    val primary: Color,
    val secondary: Color,
) {
    companion object {
        // Both fallbacks are neutral: a release page paints its table from this before the cover has
        // been read, and a coloured guess there is a flash of a colour the release never had.
        val Default = DesktopArtworkPalette(Color(0xFF8E8E93), Color(0xFFA1A1A6))
    }
}

@Composable
fun rememberDesktopArtworkPalette(url: String?): DesktopArtworkPalette {
    val palette by produceState(DesktopArtworkPalette.Default, key1 = url) {
        value = DesktopArtworkPalette.Default
        value = withContext(Dispatchers.IO) { DesktopArtworkPalette.from(DesktopArtworkCache.load(url)) }
    }
    return palette
}

/**
 * The colour a release's table writes in, held neutral until its cover has been read and then
 * carried across rather than snapped to.
 */
@Composable
fun rememberDesktopArtworkTint(url: String?): Color {
    val palette = rememberDesktopArtworkPalette(url)
    val tint by animateColorAsState(palette.secondary, tween(260), label = "artworkTint")
    return tint
}

private fun DesktopArtworkPalette.Companion.from(bitmap: ImageBitmap?): DesktopArtworkPalette {
    if (bitmap == null || bitmap.width == 0 || bitmap.height == 0) return DesktopArtworkPalette.Default
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.readPixels(pixels)
    // Read in 4-bit colour cells rather than as one average: a sleeve is usually one or two
    // colours, and the mean of a red cover and its black corner is a colour the cover never had —
    // which is why every release used to tint its page the same muddy grey.
    val cells = HashMap<Int, IntArray>()
    var red = 0L
    var green = 0L
    var blue = 0L
    var count = 0L
    pixels.forEachIndexed { index, pixel ->
        if (index % 16 != 0) return@forEachIndexed
        val r = pixel shr 16 and 0xFF
        val g = pixel shr 8 and 0xFF
        val b = pixel and 0xFF
        red += r
        green += g
        blue += b
        count++
        val key = (r shr 4 shl 8) or (g shr 4 shl 4) or (b shr 4)
        val cell = cells.getOrPut(key) { IntArray(4) }
        cell[0]++
        cell[1] += r
        cell[2] += g
        cell[3] += b
    }
    if (count == 0L) return DesktopArtworkPalette.Default
    val average = Color(
        red = (red / count).toFloat() / 255f,
        green = (green / count).toFloat() / 255f,
        blue = (blue / count).toFloat() / 255f,
    )
    // The cell that decides the page is the one that covers the most of the sleeve, with a thumb on
    // the scale for saturation so a magenta label on a black sleeve reads as magenta.
    val dominant = cells.values.maxByOrNull { cell ->
        val size = cell[0].toFloat()
        val r = cell[1] / size / 255f
        val g = cell[2] / size / 255f
        val b = cell[3] / size / 255f
        size * (1f + 3f * (maxOf(r, g, b) - minOf(r, g, b)).coerceAtLeast(0f))
    } ?: return DesktopArtworkPalette.Default
    val size = dominant[0].toFloat()
    val colour = Color(
        red = dominant[1] / size / 255f,
        green = dominant[2] / size / 255f,
        blue = dominant[3] / size / 255f,
    )
    return DesktopArtworkPalette(average.copy(alpha = 1f), colour.legible())
}

/**
 * The dominant colour lifted until it can be written with on the page — the table is set in this
 * colour on a dark ground, so a cover's near-black stays out of the text even where it is what the
 * sleeve is mostly made of. A cover with no hue keeps its grey, only lightened.
 */
private fun Color.legible(): Color {
    val high = maxOf(red, green, blue)
    val low = minOf(red, green, blue)
    if (high <= 0f) return Color(0.62f, 0.62f, 0.65f)
    val saturation = (high - low) / high
    if (saturation < 0.06f) return Color(0.60f + high * 0.18f, 0.60f + high * 0.18f, 0.63f + high * 0.18f)
    val scale = (0.78f / high).coerceAtLeast(1f)
    return Color(
        red = (red * scale).coerceAtMost(1f),
        green = (green * scale).coerceAtMost(1f),
        blue = (blue * scale).coerceAtMost(1f),
        alpha = 1f,
    )
}
