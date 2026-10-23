package com.music.bitchord.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
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
 * How far above the column's middle the sung line rests. Half the viewport is
 * the top of the pane: the sheet has to arrive already there, rather than being
 * placed at a middle and glided up once the viewport measures itself. The main
 * player keeps its own, smaller raise and is not touched by this.
 */
private const val SIDE_COLUMN_CENTRE_RAISE = 0.5f

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
                // toggles either side of the gap it leaves.
                showStatus = false,
                // The sheet this column reads from is placed at its top, so the line standing in
                // for it must wait there too: centred, it is a caption that starts in the middle
                // of the column and moves up when the lookup returns.
                placeholderAlign = Alignment.TopCenter,
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
                        // The words read near the top of the column; the
                        // full-screen player keeps its own centred anchor.
                        centreRaise = SIDE_COLUMN_CENTRE_RAISE,
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
