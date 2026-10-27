package com.music.bitchord.ui.player

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import coil3.compose.LocalPlatformContext
import com.music.bitchord.ui.graphics.Swatch
import com.music.bitchord.ui.graphics.argbPixels
import com.music.bitchord.ui.graphics.forPixelAccess
import com.music.bitchord.ui.graphics.imageBitmapOf
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
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

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
    /**
     * The sleeve's own colour regions, and where each sits on the sleeve.
     *
     * Empty for a palette that was never read off an artwork — the service
     * cards and the downloads tile hand in four colours and get the stock
     * arrangement. [rememberArtworkColors] fills this in, and it is what makes
     * the player's backdrop follow a cover instead of painting every cover with
     * the same six dots: Apple's masses are the artwork's masses, blown up.
     */
    val masses: List<MeshMass> = emptyList(),
    /**
     * The sleeve as one small, oversaturated texture, or null when these colours
     * were never read off a picture.
     *
     * [MeshGradientBackground] paints four scaled copies of it as the field
     * itself, each turning and travelling on its own period. Where there is no
     * picture to read, the masses stand in: they carry *which* colours a cover
     * has and roughly where, but the copies carry the cover's own arrangement
     * moving through the frame, which is the part a list of colours cannot do —
     * and the part that makes Apple's backdrop read as liquid rather
     * than as a set of lights in a dark room.
     */
    val vivid: ImageBitmap? = null,
)

/**
 * One mass of the mesh: a colour, and the position of the artwork region it was
 * averaged from, in sleeve coordinates (0..1 across, 0..1 down).
 */
@Immutable
data class MeshMass(val color: Color, val x: Float, val y: Float)

