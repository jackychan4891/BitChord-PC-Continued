package com.music.bitchord.ui.player

import com.music.bitchord.sharedui.resources.*

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import com.music.bitchord.ui.haptics.Haptic
import com.music.bitchord.ui.haptics.rememberHaptics
import com.music.bitchord.ui.icons.BitChordIcons
import com.music.bitchord.data.canvas.CanvasArtwork
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.data.model.Song

/**
 * The widest the two columns are allowed to get between them, centred in
 * whatever is left over.
 *
 * Only the phone-height layout uses it. Without a cap the lyric column simply
 * takes every pixel past the artwork, and on a wide window that is a column of
 * text with a hand's width of empty backdrop trailing off the right of every
 * line. Lyrics are read down, not across: past a certain measure the extra
 * width is further for the eye to travel back rather than more room for the
 * words.
 */
private val LANDSCAPE_PLAYER_MAX_WIDTH = 1100.dp

/**
 * Below this height the landscape player tightens its right column — smaller
 * transport, shorter gaps — so a phone on its side fits the credits, the
 * scrubber, the transport and the volume bar between its status and
 * navigation bars without any of them falling off the bottom.
 */
private val LANDSCAPE_COMPACT_HEIGHT = 440.dp

/**
 * Side margin of the landscape player's sleeve column and credits on a
 * phone-height window, in place of [PLAYER_GUTTER].
 */
private val LANDSCAPE_GUTTER_COMPACT = 20.dp

/**
 * How the right column hands over between the player, the lyrics and the
 * queue. The left column never moves, so a crossfade is the whole transition —
 * the same page being turned, not a panel being opened.
 */
private const val LANDSCAPE_PANE_FADE_IN_MS = 220
private const val LANDSCAPE_PANE_FADE_IN_DELAY_MS = 90
private const val LANDSCAPE_PANE_FADE_OUT_MS = 140

/** Which of its three things the player's content column is showing. */
internal enum class PlayerPane { Main, Lyrics, Queue }

/** The landscape sleeve's corner radius, at rest. */
internal val LANDSCAPE_ART_CORNER = 10.dp

/** The landscape sleeve's shape at rest, for a caller with no docking to do. */
private val LandscapeArtShape = RoundedCornerShape(LANDSCAPE_ART_CORNER)

/** Room above the landscape columns for the sheet's drag handle. */
private val LANDSCAPE_HANDLE_STRIP = 24.dp

/**
 * How far the desktop lyric sheet is lifted off the foot of its column.
 *
 * The sleeve now rests a little above the middle of the window, so the words are
 * raised by roughly the same amount to sit level with the middle of the cover
 * rather than the middle of the screen. It is applied to the scroll viewport
 * alone — see [LandscapeLyricsPane]'s [bottomInset][LandscapeLyricsPane] — so
 * the attribution bar under the sheet stays pinned to the foot, level with the
 * bottom of the transport in the other column.
 */
internal val LANDSCAPE_LYRICS_BOTTOM_LIFT = 240.dp

// ---- The desktop player's measures, taken from Apple Music's web player ----

/** `--art: min(32vw, …)`: the sleeve is just under a third of the window wide. */
private const val LANDSCAPE_ART_SHARE = 0.32f

/** `… calc(100dvh - 330px)`: everything the sleeve leaves for the controls under it. */
private val LANDSCAPE_ART_CONTROLS_RESERVE = 330.dp

/** `… 720px)`: the sleeve stops growing there, however big the monitor. */
private val LANDSCAPE_ART_MAX = 720.dp

/** Never so small that a short window squeezes the cover into a thumbnail. */
private val LANDSCAPE_ART_MIN = 200.dp

/** `column-gap: clamp(48px, 7vw, 144px)` between the sleeve and the words. */
private const val LANDSCAPE_GAP_SHARE = 0.07f
private val LANDSCAPE_GAP_MIN = 48.dp
private val LANDSCAPE_GAP_MAX = 144.dp

/** `padding: 64px clamp(24px, 9vw, 144px)` around the two columns. */
private val LANDSCAPE_SIDE_SHARE = 0.09f
private val LANDSCAPE_SIDE_MIN = 24.dp
private val LANDSCAPE_SIDE_MAX = 144.dp
private val LANDSCAPE_VERTICAL_PADDING = 64.dp

/** `minmax(0, 38vw)`: the lyric column's measure, and the queue's beside it. */
private const val LANDSCAPE_SIDE_COLUMN_SHARE = 0.38f

/** The chrome buttons: 32dp discs 16dp from the top edge and 20dp from the sides. */
private val LANDSCAPE_CHROME_DISC = 32.dp
private val LANDSCAPE_CHROME_GLYPH = 18.dp
private val LANDSCAPE_CHROME_TOP = 16.dp
private val LANDSCAPE_CHROME_SIDE = 20.dp
private const val LANDSCAPE_CHROME_MS = 100

/** The transport row: `min(100%, 390px)` across, five buttons apart. */
private val LANDSCAPE_TRANSPORT_WIDTH = 390.dp
private val LANDSCAPE_TRANSPORT_HEIGHT = 80.dp

