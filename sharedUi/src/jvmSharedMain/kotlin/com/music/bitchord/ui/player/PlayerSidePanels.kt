package com.music.bitchord.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.lyrics.LyricsSource
import com.music.bitchord.data.model.Song
import com.music.bitchord.playback.PlaybackPosition
import com.music.bitchord.sharedui.resources.*
import com.music.bitchord.ui.LyricsProviderState
import com.music.bitchord.ui.haptics.rememberHaptics
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource

// The player's lyrics and queue, standing on their own outside it: what a
// window shows in a column beside the page, the way Apple Music's sidebar
// does, while the player itself stays closed. The same pieces the landscape
// player lays out in its right-hand pane, so a line lights, a row drags and a
// translation arrives exactly as they do in there.

/**
 * How far above the column's middle the sung line rests.
 *
 * This pane has always kept its words at the top of the column, so the raise is nearly the whole
 * half-viewport and only a hair is left above the anchor for the outgoing line to rise and dim
 * into. It stops short of the top edge itself: at exactly half the viewport the leading inset
 * collapses to nothing, and a line handed over at the edge is out of sight before it is finished.
 *
 * The main player keeps its own, smaller raise and is not touched by this.
 */
private const val SIDE_COLUMN_CENTRE_RAISE = 0.46f

/**
 * The lyric sheet with its provider line and its romanization and translation
 * toggles — the landscape player's lyrics pane, without the player around it.
 *
 * [position] is read only by the panel itself, for the same reason the player
 * passes it down as an object: a tick recomposes the lines, not this.
 */
