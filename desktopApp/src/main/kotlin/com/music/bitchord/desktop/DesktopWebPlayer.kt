package com.music.bitchord.desktop

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.lyrics.LyricsSource
import com.music.bitchord.data.model.Song
import com.music.bitchord.playback.PlaybackPosition
import com.music.bitchord.ui.LyricsProviderState
import com.music.bitchord.ui.icons.BitChordIcons
import com.music.bitchord.ui.player.ArtworkMeshBackdrop
import com.music.bitchord.ui.player.LyricsSidePanel
import com.music.bitchord.ui.player.MeshGradientBackground
import com.music.bitchord.ui.player.MixPulse
import com.music.bitchord.ui.player.PlaybackQualityLabel
import com.music.bitchord.ui.player.PlayerEscapeLayer
import com.music.bitchord.ui.player.QueueSidePanel
import com.music.bitchord.ui.player.TurntableDeck
import com.music.bitchord.ui.player.rememberArtworkColors
import com.music.bitchord.ui.player.rememberArtworkMesh
import com.music.bitchord.ui.player.rememberMixPulse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The website's full-screen player, drawn in the window: the artwork centred
 * in a dark room with its own colours drifting behind it, the track's credits
 * under it, a scrubber that grows on hover, and the transport below. The
 * lyrics and the queue open as a panel to the right of the artwork, sized to
 * the player column's own height, exactly as the reference lays them out.
 */

/** The site's `--systemPrimary` on dark: 92% white. */
internal val WebPrimary = Color(0xEBFFFFFF)

/** The site's `--systemSecondary` on dark: 64% white. */
internal val WebSecondary = Color(0xA3FFFFFF)

/** The site's `--systemTertiary` on dark (`#fff6`): 40% white. */
internal val WebTertiary = Color(0x66FFFFFF)

/** The site's `--systemQuaternary` inside the full-screen player: 18% white. */
internal val WebQuaternary = Color(0x2EFFFFFF)

/** The site's `--systemQuinary` inside the full-screen player: 10% white. */
internal val WebQuinary = Color(0x1AFFFFFF)

/** The site's `--systemQuaternary` on the page (the pill's own scope): 10% white. */
internal val WebPageQuaternary = Color(0x1AFFFFFF)