/**
 * The Apple Music "Now Playing" backdrop: the album art's colours laid over the
 * surface as one continuous field, with soft masses drifting within it and the
 * whole layer blurred into a mesh. Colour changes on track skip crossfade over
 * ~1.4s instead of snapping.
 *
 * Where the palette came off a picture, [SLEEVE_LAYERS] — four scaled copies of
 * the sleeve itself, each turning and travelling on a different period — are the
 * field. That is the moving part of Apple's backdrop that a list of colours
 * cannot give: the artwork's own arrangement sliding through the frame.
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
     * Off for a surface that is only ever glanced at, on for the two that are
     * stared at: the player, and the Replay cards. The cost is a full-screen
     * blurred layer redrawn at refresh rate, the most expensive thing this app
     * does, which is why it is not simply on everywhere — but parked masses are
     * not a mesh at all, they are a wallpaper, and the reference this copies is
     * visibly travelling the whole time it plays. "Reduce animation" still
     * stops it dead wherever it is on.
     */
    continuous: Boolean = false,
    /**
     * Let the sleeve copies be the field instead of the masses.
     *
     * On for the player, which is the surface the reference builds this for and
     * the only one big enough for four moving samples of a cover to read as one
     * backdrop. Off everywhere else: a card the width of a cover would show a
     * spinning thumbnail rather than a mesh, and the masses are what the Replay
     * sheets and the Home tiles were tuned against.
     */
    sleeveStack: Boolean = false,
    /**
     * How far the blobs are smeared. The default is sized for a full screen;
     * a small surface needs proportionally less, or the four colours blend into
     * one wash before they reach its edges.
     */
    blurRadius: Dp = 60.dp,
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
    // A mass moves as well as changes colour on a skip, so the two crossfade
    // together. Zero-duration tween rather than snap: animateFloatAsState asks
    // for a finite spec.
    val floatSpec: FiniteAnimationSpec<Float> =
        if (reduceAnimation || !animated || !landed) tween(0) else tween(1400)
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

    // The copies of the sleeve under the masses. A skip swaps a whole picture
    // here, not four colours, and an instant swap of a picture is a pop — so it
    // crossfades on the same clock the colours do. The outgoing copy is kept in a
    // state read from the draw lambda rather than in composition, because what has
    // to survive the fade is a bitmap, and the fade has to be drawable against it.
    val layersFade = remember { Animatable(1f) }
    val fadingLayers = remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(shown.vivid) {
        val next = shown.vivid
        val previous = fadingLayers.value
        if (next === previous) return@LaunchedEffect
        if (previous == null || next == null || !animated || !landed || reduceAnimation) {
            fadingLayers.value = next
            layersFade.snapTo(1f)
            return@LaunchedEffect
        }
        layersFade.snapTo(0f)
        layersFade.animateTo(1f, tween(LAYER_FADE_MS, easing = FastOutSlowInEasing))
        fadingLayers.value = next
    }

    // Slot count is fixed so a skip moves each mass to where the *same* slot of
    // the new sleeve landed rather than renumbering them: a field that changes
    // how many blobs it has mid-crossfade pops.
    val slots = shown.masses.take(MESH_MASSES)
    val animatedMasses = if (slots.isEmpty()) emptyList() else (1 until MESH_MASSES).fold(slots) { acc, _ ->
        acc + acc.last()
    }.take(MESH_MASSES).mapIndexed { index, mass ->
        MeshMass(
            color = animateColorAsState(mass.color, colorSpec, label = "meshMassColor$index").value,
            x = animateFloatAsState(mass.x, floatSpec, label = "meshMassX$index").value,
            y = animateFloatAsState(mass.y, floatSpec, label = "meshMassY$index").value,
        )
    }
    val ground = animateColorAsState(
        if (animatedMasses.isEmpty()) baseColor else groundOf(animatedMasses.map(MeshMass::color)),
        colorSpec,
        label = "meshGround",
    ).value

    // The field is blurred; the corner falloff and the legibility scrim are not.
    // They used to live inside the blurred layer, and that is what left the
    // backdrop with no corners at all: 88dp of blur smeared a 62% edge wash
    // inward over a third of the window, so the darkest part of the frame read
    // the same as the middle. Apple's corners are its darkest pixels, which is
    // what stops a blown-up sleeve reading as wallpaper.
    Box(modifier = modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .graphicsLayer {
                    scaleX = MESH_OVERSCALE
                    scaleY = MESH_OVERSCALE
                }
                .background(ground)
                .blur(blurRadius),
        ) {
            val drift = phase.value
            // The four copies *are* the field, not a wash under it. This is the
            // order the reference works in: the cover, oversaturated and smeared
            // over four moving samples of itself, carries both the colour and the
            // motion. The masses are what stands in when there is no sleeve to
            // read — the cards, the Replay sheets, the moment before a first
            // cover decodes — and they stay under the same single blur either
            // way, which is what makes the whole thing read as one continuous
            // mesh rather than as a stack of transparencies.
            val sleeve = if (shown.resolved && sleeveStack) shown.vivid else null
            if (sleeve != null) {
                fadingLayers.value?.let { outgoing ->
                    if (outgoing !== sleeve) drawSleeveStack(outgoing, drift, alpha = 1f)
                }
                drawSleeveStack(sleeve, drift, alpha = layersFade.value)
            } else if (animatedMasses.isNotEmpty() && shown.resolved) {
                animatedMasses.forEachIndexed { index, mass ->
                    // Sleeve space to surface space, the way ContentScale.Crop
                    // lays a square cover over this box: the long axis gets the
                    // whole sleeve, the short one only its middle band.
                    val ratio = size.width / size.height
                    val u = (mass.x + DRIFT_TRAVEL * cos(drift * MASS_SPEEDS[index % MASS_SPEEDS.size] + index * 1.7f)).coerceIn(0f, 1f)
                    val v = (mass.y + DRIFT_TRAVEL * sin(drift * MASS_SPEEDS[index % MASS_SPEEDS.size] * 0.9f + index * 2.3f)).coerceIn(0f, 1f)
                    val center = if (ratio >= 1f) {
                        Offset(u * size.width, (0.5f + (v - 0.5f) * ratio) * size.height)
                    } else {
                        Offset((0.5f + (u - 0.5f) / ratio) * size.width, v * size.height)
                    }
                    val radius = size.maxDimension * MASS_RADIUS
                    drawCircle(
                        brush = Brush.radialGradient(
                            colorStops = MASS_FALLOFF.map { (at, keep) ->
                                at to mass.color.copy(alpha = MASS_CORE * keep)
                            }.toTypedArray(),
                            center = center,
                            radius = radius,
                        ),
                        radius = radius,
                        center = center,
                    )
                }
            } else {
                // No sleeve to follow: the stock arrangement the cards and the
                // Replay sheets have always used, translucent masses over a
                // gradient of the same four colours. Also what shows while a
                // first cover decodes, so the backdrop is never a flat wash.
                val tilt = 0.10f * sin(drift * 0.5f)
                drawRect(
                    brush = Brush.linearGradient(
                        colors = animatedColors.map { it.deepened() },
                        start = Offset(size.width * (0.05f + tilt), 0f),
                        end = Offset(size.width, size.height * (1f - tilt)),
                    ),
                )
                LEGACY_ANCHORS.forEachIndexed { index, anchor ->
                    val color = animatedColors[index % animatedColors.size]
                    val center = Offset(
                        x = (anchor.x + 0.14f * cos(drift * LEGACY_SPEEDS[index] + index * 1.7f)) * size.width,
                        y = (anchor.y + 0.14f * sin(drift * LEGACY_SPEEDS[index] * 0.9f + index * 2.3f)) * size.height,
                    )
                    val radius = size.maxDimension * 0.46f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colorStops = arrayOf(
                                0f to color.copy(alpha = 0.86f),
                                0.5f to color.copy(alpha = 0.38f),
                                1f to color.copy(alpha = 0f),
                            ),
                            center = center,
                            radius = radius,
                        ),
                        radius = radius,
                        center = center,
                    )
                }
            }
        }

        Canvas(Modifier.fillMaxSize()) {
            // One radial falloff, timed so that it only reaches in past the
            // outer ring of the frame. Ramped per axis instead, the corners
            // came right but the whole left third of the window went down with
            // them; measured from the middle, the sides stay lit and only the
            // corners — where Apple's own field is at its darkest — close in.
            drawRect(
                brush = Brush.radialGradient(
                    colorStops = vignette(0.26f),
                    center = Offset(size.width / 2f, size.height / 2f),
                    radius = sqrt(
                        (size.width * size.width + size.height * size.height) / 4f,
                    ),
                ),
            )
            // Gentle scrim so white text stays legible over bright art. Kept
            // light: the field's own depth is what carries the copy now, and a
            // heavy wash is what used to flatten the masses into one wall.
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.03f),
                        Color.Black.copy(alpha = 0.13f),
                    ),
                ),
            )
        }
    }
}