@Composable
fun LyricsSidePanel(
    song: Song,
    isPlaying: Boolean,
    position: PlaybackPosition,
    lyrics: List<LyricLine>?,
    lyricsSource: LyricsSource?,
    lyricsProviderStates: Map<LyricsSource, LyricsProviderState>,
    onSelectLyricsProvider: (LyricsSource) -> Unit,
    lyricsUnavailable: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * How far above the column's middle the sung line rests. The column that
     * stands beside a page reads from its top, so it keeps [SIDE_COLUMN_CENTRE_RAISE];
     * the full-screen player's centred pane passes its own, smaller raise.
     */
    centreRaise: Float = SIDE_COLUMN_CENTRE_RAISE,
    /**
     * Whether the words draw as the player's own block of four crisp lines
     * moving through a window rather than as a stack filling the column. The
     * full-screen player passes its "Classic lyrics" toggle here.
     */
    windowed: Boolean = false,
    /**
     * Whether the translate/romanise pair is drawn under the words. The
     * full-screen player has moved them into its song menu and passes false;
     * the home page's lyric column keeps them.
     */
    showLyricsToggles: Boolean = true,
    /**
     * Where the placeholder stands before there are lines to draw. A column
     * that reads from its top keeps it there; the centred player pane centres it.
     */
    placeholderAlign: Alignment = Alignment.TopCenter,
    /**
     * The player's own translate/romanise toggles, when this column is the
     * player's lyric pane and the song menu flips them. Each ask is consumed
     * and cleared here; the current state is mirrored back out through the
     * second of each pair so the menu rows can show their checkmarks.
     */
    playerTranslateToggle: MutableState<Boolean>? = null,
    playerShowingTranslation: MutableState<Boolean>? = null,
    playerRomanizeToggle: MutableState<Boolean>? = null,
    playerShowingRomanization: MutableState<Boolean>? = null,
) {
    val haptics = rememberHaptics()
    val lyricsOffsetMs by PlayerSettings.lyricsOffsetMs.collectAsStateWithLifecycle()
    val lyricsPlayhead = rememberLyricPlayhead(position)
    val seekToLyric: (Long) -> Unit = { lineTimeMs ->
        onSeek(adjustedLyricsSeekTarget(lineTimeMs, lyricsOffsetMs))
    }
    val lyricsLoadingLines = stringArrayResource(Res.array.lyrics_loading_lines)
    val lyricsLoadingText = remember(song.videoId, lyricsLoadingLines) {
        lyricsLoadingLines.randomOrNull().orEmpty()
    }
    val lyricsTranslation = rememberLyricsTranslation(
        trackId = song.videoId,
        lyrics = lyrics,
        lyricsSource = lyricsSource,
        lyricsUnavailable = lyricsUnavailable,
        loadingText = lyricsLoadingText,
        haptics = haptics,
    )
    // The menu rows ask; this column runs the ask and clears it, exactly as
    // the landscape player does for the same pair of toggles.
    LaunchedEffect(playerTranslateToggle?.value) {
        if (playerTranslateToggle?.value == true) {
            playerTranslateToggle.value = false
            lyricsTranslation.toggleTranslation()
        }
    }
    LaunchedEffect(lyricsTranslation.showingTranslation) {
        playerShowingTranslation?.value = lyricsTranslation.showingTranslation
    }
    LaunchedEffect(playerRomanizeToggle?.value) {
        if (playerRomanizeToggle?.value == true) {
            playerRomanizeToggle.value = false
            lyricsTranslation.toggleRomanization()
        }
    }
    LaunchedEffect(lyricsTranslation.showingRomanization) {
        playerShowingRomanization?.value = lyricsTranslation.showingRomanization
    }
    // The provider drawer frosts this column, not the page beside it.
    val haze = remember { HazeState() }
    var showLyricsProviders by remember { mutableStateOf(false) }

    Box(modifier) {
        Box(Modifier.fillMaxSize().hazeSource(haze)) {
            LandscapeLyricsPane(
                hasLyrics = lyricsTranslation.displayedLyrics.isNotEmpty(),
                placeholder = if (lyricsUnavailable) {
                    stringResource(Res.string.lyrics_not_available)
                } else {
                    lyricsLoadingText
                },
                status = lyricsTranslation.status,
                onStatusClick = { showLyricsProviders = true },
                // The provider now lives in the desktop player's song menu, so
                // this column drops the "Lyrics by …" line but keeps its two
                // toggles either side of the gap it leaves — except in the
                // player itself, where the toggles live in the song menu too.
                showStatus = false,
                showToggles = showLyricsToggles,
                placeholderAlign = placeholderAlign,
                windowed = windowed,
                romanizationToggle = {
                    RomanizationToggleButton(
                        state = lyricsTranslation.romanizationState,
                        showingRomanization = lyricsTranslation.showingRomanization,
                        enabled = !lyrics.isNullOrEmpty(),
                        onClick = lyricsTranslation.toggleRomanization,
                    )
                },
                translationToggle = {
                    TranslationToggleButton(
                        state = lyricsTranslation.translationState,
                        showingTranslation = lyricsTranslation.showingTranslation,
                        enabled = !lyrics.isNullOrEmpty(),
                        onClick = lyricsTranslation.toggleTranslation,
                    )
                },
            ) { panelModifier ->
                LyricsTranslationMotion(
                    trigger = lyricsTranslation.transition,
                    reduceMotion = lyricsTranslation.reduceMotion,
                    modifier = panelModifier,
                ) { particleProgress ->
                    // Controls always open, as in the landscape player: there
                    // is no transport hidden behind these lines for a tap to
                    // bring back.
                    LyricsPanel(
                        lines = lyrics.orEmpty(),
                        subLines = lyricsTranslation.subLines,
                        trackKey = song.videoId,
                        playhead = lyricsPlayhead,
                        looking = !lyricsUnavailable,
                        isPlaying = isPlaying && position.advancing,
                        onSeekToLine = seekToLyric,
                        controlsOpen = true,
                        onRevealControls = {},
                        onHideControls = {},
                        translationProgress = particleProgress,
                        centreRaise = centreRaise,
                        windowed = windowed,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        if (showLyricsProviders) {
            LyricsProviderSheet(
                hazeState = haze,
                currentSource = lyricsSource,
                states = lyricsProviderStates,
                onSelect = onSelectLyricsProvider,
                onDismiss = { showLyricsProviders = false },
            )
        }
    }
}

/**
 * One rung of the player's back stack: while [enabled], Escape (the window's
 * route into [PlayerBack]) reaches [onBack] before the sheet closes. The
 * full-screen player uses this so its lyric or queue pane closes first, and
 * only the next Escape leaves the player.
 */
@Composable
fun PlayerEscapeLayer(enabled: Boolean, onBack: () -> Unit) {
    PlayerBackHandler(enabled = enabled, onBack = onBack)
}

/** The live queue — the player's own, rows, drag and all — without the player. */
@Composable
fun QueueSidePanel(
    queue: List<Song>,
    queueIndex: Int,
    autoplayEnabled: Boolean,
    onJumpTo: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    InlineQueue(
        queue = queue,
        currentIndex = queueIndex,
        autoplayEnabled = autoplayEnabled,
        controlsLocked = rememberControlsLocked(),
        onJumpTo = onJumpTo,
        onRemove = onRemove,
        onMove = onMove,
        onClear = onClear,
        modifier = modifier,
    )
}