@Composable
internal fun DesktopWebPlayer(
    song: Song,
    isPlaying: Boolean,
    isLoading: Boolean,
    position: PlaybackPosition,
    durationMs: Long,
    /** Where the next Automix blend is planned to land, as fractions of the track. */
    transitionWindow: ClosedFloatingPointRange<Float>?,
    previousEnabled: Boolean,
    nextEnabled: Boolean,
    shuffleEnabled: Boolean,
    repeatMode: DesktopRepeatMode,
    liked: Boolean,
    signedIn: Boolean,
    /** The motion artwork for the playing track, when the setting lets it show. */
    canvas: DesktopCanvasArtwork?,
    onToggleLike: () -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onSeek: (Long) -> Unit,
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    onOpenAudioOutput: () -> Unit,
    /** The quality badge opens the audio pipeline readout, as it does on every other player. */
    onOpenTrackInfo: () -> Unit,
    onOpenMenu: () -> Unit,
    menu: @Composable () -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String?, String) -> Unit,
    queue: List<Song>,
    queueIndex: Int,
    autoplayEnabled: Boolean,
    onJumpTo: (Int) -> Unit,
    onRemoveFromQueue: (Int) -> Unit,
    onMoveInQueue: (Int, Int) -> Unit,
    onClearQueue: () -> Unit,
    lyrics: List<LyricLine>?,
    lyricsSource: LyricsSource?,
    lyricsProviderStates: Map<LyricsSource, LyricsProviderState>,
    onSelectLyricsProvider: (LyricsSource) -> Unit,
    lyricsUnavailable: Boolean,
    /** Right-click on a lyric line: ask Genius what that line means. */
    onLineMeaning: (String) -> Unit,
    playerLyricsToggle: MutableState<Boolean>,
    playerQueueToggle: MutableState<Boolean>,
    playerTranslateToggle: MutableState<Boolean>,
    playerShowingTranslation: MutableState<Boolean>,
    playerRomanizeToggle: MutableState<Boolean>,
    playerShowingRomanization: MutableState<Boolean>,
    fullScreen: Boolean,
    onToggleFullScreen: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val legacyMesh by DesktopPlayerSettings.legacyMeshGradient.collectAsState()
    val lyricsClassic by DesktopAppearanceSettings.lyricsClassic.collectAsState()
    val mixBlend by DesktopPlayerSettings.smartMixBlend.collectAsState()
    val mixPulse = rememberMixPulse({ mixBlend }, enabled = true, slow = true)
    // Recompose when the app language changes; see the note in DesktopAppleSidebar.
    DesktopStrings.language.collectAsState()
    var panel by webPlayerPanelState

    // Escape closes the pane first, then the player — the window's route
    // through PlayerBack sees this layer as the top of the player's stack.
    PlayerEscapeLayer(enabled = panel != null) { panel = null }

    // The song menu's rows ask for a pane; the ask is consumed here so the
    // menu's own state does not need to know where the pane lives.
    LaunchedEffect(playerLyricsToggle.value) {
        if (playerLyricsToggle.value) {
            playerLyricsToggle.value = false
            panel = WebPanel.LYRICS
        }
    }
    LaunchedEffect(playerQueueToggle.value) {
        if (playerQueueToggle.value) {
            playerQueueToggle.value = false
            panel = WebPanel.QUEUE
        }
    }

    Box(modifier.fillMaxSize().background(Color.Black)) {
        if (legacyMesh) {
            MeshGradientBackground(
                palette = rememberArtworkColors(song.thumbnailUrl),
                trackKey = song.videoId,
                continuous = true,
                driftMillis = 3_000,
                sleeveStack = true,
                blurRadius = 84.dp,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            ArtworkMeshBackdrop(
                mesh = rememberArtworkMesh(song.thumbnailUrl, artPx = 600),
                modifier = Modifier.fillMaxSize(),
            )
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // The site's --art: min(32vw, calc(100dvh - 330px), 720px).
            val art = minOf(maxWidth * 0.32f, maxHeight - 330.dp, 720.dp).coerceAtLeast(200.dp)
            // gap-x-[clamp(48px,7vw,144px)], px-[clamp(24px,9vw,144px)].
            val gap = (maxWidth * 0.07f).coerceIn(48.dp, 144.dp)
            val pagePadding = (maxWidth * 0.09f).coerceIn(24.dp, 144.dp)
            val panelWidth = maxWidth * 0.38f
            // mt-[clamp(20px,3.2vh,32px)] under the artwork, mt-[clamp(8px,1.6vh,16px)] above the transport.
            val infoTop = (maxHeight * 0.032f).coerceIn(20.dp, 32.dp)
            val transportTop = (maxHeight * 0.016f).coerceIn(8.dp, 16.dp)
            val density = LocalDensity.current
            var columnHeightPx by remember { mutableIntStateOf(0) }
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = pagePadding, vertical = 64.dp),
                horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    Modifier
                        .width(art)
                        .onSizeChanged { columnHeightPx = it.height },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    WebArtwork(
                        url = song.thumbnailUrl,
                        paused = !isPlaying,
                        canvas = canvas,
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                    )
                    WebInfo(
                        song = song,
                        liked = liked,
                        signedIn = signedIn,
                        onToggleLike = onToggleLike,
                        onOpenMenu = onOpenMenu,
                        menu = menu,
                        onOpenAlbum = onOpenAlbum,
                        onOpenArtist = onOpenArtist,
                        topMargin = infoTop,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    WebScrubber(
                        position = position,
                        durationMs = durationMs,
                        transitionWindow = transitionWindow,
                        mixPulse = mixPulse,
                        song = song,
                        isLoading = isLoading,
                        onOpenTrackInfo = onOpenTrackInfo,
                        onSeek = onSeek,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    WebTransport(
                        isPlaying = isPlaying,
                        isLoading = isLoading,
                        previousEnabled = previousEnabled,
                        nextEnabled = nextEnabled,
                        shuffleEnabled = shuffleEnabled,
                        repeatMode = repeatMode,
                        onPlayPause = onPlayPause,
                        onPrevious = onPrevious,
                        onNext = onNext,
                        onToggleShuffle = onToggleShuffle,
                        onCycleRepeat = onCycleRepeat,
                        topMargin = transportTop,
                    )
                }
                // The pane beside the player: the site's `self-stretch` panel,
                // as tall as the player column it sits next to.
                panel?.let { which ->
                    key(which) {
                        val fade = remember { Animatable(0f) }
                        LaunchedEffect(Unit) { fade.animateTo(1f, tween(250)) }
                        Box(
                            Modifier
                                .width(panelWidth)
                                .height(with(density) { columnHeightPx.toDp() })
                                .graphicsLayer { alpha = fade.value },
                        ) {
                            when (which) {
                                WebPanel.LYRICS -> LyricsSidePanel(
                                    song = song,
                                    isPlaying = isPlaying,
                                    position = position,
                                    lyrics = lyrics,
                                    lyricsSource = lyricsSource,
                                    lyricsProviderStates = lyricsProviderStates,
                                    onSelectLyricsProvider = onSelectLyricsProvider,
                                    lyricsUnavailable = lyricsUnavailable,
                                    onSeek = onSeek,
                                    onLineMeaning = onLineMeaning,
                                    modifier = Modifier.fillMaxSize(),
                                    // The words rest slightly above the pane's middle,
                                    // as the player's own sheet keeps them.
                                    centreRaise = PLAYER_LYRICS_CENTRE_RAISE,
                                    // The new default draws four crisp lines through a
                                    // window; "Classic lyrics" restores the full stack.
                                    windowed = !lyricsClassic,
                                    placeholderAlign = Alignment.Center,
                                    // The toggles live in the song menu here;
                                    // the pane itself stays words only.
                                    showLyricsToggles = false,
                                    playerTranslateToggle = playerTranslateToggle,
                                    playerShowingTranslation = playerShowingTranslation,
                                    playerRomanizeToggle = playerRomanizeToggle,
                                    playerShowingRomanization = playerShowingRomanization,
                                )
                                WebPanel.QUEUE -> Box(
                                    Modifier
                                        .fillMaxSize()
                                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                                        // The site fades the queue out over its last 80px.
                                        .drawWithContent {
                                            drawContent()
                                            val fadeTop = size.height - QUEUE_MASK_BOTTOM.toPx()
                                            drawRect(
                                                brush = Brush.verticalGradient(
                                                    0f to Color.Black,
                                                    1f to Color.Transparent,
                                                    startY = fadeTop,
                                                    endY = size.height,
                                                ),
                                                blendMode = BlendMode.DstIn,
                                            )
                                        },
                                ) {
                                    QueueSidePanel(
                                        queue = queue,
                                        queueIndex = queueIndex,
                                        autoplayEnabled = autoplayEnabled,
                                        onJumpTo = onJumpTo,
                                        onRemove = onRemoveFromQueue,
                                        onMove = onMoveInQueue,
                                        onClear = onClearQueue,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // The corners the reference keeps around the room.
            WebCornerButton(
                icon = BitChordIcons.Close,
                contentDescription = DesktopStrings["d_close", "Close"],
                iconSize = 20.dp,
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopStart).padding(start = 20.dp, top = 16.dp),
            )
            WebCornerButton(
                icon = if (fullScreen) BitChordIcons.Minimize else BitChordIcons.Maximize,
                contentDescription = if (fullScreen) {
                    DesktopStrings["d_exit_fullscreen", "Exit fullscreen"]
                } else {
                    DesktopStrings["d_fullscreen", "Fullscreen"]
                },
                iconSize = 18.dp,
                onClick = onToggleFullScreen,
                modifier = Modifier.align(Alignment.TopEnd).padding(end = 20.dp, top = 16.dp),
            )
            WebCornerButton(
                icon = if (panel == WebPanel.LYRICS) BitChordIcons.Lyrics else BitChordIcons.LyricsOff,
                contentDescription = DesktopStrings["open_lyrics", "Lyrics"],
                pressed = panel == WebPanel.LYRICS,
                iconSize = 18.dp,
                onClick = { panel = if (panel == WebPanel.LYRICS) null else WebPanel.LYRICS },
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 20.dp, bottom = 20.dp),
            )
            WebPlayerActions(
                volume = volume,
                onVolumeChange = onVolumeChange,
                onOpenAudioOutput = onOpenAudioOutput,
                queueOpen = panel == WebPanel.QUEUE,
                onToggleQueue = { panel = if (panel == WebPanel.QUEUE) null else WebPanel.QUEUE },
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 20.dp),
            )
        }
    }
}

/** The player's two panes, either of which may sit beside the artwork. */
private enum class WebPanel { LYRICS, QUEUE }

/**
 * Which pane the player had up when it closed. The player reopens on the same
 * one, so a lyric pane left open is there again on the next visit.
 */
private val webPlayerPanelState = mutableStateOf<WebPanel?>(null)

/** How far above the pane's middle the sung line rests — the player's own, smaller raise. */
private const val PLAYER_LYRICS_CENTRE_RAISE = 0.07f

/** One full turn of the turntable record, in milliseconds. */
private const val DECK_TURN_MILLIS = 4_800

/** The site's queue mask: the last 80px fade out. */
private val QUEUE_MASK_BOTTOM = 80.dp

/**
 * The artwork and its aura: the cover sharp in front, its own colours smeared
 * into a soft glow behind and below it. Paused, the whole square breathes down
 * to 92% — the reference's one rest state. The turntable toggle puts the cover
 * on a deck instead: a disc that spins while the track plays, the grooves and
 * the tonearm drawn over it, and no pause breathe — a record holds its size.
 */
@Composable
private fun WebArtwork(url: String?, paused: Boolean, canvas: DesktopCanvasArtwork?, modifier: Modifier = Modifier) {
    val scale by animateFloatAsState(
        targetValue = if (paused) 0.92f else 1f,
        animationSpec = tween(450, easing = CubicBezierEasing(0.34f, 1.3f, 0.64f, 1f)),
        label = "webArtScale",
    )
    val turntableOn by DesktopAppearanceSettings.turntableArt.collectAsState()
    val reduceAnimation by DesktopPlayerSettings.reduceAnimation.collectAsState()
    val deckTurn = remember { Animatable(0f) }
    LaunchedEffect(turntableOn, paused, reduceAnimation) {
        if (turntableOn && !paused && !reduceAnimation) {
            while (true) {
                deckTurn.animateTo(360f, tween(DECK_TURN_MILLIS, easing = LinearEasing))
                deckTurn.snapTo(0f)
            }
        }
    }
    Box(
        modifier
            .graphicsLayer { if (!turntableOn) { scaleX = scale; scaleY = scale } }
            // shadow-[0_4px_10px_#0000001a] — the shadow the reference draws under the cover.
            // Its clip stays on for the sleeve, but off for the deck: shadow() clips to the
            // shape, and clipping to the record's circle would cut the tonearm off at the rim.
            .shadow(
                10.dp,
                if (turntableOn) CircleShape else RoundedCornerShape(10.dp),
                clip = !turntableOn,
                ambientColor = Color.Black.copy(alpha = 0.1f),
                spotColor = Color.Black.copy(alpha = 0.1f),
            ),
    ) {
        // translate-y-2 scale-96 blur-[20px] saturate-200, read at 60px and
        // faded to 40%: the aura that bleeds around the cover's edges.
        WebArtworkImage(
            url = url,
            px = 60,
            maxAlpha = 0.4f,
            saturated = true,
            modifier = Modifier
                .matchParentSize()
                // Clipped to the record's circle before the blur, since the box no longer is.
                .then(if (turntableOn) Modifier.clip(CircleShape) else Modifier)
                .blur(20.dp)
                .graphicsLayer { translationY = 8.dp.toPx(); scaleX = 0.96f; scaleY = 0.96f },
        )
        if (turntableOn) {
            Box(
                Modifier
                    .fillMaxSize()
                    // The turn is the cover's alone; the deck drawn over it
                    // does not spin with the record.
                    .graphicsLayer { rotationZ = deckTurn.value }
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.07f)),
            ) {
                WebArtworkImage(url = url, px = 600, maxAlpha = 1f, modifier = Modifier.fillMaxSize())
            }
            TurntableDeck(isPlaying = !paused, reduceAnimation = reduceAnimation)
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.07f)),
            ) {
                WebArtworkImage(url = url, px = 600, maxAlpha = 1f, modifier = Modifier.fillMaxSize())
                // The motion clip plays over the still cover; it draws nothing
                // until its first frame, so the sleeve never flashes between them.
                canvas?.let {
                    DesktopCanvasView(
                        url = it.url,
                        fallbackUrl = it.fallbackUrl,
                        isPlaying = !paused,
                        publishBackdrop = true,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

/** How long the sleeve's cross-dissolve between two covers runs. */
private const val COVER_DISSOLVE_MS = 600

/** Ease-in-out: the old cover lingers, the new one settles, neither snaps. */
private val COVER_DISSOLVE_EASING = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

/**
 * One decoded cover: fetched at the size asked for, kept by the shared disk
 * cache, and cross-dissolved over the one before it — the old sleeve holds
 * under the new one so a skip is one continuous blend, never a drop to the
 * placeholder.
 */
@Composable
private fun WebArtworkImage(
    url: String?,
    px: Int,
    maxAlpha: Float,
    modifier: Modifier = Modifier,
    saturated: Boolean = false,
) {
    val sized = remember(url, px) { url?.desktopArtworkAt(px) }
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = sized) {
        value = if (sized != null) withContext(Dispatchers.IO) { DesktopArtworkCache.load(sized) } else null
    }
    // The outgoing cover holds under the incoming one while the dissolve runs,
    // so a skip is one continuous blend instead of a drop to the placeholder.
    var shown by remember { mutableStateOf<ImageBitmap?>(null) }
    var outgoing by remember { mutableStateOf<ImageBitmap?>(null) }
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(bitmap) {
        if (bitmap != null) {
            if (shown != null && shown !== bitmap) outgoing = shown
            shown = bitmap
            alpha.snapTo(0f)
            alpha.animateTo(maxAlpha, tween(COVER_DISSOLVE_MS, easing = COVER_DISSOLVE_EASING))
            if (outgoing != null) outgoing = null
        }
    }
    val filter = if (saturated) {
        remember { ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(2f) }) }
    } else {
        null
    }
    Box(modifier) {
        if (shown == null) {
            Box(Modifier.matchParentSize().background(Color.White.copy(alpha = 0.06f)))
        }
        outgoing?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                colorFilter = filter,
                modifier = Modifier.matchParentSize(),
            )
        }
        shown?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                colorFilter = filter,
                modifier = Modifier.matchParentSize().graphicsLayer { this.alpha = alpha.value },
            )
        }
    }
}