/** The credits block under the sleeve: 12 between its rows, 14 over the scrubber. */
private val LANDSCAPE_CREDITS_GAP = 12.dp
private val LANDSCAPE_SCRUBBER_GAP = 14.dp

/** `margin: clamp(8px, 1.6vh, 16px) auto 0` above the transport, at a typical window height. */
private val LANDSCAPE_TRANSPORT_GAP = 12.dp

/** The volume bar the web player has no room for and the screenshot does. */
private val LANDSCAPE_VOLUME_GAP = 14.dp

/** The lyric and queue panels' fade, both ways, as the web's `animation: .4s fade-in`. */
private const val LANDSCAPE_PANEL_FADE_MS = 400

/** Paused, the desktop sleeve settles to 92% of its square; 450ms on a gentle overshoot. */
internal val LANDSCAPE_ARTWORK_PAUSE_SCALE = 0.92f
internal const val LANDSCAPE_ARTWORK_SCALE_MS = 450
internal val LandscapeArtworkEasing = CubicBezierEasing(0.34f, 1.3f, 0.64f, 1f)

/**
 * The desktop player's column measures, for a window this size.
 *
 * Everything about the layout is a share of the window rather than a fixed
 * number — the sleeve, the gap between the columns, the margin around them and
 * the width of the words. That is how the web player does it, and it is what
 * lets one arrangement fill a laptop and a 4K display without either one being
 * an afterthought: the cover takes a third of whatever it has, and the words
 * take what a third of a very wide screen is allowed to be.
 */
private data class LandscapePlayerColumns(
    val art: Dp,
    val gap: Dp,
    val sidePadding: Dp,
    val sideColumnWidth: Dp,
)

private fun landscapePlayerColumns(width: Dp, height: Dp): LandscapePlayerColumns = LandscapePlayerColumns(
    art = minOf(width * LANDSCAPE_ART_SHARE, height - LANDSCAPE_ART_CONTROLS_RESERVE, LANDSCAPE_ART_MAX)
        .coerceAtLeast(LANDSCAPE_ART_MIN),
    gap = (width * LANDSCAPE_GAP_SHARE).coerceIn(LANDSCAPE_GAP_MIN, LANDSCAPE_GAP_MAX),
    sidePadding = (width * LANDSCAPE_SIDE_SHARE).coerceIn(LANDSCAPE_SIDE_MIN, LANDSCAPE_SIDE_MAX),
    sideColumnWidth = width * LANDSCAPE_SIDE_COLUMN_SHARE,
)

/**
 * The player in a landscape window — a desktop window, a tablet, or a phone on
 * its side. See [landscapePlayerAvailable].
 *
 * On a desktop window this is Apple Music's web player, measured off its own
 * stylesheet: the sleeve at a third of the width, the credits and the transport
 * directly under it in the same column, and the words in a second column that
 * exists only when the listener asks for it. Nothing is parked in a permanent
 * right-hand pane any more; the lyric sheet and the queue open and close from
 * the four corners, and with neither up the player is one column, centred.
 *
 * A phone-height window keeps the arrangement it has always had — two columns of
 * equal weight, the sleeve and the action row on the left, the transport, the
 * words or the queue turned over in the one on the right. There is no room for a
 * second column there, and corner buttons would sit under the thumb.
 *
 * Purely the arrangement. Every slot is built by [NowPlayingScreen], which is
 * where the state behind all of them lives — the same state the portrait
 * player reads, so rotating mid-song carries the open panel, the scrub and the
 * translation mode straight across.
 *
 * Held to [LANDSCAPE_PLAYER_MAX_WIDTH] and centred on a phone, inside the safe-drawing
 * insets: a phone on its side has its status bar, navigation bar and camera
 * cutout down the short edges, and the player's content keeps clear of all
 * three while its backdrop still runs underneath them.
 */
