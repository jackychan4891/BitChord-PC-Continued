package com.music.bitchord.ui.player

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import coil3.compose.LocalPlatformContext
import com.music.bitchord.ui.graphics.Swatch
import com.music.bitchord.ui.graphics.forPixelAccess
import com.music.bitchord.ui.graphics.paletteSwatches
import com.music.bitchord.ui.graphics.toImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.music.bitchord.ui.graphics.ColorUtils
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private val FallbackColors = listOf(
    Color(0xFF3A1C71),
    Color(0xFFD76D77),
    Color(0xFF2B5876),
    Color(0xFFFFAF7B),
)

/** The four mesh colours, wrapped so the backdrop can skip recomposition. */
@Immutable
data class MeshPalette(
    val colors: List<Color>,
    /**
     * False while these are the stock colours rather than a sleeve's own — the
     * cover is still decoding. The backdrop treats those two cases differently:
     * a real palette is what the screen should land on, a placeholder should
     * never be seen morphing out of.
     */
    val resolved: Boolean = true,
)

/**
 * The Apple Music "Now Playing" backdrop: the album art's colours laid over the
 * surface as one continuous field, with soft masses drifting within it and the
 * whole layer blurred into a mesh. Colour changes on track skip crossfade over
 * ~1.4s instead of snapping.
 *
 * The blobs drift when there is a reason to — the player opening, or
 * [trackKey] changing — and then come to rest. They used to orbit forever,
 * which meant re-blurring a full-screen layer at display refresh rate for as
 * long as the player was up: the most expensive thing in the app, for motion
 * that reads as ambient at best and is invisible while the phone is in a
 * pocket. The settled frame looks the same; only the battery drain is gone.
 */