/**
 * The credits under the artwork: the title over the artist and album, with
 * the star and the "…" at the row's right edge. The like is the reference's
 * own white star, and it is not drawn at all while signed out.
 */
@Composable
private fun WebInfo(
    song: Song,
    liked: Boolean,
    signedIn: Boolean,
    onToggleLike: () -> Unit,
    onOpenMenu: () -> Unit,
    menu: @Composable () -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String?, String) -> Unit,
    topMargin: Dp,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.padding(top = topMargin),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            // type-[600_17px/22px], a link to the album, truncated.
            WebLink(
                text = song.title,
                color = WebPrimary,
                fontSize = 17.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.SemiBold,
                enabled = song.albumId != null,
                onClick = { song.albumId?.let(onOpenAlbum) },
            )
            // type-title-3 (400 15px/20px): the artist, an em dash, the album.
            Row(verticalAlignment = Alignment.CenterVertically) {
                WebLink(
                    text = song.artist,
                    color = WebSecondary,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    onClick = { onOpenArtist(song.artistId, song.artist) },
                    modifier = Modifier.weight(1f, fill = false),
                )
                val album = song.albumName
                if (!album.isNullOrBlank()) {
                    Text(
                        " — ",
                        color = WebSecondary,
                        fontSize = 15.sp,
                        lineHeight = 20.sp,
                        maxLines = 1,
                    )
                    WebLink(
                        text = album,
                        color = WebSecondary,
                        fontSize = 15.sp,
                        lineHeight = 20.sp,
                        enabled = song.albumId != null,
                        onClick = { song.albumId?.let(onOpenAlbum) },
                    )
                }
            }
        }
        if (signedIn) {
            WebCircleButton(
                icon = if (liked) BitChordIcons.StarFilled else BitChordIcons.Star,
                contentDescription = if (liked) {
                    DesktopStrings["d_remove_from_favourites", "Remove from Favourites"]
                } else {
                    DesktopStrings["d_favourite", "Favourite"]
                },
                onClick = onToggleLike,
            )
        }
        // The song menu hangs off this 32dp box, which is the anchor the popup
        // positions itself against.
        Box(Modifier.size(32.dp)) {
            WebCircleButton(
                icon = BitChordIcons.Ellipsis,
                contentDescription = DesktopStrings["d_more", "More"],
                onClick = onOpenMenu,
                modifier = Modifier.fillMaxSize(),
            )
            menu()
        }
    }
}