@Composable
internal fun LandscapePlayerLayout(
    pane: PlayerPane,
    background: @Composable (Modifier) -> Unit,
    /** The sleeve; handed a modifier that already fixes its square size, and whether the Automix
     * aura should be clipped to the sleeve's rounded shape (desktop) rather than its square bounds. */
    artwork: @Composable (Modifier, glowRounded: Boolean) -> Unit,
    /** The row under the sleeve, emitted into its column. */
    actions: @Composable () -> Unit,
    /**
     * Credits, scrubber, transport and volume. On a desktop window this sits
     * directly under the sleeve, as wide as the sleeve, and is always visible;
     * [paneWidth] carries that width. On a phone-height window it is the right
     * column's main pane instead, exactly as before, and [paneWidth] is null.
     */
    controls: @Composable (compact: Boolean, paneWidth: Dp?) -> Unit,
    /** The lyric sheet. [compact] is a phone-height window. */
    lyricsPane: @Composable (compact: Boolean) -> Unit,
    queuePane: @Composable () -> Unit,
    /**
     * The corner button, as the web player has it: out of the player.
     * A host with no window to close hands in nothing and draws nothing.
     */
    onDismiss: (() -> Unit)? = null,
    /**
     * The top-right glyph, which the desktop window supplies and a phone leaves
     * null: full screen. It is the corner treatment the dismiss button already
     * uses, so it reads as part of the window rather than as another row of
     * controls.
     */
    onToggleFullScreen: (() -> Unit)? = null,
    fullScreen: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        background(Modifier.fillMaxSize())

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
            contentAlignment = Alignment.TopCenter,
        ) {
            val compact = maxHeight < LANDSCAPE_COMPACT_HEIGHT
            val gutter = if (compact) LANDSCAPE_GUTTER_COMPACT else PLAYER_GUTTER

            if (compact) {
                Row(
                    modifier = Modifier
                        .widthIn(max = LANDSCAPE_PLAYER_MAX_WIDTH)
                        .fillMaxSize()
                        .padding(
                            top = LANDSCAPE_HANDLE_STRIP,
                            bottom = 8.dp,
                        ),
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(horizontal = gutter),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Top,
                    ) {
                        // The sleeve is square and takes whichever of the two
                        // axes runs out first once the row below has had its
                        // height — the column's width on a big tablet, the
                        // height on anything shorter. Measured here rather than
                        // estimated, so the row under it can never be pushed off
                        // the bottom.
                        BoxWithConstraints(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            artwork(Modifier.size(minOf(maxWidth, maxHeight)), false)
                        }
                        Spacer(Modifier.height(12.dp))
                        actions()
                    }

                    AnimatedContent(
                        targetState = pane,
                        transitionSpec = {
                            fadeIn(
                                tween(
                                    LANDSCAPE_PANE_FADE_IN_MS,
                                    delayMillis = LANDSCAPE_PANE_FADE_IN_DELAY_MS,
                                ),
                            ) togetherWith fadeOut(tween(LANDSCAPE_PANE_FADE_OUT_MS))
                        },
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        label = "landscapePane",
                    ) { paneShown ->
                        when (paneShown) {
                            PlayerPane.Main -> Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = gutter),
                                contentAlignment = Alignment.Center,
                            ) {
                                controls(true, null)
                            }
                            // The lyric sheet and the queue both reach back across
                            // [PLAYER_GUTTER] on their own (see
                            // [bleedHorizontally]), so that is the padding they
                            // get: their scroll areas then run exactly to the
                            // column's edges.
                            PlayerPane.Lyrics -> Box(
                                Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = PLAYER_GUTTER),
                            ) {
                                lyricsPane(true)
                            }
                            PlayerPane.Queue -> Box(
                                Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = PLAYER_GUTTER),
                            ) {
                                queuePane()
                            }
                        }
                    }
                }
            } else {
                val columns = landscapePlayerColumns(maxWidth, maxHeight)
                // The web's `margin-top: clamp(20px, 3.2vh, 32px)` under the sleeve.
                // Read here, outside the row: the column scope has no constraints of
                // its own to ask.
                val sleeveGap = (maxHeight * 0.032f).coerceIn(20.dp, 32.dp)

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            top = LANDSCAPE_VERTICAL_PADDING,
                            bottom = LANDSCAPE_VERTICAL_PADDING,
                            start = columns.sidePadding,
                            end = columns.sidePadding,
                        ),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Pinned to the sleeve's own width. Left unbounded, the block
                    // under the cover asks for the whole row — its bars do run edge
                    // to edge, which is what the web's do under the cover — and the
                    // side column is left a sliver wide enough for one letter a line.
                    Column(
                        modifier = Modifier.width(columns.art),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        artwork(Modifier.size(columns.art), true)
                        // The credits follow the cover's own scale rather than a
                        // fixed number of lines.
                        Spacer(Modifier.height(sleeveGap))
                        controls(false, columns.art)
                    }

                    AnimatedVisibility(
                        visible = pane != PlayerPane.Main,
                        enter = fadeIn(tween(LANDSCAPE_PANEL_FADE_MS)),
                        exit = fadeOut(tween(LANDSCAPE_PANEL_FADE_MS)),
                    ) {
                        Box(
                            modifier = Modifier
                                .width(columns.sideColumnWidth)
                                .fillMaxHeight()
                                .padding(start = columns.gap),
                        ) {
                            // Going from the words to the queue, or back, is a
                            // page being turned inside a panel that stayed put.
                            Crossfade(
                                targetState = pane,
                                animationSpec = tween(LANDSCAPE_PANE_FADE_IN_MS),
                                label = "landscapeSidePane",
                            ) { sidePane ->
                                if (sidePane == PlayerPane.Queue) queuePane() else lyricsPane(false)
                            }
                        }
                    }
                }
            }

            onDismiss?.let { dismiss ->
                LandscapeChromeButton(
                    icon = BitChordIcons.Close,
                    contentDescription = stringResource(Res.string.close_player),
                    onClick = dismiss,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = LANDSCAPE_CHROME_SIDE, y = LANDSCAPE_CHROME_TOP),
                )
            }

            // The window's own, opposite it: the screen.
            val raiseFullScreen = onToggleFullScreen
            if (raiseFullScreen != null) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = -LANDSCAPE_CHROME_SIDE, y = LANDSCAPE_CHROME_TOP),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    raiseFullScreen?.let { toggle ->
                        LandscapeChromeButton(
                            icon = if (fullScreen) BitChordIcons.Minimize else BitChordIcons.Maximize,
                            contentDescription = stringResource(
                                if (fullScreen) Res.string.exit_full_screen else Res.string.full_screen,
                            ),
                            onClick = toggle,
                            selected = fullScreen,
                        )
                    }
                }
            }

            // The sheet closes from a downward drag anywhere nothing else
            // claims, as it does in portrait; this is only the promise.
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = 8.dp)
                    .width(38.dp)
                    .height(5.dp)
                    .shadow(2.dp, RoundedCornerShape(3.dp), clip = false)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White.copy(alpha = 0.70f)),
            )
        }
    }
}