@Composable
fun MeshGradientBackground(
    palette: MeshPalette,
    modifier: Modifier = Modifier,
    trackKey: Any? = null,
    driftMillis: Int = 8_000,
    /**
     * Keep the blobs orbiting instead of letting them settle.
     *
     * Off everywhere the mesh fills a screen, for the reason in the class note:
     * a full-screen blur re-drawn at refresh rate is the most expensive thing
     * this app does, and nobody is looking at it. On the Replay's cards it is
     * the opposite trade — the surface is a few hundred dp of a card the user
     * has deliberately opened and is looking straight at, the motion is what
     * makes the card feel like an object rather than a picture of one, and
     * "reduce animation" still stops it dead.
     */
    continuous: Boolean = false,
    /**
     * How far the blobs are smeared. The default is sized for a full screen;
     * a small surface needs proportionally less, or the four colours blend into
     * one wash before they reach its edges.
     */
    blurRadius: Dp = 64.dp,
    /**
     * Off for a surface that should read as a still image: colours snap
     * straight to target instead of crossfading, and the blobs never drift
     * on a [trackKey] change, only settling once on first composition. The
     * Replay page and its cards use this — a grid of these redrawing a
     * blurred layer every time a card is opened or swiped past was the
     * expensive case the class note above warns about, multiplied by however
     * many cards are on screen.
     */
    animated: Boolean = true,
) {
    val reduceAnimation by PlayerSettings.reduceAnimation.collectAsStateWithLifecycle()

    // The colours from the last sleeve that actually resolved, held under the one
    // still decoding. The stock set is not a colour anyone chose, and painting it
    // while a cover loads is what read as the backdrop starting orange; the sleeve
    // that was on screen a second ago is the honest thing to sit under the new one.
    var held by remember { mutableStateOf(palette.takeIf { it.resolved }) }
    LaunchedEffect(palette) { if (palette.resolved) held = palette }
    val shown = if (palette.resolved) palette else (held ?: palette)

    // The first real palette lands rather than fades in: there is nothing of the
    // sleeve's on screen to fade from. After that a skip crossfades.
    var landed by remember { mutableStateOf(palette.resolved) }
    LaunchedEffect(palette.resolved) { if (palette.resolved) landed = true }

    val tuned = (shown.colors.ifEmpty { FallbackColors } + FallbackColors)
        .take(4)
        .mapIndexed { index, color -> color.tuned(SlotLightness[index]) }

    // Each colour slot crossfades independently when the track (palette) changes,
    // unless "reduce animation" is on, in which case colours snap straight to target.
    val colorSpec: AnimationSpec<Color> =
        if (reduceAnimation || !animated || !landed) snap() else tween(1400)
    val animatedColors = tuned.mapIndexed { index, color ->
        animateColorAsState(color, colorSpec, label = "meshColor$index").value
    }
    val baseColor by animateColorAsState(tuned.first().dimmed(), colorSpec, label = "meshBase")

    // Read in the draw lambda, not here: an Animatable read during draw
    // invalidates only the drawing, leaving composition out of the loop.
    val phase = remember { Animatable(0f) }
    LaunchedEffect(trackKey, reduceAnimation, continuous, animated) {
        when {
            !animated || reduceAnimation -> phase.snapTo(0f)
            // A full turn at a time, restarted rather than looped with an
            // infinite spec: the blobs' speeds are irrational multiples of each
            // other, so the pattern never repeats, and a linear phase keeps the
            // orbit even instead of easing to a halt each lap.
            continuous -> while (isActive) {
                phase.animateTo(
                    targetValue = phase.value + (2 * PI).toFloat(),
                    animationSpec = tween(driftMillis * 4, easing = LinearEasing),
                )
            }
            else -> phase.animateTo(
                targetValue = phase.value + DRIFT_RADIANS,
                animationSpec = tween(driftMillis, easing = FastOutSlowInEasing),
            )
        }
    }

    // Scale up slightly so the blur's clamped edges never show, then blur the
    // whole layer (RenderEffect, API 31+; a no-op below — the radial falloff
    // already reads soft there).
    //
    // Clipped on the way out, and from a layer of its own rather than by setting
    // `clip` on the one below: that one clips what is drawn *into* it, in its own
    // coordinates, and the scale is applied after — so the overhang the scale
    // creates survives it. This has to sit outside the scale to contain it.
    //
    // The overhang is a third of the backdrop's width and it is painted, not
    // transparent: whatever this is standing in gets it. Off a full-window sheet
    // that is the far side of the window and nobody ever saw it, which is how it
    // went unnoticed; in a pane beside a page it was a hand's width of gradient
    // laid over the feed.
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .graphicsLayer {
                scaleX = 1.3f
                scaleY = 1.3f
            }
            .background(baseColor)
            .blur(blurRadius),
    ) {
        // Nothing off a sleeve yet, so the screen is its own dim ground and the
        // blobs join in when the colours do.
        if (shown.resolved) {
            val drift = phase.value

            // The field the masses sit in: the palette laid across the whole
            // surface as one gradient. Sampling Apple's backdrop cell by cell,
            // not one of them is the ground colour showing through — every part
            // of it is a sleeve colour, and the dark comes from those colours
            // being deep rather from from gaps between the blobs. Left the
            // ground bare, our gaps read as the olive mud this replaced.
            val tilt = 0.10f * sin(drift * 0.5f)
            drawRect(
                brush = Brush.linearGradient(
                    colors = animatedColors.map { it.deepened() },
                    start = Offset(size.width * (0.05f + tilt), 0f),
                    end = Offset(size.width, size.height * (1f - tilt)),
                ),
            )

            // Six masses over four colours, so each hue gets a mass of its own
            // and the repeats land across the surface from each other.
            val anchors = listOf(
                Offset(0.18f, 0.22f),
                Offset(0.82f, 0.16f),
                Offset(0.50f, 0.50f),
                Offset(0.86f, 0.78f),
                Offset(0.16f, 0.80f),
                Offset(0.52f, 0.04f),
            )
            val speeds = listOf(1f, -0.7f, 0.85f, -1.15f, 0.6f, -0.9f)

            anchors.forEachIndexed { index, anchor ->
                val color = animatedColors[index % animatedColors.size]
                val center = Offset(
                    x = (anchor.x + 0.14f * cos(drift * speeds[index] + index * 1.7f)) * size.width,
                    y = (anchor.y + 0.14f * sin(drift * speeds[index] * 0.9f + index * 2.3f)) * size.height,
                )
                // Measured off the longer edge so the masses overlap into one
                // surface, and kept well under a full alpha: a core that holds
                // at 1 paints a disc, and a disc big enough to cover a side of
                // the window is what turned Apple's four hues into one flat wall
                // of red. The field underneath shows through instead. Small
                // enough that it does not — Apple's backdrop has pockets at 7%
                // lightness between its masses, and six overlapping discs of
                // 0.46 of the width left no gap to have them in.
                val radius = size.maxDimension * 0.40f
                drawCircle(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0f to color.copy(alpha = 0.95f),
                            0.45f to color.copy(alpha = 0.45f),
                            1f to color.copy(alpha = 0f),
                        ),
                        center = center,
                        radius = radius,
                    ),
                    radius = radius,
                    center = center,
                )
            }

            // Apple's four corners are the darkest thing on its backdrop — its
            // own are at 7–8% lightness against masses at 25% — and without them
            // the field runs to the edge of the window like a wallpaper instead
            // of sitting behind the cover.
            drawRect(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0.40f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.62f),
                    ),
                    center = Offset(size.width / 2f, size.height / 2f),
                    radius = size.maxDimension * 0.72f,
                ),
            )
        }

        // Gentle scrim so white text stays legible over bright art. Lighter than
        // it was: it is what flattened the blobs into one another once they were
        // bright enough to separate.
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.Black.copy(alpha = 0.06f),
                    Color.Black.copy(alpha = 0.30f),
                ),
            ),
        )
    }
}