/**
 * One copy in the stack: how big it is, where it sits, and how fast it turns and
 * travels relative to the mesh's own phase.
 */
private class SleeveLayer(
    /** Its side, as a fraction of the surface's width. */
    val scale: Float,
    /** Where its middle sits, as fractions of the surface. */
    val cx: Float,
    val cy: Float,
    /**
     * How much this copy replaces the ones behind it rather than mixing with them.
     *
     * Only the base copy is opaque. See [SLEEVE_LAYERS].
     */
    val opacity: Float,
    /** Radians of its own rotation per radian of the mesh phase; negative turns it back. */
    val spin: Float,
    /** Radius of its circular track, as a fraction of the surface's width. Zero stays in place. */
    val orbit: Float,
    /** Radians of its travel per radian of the mesh phase. */
    val track: Float,
    /** Where it starts on that track, so no two copies launch in line. */
    val start: Float,
)

/**
 * The stack: four copies of the sleeve at 25%, 50%, 80% and 125% of the surface's
 * width, drawn largest first.
 *
 * This is the arrangement Apple's web player ships — the two largest copies spin in
 * place and the two smallest spin while they travel circular tracks, so the field
 * is the cover itself smeared over four moving samples of itself rather than a set
 * of lights drifting over a flat ground. The 125% copy is the one that guarantees
 * coverage: a square that size has an inscribed circle of half the window's width
 * plus a quarter, which reaches past every corner even dead on its diagonal, so
 * nothing under it is ever exposed. The 80% copy deliberately does not reach the
 * corners, which is where the copy behind it shows through.
 *
 * The one thing the reference gets from its twist, and that a plain sprite cannot
 * give, is that a copy never fully erases what it lands on. Drawn opaque, a copy
 * that happens to be turning a sleeve's shadow quarter across the window *is* the
 * window for as long as it takes: measured over one orbit, the field's mean
 * luminance halved and a quarter of its pixels went under 28/255, which is the
 * dark colour coming in and overpowering everything. So only the base copy is
 * opaque — it carries the exposure, being the whole sleeve at once — and the three
 * copies over it tint it by their own opacity rather than replacing it. A dark
 * quarter can then only dim the field by a fifth, a half, or two thirds of itself
 * depending on how far it reaches, and the brightness underneath survives it.
 *
 * Rates are written as multiples of the mesh phase and none of them share a factor,
 * so the pattern never closes up. [phase] advances one full turn every twelve
 * seconds, so a spin of 0.11 is a 109-second revolution and a track of 0.31 is a
 * 39-second orbit.
 */