/**
 * One of the player's four corner buttons.
 *
 * The web's own treatment, which the rest of the player's glyphs are not: a
 * plain icon at two-thirds white that lights to full white over the faintest
 * disc when the cursor reaches it, and gives a little as it is pressed. It sits
 * over the backdrop rather than on it, so it has no border and no plate of its
 * own until the pointer asks for one.
 */
@Composable
private fun LandscapeChromeButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    haptic: Haptic = Haptic.Tap,
) {
    val haptics = rememberHaptics()
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val lit by animateFloatAsState(
        targetValue = if (hovered || pressed || selected) 1f else 0f,
        animationSpec = tween(LANDSCAPE_CHROME_MS),
        label = "chromeHover",
    )
    val squeeze by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = tween(LANDSCAPE_CHROME_MS),
        label = "chromePress",
    )
    Box(
        modifier = modifier
            .size(LANDSCAPE_CHROME_DISC)
            .graphicsLayer {
                scaleX = squeeze
                scaleY = squeeze
            }
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.10f * lit))
            .hoverable(interaction)
            .clickable(
                interactionSource = interaction,
                indication = null,
            ) {
                haptics.play(haptic)
                onClick()
            }
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            // `aria-pressed` on the web is the same full-white the pointer
            // gets: a chosen panel is a lit button, not a coloured one.
            tint = lerp(Color.White.copy(alpha = 0.64f), Color.White, lit),
            modifier = Modifier.size(LANDSCAPE_CHROME_GLYPH),
        )
    }
}

/** The aura only dips to two-thirds between beats: a bloom that vanished entirely read as a flicker. */
private const val GLOW_REST = 0.66f

/** The aura's peak opacity. Full-strength white over the backdrop read as a blown-out halo. */
private const val GLOW_PEAK = 0.5f

/** Aura radius as a multiple of the sleeve's shorter side, so it reaches past the corners. */
private const val GLOW_RADIUS = 0.95f

/** One turn of the deck's record. Slow enough to read as a motor rather than a spinner. */
private const val DECK_TURN_MILLIS = 4_800

/**
 * The landscape player's sleeve: the still cover, and over it the looping
 * clip when there is one.
 *
 * Draws from the screen's own [request][PlayerArtwork.request] rather than a
 * request of its own, so it shares the portrait sleeve's bitmap, its retries
 * and its [loaded][PlayerArtwork.loaded] — rotating never reloads the cover.
 *
 * The clip keeps playing whichever pane is up. The portrait player stops it
 * because its sleeve shrinks to a thumbnail behind a panel; this one never
 * shrinks.
 */