/** One credit line that is a link: underlined under the pointer, as the reference draws them. */
@Composable
private fun WebLink(
    text: String,
    color: Color,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    fontWeight: FontWeight = FontWeight.Normal,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Text(
        text = text,
        color = color,
        fontSize = fontSize,
        lineHeight = lineHeight,
        fontWeight = fontWeight,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textDecoration = if (hovered && enabled) TextDecoration.Underline else null,
        modifier = modifier
            .hoverable(interaction, enabled = enabled)
            .clickable(interaction, indication = null, enabled = enabled, onClick = onClick),
    )
}

/**
 * The player's scrubber: a 4px bar that grows to 8px under the pointer, its
 * fill brightening from 64% to 92% white, with the elapsed and remaining
 * times below and the quality badge in the gap between them — and the
 * "Mixing" label that takes the badge's place while a blend covers the bar.
 */
@Composable
private fun WebScrubber(
    position: PlaybackPosition,
    durationMs: Long,
    transitionWindow: ClosedFloatingPointRange<Float>? = null,
    mixPulse: MixPulse,
    song: Song,
    isLoading: Boolean,
    onOpenTrackInfo: () -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hitHover = remember { MutableInteractionSource() }
    val overHit by hitHover.collectIsHoveredAsState()
    var scrub by remember { mutableStateOf<Float?>(null) }
    val dragging = scrub != null
    val focus by animateFloatAsState(
        if (overHit || dragging) 1f else 0f,
        tween(120),
        label = "webScrubFocus",
    )
    val fillColor = lerp(WebSecondary, WebPrimary, focus)
    Column(modifier.padding(top = 14.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(16.dp)
                .hoverable(hitHover)
                .pointerInput(durationMs) {
                    fun fraction(x: Float) = (x / size.width.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        scrub = fraction(down.position.x)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            change.consume()
                            scrub = fraction(change.position.x)
                        }
                        scrub?.let { onSeek((it * durationMs).toLong()) }
                        scrub = null
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp + 4.dp * focus)
                    .clip(RoundedCornerShape(50))
                    .background(WebQuaternary),
            ) {
                val base = (
                    scrub ?: if (durationMs > 0) position.positionMs.toFloat() / durationMs.toFloat() else 0f
                    ).coerceIn(0f, 1f)
                val fill = base + (1f - base) * mixPulse.cover
                // The stretch the next Automix blend is planned to occupy,
                // brighter than the unplayed track so the mix is visible
                // before it arrives — and under the played fill, so reaching
                // it hands the marker over to ordinary progress.
                BoxWithConstraints(Modifier.matchParentSize()) {
                    transitionWindow?.let { window ->
                        val from = maxWidth * window.start.coerceIn(0f, 1f)
                        val to = maxWidth * window.endInclusive.coerceIn(0f, 1f)
                        if (to > from) {
                            Box(
                                Modifier
                                    .fillMaxHeight()
                                    .width(to - from)
                                    .offset(x = from)
                                    .background(WebTertiary),
                            )
                        }
                    }
                }
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fill)
                        .background(fillColor),
                )
            }
        }
        Box(Modifier.fillMaxWidth().padding(top = 2.dp)) {
            val shown = (
                scrub ?: if (durationMs > 0) position.positionMs.toFloat() / durationMs.toFloat() else 0f
                ).coerceIn(0f, 1f)
            Row(
                Modifier
                    .fillMaxWidth()
                    // While a blend covers the bar, the times give way to its label.
                    .graphicsLayer { alpha = 1f - mixPulse.cover },
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    webTime((shown * durationMs).toLong()),
                    color = WebTertiary,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    "-" + webTime(((1f - shown) * durationMs).toLong()),
                    color = WebTertiary,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            Text(
                DesktopStrings["d_mixing", "Mixing"],
                color = WebSecondary,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.Center).graphicsLayer { alpha = mixPulse.cover },
            )
            // The badge holds the centre the "Mixing" label takes over, so it
            // fades out on exactly the same curve the times do.
            PlaybackQualityLabel(
                song = song,
                isLoading = isLoading,
                onOpenTrackInfo = onOpenTrackInfo,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 8.dp)
                    .graphicsLayer { alpha = 1f - mixPulse.cover },
            )
        }
    }
}