/**
 * Loads the artwork with Coil (software bitmap, thumbnail-sized) and pulls a
 * 4-colour palette out of it. Recomputes when [imageUrl] changes.
 *
 * A track's motion artwork is frequently lit nothing like its still sleeve —
 * a different shot, a different grade. [canvasFrame], a frame captured off
 * the playing clip once one is available, is quantised the same way and
 * takes over from there, crossfading in exactly like a track skip.
 */
@Composable
fun rememberArtworkColors(imageUrl: String?, canvasFrame: ImageBitmap? = null): MeshPalette {
    val context = LocalPlatformContext.current
    // Seeded from the cache, so a sleeve this session has already read is on its
    // own colours in its first frame — nothing to crossfade in from.
    var palette by remember(imageUrl) {
        mutableStateOf(imageUrl?.let(paletteCache::get) ?: MeshPalette(FallbackColors, resolved = false))
    }

    LaunchedEffect(imageUrl) {
        if (imageUrl == null) return@LaunchedEffect
        val request = ImageRequest.Builder(context)
            .data(imageUrl)
            .size(128) // palette quality is fine at thumbnail size, and it's fast
            .forPixelAccess() // Palette needs pixel access
            .build()
        val result = SingletonImageLoader.get(context).execute(request)
        val bitmap = (result as? SuccessResult)?.image?.toImageBitmap() ?: return@LaunchedEffect
        val found = MeshPalette(paletteOf(bitmap))
        paletteCache[imageUrl] = found
        palette = found
    }

    LaunchedEffect(canvasFrame) {
        val frame = canvasFrame ?: return@LaunchedEffect
        // Reject near-black frames (first read after surface recreation, before
        // ExoPlayer decodes real content).  A dark sleeve will still exceed the
        // threshold because compression noise pushes mean luminance above ~12.
        if (isLikelyBlackFrame(frame)) return@LaunchedEffect
        val colors = withContext(Dispatchers.Default) { paletteOf(frame) }
        palette = MeshPalette(colors)
    }
    return palette
}

/**
 * How far the blobs travel in one settle. A shade under half a turn: enough
 * that the backdrop visibly reacts to a track change, short of a full orbit
 * that would land the blobs back where they started.
 */
private const val DRIFT_RADIANS = (PI * 0.45f).toFloat()

/**
 * Palettes already read, keyed by artwork URL — a cover cannot change colour
 * under its own address, so asking twice buys a second decode for the same
 * answer. A hit here is also the backdrop being on colour in its first frame.
 *
 * Clip frames never land here: the next frame of the same clip is a different
 * picture.
 */
private val paletteCache = object : LinkedHashMap<String, MeshPalette>(0, 0.75f, true) {
    override fun removeEldestEntry(eldest: Map.Entry<String, MeshPalette>) = size > PALETTE_CACHE_ENTRIES
}

/** A session's worth of covers, four colours deep. */
private const val PALETTE_CACHE_ENTRIES = 64

/**
 * Four mesh colours drawn from the artwork, separated by hue.
 *
 * The named swatches — vibrant, muted and friends — are a convenience over the
 * full set, and on dark or desaturated sleeves every vibrant slot comes back
 * null: Karan Aujla's marble interior fills two of the five. Topping the rest
 * up from [FallbackColors] is what left those covers sitting under the stock
 * purple. So the whole swatch list is read instead, and any shortfall is
 * derived from the art's own colours rather than borrowed.
 *
 * Two further things make a sleeve read as Apple's backdrop rather than as a
 * smear of itself. The quantiser's filter is off: its "red I-line" rule throws
 * away hue 10–37° under 82% saturation, which is exactly the warm band Apple
 * paints a third of its mesh with. And the winners are taken one per slice of
 * hue, so a green sleeve yields green, its neighbouring gold, its shadow and
 * its highlight, rather than four greens laid one over another.
 */
private fun paletteOf(bitmap: ImageBitmap): List<Color> {
    val found = bitmap.swatches(clearFilters = true)
        .ifEmpty { bitmap.swatches(clearFilters = false) }
        .sortedByDescending { it.score() }
        .spreadByHue()
    return when {
        found.isEmpty() -> FallbackColors
        found.size >= 4 -> found.take(4)
        else -> found.expandedTo(4)
    }
}

private fun ImageBitmap.swatches(clearFilters: Boolean): List<Swatch> =
    paletteSwatches(this, 24, clearFilters)