@Composable
internal fun LandscapeArtwork(
    artRequest: ImageRequest,
    artLoaded: Boolean,
    onArtState: (AsyncImagePainter.State) -> Unit,
    canvas: CanvasArtwork?,
    canvasRendered: Boolean,
    isPlaying: Boolean,
    onCanvasRenderedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    /** Halts the clip for a transition — see [CanvasArtworkPlayer]'s own. */
    pausedForTransition: Boolean = false,
    /**
     * The sleeve's shape, asked for each frame: its corner rounds off into the
     * mini player's cover's as the player docks into it.
     */
    sleeveShape: () -> Shape = { LandscapeArtShape },
    /** How much of the sleeve's shadow is cast, 1 at rest. */
    shadowFraction: () -> Float = { 1f },
    /**
     * The colour of the aura that blooms around the sleeve while an Automix blend runs. Tint it
     * with the artwork's own palette where one is at hand; it defaults to a neutral light.
     */
    glowColor: Color = Color.White,
    /**
     * Clip the Automix aura to the sleeve's rounded shape rather than its square bounds. The
     * desktop player sets this so the white bloom that pulses during a blend reads as a rounded
     * halo, not a square flash; the phone-landscape player leaves it off and is unchanged.
     */
    glowRounded: Boolean = false,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val casts = artLoaded || canvasRendered
    // The same beat clock the scrubber and the "Mixing" label breathe on, so the aura pulses in
    // step with them. Held as a State and read only inside the draw below: the blend republishes
    // every fade tick, and collecting it by value here would recompose the sleeve for a change
    // only the canvas needs.
    val mixBlend = PlayerSettings.smartMixBlend.collectAsStateWithLifecycle()
    val reduceAnimation by PlayerSettings.reduceAnimation.collectAsStateWithLifecycle()
    val glowPulse = rememberMixPulse({ mixBlend.value }, enabled = !reduceAnimation)
    val turntable by PlayerSettings.turntableArt.collectAsStateWithLifecycle()
    // A record is driven by a motor rather than pushed by a finger: it comes up to speed once and
    // stops where it is when the music does, instead of winding down.
    val deckTurn = remember { Animatable(0f) }
    LaunchedEffect(turntable, isPlaying, reduceAnimation) {
        if (turntable && isPlaying && !reduceAnimation) {
            while (true) {
                deckTurn.animateTo(360f, tween(DECK_TURN_MILLIS, easing = LinearEasing))
                deckTurn.snapTo(0f)
            }
        }
    }
    // The deck is laid down only for a sleeve at rest: on its way into the mini player the cover
    // keeps the square-ish shape the bar is coming to.
    fun onDeck() = turntable && shadowFraction() > 0.99f
    fun deckShape(): Shape = if (onDeck()) CircleShape else sleeveShape()
    Box(
        modifier = modifier
            // Behind the sleeve, bleeding past its edges: a soft bloom that fades in with the
            // blend and beats with it. Read in the draw, so the pulse repaints the aura every
            // frame without recomposing the sleeve.
            .drawBehind {
                val cover = glowPulse.cover
                if (cover <= 0f) return@drawBehind
                val alpha = cover * glowPulse.alpha(1f, restShare = GLOW_REST) * GLOW_PEAK
                if (alpha <= 0f) return@drawBehind
                val radius = size.minDimension * GLOW_RADIUS
                val brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0.42f to Color.Transparent,
                        0.66f to glowColor.copy(alpha = alpha),
                        1f to Color.Transparent,
                    ),
                    center = center,
                    radius = radius,
                )
                if (glowRounded) {
                    val outline = deckShape().createOutline(size, layoutDirection, this)
                    clipPath(Path().apply { addOutline(outline) }) {
                        drawRect(brush = brush)
                    }
                } else {
                    drawRect(brush = brush)
                }
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // What `.shadow` and then `.clip` laid down, asked for in the
                // layers' own blocks so a docking player can move them
                // without recomposing the sleeve.
                //
                // Only cast once there is a picture: on the flat placeholder
                // tile a shadow reads as a second, darker square.
                .graphicsLayer {
                    shadowElevation = if (casts) 14.dp.toPx() * shadowFraction() else 0f
                    shape = deckShape()
                    // As `.shadow` did: it clips only when it casts.
                    clip = casts
                }
                .graphicsLayer {
                    shape = deckShape()
                    clip = true
                    // The turn belongs to the layer inside the one that casts, so the record
                    // spins under a shadow that sits still. A clip is applied in a layer's own
                    // pixels, so the disc's edge never wanders as it goes round.
                    rotationZ = if (onDeck()) deckTurn.value else 0f
                }
                .background(Color.Black.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            if (!artLoaded && !canvasRendered) {
                Icon(
                    imageVector = BitChordIcons.MusicNote,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxSize(0.36f),
                )
            }
            AsyncImage(
                model = artRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onState = onArtState,
                // Not drawn under a clip that got there first — see the
                // portrait sleeve's note on TextureView stacking order.
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent { if (artLoaded || !canvasRendered) drawContent() },
            )
            canvas?.let { clip ->
                CanvasArtworkPlayer(
                    canvas = clip,
                    isPlaying = isPlaying,
                    pausedForTransition = pausedForTransition,
                    onRenderedChanged = onCanvasRenderedChange,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        // What does not turn — the grooves, the rim and the arm — laid over the artwork the
        // sleeve spins. Under the sheet, which belongs on top of the player rather than under a deck.
        if (onDeck()) TurntableDeck(isPlaying, reduceAnimation)
        overlay()
    }
}

/**
 * The block under the desktop sleeve, or the landscape player's right column in
 * its main state: where the session came from, the credits, the current line,
 * the scrubber, the transport and the volume bar — the portrait half-player,
 * less the row the left column already has.
 *
 * On a desktop window [paneWidth] is the sleeve's own width, because that is how
 * the web player sizes it: the credits run edge to edge under the cover, the
 * transport is the one thing that stops short and sits centred in the middle.
 * Centred in the column rather than pinned to its foot: there is no artwork
 * above it here to hand the spare height to.
 */
@Composable
internal fun LandscapeMainPane(
    compact: Boolean,
    /** "Playing from …", or null where Settings hides it. */
    caption: String?,
    onOpenCaption: () -> Unit,
    credits: @Composable () -> Unit,
    /** The one-line lyric over the scrubber; null where synced lyrics are off. */
    lyricStrip: (@Composable () -> Unit)?,
    scrubber: @Composable () -> Unit,
    transport: @Composable () -> Unit,
    /** Null where Settings hides the volume bar. */
    volume: (@Composable () -> Unit)?,
    /** The desktop's block, as wide as the sleeve over it; null caps a phone's. */
    paneWidth: Dp? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .then(if (paneWidth == null) Modifier.widthIn(max = PLAYER_MAX_WIDTH) else Modifier)
            .fillMaxWidth(),
    ) {
        if (caption != null) {
            PlaybackOriginCaption(
                text = caption,
                onClick = onOpenCaption,
                textAlign = TextAlign.Start,
                contentPadding = PaddingValues(vertical = 2.dp),
            )
            Spacer(Modifier.height(if (compact) 4.dp else 8.dp))
        }
        credits()
        Spacer(Modifier.height(if (compact) 2.dp else LANDSCAPE_SCRUBBER_GAP))
        lyricStrip?.invoke()
        scrubber()
        Spacer(Modifier.height(if (compact) 0.dp else LANDSCAPE_TRANSPORT_GAP))
        transport()
        if (volume != null) {
            Spacer(Modifier.height(if (compact) 0.dp else LANDSCAPE_VOLUME_GAP))
            volume()
        }
    }
}

/**
 * Title, artist, like and the three-dot menu, for the landscape player.
 *
 * Marquee rather than a plain ellipsis, matching the portrait player: a tablet
 * is wider, not infinitely wide, and a long title truncated on the one surface
 * with room to scroll it would be the odd one out.
 *
 * Set in the web player's own hand: the title at 17 with weight behind it, the
 * line under it a shade lighter at 15, and both given the full width of the
 * sleeve above them rather than the narrower measure the Material styles were
 * sized for.
 */
@Composable
internal fun LandscapeCredits(
    song: Song,
    signedIn: Boolean,
    likeStatus: LikeStatus,
    /** The menu glyph is briefly the undo arrow after a quality upgrade. */
    showRevertCue: Boolean,
    onToggleLike: () -> Unit,
    /**
     * The song menu, or null on a host that raises it some other way. A phone,
     * and a window too narrow for this shape, keep the dots.
     */
    onOpenMenu: (() -> Unit)?,
    onOpenAlbum: (String) -> Unit,
    /** Null browse id for anyone but the lead credit — see [NowPlayingScreen]. */
    onOpenArtist: (browseId: String?, name: String) -> Unit,
    /** The web's 32dp discs; a phone-height window keeps the set's own 34. */
    discSize: Dp = 34.dp,
    /**
     * Where the menu glyph ends up, in the window's own coordinates. A desktop
     * host draws the song menu outside this screen and needs to put it exactly
     * over the three dots that opened it — the sleeve's width, and so the row's
     * height above the middle of the window, both change with the window.
     */
    onMenuBounds: ((Rect) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    // The colour the quality badge next door wears, so the credits and the two
    // corner discs read as part of the sleeve rather than as white on top of it.
    val artworkSyncColor = rememberArtworkSyncColor(song.thumbnailUrl)
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Crossfade(
            targetState = song.title to song.artist,
            animationSpec = tween(durationMillis = 300),
            modifier = Modifier.weight(1f),
            label = "landscapeCredits",
        ) {
            Column {
                var titleOverflowing by remember { mutableStateOf(false) }
                MarqueeText(
                    text = song.title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 17.sp,
                        lineHeight = 22.sp,
                        fontWeight = FontWeight.W600,
                    ),
                    color = Color.White,
                    onOverflowChange = { titleOverflowing = it },
                    leading = if (song.isExplicit == true) {
                        { ExplicitBadge(Color.White) }
                    } else {
                        null
                    },
                    modifier = Modifier.opensPage(song.albumId, onOpenAlbum),
                )
                Spacer(Modifier.height(2.dp))
                ArtistCreditsMarquee(
                    text = song.artist,
                    credits = remember(song.artist, song.artists, song.artistId) {
                        artistCredits(song.artist, song.artists, song.artistId)
                    },
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 15.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.W400,
                    ),
                    // `--systemSecondary`, which the web player's subtitle is
                    // two-thirds white — here the sleeve's own colour at that
                    // same strength, so it matches the quality badge.
                    color = artworkSyncColor.copy(alpha = 0.64f),
                    // A title that's also scrolling gets to go first — starting
                    // together reads as clutter, so the artist waits a beat.
                    startDelayMillis = if (titleOverflowing) MARQUEE_ARTIST_STAGGER_MS else 0L,
                    onOpenArtist = onOpenArtist,
                )
            }
        }
        Spacer(Modifier.width(LANDSCAPE_CREDITS_GAP))
        // Same gate as the portrait player's: no account to like against for a
        // guest, and no YouTube identity to rate a local file or a finished
        // download against either.
        if (signedIn && song.localUri == null) {
            val liked = likeStatus == LikeStatus.LIKE
            CircleGlyph(
                icon = if (liked) BitChordIcons.StarFilled else BitChordIcons.Star,
                contentDescription = stringResource(
                    if (liked) Res.string.remove_from_liked else Res.string.like,
                ),
                onClick = onToggleLike,
                active = liked,
                diameter = discSize,
                tint = artworkSyncColor,
                haptic = if (liked) Haptic.ToggleOff else Haptic.ToggleOn,
            )
            Spacer(Modifier.width(8.dp))
        }
        if (onOpenMenu != null) {
            Box(
                Modifier.onGloballyPositioned { coordinates ->
                    onMenuBounds?.invoke(coordinates.boundsInRoot())
                },
            ) {
                CircleGlyph(
                    icon = if (showRevertCue) Icons.AutoMirrored.Rounded.Undo else BitChordIcons.Ellipsis,
                    contentDescription = stringResource(Res.string.more),
                    onClick = onOpenMenu,
                    diameter = discSize,
                    tint = artworkSyncColor,
                )
            }
        }
    }
}