/**
 * The transport: shuffle and repeat dimmed until pressed, the pair of skips
 * and the big play between them. Every button takes a hover wash and presses
 * down to 90%, over 100ms, as the reference does.
 */
@Composable
private fun WebTransport(
    isPlaying: Boolean,
    isLoading: Boolean,
    previousEnabled: Boolean,
    nextEnabled: Boolean,
    shuffleEnabled: Boolean,
    repeatMode: DesktopRepeatMode,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    topMargin: Dp,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .padding(top = topMargin)
            .widthIn(max = 315.dp)
            .fillMaxWidth()
            .height(52.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WebTransportButton(
            icon = BitChordIcons.TransportShuffle,
            size = 36.dp,
            iconSize = 30.dp,
            tint = if (shuffleEnabled) WebPrimary else WebTertiary,
            contentDescription = DesktopStrings["shuffle", "Shuffle"],
            enabled = true,
            onClick = onToggleShuffle,
        )
        WebTransportButton(
            icon = BitChordIcons.TransportPrevious,
            size = 48.dp,
            iconSize = 40.dp,
            tint = WebPrimary,
            contentDescription = DesktopStrings["widget_previous", "Previous"],
            enabled = previousEnabled,
            onClick = onPrevious,
        )
        // Buffering shows the pause glyph, exactly as the reference does.
        WebTransportButton(
            icon = if (isPlaying || isLoading) BitChordIcons.TransportPause else BitChordIcons.TransportPlay,
            size = 56.dp,
            iconSize = 48.dp,
            tint = WebPrimary,
            contentDescription = if (isPlaying || isLoading) {
                DesktopStrings["pause", "Pause"]
            } else {
                DesktopStrings["play", "Play"]
            },
            enabled = true,
            onClick = onPlayPause,
        )
        WebTransportButton(
            icon = BitChordIcons.TransportNext,
            size = 48.dp,
            iconSize = 40.dp,
            tint = WebPrimary,
            contentDescription = DesktopStrings["widget_next", "Next"],
            enabled = nextEnabled,
            onClick = onNext,
        )
        WebTransportButton(
            icon = if (repeatMode == DesktopRepeatMode.ONE) BitChordIcons.TransportRepeatOne else BitChordIcons.TransportRepeat,
            size = 36.dp,
            iconSize = 30.dp,
            tint = if (repeatMode != DesktopRepeatMode.OFF) WebPrimary else WebTertiary,
            contentDescription = repeatMode.menuLabel(),
            enabled = true,
            onClick = onCycleRepeat,
        )
    }
}