private val SLEEVE_LAYERS = listOf(
    SleeveLayer(scale = 1.25f, cx = 0.50f, cy = 0.50f, opacity = 1.00f, spin = 0.110f, orbit = 0f, track = 0f, start = 0f),
    SleeveLayer(scale = 0.80f, cx = 0.50f, cy = 0.45f, opacity = 0.80f, spin = -0.145f, orbit = 0f, track = 0f, start = 0f),
    SleeveLayer(scale = 0.50f, cx = 0.50f, cy = 0.50f, opacity = 0.68f, spin = 0.220f, orbit = 0.19f, track = 0.31f, start = 2.1f),
    SleeveLayer(scale = 0.25f, cx = 0.50f, cy = 0.50f, opacity = 0.55f, spin = -0.160f, orbit = 0.31f, track = -0.47f, start = 4.4f),
)

/** Radians to degrees, for turning the mesh's phase into a rotation. */
private const val RAD_TO_DEG = 57.29578f

/** How long a skip takes to swap the sleeve copies, matched to the colours' 1.4s. */
private const val LAYER_FADE_MS = 1_400

/**
 * Paints [art] four times over the surface at the sizes and on the motion the
 * reference ships: two copies turning where they stand, two turning as they go
 * round a track.
 *
 * [phase] is the mesh's own drift phase, so the copies travel with the field rather
 * than on a clock beside it, and "reduce animation" parks the whole backdrop at
 * once. [alpha] scales all four, for a skip's crossfade, on top of each copy's own
 * opacity.
 */