/**
 * The transport as Apple Music's web player lays it: five buttons in a row no
 * wider than 315dp, spread the full width of it rather than bunched around the
 * play control. Shuffle and repeat are reachable at the ends, where they are
 * decided once per session, and the three that move the music are in the middle.
 *
 * The two mode buttons are the dimmest thing in the player until they are set —
 * two-fifths white off, full white on — which is the web's way of saying the
 * same thing the filled star says about a like: state is a change of weight, not
 * of colour, because there is no colour on this page.
 */
@Composable
internal fun LandscapeTransportRow(
    isPlaying: Boolean,
    /** While the stream resolves and buffers, the play glyph would be a lie. */
    isLoading: Boolean,
    previousEnabled: Boolean,
    nextEnabled: Boolean,
    shuffleEnabled: Boolean,
    repeatMode: Int,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = LANDSCAPE_TRANSPORT_WIDTH)
                .fillMaxWidth()
                .height(LANDSCAPE_TRANSPORT_HEIGHT),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TransportButton(
                icon = BitChordIcons.TransportShuffle,
                contentDescription = stringResource(
                    if (shuffleEnabled) Res.string.shuffle_on else Res.string.shuffle_off,
                ),
                onClick = onToggleShuffle,
                selected = shuffleEnabled,
                size = LANDSCAPE_TOGGLE_TARGET,
                glyphSize = LANDSCAPE_TOGGLE_GLYPH,
                haptic = if (shuffleEnabled) Haptic.ToggleOff else Haptic.ToggleOn,
            )
            TransportButton(
                icon = BitChordIcons.TransportPrevious,
                contentDescription = stringResource(Res.string.widget_previous),
                onClick = onPrevious,
                size = LANDSCAPE_SKIP_TARGET,
                glyphSize = LANDSCAPE_SKIP_GLYPH,
                enabled = previousEnabled,
                haptic = Haptic.SkipPrevious,
            )
            if (isLoading) {
                // The play button's own footprint, so the row does not shift on
                // every resolve and buffer.
                Box(Modifier.size(LANDSCAPE_PLAY_TARGET), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(42.dp),
                    )
                }
            } else {
                TransportButton(
                    icon = if (isPlaying) BitChordIcons.TransportPause else BitChordIcons.TransportPlay,
                    contentDescription = stringResource(if (isPlaying) Res.string.pause else Res.string.play),
                    onClick = onPlayPause,
                    size = LANDSCAPE_PLAY_TARGET,
                    glyphSize = LANDSCAPE_PLAY_GLYPH,
                    haptic = if (isPlaying) Haptic.Pause else Haptic.Resume,
                )
            }
            TransportButton(
                icon = BitChordIcons.TransportNext,
                contentDescription = stringResource(Res.string.widget_next),
                onClick = onNext,
                size = LANDSCAPE_SKIP_TARGET,
                glyphSize = LANDSCAPE_SKIP_GLYPH,
                enabled = nextEnabled,
                haptic = Haptic.SkipNext,
            )
            TransportButton(
                icon = if (repeatMode == RepeatModes.ONE) {
                    BitChordIcons.TransportRepeatOne
                } else {
                    BitChordIcons.TransportRepeat
                },
                contentDescription = stringResource(
                    when (repeatMode) {
                        RepeatModes.ONE -> Res.string.repeat_one
                        RepeatModes.ALL -> Res.string.repeat_all
                        else -> Res.string.repeat_off
                    },
                ),
                onClick = onCycleRepeat,
                selected = repeatMode != RepeatModes.OFF,
                size = LANDSCAPE_TOGGLE_TARGET,
                glyphSize = LANDSCAPE_TOGGLE_GLYPH,
                haptic = Haptic.ToggleOn,
            )
        }
    }
}