/** One button of the transport row: hover wash, 90% press, 40% when disabled. */
@Composable
private fun WebTransportButton(
    icon: ImageVector,
    size: Dp,
    iconSize: Dp,
    tint: Color,
    contentDescription: String?,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val isPressed by interaction.collectIsPressedAsState()
    val bg by animateColorAsState(
        if (hovered && enabled) WebQuinary else Color.Transparent,
        tween(100),
        label = "transportBg",
    )
    val scale by animateFloatAsState(
        if (isPressed && enabled) 0.9f else 1f,
        tween(100),
        label = "transportScale",
    )
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(bg)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.4f
            }
            .hoverable(interaction, enabled = enabled)
            .clickable(interaction, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/** One of the reference's corner discs: 32px, a hover wash, brightening to primary on hover. */
@Composable
private fun WebCornerButton(
    icon: ImageVector,
    contentDescription: String?,
    iconSize: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    pressed: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val bg by animateColorAsState(
        if (hovered) WebQuinary else Color.Transparent,
        tween(100),
        label = "cornerBg",
    )
    val tint by animateColorAsState(
        if (hovered || pressed) WebPrimary else WebSecondary,
        tween(100),
        label = "cornerTint",
    )
    Box(
        modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(bg)
            .hoverable(interaction)
            .clickable(interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/**
 * The reference's round info buttons — the star and the "…": quinary fill
 * with a quaternary hover wash, the glyph drawn white at 18px.
 */
@Composable
private fun WebCircleButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(if (hovered) WebQuaternary else WebQuinary)
            .hoverable(interaction)
            .clickable(interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = WebPrimary, modifier = Modifier.size(18.dp))
    }
}

/**
 * The bottom-right corner: the queue disc and the volume disc, with the
 * volume slider sliding in between them while the pointer is anywhere over
 * the corner. The queue fades out to make room, exactly as the reference's
 * `--volume-gap:36px` slider replaces it.
 */
@Composable
private fun WebPlayerActions(
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    onOpenAudioOutput: () -> Unit,
    queueOpen: Boolean,
    onToggleQueue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val actionsHover = remember { MutableInteractionSource() }
    val volumeHover = remember { MutableInteractionSource() }
    val overActions by actionsHover.collectIsHoveredAsState()
    val overVolume by volumeHover.collectIsHoveredAsState()
    var sliderOpen by remember { mutableStateOf(false) }
    var volumeMenu by remember { mutableStateOf(false) }
    LaunchedEffect(overVolume, overActions) {
        if (overVolume) sliderOpen = true else if (!overActions) sliderOpen = false
    }
    val reveal by animateFloatAsState(if (sliderOpen) 1f else 0f, tween(150), label = "actionsReveal")
    // 132 wide so the 96dp slider, right-aligned 36dp in from the edge, sits
    // fully inside and stays hit-testable.
    Box(modifier.width(132.dp).height(32.dp).hoverable(actionsHover)) {
        Row(
            Modifier.align(Alignment.CenterEnd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.graphicsLayer { alpha = 1f - reveal }) {
                WebCornerButton(
                    icon = BitChordIcons.Queue,
                    contentDescription = DesktopStrings["up_next", "Up next"],
                    iconSize = 18.dp,
                    pressed = queueOpen,
                    onClick = onToggleQueue,
                )
            }
            Box(Modifier.hoverable(volumeHover)) {
                WebCornerButton(
                    icon = BitChordIcons.TransportVolume(volume),
                    contentDescription = DesktopStrings["d_volume_and_audio_output", "Volume and audio output"],
                    iconSize = 18.dp,
                    onClick = { volumeMenu = true },
                )
            }
        }
        if (reveal > 0f) {
            WebVolumeSlider(
                value = volume,
                onValueChange = onVolumeChange,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = (-36).dp)
                    .graphicsLayer {
                        alpha = reveal
                        translationX = (1f - reveal) * 8.dp.toPx()
                    }
                    .hoverable(volumeHover),
            )
        }
        // A click on the disc opens the fuller menu — the same slider plus the
        // audio output row — rather than just the inline bar.
        DesktopDropdownMenu(volumeMenu, { volumeMenu = false }) {
            Box(Modifier.width(180.dp).padding(horizontal = 16.dp)) {
                DesktopThinSlider(volume, onVolumeChange, idleHeight = 4.dp, activeHeight = 8.dp)
            }
            DropdownMenuItem(
                text = { Text(DesktopStrings["audio_output", "Audio output"]) },
                leadingIcon = { Icon(BitChordIcons.Headphones, null) },
                onClick = {
                    volumeMenu = false
                    onOpenAudioOutput()
                },
            )
        }
    }
}

/**
 * The reference's volume bar: a 4px two-tone track — primary up to the fill,
 * quaternary after it — with a 12px white thumb. Draggable and wheel-stepped,
 * shared by the full player and the pill.
 */
@Composable
internal fun WebVolumeSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    trackColor: Color = WebQuaternary,
) {
    val currentValue by rememberUpdatedState(value)
    val currentChange by rememberUpdatedState(onValueChange)
    Canvas(
        modifier
            .size(width = 96.dp, height = 16.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    fun set(x: Float) {
                        currentChange((x / size.width.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f))
                    }
                    set(down.position.x)
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        change.consume()
                        set(change.position.x)
                    }
                }
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        event.changes.forEach { change ->
                            if (change.isConsumed) return@forEach
                            val delta = change.scrollDelta.y
                            if (delta != 0f) {
                                currentChange((currentValue - delta / 400f).coerceIn(0f, 1f))
                                change.consume()
                            }
                        }
                    }
                }
            },
    ) {
        val trackHeight = 4.dp.toPx()
        val top = (size.height - trackHeight) / 2f
        val radius = CornerRadius(trackHeight / 2f)
        drawRoundRect(
            color = trackColor,
            topLeft = Offset(0f, top),
            size = Size(size.width, trackHeight),
            cornerRadius = radius,
        )
        val fill = size.width * value.coerceIn(0f, 1f)
        if (fill > 0f) {
            drawRoundRect(
                color = WebPrimary,
                topLeft = Offset(0f, top),
                size = Size(fill.coerceAtLeast(trackHeight), trackHeight),
                cornerRadius = radius,
            )
        }
        // The thumb: shadow-[0_0_2px_#0006], drawn as a soft dark disc behind it.
        val thumbX = fill.coerceIn(trackHeight / 2f, size.width - trackHeight / 2f)
        drawCircle(Color.Black.copy(alpha = 0.25f), radius = 7.dp.toPx(), center = Offset(thumbX, size.height / 2f))
        drawCircle(Color.White, radius = 6.dp.toPx(), center = Offset(thumbX, size.height / 2f))
    }
}

/** The reference's time: whole seconds as m:ss, floor'd like its own formatter. */
internal fun webTime(ms: Long): String {
    val total = ms.coerceAtLeast(0L) / 1_000L
    return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
}