/**
 * How much of the sleeve a colour covers, weighed toward the colours a mesh
 * wants: well saturated, and sitting in the deep band Apple's own backdrop is
 * made of rather than at either end of the exposure.
 *
 * Saturation is squared because a mesh has no use for the near-grey cells a
 * population count hands to it — they were the olive wash, since a colour with
 * nothing in it still covers a whole quarter of the screen.
 */
private fun Swatch.score(): Float {
    val hsl = hslOf(rgb)
    val chroma = 0.10f + hsl[1] * hsl[1]
    val ground = when {
        hsl[2] < 0.06f -> 0.3f
        hsl[2] > 0.95f -> 0.4f
        else -> 1f
    }
    return population * chroma * ground * (1f - abs(hsl[2] - 0.38f) * 1.35f)
}

/**
 * The best-scoring swatch of each 30° of hue, in the order they scored.
 *
 * No filter of the quantiser's guarantees colour *spread*, which is what makes
 * the masses read as separate; sorting by population alone hands the four slots
 * to whichever hue the sleeve is mostly made of.
 */
private fun List<Swatch>.spreadByHue(): List<Color> {
    val best = LinkedHashMap<Int, Color>()
    forEach { swatch ->
        val hsl = hslOf(swatch.rgb)
        // Grey has no slice to sit in, so it keeps a key of its own: one achromatic
        // colour may join the mesh, and its low chroma score is what puts it last.
        val slice = if (hsl[1] < 0.18f) ACHROMATIC_HUE else hsl[0].toInt() / HUE_SLICE_DEGREES
        if (slice !in best) best[slice] = Color(swatch.rgb)
    }
    return best.values.toList()
}

/** Twelve slices of the wheel, so two colours that share one cannot both be a blob. */
private const val HUE_SLICE_DEGREES = 30
private const val ACHROMATIC_HUE = -1

/** Fill the empty slots off the art itself, fanning hue and lightness out. */
private fun List<Color>.expandedTo(count: Int): List<Color> {
    val out = toMutableList()
    var step = 1
    while (out.size < count) {
        out += this[(out.size - size) % size].shifted(24f * step, 0.12f * step)
        step++
    }
    return out
}

private fun Color.shifted(hue: Float, lightness: Float): Color {
    val hsl = hsl()
    hsl[0] = (hsl[0] + hue) % 360f
    hsl[2] = (hsl[2] + lightness).coerceIn(0.2f, 0.7f)
    return Color(ColorUtils.HSLToColor(hsl))
}

private fun Color.hsl(): FloatArray = hslOf(toArgb())

private fun hslOf(argb: Int): FloatArray = FloatArray(3).also { ColorUtils.colorToHSL(argb, it) }

/**
 * Saturate a sleeve colour and put it in Apple's lightness band.
 *
 * Measured off Apple's own backdrop, its masses sit at 10–30% lightness and
 * 40–80% saturation. Ours were at 24–42% lightness, which is the difference
 * between a deep field and a glowing one, and its low-saturation cells were
 * 17–31%, which is the olive mud. The saturation floor is what forces a pale
 * or grey sleeve colour into a hue that can carry a mass at all; the ceiling
 * keeps a pure red from banding out to a flat wall across a side of the window.
 *
 * [lightness] is the slot's own exposure, so a sleeve whose four colours are
 * all equally bright still yields masses that differ from one another.
 */
private fun Color.tuned(lightness: Float): Color {
    val hsl = hsl()
    hsl[1] = (hsl[1] * 1.55f).coerceIn(0.45f, 0.92f)
    hsl[2] = (hsl[2] * 0.55f * lightness).coerceIn(0.07f, 0.30f)
    return Color(ColorUtils.HSLToColor(hsl))
}

/**
 * How much each of the four slots is lit.
 *
 * Sampling Apple's backdrop cell by cell, its masses are not at one exposure:
 * they run from about 10% lightness to about 30%, and the step between them is
 * most of what makes them read as separate bodies rather than as one long
 * gradient. Held to a single exposure, ours flattened into exactly that — a
 * smooth ramp across the right of the window with nothing in it.
 */
private val SlotLightness = listOf(1.0f, 0.58f, 1.3f, 0.82f)

/** The same colour as a ground rather than as a mass: the field under the blobs. */
private fun Color.deepened(): Color {
    val hsl = hsl()
    hsl[2] = (hsl[2] * 0.55f).coerceAtLeast(0.04f)
    return Color(ColorUtils.HSLToColor(hsl))
}

private fun Color.dimmed(): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(toArgb(), hsl)
    hsl[2] = 0.12f
    return Color(ColorUtils.HSLToColor(hsl))
}