/**
 * One transport button: the glyph, the disc that comes up under the cursor, and
 * the little give the web's `:active` has.
 */
@Composable
private fun TransportButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    size: Dp,
    glyphSize: Dp,
    /** A mode that is set is full white; one that is unset is the dimmest thing here. */
    selected: Boolean? = null,
    enabled: Boolean = true,
    haptic: Haptic = Haptic.Tap,
) {
    val haptics = rememberHaptics()
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val lit by animateFloatAsState(
        targetValue = if (enabled && (hovered || pressed)) 1f else 0f,
        animationSpec = tween(LANDSCAPE_CHROME_MS),
        label = "transportHover",
    )
    val squeeze by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = tween(LANDSCAPE_CHROME_MS),
        label = "transportPress",
    )
    // Everything on this row is full white when it is lit and when it is set; a
    // mode that is off is the only thing dimmer than that, and the pointer still
    // lifts it to full white the moment it arrives.
    val dim = if (selected == false) Color.White.copy(alpha = 0.4f) else Color.White
    Box(
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                scaleX = squeeze
                scaleY = squeeze
            }
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.10f * lit))
            .hoverable(interaction)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
            ) {
                haptics.play(haptic)
                onClick()
            }
            // Faded rather than hidden, the way the old transport was: a queue
            // that ends keeps its shape.
            .alpha(if (enabled) 1f else 0.4f)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = lerp(dim, Color.White, lit),
            modifier = Modifier.size(glyphSize),
        )
    }
}