private fun DrawScope.drawSleeveStack(
    art: ImageBitmap,
    phase: Float,
    alpha: Float,
) {
    if (alpha <= 0.004f) return
    val width = size.width
    val height = size.height
    SLEEVE_LAYERS.forEach { layer ->
        val side = (width * layer.scale).roundToInt().coerceAtLeast(1)
        val track = phase * layer.track + layer.start
        // The upscale lives in the transform, not in drawImage's dst size: the
        // dst-size overload drops copies whose rectangle is large relative to
        // the texture on Skia desktop, which left the field half ground.
        withTransform({
            translate(
                width * layer.cx + width * layer.orbit * cos(track),
                height * layer.cy + width * layer.orbit * sin(track),
            )
            rotate(
                phase * layer.spin * RAD_TO_DEG + layer.start * 2f,
                pivot = Offset.Zero,
            )
            scale(side / art.width.toFloat(), side / art.height.toFloat(), pivot = Offset.Zero)
        }) {
            drawImage(
                image = art,
                topLeft = Offset(-art.width / 2f, -art.height / 2f),
                alpha = alpha * layer.opacity,
            )
        }
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
        val found = withContext(Dispatchers.Default) {
            MeshPalette(paletteOf(bitmap), masses = massesOf(bitmap), vivid = bitmap.vividLayers())
        }
        paletteCache[imageUrl] = found
        palette = found
    }

    LaunchedEffect(canvasFrame) {
        val frame = canvasFrame ?: return@LaunchedEffect
        // Reject near-black frames (first read after surface recreation, before
        // ExoPlayer decodes real content).  A dark sleeve will still exceed the
        // threshold because compression noise pushes mean luminance above ~12.
        if (isLikelyBlackFrame(frame)) {
            return@LaunchedEffect
        }
        val read = withContext(Dispatchers.Default) {
            MeshPalette(paletteOf(frame), masses = massesOf(frame), vivid = frame.vividLayers())
        }
        palette = read
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
 * Masses the field is built from. Seven is what Apple's mesh resolves to on the
 * screens measured here — enough that a sleeve's own regions survive the blur,
 * few enough that the field keeps dark between them. Fixed so a skip moves each
 * mass to the same slot of the new sleeve instead of renumbering them.
 */
private const val MESH_MASSES = 7

/**
 * How much larger than the surface the field is drawn, then cropped.
 *
 * A blur has nothing to sample outside the layer, so its edges fade toward
 * transparent and the outermost ring of the window goes flat and dark. The
 * overscale puts that dead zone off-screen. It is modest because the sleeve's
 * regions are placed *on* the surface: scale the field up too far and they
 * smear together, which is how the old backdrop lost all its spread.
 */
private const val MESH_OVERSCALE = 1.15f

/**
 * One mass's radius, as a fraction of the surface's longest side. Sized so a
 * sleeve's regions stay readable as separate masses rather than merging into one
 * wash behind the artwork — Apple's field has blobs you can point at, and at
 * 0.62 ours had gradients you could only describe. The blur averages a shallow
 * gap back into a mid-tone, so this is a chroma dial rather than a depth one:
 * narrowing it keeps each mass its own colour near its centre and lets more of
 * the drained ground stand at the bottom of the range.
 */
private const val MASS_RADIUS = 0.44f

/** How opaque a mass is at its own centre. */
private const val MASS_CORE = 0.95f

/**
 * A mass's fade: distance along its radius, and the fraction of its core alpha
 * kept there. Measured against Apple's own field, which is a continuous ramp
 * from one hue to the next across the whole window — the masses overlap and
 * blend, and the dark only reaches in at the frame's edge. A steeper falloff
 * here leaves most of the surface to the ground colour, and the field reads as
 * a few lights in a dark room rather than as one gradient.
 */
private val MASS_FALLOFF = listOf(
    0f to 1.0f,
    0.3f to 0.85f,
    0.55f to 0.60f,
    0.75f to 0.32f,
    0.9f to 0.08f,
    1f to 0f,
)

/** Relative drift speed of each mass; no two move in step. */
private val MASS_SPEEDS = listOf(1f, -0.72f, 0.86f, -1.1f, 0.55f, -0.93f, 0.7f)

/**
 * How far a mass wanders from the artwork region it came from, in sleeve
 * fractions. Wide enough to read as movement at a glance: the masses are placed
 * where the sleeve put them, so a travel this small leaves the field looking
 * frozen next to Apple's, which is visibly travelling the whole time it plays.
 */
private const val DRIFT_TRAVEL = 0.16f

/** Where the stock six sit, for a palette that was never read off an artwork. */
private val LEGACY_ANCHORS = listOf(
    Offset(0.18f, 0.22f),
    Offset(0.82f, 0.16f),
    Offset(0.50f, 0.50f),
    Offset(0.86f, 0.78f),
    Offset(0.16f, 0.80f),
    Offset(0.52f, 0.04f),
)
private val LEGACY_SPEEDS = listOf(1f, -0.7f, 0.85f, -1.15f, 0.6f, -0.9f)

/** The sleeve is averaged as 5 columns by 4 rows, and the masses are picked from those cells. */
private const val MASS_COLS = 5
private const val MASS_ROWS = 4

/** Two cells closer than this (in cells) would blur into one body rather than two. */
private const val MASS_MIN_SPACING = 1.2f

/** How far the wheel is divided when limiting how often one hue may be a mass. */
private const val HUE_SLICES = 6

/** Masses allowed per [HUE_SLICES] slice — the field's colour spread, capped per hue. */
private const val MASS_MAX_PER_HUE = 2

/**
 * The field under the masses: the masses' own hue, drained.
 *
 * It used to sit at 6% lightness because that is where the reference's darkest
 * 2% of pixels are, and it was the wrong thing to optimise for: masses drift, so
 * whatever the ground is, that is what a corner becomes every time the masses
 * travel away from it, and at 6% the player spent a good part of every song with
 * a black frame round it. A ground this side of dead black keeps the gaps reading
 * as the deep part of one colour instead.
 */
private const val GROUND_SATURATION = 0.58f
private const val GROUND_LIGHTNESS = 0.10f

/**
 * The texture the layer stack is drawn from: the sleeve averaged to
 * [VIVID_PX] square and pushed into the band the field is made of.
 *
 * Averaging is what makes it a *field* rather than a picture — the copies have to
 * survive being stretched across a window and read as colour, not as a blurred
 * thumbnail. The size is a compromise in both directions: a coarse grid averages
 * enough that each cell becomes a plateau tens of pixels wide once stretched, and
 * the blur leaves visible contour rings where the ramps between them meet, which
 * is the topographic-map look the first pass of this shipped. Averaging a block of
 * pixels also greys it, which is why the saturation lift is here rather than at the
 * call site: these copies are the one part of the backdrop that is a *picture*, and
 * a grey picture under a blur is the flat wash this backdrop used to be.
 */
private fun ImageBitmap.vividLayers(): ImageBitmap? {
    val width = width
    val height = height
    if (width < 2 || height < 2) return null
    val source = argbPixels()
    val out = IntArray(VIVID_PX * VIVID_PX)
    for (cellY in 0 until VIVID_PX) {
        val y0 = cellY * height / VIVID_PX
        val y1 = ((cellY + 1) * height / VIVID_PX).coerceAtLeast(y0 + 1).coerceAtMost(height)
        for (cellX in 0 until VIVID_PX) {
            val x0 = cellX * width / VIVID_PX
            val x1 = ((cellX + 1) * width / VIVID_PX).coerceAtLeast(x0 + 1).coerceAtMost(width)
            var red = 0L
            var green = 0L
            var blue = 0L
            var counted = 0L
            for (y in y0 until y1) {
                val row = y * width
                for (x in x0 until x1) {
                    val argb = source[row + x]
                    red += argb shr 16 and 0xFF
                    green += argb shr 8 and 0xFF
                    blue += argb and 0xFF
                    counted++
                }
            }
            out[cellY * VIVID_PX + cellX] = (0xFF shl 24) or
                ((red / counted).toInt() shl 16) or
                ((green / counted).toInt() shl 8) or
                (blue / counted).toInt()
        }
    }
    out.indices.forEach { index -> out[index] = out[index].oversaturated() }
    return imageBitmapOf(out, VIVID_PX, VIVID_PX)
}

/** How far the stack's texture is averaged down. 64KB, and blurred anyway. */
private const val VIVID_PX = 128

/**
 * A cell average lifted into the band the field is made of, leaving its hue
 * alone unless there was a hue to leave.
 *
 * Near-grey cells keep their greyness, and the floor for "near-grey" has to be
 * well above what a cover's own neutrals sit at: a cream veil or a washed-out sky
 * has a hue only in the sense that compression favoured one channel, and lifting
 * it turned this backdrop's first pass into a green-and-yellow rainbow over a
 * sleeve that has neither colour in it. Forcing a floor on those cells is how a
 * black-and-white sleeve used to come out maroon, only louder.
 *
 * The band is the exposure, and after it [VIVID_LUMA_MIN] takes over: the two
 * catch different cells, because lightness is not how bright a colour looks.
 */
private fun Int.oversaturated(): Int {
    val hsl = hslOf(this)
    if (hsl[1] > VIVID_CHROMA_MIN) hsl[1] = (hsl[1] * VIVID_SATURATION).coerceAtMost(1f)
    hsl[2] = (hsl[2] * VIVID_EXPOSURE).coerceIn(VIVID_LIGHT_MIN, VIVID_LIGHT_MAX)
    val chroma = hsl[1]
    var argb = ColorUtils.HSLToColor(hsl)
    if (argb.displayLuma() >= VIVID_LUMA_MIN) return argb

    // Two exact linearities make the lift arithmetic instead of a search: below
    // the kink at 50% lightness, HSL-to-RGB scales every channel with l, so luma
    // runs through the origin in l; and at fixed l, luma is linear in s between the
    // hue at full chroma and the grey at that lightness.
    if (hsl[2] < LUMA_KINK) {
        hsl[2] = (hsl[2] * VIVID_LUMA_MIN / argb.displayLuma()).coerceAtMost(LUMA_KINK)
        argb = ColorUtils.HSLToColor(hsl)
    }
    if (argb.displayLuma() >= VIVID_LUMA_MIN) return argb

    val grey = ColorUtils.HSLToColor(hsl.copyOf().apply { this[1] = 0f }).displayLuma()
    hsl[1] = chroma * ((VIVID_LUMA_MIN - grey) / (argb.displayLuma() - grey)).coerceIn(0f, 1f)
    return ColorUtils.HSLToColor(hsl)
}

/** Where HSL's chroma term stops growing, and the first linearity with it. */
private const val LUMA_KINK = 0.5f

/**
 * Rec.709 over the *encoded* channels, so the same 0..1 units a screenshot is
 * measured in — deliberately not [ColorUtils.calculateLuminance], which linearises
 * first and so neither matches what the eye sees nor stays linear in HSL.
 */
private fun Int.displayLuma(): Float =
    ((this shr 16 and 0xFF) * 0.2126f + (this shr 8 and 0xFF) * 0.7152f +
        (this and 0xFF) * 0.0722f) / 255f

private const val VIVID_CHROMA_MIN = 0.16f
private const val VIVID_SATURATION = 1.18f
private const val VIVID_EXPOSURE = 1.0f

/**
 * Nothing in this field is allowed to be dark, and lightness was the wrong place
 * to enforce that: HSL's L is a channel position, not a brightness. Its floor sat
 * at 30% while the violet sleeve of the last build measured 18/255 of luma at the
 * same 30% — blue carries 7% of the signal, so a cell can obey the lightness floor
 * and still be near-black on screen, and one whole side of the window goes with it
 * when a copy's dark cells happen to travel there.
 *
 * So the floor is luma, and it is reached by lifting first and only draining where
 * lifting cannot get there: a blue at the top of the band needs its chroma down to
 * 0.6 and comes out the colour of a lit periwinkle rather than a grey, which is
 * still the sleeve's own hue. Below this, nothing.
 */
private const val VIVID_LUMA_MIN = 0.243f
private const val VIVID_LIGHT_MIN = 0.30f
private const val VIVID_LIGHT_MAX = 0.55f

/**
 * The sleeve's own colour regions, as masses: where a cover has its violet, its
 * warm red and its shadow, blown up.
 *
 * Averaged over a coarse grid rather than taken from a quantiser, because a
 * mesh wants *places*, not the four most-populous colours — and a quantiser's
 * palette is the same list whatever the layout of the art is. Two cells of one
 * colour and one cell of another read as three masses; four greens read as
 * nothing. Hence the spacing rule and the score, which prefers chroma over
 * coverage and steers toward the middle of the exposure band.
 */
private fun massesOf(bitmap: ImageBitmap): List<MeshMass> {
    val width = bitmap.width
    val height = bitmap.height
    if (width <= 0 || height <= 0) return emptyList()
    val red = LongArray(MASS_COLS * MASS_ROWS)
    val green = LongArray(MASS_COLS * MASS_ROWS)
    val blue = LongArray(MASS_COLS * MASS_ROWS)
    val counted = LongArray(MASS_COLS * MASS_ROWS)
    bitmap.argbPixels().forEachIndexed { index, argb ->
        val col = (index % width) * MASS_COLS / width
        val row = (index / width) * MASS_ROWS / height
        val cell = row.coerceIn(0, MASS_ROWS - 1) * MASS_COLS + col.coerceIn(0, MASS_COLS - 1)
        red[cell] += argb shr 16 and 0xFF
        green[cell] += argb shr 8 and 0xFF
        blue[cell] += argb and 0xFF
        counted[cell]++
    }
    val cells = (red.indices).mapNotNull { cell ->
        val count = counted[cell]
        if (count == 0L) return@mapNotNull null
        val r = (red[cell] / count).toInt().coerceIn(0, 0xFF)
        val g = (green[cell] / count).toInt().coerceIn(0, 0xFF)
        val b = (blue[cell] / count).toInt().coerceIn(0, 0xFF)
        val average = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        MassCandidate(
            col = cell % MASS_COLS,
            row = cell / MASS_COLS,
            hue = hslOf(average)[0],
            color = Color(average).asMass(),
            score = massScore(hslOf(average)),
        )
    }
    // Greedy, best cell first: a fixed 7 would put a mass wherever the grid
    // happened to fall, including over a cell that is the same colour as its
    // neighbour. Two per third of the wheel, because a sleeve that is mostly
    // one colour is mostly one colour in the *artwork*; a mesh that repeats it
    // seven times is the orange wall this backdrop used to be.
    val picked = ArrayList<MassCandidate>(MESH_MASSES)
    val perHue = IntArray(HUE_SLICES)
    for (candidate in cells.sortedByDescending(MassCandidate::score)) {
        if (picked.size == MESH_MASSES) break
        val slice = (candidate.hue / (360f / HUE_SLICES)).toInt() % HUE_SLICES
        if (perHue[slice] >= MASS_MAX_PER_HUE) continue
        if (picked.any { (candidate spacingFrom it) < MASS_MIN_SPACING }) continue
        perHue[slice]++
        picked += candidate
    }
    return picked.map { mass ->
        MeshMass(mass.color, (mass.col + 0.5f) / MASS_COLS, (mass.row + 0.5f) / MASS_ROWS)
    }
}

private class MassCandidate(
    val col: Int,
    val row: Int,
    val hue: Float,
    val color: Color,
    val score: Float,
)

private infix fun MassCandidate.spacingFrom(other: MassCandidate): Float {
    val dx = (col - other.col).toFloat()
    val dy = (row - other.row).toFloat()
    return sqrt(dx * dx + dy * dy)
}

/**
 * Whether a cell deserves to be one of the seven.
 *
 * Chroma, because a mass made of near-grey paints nothing — the sleeve's own
 * highlight cells were what turned the old field to mud. And a pull toward the
 * middle of the exposure, because the pale cells and the black ones both only
 * exist to give the middle cells somewhere to sit.
 */
private fun massScore(cell: FloatArray): Float =
    cell[1].pow(0.7f) * (1f - abs(cell[2] - 0.42f) * 1.6f)

/**
 * A cell average lifted into the band the field is made of. Sampled from
 * Apple's own field for this cover, its masses sit between about 20% and 32%
 * lightness and 67% and 80% saturation — a lit colour, never a black one. The
 * saturation band is entered wider than that because 60dp of blur and the
 * ground underneath take chroma back out: measured at the same percentile, our
 * field was a third less saturated than Apple's at the top of its range. The
 * lightness floor matters as much as the ceiling: a sleeve's shadow cell
 * averaged to 5% lightness is not a mass, it is a hole in the field, and the
 * dark green of this cover's foliage sitting at the bottom of the band is what
 * made one whole side of the window go black.
 */
private fun Color.asMass(): Color {
    val hsl = hsl()
    hsl[1] = (hsl[1] * 3.0f).coerceIn(0.60f, 0.98f)
    hsl[2] = (0.04f + hsl[2]).coerceIn(0.19f, 0.33f)
    return Color(ColorUtils.HSLToColor(hsl))
}

/** How far out from the middle the frame is still left alone, as a fraction of the corner radius. */
private const val VIGNETTE_SHOULD = 0.78f

/**
 * Clear through the middle [VIGNETTE_SHOULD] of the frame, then [alpha] black at
 * a corner, and kept shallow. It used to be 0.52 reaching in from 0.62, which
 * bought the field a dark tail on the percentiles and a black frame on every
 * sleeve whose own masses stay out of the corners — a light cover with a small
 * subject is exactly that, and the window read as a vignette rather than as the
 * artwork's colour. Apple's edge columns sit at the same lightness as its
 * middle; whatever depth this paints in is ours, not the reference's.
 */
private fun vignette(alpha: Float): Array<Pair<Float, Color>> = arrayOf(
    0f to Color.Transparent,
    VIGNETTE_SHOULD to Color.Transparent,
    1f to Color.Black.copy(alpha = alpha),
)

/** The mean hue of the masses, drained — the ground they float on. */
private fun groundOf(masses: List<Color>): Color {
    var red = 0L
    var green = 0L
    var blue = 0L
    masses.forEach { mass ->
        val argb = mass.toArgb()
        red += argb shr 16 and 0xFF
        green += argb shr 8 and 0xFF
        blue += argb and 0xFF
    }
    val count = masses.size.coerceAtLeast(1)
    val average = (0xFF shl 24) or
        ((red / count).toInt() shl 16) or
        ((green / count).toInt() shl 8) or
        (blue / count).toInt()
    val hsl = hslOf(average)
    hsl[1] = GROUND_SATURATION
    hsl[2] = GROUND_LIGHTNESS
    return Color(ColorUtils.HSLToColor(hsl))
}

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