/**
 * The transport measures. The web's are 36 for the modes and 44 for the play,
 * which read small on a desktop window that is a metre wide: these are the ones
 * reached for constantly, so the three that move the music are set well above
 * the modes and the play above them.
 */
private val LANDSCAPE_TOGGLE_TARGET = 42.dp
private val LANDSCAPE_SKIP_TARGET = 66.dp
private val LANDSCAPE_PLAY_TARGET = 80.dp
private val LANDSCAPE_TOGGLE_GLYPH = 21.dp
private val LANDSCAPE_SKIP_GLYPH = 36.dp
private val LANDSCAPE_PLAY_GLYPH = 46.dp

/**
 * The landscape player's right column with the lyrics open: the sheet, and
 * under it the romanize / source / translate bar.
 *
 * The bar is where the portrait player's status line and its two floating
 * toggles went. There the line sits over the scrubber and the toggles float
 * over the foot of the words; here there is no scrubber beside the lyrics, and
 * the bar gives all three a place of their own that costs the sheet no lines.
 */
@Composable
internal fun LandscapeLyricsPane(
    hasLyrics: Boolean,
    /** Shown in place of the sheet while there are no lines to draw. */
    placeholder: String,
    status: String,
    onStatusClick: () -> Unit,
    /** Whether lines are being picked, which is what the bar stands in for. */
    picking: Boolean = false,
    /** The pick bar itself, drawn in place of the row below. */
    pickBar: @Composable () -> Unit = {},
    romanizationToggle: @Composable () -> Unit,
    translationToggle: @Composable () -> Unit,
    /**
     * How far the scroll viewport is lifted off the foot of the column, short of
     * the bar below it. Raises the line being sung toward the middle of the
     * sleeve on a desktop window without moving the bar — see
     * [LANDSCAPE_LYRICS_BOTTOM_LIFT].
     */
    bottomInset: Dp = 0.dp,
    /**
     * Whether the romanize / source / translate bar sits under the sheet. Every
     * shape keeps it: the pair of toggles is how the sheet is read, and a window
     * that hid them left the only way in through the player's overflow.
     */
    showBar: Boolean = true,
    /**
     * Whether the bar's centred status line is drawn — the "Lyrics by …"
     * caption, its loading and translation siblings. The desktop player moved
     * the provider into its song menu, so its docked lyric column drops this
     * line while keeping the two toggles either side of it.
     */
    showStatus: Boolean = true,
    /**
     * Where the placeholder stands before there are lines to draw. The player centres it in the
     * sleeve, which is where its sung line rests. A column that reads from its top cannot: the
     * sheet lands at the top the moment the lookup returns, so a centred caption is a line that
     * visibly starts in the middle and moves up.
     */
    placeholderAlign: Alignment = Alignment.Center,
    modifier: Modifier = Modifier,
    panel: @Composable (Modifier) -> Unit,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(bottom = bottomInset),
        ) {
            if (hasLyrics) {
                panel(Modifier.fillMaxSize())
            } else {
                // Held at a fixed line rather than through
                // [LyricsUnavailableLine] or [LyricsLoadingLine]: those fade
                // out after a few seconds, which here would leave the whole
                // column blank for as long as the track keeps playing.
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(placeholderAlign),
                )
            }
        }
        // While a pick is open the row below gives way to the bar: the two
        // toggles in it would redraw the very lines the pick is indexing, and
        // the status line is the one thing the bar's own Cancel and Share sit
        // where.
        if (picking) {
            Box(Modifier.fillMaxWidth()) { pickBar() }
        } else if (showBar) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // A disc's width of empty on the leading side, so the status line
                // stays centred behind the pair on the right as lyrics arrive.
                Box(Modifier.size(34.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (showStatus) {
                        LyricsStatusWithChange(
                            status = status,
                            onStatusClick = onStatusClick,
                        )
                    }
                }
                // One column rather than a disc at either end: the way of reading
                // the sheet is asked for top-down — translate it, and only then
                // romanise it — and a listener looking for the second does not
                // have to sweep the whole width to find it.
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(Modifier.size(34.dp)) { if (hasLyrics) translationToggle() }
                    Box(Modifier.size(34.dp)) { if (hasLyrics) romanizationToggle() }
                }
            }
        }
    }
}
