package com.music.bitchord.desktop

import androidx.compose.foundation.lazy.LazyListState
import com.music.bitchord.ui.components.AppleScrollbarBox
import com.music.bitchord.ui.components.LocalShelfRowChrome
import com.music.bitchord.ui.components.ShelfRowChrome
import com.music.bitchord.ui.components.ShelfRow
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.pointer.isBackPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.mutableStateListOf
import com.music.bitchord.ui.components.contextClick
import com.music.bitchord.ui.components.trackColumnWidth
import com.music.bitchord.ui.replay.ReplayCreditCard
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.EntityType
import com.music.bitchord.data.model.SearchHistoryEntity
import com.music.bitchord.ui.LocalPullToRefreshEnabled
import com.music.bitchord.ui.screens.LibraryGridPage
import com.music.bitchord.ui.screens.LibraryLink
import com.music.bitchord.ui.screens.LibraryScreen
import com.music.bitchord.ui.screens.MoodGenrePlaylistsScreen
import com.music.bitchord.ui.screens.SearchScreen
import com.music.bitchord.data.NerdStats
import com.music.bitchord.playback.PlaybackPosition
import com.music.bitchord.playback.QueueSource
import com.music.bitchord.playback.QueueTimeline
import com.music.bitchord.playback.QueueTimeline.asQueueEntry
import com.music.bitchord.data.listentogether.partyQueueIndexOf
import com.music.bitchord.data.listentogether.partyUpcomingAfter
import com.music.bitchord.ui.LyricsProviderState
import com.music.bitchord.data.lyrics.LyricsSource
import com.music.bitchord.ui.player.LyricsSidePanel
import com.music.bitchord.ui.player.LocalLyricsFontFamily
import com.music.bitchord.ui.player.NowPlayingScreen
import com.music.bitchord.ui.player.QueueSidePanel
import com.music.bitchord.ui.player.PlayerBack
import com.music.bitchord.ui.player.RepeatModes
import com.music.bitchord.ui.player.rememberMixPulse
import com.music.bitchord.data.model.QueueTier
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.BlurOff
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DataUsage
import androidx.compose.material.icons.rounded.Deck
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Gradient
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.HdrAuto
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.LinearScale
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Monitor
import androidx.compose.material.icons.rounded.MoneyOff
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.NorthWest
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.SurroundSound
import androidx.compose.material.icons.rounded.Theaters
import androidx.compose.material.icons.rounded.Timelapse
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import bitchord.desktopapp.generated.resources.Res
import bitchord.desktopapp.generated.resources.dm_sans_bold
import bitchord.desktopapp.generated.resources.dm_sans_extrabold
import bitchord.desktopapp.generated.resources.dm_sans_medium
import bitchord.desktopapp.generated.resources.dm_sans_regular
import bitchord.desktopapp.generated.resources.dm_sans_semibold
import bitchord.desktopapp.generated.resources.logo_mark
import bitchord.desktopapp.generated.resources.sf_pro_display_bold
import bitchord.desktopapp.generated.resources.sf_pro_display_heavy
import bitchord.desktopapp.generated.resources.sf_pro_display_medium
import bitchord.desktopapp.generated.resources.sf_pro_display_regular
import bitchord.desktopapp.generated.resources.sf_pro_display_semibold
import com.music.bitchord.data.model.ArtistPage
import com.music.bitchord.data.model.BrowseItem
import com.music.bitchord.data.model.BrowseType
import com.music.bitchord.data.model.CARD_ART_PX
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.LibraryPage
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.data.model.MoodGenre
import com.music.bitchord.data.model.MoodGenreSection
import com.music.bitchord.data.model.PLAYER_ART_PX
import com.music.bitchord.data.model.PlaylistPrivacy
import com.music.bitchord.data.model.ROW_ART_PX
import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.PlaybackSourceType
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.SubscriptionState
import com.music.bitchord.data.model.UiState
import com.music.bitchord.data.model.UserPlaylist
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.data.model.durationMillis
import com.music.bitchord.data.model.isSameTrackAs
import com.music.bitchord.data.model.withoutRepeatsOf
import com.music.bitchord.data.settings.AutomixPerformanceMode
import com.music.bitchord.data.settings.LastPlayerScreen
import com.music.bitchord.data.settings.SmartAnalysis
import com.music.bitchord.data.settings.TrackAnalysisState
import com.music.bitchord.data.settings.TransitionWindow
import com.music.bitchord.ui.icons.BitChordIcons
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.HazeMaterials
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.system.exitProcess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import org.jetbrains.compose.resources.Font as composeFont
import org.jetbrains.compose.resources.painterResource

/**
 * The margin every page keeps from the window's edge.
 *
 * Desktop-only pages share Library's edge inset so switching destinations does not move the page
 * content sideways.
 */
internal val DesktopPageGutter = 40.dp

internal val DesktopBackground = Color(0xFF1F1F1F)
internal val DesktopSurface = Color(0xFF1F1F1F)
private val DesktopSurfaceRaised = Color(0xFF2A2A2E)
internal val DesktopGlass = Color(0x802A2A2E)
internal val DesktopGlassStrong = Color(0xE017181C)

/**
 * What a page lays under its own content.
 *
 * Pages used to paint a solid slab here, which put the window's artwork wash out of sight on
 * everything but Replay — the ambient backdrop only reads where the page lets it through. With
 * the wash off, or with nothing playing, the solid ground comes back.
 */
@Composable
internal fun DesktopPageGround(): Color {
    val ambient by DesktopAppearanceSettings.ambientBackdrop.collectAsState()
    return if (ambient) Color.Transparent else DesktopBackground
}

/** The floating bars in the narrow layout. */
private val DesktopBarGlass = Color(0xF0242428)

/** The backdrop the floating bars blur. */
private val LocalDesktopHaze = staticCompositionLocalOf<HazeState?> { null }

/** How much a page's scrollable should keep clear of the floating bars. */
private val LocalDesktopBottomInset = staticCompositionLocalOf { 0.dp }

/** Android's own edge on these bars: `Color.White.copy(alpha = 0.10f)`. */
private val DesktopBarEdge = Color(0x1AFFFFFF)
internal val DesktopAccent = Color(0xFFFA586A)

/** Deleting, signing out, and anything that failed — the one place a colour survives, as on Android. */
internal val DesktopDestructive = Color(0xFFFF3B30)

/** A chip the way Android draws a chosen segment: solid white with black on it, or nothing at all. */
@Composable
internal fun desktopChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = Color.Transparent,
    labelColor = DesktopSecondary,
    selectedContainerColor = Color.White,
    selectedLabelColor = Color.Black,
)

/** Apple's switch: a red track with a white knob on, a faint wash off. */
@Composable
internal fun desktopSwitchColors() = SwitchDefaults.colors(
    checkedTrackColor = DesktopAccent,
    checkedThumbColor = Color.White,
    checkedBorderColor = Color.Transparent,
    uncheckedTrackColor = Color.White.copy(alpha = 0.10f),
    uncheckedThumbColor = Color.White.copy(alpha = 0.35f),
    uncheckedBorderColor = Color.Transparent,
)
internal val DesktopSecondary = Color(0xFFAAAAAD)
internal val DesktopDivider = Color(0x1AFFFFFF)
private const val STATS_SAMPLE_MS = 5_000L

/** How often the playhead is offered to the history tracker. */
private const val HISTORY_SAMPLE_MS = 5_000L

/** How long the search field is left alone before its typeahead is asked for. */
private const val SUGGESTION_DEBOUNCE_MS = 220L

/** How many tracks a station is built with when one is started explicitly. */
/** A search costs more than a completion, so the live rows wait for a longer pause. */
private const val TYPEAHEAD_MEDIA_DEBOUNCE_MS = 400L

/** How many playable rows the dropdown shows, matching Android's own limit. */
private const val TYPEAHEAD_MEDIA_LIMIT = 8

private const val INITIAL_RADIO_TRACKS = 24

/** An artist's top songs, capped so the release shelves are not buried. */
private const val MAX_ARTIST_SONGS = 20


/** The library shelf the account's own playlists arrive on, and the one edits are spliced into. */
private const val PLAYLISTS_SHELF = "Playlists"
private const val STATS_MAX_DELTA_MS = 15_000L
private const val SCROBBLE_THRESHOLD_MS = 180_000L

/** Used for tracking restart of the current song when previous button is pressed. */
private const val BACK_RESTARTS_AFTER_MS = 10_000L

/** How long a manual skip takes to blend into the next track when Automix on skip is on. */
private const val SKIP_MIX_ON_SKIP_SECONDS = 15

/** A skip made before this many seconds have played cuts instead of blending. */
private const val DEFAULT_INSTANT_SKIP_SECONDS = 15
private const val KEY_INSTANT_SKIP_SECONDS = "instant_skip_seconds"

/** The longest early-skip window the setting offers. */
private const val MAX_INSTANT_SKIP_SECONDS = 30

/** How long the supplier waits for the server to echo an AutoPlay queue addition. */
private const val PARTY_AUTOPLAY_ECHO_TIMEOUT_MS = 5_000L

/** The phone gives one empty station response a delayed second chance. */
private const val AUTOPLAY_EMPTY_REFRESH_DELAY_MS = 2_000L

/** The phone's dark scheme, which the shared pages are drawn against. */
internal fun desktopColorScheme() = darkColorScheme(
    primary = DesktopAccent,
    onPrimary = Color.White,
    primaryContainer = DesktopAccent.copy(alpha = 0.16f),
    onPrimaryContainer = Color.White,
    background = DesktopBackground,
    onBackground = Color.White,
    surface = DesktopSurface,
    onSurface = Color.White,
    surfaceVariant = DesktopSurfaceRaised,
    onSurfaceVariant = DesktopSecondary,
    outline = Color(0xFF3A3A3C),
    outlineVariant = Color(0x1AFFFFFF),
    surfaceTint = DesktopAccent,
)

@Composable
internal fun desktopTypography(): Typography {
    val sfProDisplay = FontFamily(
        composeFont(Res.font.sf_pro_display_regular, FontWeight.W400),
        composeFont(Res.font.sf_pro_display_medium, FontWeight.W500),
        composeFont(Res.font.sf_pro_display_semibold, FontWeight.W600),
        composeFont(Res.font.sf_pro_display_bold, FontWeight.W700),
        composeFont(Res.font.sf_pro_display_heavy, FontWeight.W800),
    )
    val defaults = Typography()
    return defaults.copy(
        displayLarge = defaults.displayLarge.copy(fontFamily = sfProDisplay, fontWeight = FontWeight.W700, fontSize = 34.sp, letterSpacing = (-0.8).sp),
        displayMedium = defaults.displayMedium.copy(fontFamily = sfProDisplay),
        displaySmall = defaults.displaySmall.copy(fontFamily = sfProDisplay),
        headlineLarge = defaults.headlineLarge.copy(fontFamily = sfProDisplay, fontWeight = FontWeight.W800, fontSize = 30.sp, letterSpacing = (-0.7).sp),
        headlineMedium = defaults.headlineMedium.copy(fontFamily = sfProDisplay, fontWeight = FontWeight.W700, fontSize = 22.sp, letterSpacing = (-0.4).sp),
        headlineSmall = defaults.headlineSmall.copy(fontFamily = sfProDisplay),
        titleLarge = defaults.titleLarge.copy(fontFamily = sfProDisplay, fontWeight = FontWeight.W700, fontSize = 17.sp, letterSpacing = (-0.3).sp),
        titleMedium = defaults.titleMedium.copy(fontFamily = sfProDisplay, fontWeight = FontWeight.W600, fontSize = 16.sp, letterSpacing = (-0.2).sp),
        titleSmall = defaults.titleSmall.copy(fontFamily = sfProDisplay),
        bodyLarge = defaults.bodyLarge.copy(fontFamily = sfProDisplay, fontWeight = FontWeight.W400, fontSize = 16.sp),
        bodyMedium = defaults.bodyMedium.copy(fontFamily = sfProDisplay, fontWeight = FontWeight.W400, fontSize = 14.sp),
        bodySmall = defaults.bodySmall.copy(fontFamily = sfProDisplay),
        labelLarge = defaults.labelLarge.copy(fontFamily = sfProDisplay),
        labelMedium = defaults.labelMedium.copy(fontFamily = sfProDisplay, fontWeight = FontWeight.W600, fontSize = 12.sp),
        labelSmall = defaults.labelSmall.copy(fontFamily = sfProDisplay, fontWeight = FontWeight.W600, fontSize = 11.sp),
    )
}

/**
 * The face lyric sheets are set in: SF Pro Display, the typeface the reference
 * site's own sheet is set in — its stack is `'BitChord SF Pro Display', 'DM
 * Sans'`, with the first name a self-hosted face and DM Sans only the fallback.
 *
 * A family of its own rather than the chrome's [desktopTypography] face, since
 * the sheet sets its lines ExtraBold. It reaches
 * [sharedUi][com.music.bitchord.ui.player] through
 * [LocalLyricsFontFamily], which a module cannot fill from its own resources.
 */
@Composable
internal fun desktopLyricsFont(): FontFamily {
    val regular = composeFont(Res.font.sf_pro_display_regular, FontWeight.W400)
    val medium = composeFont(Res.font.sf_pro_display_medium, FontWeight.W500)
    val semibold = composeFont(Res.font.sf_pro_display_semibold, FontWeight.W600)
    val bold = composeFont(Res.font.sf_pro_display_bold, FontWeight.W700)
    val extraBold = composeFont(Res.font.sf_pro_display_heavy, FontWeight.W800)
    return remember(regular, medium, semibold, bold, extraBold) {
        FontFamily(listOf(regular, medium, semibold, bold, extraBold))
    }
}

/**
 * The sidebar's pages. [id] is what gets persisted and compared against, so it stays
 * English whatever the app reads as; [label] is the copy the user sees.
 */
internal enum class DesktopDestination(val id: String, private val key: String, private val english: String) {
    LISTEN_NOW("listen-now", "d_home", "Home"),
    EXPLORE("explore", "d_new", "New"),
    RADIO("radio", "d_radio", "Radio"),
    LIBRARY("library", "library", "Library"),
    SEARCH("search", "search", "Search"),
    HISTORY("history", "d_recently_played", "Recently Played"),
    DOWNLOADS("downloads", "downloads", "Downloads"),
    LOCAL_MUSIC("local-music", "local_music", "Local Music"),
    SETTINGS("settings", "settings", "Settings"),
    ;

    val label: String get() = DesktopStrings[key, english]
}

internal enum class DesktopRepeatMode {
    OFF,
    ALL,
    ONE,
    ;

    fun next(): DesktopRepeatMode = when (this) {
        OFF -> ALL
        ALL -> ONE
        ONE -> OFF
    }

    /** The one word the settings row and the loop button's tooltip show. */
    fun label(): String = when (this) {
        OFF -> DesktopStrings["d_off", "Off"]
        ALL -> DesktopStrings["d_all", "All"]
        ONE -> DesktopStrings["d_one", "One"]
    }

    /** The whole phrase a menu item or a tooltip reads as. */
    fun menuLabel(): String = when (this) {
        OFF -> DesktopStrings["repeat_off", "Repeat off"]
        ALL -> DesktopStrings["repeat_all", "Repeat all"]
        ONE -> DesktopStrings["repeat_one", "Repeat one"]
    }
}

@Composable
fun BitChordDesktopApp() {
    val scope = rememberCoroutineScope()
    val radioPlayer = remember { DesktopRadioPlayer() }
    val radioPlayback by radioPlayer.state.collectAsState()
    DisposableEffect(radioPlayer) {
        onDispose { radioPlayer.release() }
    }
    // Filled once the audio engine exists. Local playback helpers use this single hook so every
    // transport and queue gesture reaches Listen Together without duplicating protocol logic.
    val partySyncHolder = remember { arrayOfNulls<DesktopPartySync>(1) }
    val partyState by DesktopListenTogether.state.collectAsState()
    var personalQueueStash by remember { mutableStateOf<DesktopQueue?>(null) }
    var personalPositionStash by remember { mutableStateOf(0L) }
    var personalPlayingStash by remember { mutableStateOf(false) }
    val persistence = remember { DesktopPersistence() }
    var destination by remember { mutableStateOf(DesktopDestination.LISTEN_NOW) }
    var query by remember { mutableStateOf("") }
    var searchFilter by remember { mutableStateOf(SearchFilter.ALL) }
    var searchRows by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    // What has been searched before, and what YouTube thinks is being typed.
    val searchHistory by DesktopSearchHistory.recent.collectAsState()
    // Whether anything has been searched for since the field was last emptied: until then the
    // page shows recent searches rather than a result set.
    var searchCommitted by remember { mutableStateOf(false) }
    var searchScrollReset by remember { mutableStateOf(0) }
    var searchFocusRequested by remember { mutableStateOf(false) }
    var searchSuggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    // Playable rows for the half-typed query, shown under the text completions.
    var searchTypeahead by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    // False while the query is being set *by* the app.
    var searchTyping by remember { mutableStateOf(false) }
    var searchLoading by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var homeState by remember { mutableStateOf<UiState<List<HomeShelf>>>(UiState.Loading) }
    var appleBrowseState by remember { mutableStateOf<UiState<List<HomeShelf>>>(UiState.Loading) }
    var appleBrowseReloads by remember { mutableStateOf(0) }
    var appleRadioState by remember { mutableStateOf<UiState<List<HomeShelf>>>(UiState.Loading) }
    var appleRadioReloads by remember { mutableStateOf(0) }
    var appleActionError by remember { mutableStateOf<String?>(null) }
    var appleCollectionMenu by remember { mutableStateOf<(ShelfItem) -> Unit>({}) }
    val radioOffAir by DesktopRadioNowPlaying.lines.collectAsState()
    LaunchedEffect(radioPlayback.error) {
        radioPlayback.error?.let { appleActionError = it }
    }
    LaunchedEffect(destination, appleRadioReloads) {
        if (destination == DesktopDestination.RADIO) {
            appleRadioState = UiState.Loading
            DesktopAppleBrowseClient.page("https://music.apple.com/gb/radio", "Radio").fold(
                onSuccess = { appleRadioState = UiState.Success(DesktopRadioClient.freeShelves(it)) },
                onFailure = { appleRadioState = UiState.Error(it.message ?: "Could not load radio") },
            )
        }
    }
    // What a station is carrying is only worth asking for while somebody can see the answer: the radio
    // page, or a stream in the player. Apple names nothing in a feed a listener may ask for, so the
    // record is read off the stream itself, on the page's beat. A station still inside its own ttl is
    // not re-asked, and one that says nothing keeps showing what it said last rather than going blank.
    LaunchedEffect(destination, appleRadioState, radioPlayback.station) {
        val onRadioPage = destination == DesktopDestination.RADIO
        val playing = radioPlayback.station
        if (!onRadioPage && playing == null) return@LaunchedEffect
        val stations = buildList {
            if (onRadioPage) (appleRadioState as? UiState.Success)?.data
                ?.flatMap { it.items }?.mapNotNullTo(this, DesktopRadioClient::station)
            if (playing != null && none { it.id == playing.id }) add(playing)
        }
        while (true) {
            DesktopRadioNowPlaying.refresh(stations)
            delay(15_000)
        }
    }
    LaunchedEffect(appleBrowseReloads) {
        appleBrowseState = UiState.Loading
        DesktopAppleBrowseClient.newPage().fold(
            onSuccess = { appleBrowseState = UiState.Success(it) },
            onFailure = { appleBrowseState = UiState.Error(it.message ?: "Could not load Apple Music") },
        )
    }
    // Radio is the other Apple page that arrives a beat after the click. Fetching it once the New
    // page is on screen waits on nothing the user is looking at, and the click finds it in the
    // cache instead of on Apple's servers.
    LaunchedEffect(appleBrowseState) {
        if (appleBrowseState is UiState.Success) {
            DesktopAppleBrowseClient.page("https://music.apple.com/gb/radio", "Radio")
        }
    }
    // The home feed is paged: `FEmusic_home` answers with a couple of shelves and a token, and
    // everything else arrives by following it.
    var homeContinuation by remember { mutableStateOf<String?>(null) }
    var homeLoadingMore by remember { mutableStateOf(false) }
    // Recently Played has its own request and its own skeleton at the head of the page, as on the
    // phone; it is only asked for with an account to have played on.
    var homeRecentsLoading by remember { mutableStateOf(false) }
    // Held out here so a page keeps its place when another is visited and it is come back to.
    val homeListState = rememberLazyListState()
    val exploreListState = rememberLazyListState()
    val moodGenreListState = rememberLazyListState()
    val searchListState = rememberLazyListState()
    val radioListState = rememberLazyListState()
    val libraryListState = rememberLazyListState()
    val replayListState = rememberLazyListState()
    val libraryGridState = rememberLazyGridState()
    /** A Library shelf's "Show all", open as a grid in place of the page. */
    var libraryShowAll by remember { mutableStateOf<HomeShelf?>(null) }
    /** Which sidebar Library shortcut [libraryShowAll] is following, if one opened it. */
    var libraryShortcut by remember { mutableStateOf<String?>(null) }
    /** Where the one back button in the top bar goes: every place visited, oldest first. */
    val navHistory = remember { mutableStateListOf<DesktopNavEntry>() }
    /** Set while [goBack] puts a place back, so that move is not itself recorded as a visit. */
    val navRestoring = remember { booleanArrayOf(false) }
    /** Opens a song's menu at the pointer; set by the pages' menu host once it is in place. */
    var pageMenu by remember { mutableStateOf<(Song) -> Unit>({}) }
    val homeSeenTitles = remember { HashSet<String>() }
    var homeGeneration by remember { mutableStateOf(0) }
    var homeIdentity by remember { mutableStateOf<String?>(null) }
    var exploreState by remember { mutableStateOf<UiState<List<MoodGenreSection>>>(UiState.Loading) }
    var selectedMoodGenre by remember { mutableStateOf<MoodGenre?>(null) }
    /** The line under an opened release's title — "Apple Music Pop", an album's artist — which the
     * tile that opened it knew and the page itself has to fetch again. */
    var applePageCredit by remember { mutableStateOf<String?>(null) }
    // Bumped by a retry so the loaders below re-run without the state they are keyed on having to
    // change to something and back again.
    var exploreReloads by remember { mutableStateOf(0) }
    var moodGenreReloads by remember { mutableStateOf(0) }
    var moodGenreShelves by remember { mutableStateOf<UiState<List<HomeShelf>>>(UiState.Loading) }
    var selectedSong by remember { mutableStateOf<Song?>(null) }
    // The live queue: what is playing, what played before it, what is next.
    var liveQueue by remember {
        mutableStateOf(
            DesktopQueue.restored(
                persistence.queue(),
                persistence.string("queue_index", "0").toIntOrNull() ?: 0,
            ),
        )
    }
    // The order the queue was in before shuffle rearranged it, so the toggle can be undone.
    var preShuffleOrder by remember { mutableStateOf<List<String>>(emptyList()) }
    // One party AutoPlay top-up at a time; see topUpPartyAutoplay.
    val partyAutoplayLock = remember { Mutex() }
    val queue = liveQueue.songs
    // What was played on this computer. The account's own history replaces it
    // while signed in — see [remoteHistory] — because that is what Android's
    // History screen is and what makes a phone and a desktop agree.
    var history by remember { mutableStateOf(persistence.history()) }
    var remoteHistory by remember { mutableStateOf<List<Song>>(emptyList()) }
    var likedIds by remember { mutableStateOf(persistence.likedIds()) }
    // In-flight tail of the Liked Music chain; replaced whenever the library is fetched again.
    var likedSyncJob by remember { mutableStateOf<Job?>(null) }
    var dislikedIds by remember { mutableStateOf(persistence.dislikedIds()) }
    val overlays = remember { DesktopOverlays() }
    var downloads by remember { mutableStateOf(persistence.downloads()) }
    var localSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    val downloadQueue by DesktopDownloadQueue.active.collectAsState()
    val downloadInProgress = downloadQueue.keys
    var playlists by remember { mutableStateOf(persistence.playlists()) }
    var statsRevision by remember { mutableStateOf(0L) }
    var replayPeriod by remember { mutableStateOf(DesktopReplayPeriod.ALL_TIME) }
    val replayHolder = remember { DesktopAccounts.active()?.name.orEmpty() }
    var replaySummary by remember { mutableStateOf(DesktopListeningStats.summary()) }
    var statsCountedSongs by remember { mutableStateOf<Set<String>>(emptySet()) }
    var playlistTarget by remember { mutableStateOf<Song?>(null) }
    // One busy flag and one message for every playlist edit: only one of them can be in flight,
    // because each is driven by a dialog that is modal.
    var playlistBusy by remember { mutableStateOf(false) }
    var playlistError by remember { mutableStateOf<String?>(null) }
    // The album and artist pages the playing track belongs to, looked up while
    // the player is open. Whatever started a track knew its title and artwork
    // but rarely its ids — a home-feed tile carries neither — so without this
    // the credit under the title is not a link at all. Android does the same
    // thing, and for the same reason; see its `links` in MainActivity.
    var trackLinks by remember { mutableStateOf<Song?>(null) }
    // Settings is a modal over whatever page is open, the way Music puts its own preferences in a
    // sheet rather than a navigation destination.
    var autoplay by remember { mutableStateOf(persistence.boolean("autoplay", true)) }
    var dontRepeatSuggestions by remember {
        mutableStateOf(persistence.boolean(KEY_DONT_REPEAT_SUGGESTIONS, false))
    }
    /** Everything AutoPlay has played or offered this run — see [KEY_DONT_REPEAT_SUGGESTIONS]. */
    val sessionSongHistory = remember { mutableListOf<Song>() }
    var filterNonMusicAudio by remember {
        mutableStateOf(persistence.boolean(DesktopLocalMusic.KEY_FILTER_NON_MUSIC_AUDIO, false))
    }
    var localMusicRevision by remember { mutableStateOf(0) }
    // The track the queued station was built around, so a top-up is asked for once per song rather
    // than once per recomposition.
    var autoplaySeed by remember { mutableStateOf<String?>(null) }
    var autoplayJob by remember { mutableStateOf<Job?>(null) }
    var automix by remember { mutableStateOf(persistence.boolean("automix", false)) }
    // A manual skip blends into the already-prepared next track over ~15s instead of cutting.
    var automixOnSkip by remember { mutableStateOf(persistence.boolean("automix_on_skip", false)) }
    // Skipping this early in a song cuts straight to the next one rather than mixing; 0 = never.
    var instantSkipSeconds by remember {
        mutableStateOf(
            persistence.string(KEY_INSTANT_SKIP_SECONDS, "$DEFAULT_INSTANT_SKIP_SECONDS")
                .toIntOrNull()?.coerceIn(0, MAX_INSTANT_SKIP_SECONDS)
                ?: DEFAULT_INSTANT_SKIP_SECONDS,
        )
    }
    // Apple-style Automix phrase length in bars: 0 = adaptive (default), 8 or 16 = fixed.
    var automixPhraseBars by remember {
        mutableStateOf(persistence.string("automix_phrase_bars", "0").toIntOrNull()?.let { if (it == 8 || it == 16) it else 0 } ?: 0)
    }
    var automixPerformance by remember {
        mutableStateOf(
            runCatching { AutomixPerformanceMode.valueOf(persistence.string("automix_performance", "BALANCED")) }
                .getOrDefault(AutomixPerformanceMode.BALANCED),
        )
    }
    var shuffle by remember { mutableStateOf(persistence.boolean("shuffle", false)) }
    var repeatMode by remember {
        mutableStateOf(
            runCatching { DesktopRepeatMode.valueOf(persistence.string("repeat_mode", "OFF")) }
                .getOrDefault(DesktopRepeatMode.OFF),
        )
    }
    var playbackSpeed by remember { mutableStateOf(persistence.string("playback_speed", "1.0").toFloatOrNull() ?: 1.0f) }
    var volume by remember { mutableStateOf(persistence.string("volume", "1.0").toFloatOrNull()?.coerceIn(0.0f, 1.0f) ?: 1.0f) }
    var crossfadeSeconds by remember { mutableStateOf(persistence.string("crossfade_seconds", "0").toIntOrNull()?.coerceIn(0, 12) ?: 0) }
    var downloadQuality by remember { mutableStateOf(persistence.string("download_quality", "LOSSLESS")) }
    var trayIconEnabled by remember { mutableStateOf(persistence.boolean("tray_icon", true)) }
    var closeToTray by remember { mutableStateOf(persistence.boolean("close_to_tray", true)) }
    // The three the FFmpeg engine unlocked: none of them could exist while JavaFX owned the decode,
    // because none of them can be done without the samples themselves.
    var spatialAudio by remember { mutableStateOf(persistence.boolean("spatial_audio", false)) }
    var dolbyAtmos by remember { mutableStateOf(persistence.boolean("dolby_atmos", true)) }
    var skipSilence by remember { mutableStateOf(persistence.boolean("skip_silence", false)) }
    var outputPrecision by remember { mutableStateOf(persistence.string("output_precision", "PCM_16")) }
    // The blob backdrop the mesh replaced, kept as an opt-out.
    var legacyMeshGradient by remember { mutableStateOf(persistence.boolean("legacy_mesh_gradient", false)) }
    var animatedCanvas by remember { mutableStateOf(persistence.boolean("animated_canvas", true)) }
    var showNerdStats by remember { mutableStateOf(persistence.boolean("show_nerd_stats", false)) }
    var syncedLyrics by remember {
        mutableStateOf(persistence.boolean(DesktopLyricsClient.KEY_SYNCED_LYRICS, true))
    }
    var lyricsBlur by remember {
        mutableStateOf(persistence.boolean(DesktopLyricsClient.KEY_LYRICS_BLUR, true))
    }
    var prioritizeSyllables by remember {
        mutableStateOf(persistence.boolean(DesktopLyricsClient.KEY_PRIORITIZE_SYLLABLES, false))
    }
    var lyricsOrder by remember { mutableStateOf(persistence.lyricsSourceOrder()) }
    var lyricsOn by remember { mutableStateOf(persistence.lyricsEnabledSources()) }
    var fullBleedArtwork by remember { mutableStateOf(persistence.boolean("full_bleed_artwork", false)) }
    var accounts by remember { mutableStateOf(DesktopAccounts.accounts()) }
    var activeAccountId by remember { mutableStateOf(DesktopAccounts.activeAccountId()) }
    var activeProfileId by remember { mutableStateOf(DesktopAccounts.activeProfileId()) }
    var signInBusy by remember { mutableStateOf<String?>(null) }
    var signInError by remember { mutableStateOf<String?>(null) }
    var browserSignInJob by remember { mutableStateOf<Job?>(null) }
    val interactiveSignInBrowser = remember { DesktopBrowserSignIn.preferred() }
    val activeAccount = accounts.firstOrNull { it.accountId == activeAccountId } ?: accounts.firstOrNull()
    // The account's own library, fetched once a session is in force.
    var libraryState by remember { mutableStateOf<UiState<LibraryPage>>(UiState.Loading) }
    // Whether YouTube is actually accepting the session, which is not the same as having an account
    // saved.
    var youtubeSignedIn by remember { mutableStateOf(false) }
    // Set by an edit that the library feed has not caught up with yet.
    var libraryStale by remember { mutableStateOf(false) }
    // Bumped when the session changes underneath, so the loader below re-runs without anything it
    // is keyed on having to change to something and back.
    var sessionRevision by remember { mutableStateOf(0) }
    // Read off the library rather than fetched again: the Playlists shelf is the same feed the
    // picker wants, minus the entries no edit can be aimed at.
    val accountPlaylists = remember(libraryState) {
        (libraryState as? UiState.Success)?.data?.shelves
            ?.firstOrNull { it.title == PLAYLISTS_SHELF }
            ?.items
            ?.let(DesktopSearchClient::userPlaylists)
            .orEmpty()
    }
    // The add-to-playlist dialog intentionally keeps only playlists owned by the account. The
    // sidebar is a library browser instead, so it shows the complete paged shelf: owned, saved,
    // and YouTube's built-in playlists such as Liked Music.
    val sidebarAccountPlaylists = remember(libraryState) {
        (libraryState as? UiState.Success)?.data?.shelves
            ?.firstOrNull { it.title == PLAYLISTS_SHELF }
            ?.items
            ?.filter { it.browseId != null }
            .orEmpty()
    }

    fun reloadLibrary() {
        libraryState = UiState.Loading
        sessionRevision++
    }

    /** Edits the Playlists shelf in place, without re-fetching it. */
    fun editPlaylistShelf(edit: (List<ShelfItem>) -> List<ShelfItem>) {
        val page = (libraryState as? UiState.Success)?.data ?: return
        val index = page.shelves.indexOfFirst { it.title == PLAYLISTS_SHELF }
        val shelves = if (index >= 0) {
            page.shelves.toMutableList().apply {
                this[index] = this[index].copy(items = edit(this[index].items))
            }
        } else {
            // A fresh account has no Playlists shelf at all, and it is exactly the account most
            // likely to be making its first one.
            val items = edit(emptyList())
            if (items.isEmpty()) return
            listOf(HomeShelf(PLAYLISTS_SHELF, items)) + page.shelves
        }
        libraryState = UiState.Success(page.copy(shelves = shelves.toList()))
    }

    // Whoever was signed in last is signed in again, before anything asks YouTube for something
    // that depends on it.
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { DesktopAccounts.activate() }
        sessionRevision++
    }
    var audioQuality by remember { mutableStateOf(persistence.audioQuality()) }
    var sourceConfigs by remember { mutableStateOf(persistence.sourceConfigs()) }
    var sourceStatus by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    val youtubeSourceEnabled = sourceConfigs.any {
        it.kind == DesktopSourceKind.YOUTUBE && it.enabled
    }
    val sleepTimerMinutes by DesktopSleepTimer.minutes.collectAsState()
    val sleepAfterTrack by DesktopSleepTimer.afterTrack.collectAsState()
    var sleepTimerTick by remember { mutableStateOf(0L) }
    LaunchedEffect(sleepTimerMinutes) {
        while (sleepTimerMinutes != null && currentCoroutineContext().isActive) {
            delay(1_000L)
            sleepTimerTick++
        }
    }
    val sleepRemainingMs = remember(sleepTimerMinutes, sleepTimerTick) {
        DesktopSleepTimer.remainingMs()
    }
    var openedCollection by remember { mutableStateOf<DesktopCollection?>(null) }
    var collectionLoadingMore by remember { mutableStateOf(false) }
    var collectionError by remember { mutableStateOf<String?>(null) }
    var collectionReloads by remember { mutableStateOf(0) }
    // The artist page is its own destination rather than a collection with a different header.
    var openedArtist by remember { mutableStateOf<DesktopArtistTarget?>(null) }
    var artistState by remember { mutableStateOf<UiState<ArtistPage>>(UiState.Loading) }
    var artistReloads by remember { mutableStateOf(0) }

    fun canonicalSong(song: Song): Song {
        val homeSong = (homeState as? UiState.Success)?.data.orEmpty()
            .asSequence()
            .flatMap { it.items.asSequence() }
            .filter { it.videoId == song.videoId }
            .map(ShelfItem::toSong)
            .firstOrNull()
        return if (homeSong != null && song.artist.isUnknownArtist()) {
            song.copy(
                artist = homeSong.artist,
                durationText = song.durationText ?: homeSong.durationText,
                thumbnailUrl = song.thumbnailUrl ?: homeSong.thumbnailUrl,
            )
        } else {
            song
        }
    }

    lateinit var startSong: (Song, Boolean) -> Unit
    lateinit var playbackEngine: DesktopPlaybackEngine

    fun saveQueue() {
        // A party queue is temporary. The phone keeps the listener's own queue aside and so does
        // desktop; never let a shared running order overwrite the queue restored after leaving or
        // after a process restart.
        if (DesktopListenTogether.state.value.inParty) return
        persistence.saveQueue(liveQueue.songs)
        persistence.saveString("queue_index", liveQueue.index.toString())
    }

    /** Opens whatever the queue is currently pointing at. */
    fun playCurrent(startPlaying: Boolean = true) {
        val song = liveQueue.current ?: return
        radioPlayer.stop()
        selectedSong = song
        history = (listOf(song) + history.filterNot { it.videoId == song.videoId }).take(50)
        saveQueue()
        persistence.saveHistory(history)
        playbackEngine.load(song, startPlaying)
        partySyncHolder[0]?.onLocalIntent(song.videoId)
        scope.launch { DesktopScrobbling.updateNowPlaying(song) }
    }

    fun partyTrackChangeBlocked(): Boolean {
        if (!DesktopListenTogether.state.value.controlsLocked) return false
        DesktopPlayerHost.showMessage("Only the party host can change playback")
        return true
    }

    /**
     * The phone's party queue for a track picked here: that track, then what the party's members
     * queued by hand. Never the album around it, and never the last track's AutoPlay — the party
     * shares one running order, and the phone builds it exactly this way.
     */
    fun partyPlaybackQueue(tapped: Song): List<Song> {
        val party = DesktopListenTogether.state.value
        val upcoming = partyUpcomingAfter(party.queue, party.playback, party.playback.track?.videoId)
        return QueueTimeline.buildPartyPlaybackQueue(
            tapped,
            tapped.queueSource(),
            DesktopQueue.adopt(liveQueue.upcoming, upcoming.map { it.toDesktopSong() }),
        )
    }

    /** A song played on its own — from a search row, a shelf card, history. */
    fun playSong(song: Song, startPlaying: Boolean = true, source: DesktopQueueSource? = null) {
        if (partyTrackChangeBlocked()) return
        val tapped = canonicalSong(song).withSource(source)
        val songs = if (DesktopListenTogether.state.value.inParty) {
            partyPlaybackQueue(tapped)
        } else {
            // The phone's one-off queue: the song, then whatever the listener had queued by hand.
            QueueTimeline.buildOneOffQueue(liveQueue.songs, liveQueue.index, tapped, tapped.queueSource())
        }
        liveQueue = DesktopQueue(songs, index = 0)
        preShuffleOrder = emptyList()
        playCurrent(startPlaying)
    }

    /** Takes a captured cookie all the way to a saved account. */
    suspend fun signIn(cookie: String, label: String, sourceProfile: String? = null) {
        signInError = null
        signInBusy = label
        try {
            val scope = withContext(Dispatchers.IO) { DesktopYouTubeSession.adoptSessionScope(cookie) }
            if (scope == null) {
                signInError = "$label is not signed in to YouTube Music."
                return
            }
            DesktopYouTubeAuth.adopt(scope)
            // Asked with the session in force, so they describe the account that was just captured
            // rather than nobody in particular.
            val details = DesktopSearchClient.accountMenu().getOrNull()?.let(DesktopAccountParser::account)
            val channels = DesktopSearchClient.accountsList().getOrNull()
                ?.let(DesktopAccountParser::channels)
                .orEmpty()
            // Off the UI thread: saving reaches the platform keyring over D-Bus, and a blocking
            // call to another process is not something to do on the thread that is drawing.
            withContext(Dispatchers.IO) { DesktopAccounts.save(cookie, scope, details, channels, sourceProfile) }
            accounts = withContext(Dispatchers.IO) { DesktopAccounts.accounts() }
            activeAccountId = DesktopAccounts.activeAccountId()
            activeProfileId = DesktopAccounts.activeProfileId()
            overlays.signIn = false
            overlays.accounts = false
        } finally {
            signInBusy = null
        }
    }

    fun playSongs(songs: List<Song>, startIndex: Int = 0, source: DesktopQueueSource? = null) {
        if (songs.isEmpty()) return
        if (partyTrackChangeBlocked()) return
        val playable = songs.map(::canonicalSong).map { it.withSource(source) }
        val at = startIndex.coerceIn(playable.indices)
        if (DesktopListenTogether.state.value.inParty) {
            liveQueue = DesktopQueue(partyPlaybackQueue(playable[at]), index = 0)
            preShuffleOrder = emptyList()
            playCurrent()
            return
        }
        // The phone's context queue: the album up to the pick, the pick, what the listener had
        // queued by hand, then the rest of the album.
        val built = QueueTimeline.buildContextQueue(
            currentTimeline = liveQueue.songs,
            currentIndex = liveQueue.index,
            newContextSongs = playable,
            selectedIndex = at,
            contextSource = playable[at].queueSource(),
        )
        liveQueue = if (shuffle) {
            DesktopQueue.shuffledStartingAt(built.timeline, built.startIndex)
        } else {
            // Keep the complete queue so Previous can navigate to the tracks before the selected one.
            DesktopQueue.startingAt(built.timeline, built.startIndex)
        }
        preShuffleOrder = if (shuffle) built.timeline.map(DesktopQueue::orderKey) else emptyList()
        playCurrent()
    }

    /** Moves within the queue, dropping whatever a forward jump passed over. */
    fun playQueueIndex(target: Int) {
        if (target !in liveQueue.songs.indices) return
        if (partyTrackChangeBlocked()) return
        liveQueue = liveQueue.jumpTo(target)
        playCurrent()
    }

    fun cycleSleepTimer() {
        when {
            sleepAfterTrack -> DesktopSleepTimer.cancel()
            sleepTimerMinutes == null -> DesktopSleepTimer.start(DesktopSleepTimer.presets.first())
            sleepTimerMinutes == DesktopSleepTimer.presets.last() -> DesktopSleepTimer.startAfterTrack()
            else -> {
                val current = DesktopSleepTimer.presets.indexOf(sleepTimerMinutes)
                DesktopSleepTimer.start(DesktopSleepTimer.presets.getOrElse(current + 1) { DesktopSleepTimer.presets.last() })
            }
        }
    }
    startSong = ::playSong

    /** Applies a rating locally and carries it to the account. */
    fun rate(song: Song, status: LikeStatus) {
        likedIds = if (status == LikeStatus.LIKE) likedIds + song.videoId else likedIds - song.videoId
        dislikedIds = if (status == LikeStatus.DISLIKE) {
            dislikedIds + song.videoId
        } else {
            dislikedIds - song.videoId
        }
        persistence.saveLikedIds(likedIds)
        persistence.saveDislikedIds(dislikedIds)
        scope.launch {
            DesktopSearchClient.rate(song.videoId, status)
                .onFailure { DesktopTrackLog.log("youtube: rating ${song.videoId} failed: ${it.message}") }
        }
    }

    fun toggleLike(song: Song) =
        rate(song, if (song.videoId in likedIds) LikeStatus.INDIFFERENT else LikeStatus.LIKE)

    fun toggleDislike(song: Song) =
        rate(song, if (song.videoId in dislikedIds) LikeStatus.INDIFFERENT else LikeStatus.DISLIKE)

    /** The origin the live queue is carrying, for anything appended to it. */
    fun currentQueueSource(): DesktopQueueSource? = liveQueue.current?.playbackSource?.let {
        DesktopQueueSource(
            it,
            liveQueue.current?.playbackSourceType ?: PlaybackSourceType.QUEUE,
            liveQueue.current?.playbackSourceId,
        )
    }

    /**
     * Queues a track by hand, into the listener's own section — "Play next" at its head, "Add to
     * queue" at its end, above the album and AutoPlay either way. In a party, held to the same
     * twenty-five upcoming songs the phone and the server allow.
     */
    fun enqueue(song: Song, next: Boolean) {
        val party = DesktopListenTogether.state.value
        if (party.controlsLocked) return
        if (party.inParty && liveQueue.upcoming.size >= DesktopPartySync.MAX_PARTY_UPCOMING_QUEUE) {
            DesktopPlayerHost.showMessage(
                "Queue is full (maximum ${DesktopPartySync.MAX_PARTY_UPCOMING_QUEUE} songs in party)",
            )
            return
        }
        val queued = canonicalSong(song).copy(radioName = liveQueue.current?.radioName)
            .withSource(currentQueueSource())
        liveQueue = liveQueue.enqueue(queued, playNext = next)
        saveQueue()
        partySyncHolder[0]?.onLocalIntent()
    }

    /** Slots a track in right after the one playing. */
    fun playNext(song: Song) = enqueue(song, next = true)

    /** Puts a track at the end of what the listener queued — not the end of the queue. */
    fun addToQueue(song: Song) = enqueue(song, next = false)

    /** Starts the station YouTube Music builds around one track. */
    fun startRadio(song: Song) {
        if (partyTrackChangeBlocked()) return
        val seed = canonicalSong(song).copy(radioName = song.title)
        scope.launch {
            // AutoPlay's section behind the seed, as the phone queues a station — which is also
            // what lets a party share it: a context tail is never published.
            val related = DesktopAutoplay.tracksFor(listOf(seed), seed, INITIAL_RADIO_TRACKS)
                .getOrNull()
                .orEmpty()
            if (related.isEmpty()) {
                DesktopTrackLog.log("radio: nothing to build a station on for '${song.title}'")
                return@launch
            }
            if (partyTrackChangeBlocked()) return@launch
            if (liveQueue.current?.videoId == seed.videoId) {
                liveQueue = DesktopQueue(listOf(liveQueue.current!!) + related, index = 0)
                saveQueue()
                partySyncHolder[0]?.onLocalIntent()
            } else {
                val station = listOf(seed.asQueueEntry(QueueTier.CONTEXT)) + related
                liveQueue = if (shuffle) DesktopQueue.shuffledStartingAt(station, 0) else DesktopQueue(station, 0)
                preShuffleOrder = if (shuffle) station.map(DesktopQueue::orderKey) else emptyList()
                playCurrent()
            }
            DesktopTrackLog.log("radio: started a station on '${song.title}' with ${related.size} tracks")
        }
    }

    /**
     * Opens an album or playlist in place of whatever page was open. The page it replaces is kept
     * in the history, not underneath: an artist page left standing under an album would be drawn
     * over it, which is how an album opened from an artist once went nowhere.
     */
    fun showCollection(collection: DesktopCollection) {
        openedArtist = null
        overlays.replay = false
        overlays.settingsPage = null
        collectionError = null
        openedCollection = collection
    }

    /**
     * Opens an album or playlist by id straight away, with a loader where the tracks go, the way an
     * artist's page opens — rather than leaving the click with nothing to show until YouTube answers.
     * [fallback] is what the card that was clicked already knew.
     */
    fun openCollection(browseId: String, fallback: BrowseItem? = null) {
        val current = openedCollection
        if (current?.browseId == browseId && !current.loading) {
            showCollection(current)
            return
        }
        showCollection(
            DesktopCollection(
                browseId = browseId,
                title = fallback?.title.orEmpty(),
                subtitle = fallback?.subtitle.orEmpty(),
                thumbnailUrl = fallback?.thumbnailUrl,
                type = fallback?.type?.takeIf { it != BrowseType.OTHER } ?: browseTypeOf(browseId),
                songs = emptyList(),
                loading = true,
            ),
        )
        collectionReloads++
    }

    // Keyed on the id, so a second page opened before the first answered cancels the first rather
    // than racing it; and a loading page restored by Back fetches again.
    LaunchedEffect(openedCollection?.browseId, openedCollection?.loading, collectionReloads) {
        val pending = openedCollection?.takeIf { it.loading } ?: return@LaunchedEffect
        collectionError = null
        DesktopSearchClient.browse(
            browseId = pending.browseId,
            fallback = BrowseItem(
                browseId = pending.browseId,
                title = pending.title,
                subtitle = pending.subtitle,
                thumbnailUrl = pending.thumbnailUrl,
                type = pending.type,
            ),
        ).fold(
            onSuccess = { loaded ->
                if (openedCollection?.browseId == pending.browseId) openedCollection = loaded
            },
            onFailure = { collectionError = it.message ?: "Could not open ${pending.title.ifBlank { "this page" }}" },
        )
    }

    fun openAlbum(browseId: String) = openCollection(browseId)

    /** Copies the track's YouTube Music link. */
    fun shareSong(song: Song) {
        DesktopExternalLinks.copy("https://music.youtube.com/watch?v=${song.videoId}")
        DesktopTrackLog.log("copied a link to '${song.title}'")
    }

    /**
     * The link YouTube Music's own overflow shares for a release, built from the browse id rather
     * than fetched — nothing about it depends on the tracks or the account. Albums and playlists
     * only: an artist's is a channel link, not a release.
     */
    fun shareCollection(collection: DesktopCollection) {
        val id = collection.browseId
        val url = when (collection.type) {
            BrowseType.PLAYLIST -> "https://music.youtube.com/playlist?list=${id.removePrefix("VL")}"
            BrowseType.ALBUM -> "https://music.youtube.com/browse/$id"
            else -> return
        }
        DesktopExternalLinks.copy(url)
        DesktopTrackLog.log("copied a link to '${collection.title}'")
    }

    fun downloadSong(song: Song) {
        // A second tap on something already queued or running calls it off.
        if (song.videoId in downloadInProgress) {
            DesktopDownloadQueue.cancel(song.videoId)
            return
        }
        if (song.videoId in downloads.map(Song::videoId) || song.localPath != null) return
        DesktopDownloadQueue.enqueue(song)
    }

    /** Deletes the file a download saved and forgets the record. */
    fun removeDownload(song: Song) {
        scope.launch {
            withContext(Dispatchers.IO) { DesktopDownloadManager.delete(song) }
            downloads = downloads.filterNot { it.videoId == song.videoId }
            persistence.saveDownloads(downloads)
        }
    }

    /** Everything on a page, in the order it is listed. */
    fun downloadAll(songs: List<Song>) {
        val have = downloads.map(Song::videoId).toSet()
        DesktopDownloadQueue.enqueueAll(
            songs.filterNot { it.videoId in have || it.localPath != null },
        )
    }

    /** Keeps a station queued ahead of whatever is playing. */

    /**
     * How many AutoPlay tracks the party already has waiting after [videoId], or null outside a
     * party or while its queue does not hold that track yet.
     */
    fun partyAutoplayWaiting(party: DesktopListenTogether.State, videoId: String): Int? {
        if (!party.inParty) return null
        if (partyQueueIndexOf(party.queue, party.playback, videoId) < 0) return null
        return partyUpcomingAfter(party.queue, party.playback, videoId).count { it.fromAutoplay }
    }

    /**
     * Sends [suggestions] to the party and waits for them to come back; the phone's
     * `topUpPartyAutoplay`.
     *
     * Serialised, and the send-and-wait not cancellable, because a track change cancels the load in
     * flight and starts another: the second used to read the party before the first batch had
     * echoed, find nothing waiting, and send the same station again — every suggestion twice, on
     * every device. Holding the lock until the echo means the next top-up reads a queue that
     * already has these in it, and filters against it.
     */
    suspend fun topUpPartyAutoplay(currentId: String, suggestions: List<Song>): Boolean =
        partyAutoplayLock.withLock {
            withContext(NonCancellable) {
                val party = DesktopListenTogether.state.value
                val waiting = partyUpcomingAfter(party.queue, party.playback, currentId)
                val waitingIds = waiting.mapTo(HashSet()) { it.videoId }
                val room = MAX_QUEUED_AUTOPLAY - waiting.count { it.fromAutoplay }
                val fresh = suggestions.filterNot { it.videoId in waitingIds }.take(room.coerceAtLeast(0))
                if (fresh.isEmpty()) return@withContext true
                DesktopListenTogether.queueAdd(fresh.map { it.toPartyTrack(0L) })
                val added = fresh.first().videoId
                withTimeoutOrNull(PARTY_AUTOPLAY_ECHO_TIMEOUT_MS) {
                    DesktopListenTogether.state.first { state -> state.queue.items.any { it.videoId == added } }
                } != null
            }
        }

    fun loadAutoplaySongs(playFirst: Boolean = false) {
        val current = selectedSong ?: return
        val party = DesktopListenTogether.state.value
        if (!DesktopAutoplay.enabled(party, autoplay) ||
            repeatMode == DesktopRepeatMode.ALL ||
            (party.inParty && DesktopAutoplay.supplierId(party) != party.you?.memberId)
        ) return

        if (dontRepeatSuggestions) sessionSongHistory += current

        // In a party the server's queue is the one being topped up, and this computer's copy of it
        // lags by however long reconcile is held off. Counted from the copy, a batch that had
        // already landed read as missing and the same station went out again.
        val queued = partyAutoplayWaiting(party, current.videoId)
            ?: liveQueue.songs.drop(liveQueue.index + 1).count { it.fromAutoplay }
        val needed = MAX_QUEUED_AUTOPLAY - queued
        if (needed <= 0 && !playFirst) return
        // One request per seed.
        if (!playFirst && autoplaySeed == current.videoId) return
        autoplaySeed = current.videoId

        autoplayJob?.cancel()
        autoplayJob = scope.launch {
            var remaining = needed.coerceAtLeast(1)
            var emptyRefreshesRemaining = 1
            while (isActive) {
                DesktopTrackLog.log("autoplay: building a station from '${current.title}'")
                val at = liveQueue.songs.size
                // What the queue holds — the party's too — plus everything this session has
                // already offered.
                val partyNow = DesktopListenTogether.state.value
                val partyQueue = partyNow
                    .takeIf { it.inParty }?.queue?.items.orEmpty().map { it.toDesktopSong() }
                val known = if (dontRepeatSuggestions) {
                    liveQueue.songs + partyQueue + sessionSongHistory
                } else {
                    liveQueue.songs + partyQueue
                }
                val suggestions = DesktopAutoplay.tracksFor(
                    existing = known,
                    seedSong = current,
                    limit = remaining,
                ).getOrElse { failure ->
                    DesktopTrackLog.log("autoplay: could not build a station: ${failure.message}")
                    emptyList()
                }
                // The listener may have moved on while the station was being fetched; appending
                // then would attach it to the wrong seed.
                val latestParty = DesktopListenTogether.state.value
                if (selectedSong?.videoId != current.videoId ||
                    !DesktopAutoplay.enabled(latestParty, autoplay) ||
                    (latestParty.inParty && DesktopAutoplay.supplierId(latestParty) != latestParty.you?.memberId)
                ) return@launch
                if (suggestions.isEmpty()) {
                    DesktopTrackLog.log("autoplay: no station came back for '${current.title}'")
                    // Match the phone's bounded recovery: retry once after a short delay, but only
                    // while the current track genuinely has no tail to play next.
                    if (liveQueue.index < 0 || liveQueue.index != liveQueue.songs.lastIndex) return@launch
                    if (emptyRefreshesRemaining-- <= 0) return@launch
                    delay(AUTOPLAY_EMPTY_REFRESH_DELAY_MS)
                    remaining = MAX_QUEUED_AUTOPLAY
                    continue
                }
                // The station carries on from what was playing, so it keeps that queue's origin —
                // Android holds the source on every queue item, not just the ones picked by hand.
                val inherited = current.playbackSource?.let {
                    DesktopQueueSource(it, current.playbackSourceType ?: PlaybackSourceType.QUEUE, current.playbackSourceId)
                }
                val queuedSuggestions = suggestions.map { it.withSource(inherited) }
                if (latestParty.inParty) {
                    // Match the phone: the server owns the party queue. Do not mutate this one
                    // device first; queueAdd is echoed back and all members apply it atomically.
                    val landed = topUpPartyAutoplay(current.videoId, queuedSuggestions)
                    if (!landed) {
                        autoplaySeed = null
                        DesktopTrackLog.log("autoplay: party refused the queue top-up; retry is armed")
                        return@launch
                    }
                    if (playFirst) DesktopListenTogether.next()
                } else {
                    liveQueue = liveQueue.append(queuedSuggestions)
                    saveQueue()
                    // The mix continues from where it was added rather than starting a queue of
                    // its own.
                    if (playFirst) playQueueIndex(at)
                }
                if (dontRepeatSuggestions) sessionSongHistory += suggestions
                DesktopTrackLog.log(
                    "autoplay: queued ${suggestions.size} after '${current.title}'",
                )
                return@launch
            }
        }
    }

    /**
     * Turning AutoPlay on or off, and what that does to the queue.
     *
     * Android runs one path for this — `toggleAutoplayFromNotification` — whether the switch was
     * thrown in the player or anywhere else, and it does more than set a flag. Off drops the
     * suggestions it had already queued, because switching it off is the listener saying they do
     * not want them. On clears the seed first, so the station is fetched again for the track
     * already playing rather than being refused as one this seed has been loaded for.
     */
    fun setAutoplay(enabled: Boolean) {
        if (DesktopListenTogether.state.value.controlsLocked) return
        autoplay = enabled
        persistence.saveBoolean("autoplay", enabled)
        autoplayJob?.cancel()
        autoplayJob = null
        autoplaySeed = null
        if (DesktopListenTogether.state.value.inParty) {
            // As on the phone, the server owns the effective setting and queue while connected.
            // Keep the personal preference for after leaving, but do not trim or top up this one
            // device ahead of the server's state broadcast.
            DesktopListenTogether.setAutoplay(enabled)
            return
        }
        if (enabled) {
            loadAutoplaySongs()
        } else {
            val trimmed = liveQueue.withoutAutoplay()
            if (trimmed !== liveQueue) {
                liveQueue = trimmed
                saveQueue()
            }
        }
    }

    fun playNext() {
        if (radioPlayer.state.value.station != null) return
        if (DesktopListenTogether.state.value.controlsLocked) return
        // The ordinary skip, and the fallback when a mix cannot be had.
        val cutToNext: () -> Unit = {
            when {
                liveQueue.hasNext -> {
                    liveQueue = liveQueue.next()
                    playCurrent()
                }
                repeatMode == DesktopRepeatMode.ALL && liveQueue.songs.isNotEmpty() -> {
                    liveQueue = liveQueue.copy(index = 0)
                    playCurrent()
                }
                DesktopAutoplay.enabled(DesktopListenTogether.state.value, autoplay) ->
                    loadAutoplaySongs(playFirst = true)
            }
        }
        // Automix on skip: when enabled and the next track is blendable, crossfade into it over a
        // short fade instead of cutting. The blend's own hand-off advances the queue, so a
        // successful mix returns here without touching liveQueue; anything else cuts.
        // A skip made in the first [instantSkipSeconds] cuts as well: a song the listener has barely
        // heard is being rejected, not mixed out of, and the blend would carry it on for seconds.
        // A lossless or Hi-Res track has no deck open ahead of it, so the engine arms one on the
        // press and holds the skip while it opens; the cut above runs if it never answers.
        val pastInstantSkip = instantSkipSeconds == 0 ||
            playbackEngine.state.value.positionMs >= instantSkipSeconds * 1000L
        if (automixOnSkip && pastInstantSkip && repeatMode != DesktopRepeatMode.ONE) {
            val currentId = selectedSong?.videoId
            val upcoming = currentId?.let {
                liveQueue.followingFor(it, repeatAll = repeatMode == DesktopRepeatMode.ALL)
            }
            if (upcoming != null &&
                playbackEngine.beginSkipMix(upcoming.videoId, SKIP_MIX_ON_SKIP_SECONDS, cutToNext)
            ) {
                return
            }
        }
        cutToNext()
    }

    fun playPrevious() {
        if (radioPlayer.state.value.station != null) return
        if (DesktopListenTogether.state.value.controlsLocked) return
        val positionMs = playbackEngine.state.value.positionMs

        // After 10 seconds, Previous restarts the current song.
        if (positionMs > BACK_RESTARTS_AFTER_MS) {
            playbackEngine.seekTo(0L)
            partySyncHolder[0]?.onLocalIntent()
            return
        }

        // Within 10 seconds, Previous goes to the previous song in queue.
        if (!liveQueue.hasPrevious) return
        liveQueue = liveQueue.previous()
        playCurrent()
    }

    /**
     * Shuffle rearranges the queue rather than switching playback to a hidden random order, so what
     * the queue shows stays what plays.
     */
    fun setShuffle(enabled: Boolean) {
        if (DesktopListenTogether.state.value.controlsLocked) return
        shuffle = enabled
        persistence.saveBoolean("shuffle", enabled)
        liveQueue = if (enabled) {
            preShuffleOrder = liveQueue.orderKeys()
            liveQueue.shuffledAhead()
        } else {
            liveQueue.inOrderOf(preShuffleOrder).also { preShuffleOrder = emptyList() }
        }
        saveQueue()
        partySyncHolder[0]?.onLocalIntent()
    }

    fun closePlaylistDialogs() {
        overlays.playlistDialog = false
        playlistTarget = null
        overlays.rename = false
        overlays.delete = false
        overlays.reorder = false
        playlistError = null
        playlistBusy = false
    }

    /** Creates a playlist — on the account when there is one, otherwise here. */
    fun createPlaylist(title: String, privacy: PlaylistPrivacy) {
        val name = title.trim()
        if (name.isBlank()) return
        val seed = playlistTarget
        // A track from another source cannot go in an account playlist, so the playlist made to
        // hold it is one kept here.
        if (!DesktopYouTubeAuth.isSignedIn || (seed != null && !DesktopSearchClient.isVideoId(seed.videoId))) {
            playlists = playlists + DesktopPlaylist(title = name, songs = listOfNotNull(seed))
            persistence.savePlaylists(playlists)
            closePlaylistDialogs()
            return
        }
        playlistBusy = true
        playlistError = null
        scope.launch {
            DesktopSearchClient.createPlaylist(name, privacy, listOfNotNull(seed?.videoId))
                .onSuccess { playlistId ->
                    closePlaylistDialogs()
                    editPlaylistShelf { items ->
                        val card = ShelfItem(
                            title = name,
                            // Only what this request itself establishes: an unseeded playlist gets
                            // a card of just its name rather than a guess at what the feed will
                            // call it.
                            subtitle = if (seed != null) DesktopStrings["d_one_song", "1 song"] else "",
                            thumbnailUrl = seed?.thumbnailUrl,
                            videoId = null,
                            browseId = "VL$playlistId",
                        )
                        // Leads the shelf because it is the newest, which is the order the feed
                        // itself comes in.
                        listOf(card) + items.filterNot { it.browseId == card.browseId }
                    }
                    libraryStale = true
                }
                .onFailure {
                    playlistBusy = false
                    playlistError = it.message ?: "Could not create the playlist"
                }
        }
    }

    fun openPlaylist(playlist: DesktopPlaylist) {
        showCollection(
            DesktopCollection(
                browseId = playlist.id,
                title = playlist.title,
                subtitle = DesktopStrings.format(
                    "d_playlist_songs",
                    playlist.songs.size,
                    fallback = "Playlist • ${playlist.songs.size} songs",
                ),
                thumbnailUrl = playlist.songs.firstOrNull()?.thumbnailUrl,
                type = BrowseType.PLAYLIST,
                songs = playlist.songs,
            ),
        )
    }

    fun addToPlaylist(playlist: DesktopPlaylist) {
        val song = playlistTarget ?: return
        playlists = playlists.map { current ->
            if (current.id == playlist.id) current.copy(songs = (current.songs + song).distinctBy(Song::videoId)) else current
        }
        persistence.savePlaylists(playlists)
        playlistTarget = null
    }

    fun addToAccountPlaylist(playlist: UserPlaylist) {
        val song = playlistTarget ?: return
        playlistBusy = true
        playlistError = null
        scope.launch {
            DesktopSearchClient.addToPlaylist(playlist.playlistId, listOf(song.videoId))
                .onSuccess { closePlaylistDialogs() }
                .onFailure {
                    DesktopTrackLog.log("playlist: could not add ${song.videoId}: ${it.message}")
                    playlistBusy = false
                    playlistError = it.message ?: "Could not add to ${playlist.title}"
                }
        }
    }

    playbackEngine = remember {
        DesktopPlaybackEngine(
            onEnded = {
                selectedSong?.let(DesktopScrobbling::onPlaybackEnded)
                if (DesktopSleepTimer.afterTrack.value) {
                    DesktopSleepTimer.cancel()
                } else if (repeatMode == DesktopRepeatMode.ONE) {
                    // Replayed in place. [startSong] makes the track a queue of one, which threw
                    // the rest of the queue away the first time it repeated.
                    val song = selectedSong
                    if (song != null && liveQueue.current?.videoId == song.videoId) {
                        playCurrent()
                    } else {
                        song?.let { startSong(it, true) }
                    }
                } else {
                    playNext()
                }
            },
            onCrossfaded = { song ->
                scope.launch {
                    // The queue moves with the audio. Only the selection used to, which left the
                    // queue on the track that had just finished — so Next "advanced" onto the song
                    // already playing, and every later skip landed one behind.
                    liveQueue = liveQueue.afterHandoffTo(song.videoId)
                    saveQueue()
                    selectedSong = song
                    history = (listOf(song) + history.filterNot { it.videoId == song.videoId }).take(50)
                    persistence.saveHistory(history)
                    DesktopScrobbling.updateNowPlaying(song)
                    partySyncHolder[0]?.onLocalIntent()
                }
            },
        )
    }
    val partySync = remember(playbackEngine) {
        DesktopPartySync(
            scope = scope,
            engine = playbackEngine,
            playTrack = { shared, at ->
                // The phone's PartySync.load: the party's running order, standing on its track —
                // so the queue on screen moves with the song rather than keeping the old one as
                // "now playing" and the new one as the first thing still to come.
                val songs = DesktopQueue.adopt(liveQueue.songs, shared.map { it.toDesktopSong() })
                liveQueue = DesktopQueue(songs, at.coerceIn(songs.indices)).trimmed()
                liveQueue.current?.let { song ->
                    radioPlayer.stop()
                    selectedSong = song
                    playbackEngine.load(song, playWhenReady = false)
                }
            },
            localQueue = { liveQueue.songs to liveQueue.index },
            applyPartyUpcoming = { upcoming ->
                val aligned = liveQueue.withPartyUpcoming(upcoming.map { it.toDesktopSong() })
                if (aligned !== liveQueue) liveQueue = aligned
            },
            applyPartyAutoplay = { _ ->
                // The party setting overrides the personal preference only while connected. The
                // phone deliberately does not persist it or delete queue rows on this device.
                autoplayJob?.cancel()
                autoplayJob = null
                autoplaySeed = null
            },
            onEnteredParty = {
                radioPlayer.stop()
                if (personalQueueStash == null) {
                    personalQueueStash = liveQueue
                    personalPositionStash = playbackEngine.state.value.positionMs
                    personalPlayingStash = playbackEngine.state.value.isPlaying
                }
            },
            onLeftParty = {
                personalQueueStash?.let { stashed ->
                    liveQueue = stashed
                    selectedSong = stashed.current
                    stashed.current?.let {
                        playbackEngine.load(it, personalPlayingStash, personalPositionStash)
                    }
                    saveQueue()
                }
                personalQueueStash = null
            },
        )
    }
    DisposableEffect(partySync) {
        partySyncHolder[0] = partySync
        partySync.start()
        onDispose {
            partySync.stop()
            if (partySyncHolder[0] === partySync) partySyncHolder[0] = null
        }
    }
    fun togglePlayPauseFromUser() {
        if (radioPlayer.state.value.station != null) {
            if (radioPlayer.state.value.isPlaying || radioPlayer.state.value.isLoading) radioPlayer.pause()
            else radioPlayer.play()
            return
        }
        if (partySync.handleLockedPlayPause()) return
        playbackEngine.togglePlayPause()
        partySync.onLocalIntent()
    }
    fun playFromUser() {
        if (radioPlayer.state.value.station != null) {
            radioPlayer.play()
            return
        }
        if (DesktopListenTogether.state.value.controlsLocked) {
            if (!playbackEngine.state.value.isPlaying) partySync.handleLockedPlayPause()
            return
        }
        playbackEngine.play()
        partySync.onLocalIntent()
    }
    fun pauseFromUser() {
        if (radioPlayer.state.value.station != null) {
            radioPlayer.pause()
            return
        }
        if (DesktopListenTogether.state.value.controlsLocked) {
            if (playbackEngine.state.value.isPlaying) partySync.handleLockedPlayPause()
            return
        }
        playbackEngine.pause()
        partySync.onLocalIntent()
    }
    val mprisController = remember(playbackEngine) {
        DesktopMprisController(
            onPlay = ::playFromUser,
            onPause = ::pauseFromUser,
            onPlayPause = ::togglePlayPauseFromUser,
            onNext = ::playNext,
            onPrevious = ::playPrevious,
            onShuffleChanged = { enabled ->
                shuffle = enabled
                persistence.saveBoolean("shuffle", enabled)
            },
            onLoopStatusChanged = { status ->
                repeatMode = when (status) {
                    "Track" -> DesktopRepeatMode.ONE
                    "Playlist" -> DesktopRepeatMode.ALL
                    else -> DesktopRepeatMode.OFF
                }
                persistence.saveString("repeat_mode", repeatMode.name)
            },
            onRateChanged = { value ->
                playbackSpeed = value.toFloat().coerceIn(0.25f, 3.0f)
                persistence.saveString("playback_speed", playbackSpeed.toString())
            },
            onVolumeChanged = { value ->
                volume = value.toFloat().coerceIn(0.0f, 1.0f)
                persistence.saveString("volume", volume.toString())
            },
            onSeek = { positionMs ->
                if (radioPlayer.state.value.station == null && !DesktopListenTogether.state.value.controlsLocked) {
                    playbackEngine.seekTo(positionMs)
                    partySync.onLocalIntent()
                }
            },
        )
    }
    DisposableEffect(mprisController) {
        mprisController.start()
        onDispose { mprisController.stop() }
    }
    // The Windows half of the same idea. Linux has MPRIS above; on Windows this
    // is what the media keys and the volume flyout's card talk to.
    DisposableEffect(playbackEngine) {
        DesktopWindowsMedia.start(
            DesktopWindowsMedia.Controller(
                onPlay = ::playFromUser,
                onPause = ::pauseFromUser,
                onNext = ::playNext,
                onPrevious = ::playPrevious,
                onStop = ::pauseFromUser,
                onSeek = { positionMs ->
                    if (radioPlayer.state.value.station == null && !DesktopListenTogether.state.value.controlsLocked) {
                        playbackEngine.seekTo(positionMs)
                        partySync.onLocalIntent()
                    }
                },
            ),
        )
        onDispose { DesktopWindowsMedia.stop() }
    }

    LaunchedEffect(playbackSpeed) { playbackEngine.setPlaybackSpeed(playbackSpeed) }
    LaunchedEffect(volume) {
        playbackEngine.setVolume(volume)
        radioPlayer.setVolume(volume)
    }
    LaunchedEffect(audioQuality) { playbackEngine.setAudioQuality(audioQuality.name) }
    LaunchedEffect(automixPerformance) {
        playbackEngine.setAutomixPerformance(automixPerformance)
    }
    // Every one of the seven settings renders the same curve, so they are collected together
    // rather than destructured — seven sources of one tuning is seven chances to read them in the
    // wrong order.
    val eqEnabled by DesktopEqualizerSettings.enabled.collectAsState()
    val eqMode by DesktopEqualizerSettings.mode.collectAsState()
    val eqToneX by DesktopEqualizerSettings.toneX.collectAsState()
    val eqToneY by DesktopEqualizerSettings.toneY.collectAsState()
    val eqFocused by DesktopEqualizerSettings.focused.collectAsState()
    val eqBalance by DesktopEqualizerSettings.balance.collectAsState()
    val eqBands by DesktopEqualizerSettings.bands.collectAsState()
    val eqLimiter by DesktopEqualizerSettings.peakLimiter.collectAsState()
    val eqVocalRemoval by DesktopEqualizerSettings.vocalRemoval.collectAsState()
    LaunchedEffect(eqEnabled, eqMode, eqToneX, eqToneY, eqFocused, eqBalance, eqBands, eqLimiter, eqVocalRemoval) {
        playbackEngine.setEqualizer(
            eqEnabled,
            DesktopEqualizerSettings.audioCurve(),
            eqBalance,
            eqLimiter,
            eqVocalRemoval,
        )
    }
    LaunchedEffect(spatialAudio, skipSilence, outputPrecision) {
        playbackEngine.setSpatialAudio(spatialAudio)
        playbackEngine.setSkipSilence(skipSilence)
        playbackEngine.setOutputPrecision(outputPrecision)
    }
    // Not while listening together, exactly as on the phone: a blend starts the next track early,
    // by a length this computer decides from its own copy of the audio, so every member would begin
    // the next song at a different moment and be dragged back by a correcting seek. The transition a
    // party shares is the plain one. The settings themselves are left alone and come back after.
    val inParty = partyState.inParty
    LaunchedEffect(automix, automixOnSkip, automixPhraseBars, crossfadeSeconds, inParty, audioQuality, selectedSong?.videoId, queue, liveQueue.index, shuffle, repeatMode) {
        playbackEngine.setAutomixEnabled(automix && !inParty)
        playbackEngine.setAutomixPhraseBars(if (automix && !inParty) automixPhraseBars else 0)
        playbackEngine.setCrossfadeSeconds(if (inParty) 0 else crossfadeSeconds)
        // A party mixes only with the plain transition, so a skip there cuts like it always did.
        playbackEngine.setSkipMixEnabled(automixOnSkip && !inParty)
        // Exactly what Next would play, so a crossfade can never blend into anything else. Shuffle
        // is already the queue's order; picking "the first track that is not this one" under it
        // blended into a song from history whenever the current one was not at the top.
        // Repeat-one prepares nothing: the track ends and [onEnded] starts it again, rather than
        // blending into the next one.
        val next = selectedSong
            ?.takeIf { repeatMode != DesktopRepeatMode.ONE }
            ?.let { liveQueue.followingFor(it.videoId, repeatAll = repeatMode == DesktopRepeatMode.ALL) }
        playbackEngine.prepareNext(next)
        // Then the tracks behind it, opened while the current one plays: a song that simply starts
        // — no crossfade, no Automix, and nothing prepared by the call above — otherwise spends its
        // first seconds walking the sources for a stream URL and opening FFmpeg on it. Repeat-one
        // wants nothing ahead of it, since the track ends and starts again.
        val ahead = selectedSong
            ?.takeIf { repeatMode != DesktopRepeatMode.ONE }
            ?.let {
                liveQueue.followingManyFor(
                    it.videoId,
                    repeatAll = repeatMode == DesktopRepeatMode.ALL,
                    limit = DesktopPlaybackEngine.PRELOAD_SONGS,
                )
            }
            ?: emptyList()
        playbackEngine.preloadUpcoming(ahead)
    }
    // Topped up on every track change, the way Android does it, rather than only once the queue has
    // run dry.
    LaunchedEffect(selectedSong?.videoId, autoplay, repeatMode) {
        loadAutoplaySongs()
    }
    val partyAutoplaySupplier = DesktopAutoplay.supplierId(partyState)
    LaunchedEffect(
        partyState.code,
        partyState.playback.autoplayEnabled,
        partyAutoplaySupplier,
    ) {
        if (partyState.inParty &&
            partyState.playback.autoplayEnabled &&
            partyAutoplaySupplier == partyState.you?.memberId
        ) {
            autoplayJob?.cancel()
            autoplayJob = null
            autoplaySeed = null
            loadAutoplaySongs()
        }
    }
    LaunchedEffect(liveQueue.songs.size, liveQueue.index, partyState.queue.seq) {
        val currentId = selectedSong?.videoId
        val shouldRefresh = DesktopAutoplay.queueNeedsRefresh(
            enabled = DesktopAutoplay.enabled(partyState, autoplay),
            repeatAll = repeatMode == DesktopRepeatMode.ALL,
            currentIndex = liveQueue.index,
            itemCount = liveQueue.songs.size,
            loadInProgress = autoplayJob?.isActive == true,
        )
        // Only re-arm a seed that has already been attempted. The track-change effect above owns
        // the initial load; this effect owns later local or server queue edits that expose a tail.
        if (shouldRefresh && currentId != null && autoplaySeed == currentId) {
            autoplayJob = null
            autoplaySeed = null
            loadAutoplaySongs()
        }
    }
    LaunchedEffect(Unit) {
        while (currentCoroutineContext().isActive) {
            delay(1_000L)
            if (DesktopSleepTimer.isExpired()) {
                DesktopSleepTimer.cancel()
                playbackEngine.pause()
                radioPlayer.pause()
            }
        }
    }
    val playback by playbackEngine.state.collectAsState()
    val liveRadio = radioPlayback.station != null
    val radioSong = radioPlayback.station?.let { station ->
        // What the station is carrying is read off its own stream, on the page's timer. A station that
        // says nothing is only ever "live on the system's output".
        val line = radioOffAir[station.id]
        Song(
            "live-radio:${station.id}",
            station.title,
            line?.let { "${DesktopStrings["d_radio_live", "Live"]} · $it" }
                ?: DesktopStrings["d_radio_system_output", "Radio uses system default output"],
            station.artworkUrl,
        )
    }
    val transportPlayback = if (liveRadio) DesktopPlaybackState(
        song = radioSong,
        isPlaying = radioPlayback.isPlaying,
        isLoading = radioPlayback.isLoading,
        volume = volume,
    ) else playback
    LaunchedEffect(playback.isPlaying, liveRadio, inParty) {
        if (liveRadio) {
            if (inParty) radioPlayer.stop()
            else if (playback.isPlaying) playbackEngine.pause()
        }
    }

    // The shared player reads the playhead off this one object, and only where it
    // draws it — see PlaybackPosition — so a tick never recomposes the player.
    val playerPosition = remember { PlaybackPosition() }
    LaunchedEffect(playbackEngine) {
        playbackEngine.state.collect {
            // The audio thread's own timestamp, not this collector's: it runs on the UI thread and
            // gets to a reading as late as the UI is busy.
            playerPosition.report(it.positionMs, it.positionSampledAtNanos)
            playerPosition.seeks = it.seeks
            playerPosition.advancing = !it.awaitingAudio
        }
    }

    // Lyrics and motion artwork follow whatever is *playing*, not whatever page happens to be open.
    // This is where Android keeps it — `MainActivity`, keyed on the player's own track — so the
    // lookup starts the moment a track does and the panel is already populated by the time anyone
    // opens the player. Run from the player page instead, nothing was fetched until it was opened,
    // and a track half a minute in then showed a skeleton and arrived mid-song.
    var lyrics by remember { mutableStateOf<DesktopLyrics?>(null) }
    // The provider picked for this track in the player's lyrics drawer, if any.
    var lyricsOnly by remember(selectedSong?.videoId) { mutableStateOf<String?>(null) }
    // A title and artist typed into the song menu's lyrics search, overriding the
    // track's own credits for the lookup — for a cover or a mislabelled upload.
    var lyricsQuery by remember(selectedSong?.videoId) { mutableStateOf<Pair<String, String>?>(null) }
    // Bumped on every search so re-running the same query still refetches.
    var lyricsSearchTick by remember(selectedSong?.videoId) { mutableStateOf(0) }
    // What each provider's most recent race turned up, so the song menu's
    // provider drawer can say "Found" or "Not found" per source, not just name
    // the winner. It is keyed on the track and is only ever added to, never
    // cleared mid-track: a repeat search a cache answers straight away fires no
    // callbacks, so clearing on start would blank the very states it should hold.
    val lyricsProviderResults = remember(selectedSong?.videoId) {
        mutableStateOf<Map<LyricsSource, LyricsProviderState>>(emptyMap())
    }
    var lyricsLoading by remember { mutableStateOf(false) }
    var lyricsError by remember { mutableStateOf<String?>(null) }
    var canvas by remember { mutableStateOf<DesktopCanvasArtwork?>(null) }

    // The length the lyrics are matched on, taken once per track. A better rendition swapped in
    // mid-song reports its own, slightly different length — or none, for a manifest — and keyed
    // on the live duration, every such upgrade threw away the lyrics on screen and fetched them
    // all over again. Only the playing track's own report counts: right after a skip, [playback]
    // still describes the track before.
    var lyricsLengthMs by remember(selectedSong?.videoId) { mutableStateOf(0L) }
    val playingLengthMs = playback.durationMs.takeIf { playback.song?.videoId == selectedSong?.videoId } ?: 0L
    LaunchedEffect(selectedSong?.videoId, playingLengthMs > 0L) {
        if (lyricsLengthMs <= 0L && playingLengthMs > 0L) lyricsLengthMs = playingLengthMs
    }

    // Keyed on the duration too: it lands a beat after the track, and a database match needs it,
    // so looking up against a length of zero would settle on the wrong recording.
    LaunchedEffect(
        selectedSong?.videoId,
        lyricsLengthMs,
        syncedLyrics,
        prioritizeSyllables,
        lyricsOn,
        lyricsOrder,
        lyricsOnly,
        lyricsQuery,
        lyricsSearchTick,
    ) {
        val current = selectedSong
        lyrics = null
        lyricsError = null
        if (current == null) {
            lyricsLoading = false
            return@LaunchedEffect
        }
        if (!syncedLyrics) {
            lyricsError = "Synced lyrics are switched off"
            lyricsLoading = false
            return@LaunchedEffect
        }
        // A length is what a database match is made on, and it lands a beat after the track. Looking
        // up without one would settle on whichever recording shares the name — so this waits, the
        // same way Android's `loadLyrics` turns the call away until a duration exists.
        val length = lyricsLengthMs.takeIf { it > 0L } ?: current.durationMillis()
        if (length <= 0L) {
            lyricsLoading = true
            return@LaunchedEffect
        }
        lyricsLoading = true
        // The manual search asks the same providers under the typed words rather
        // than the track's own credits; a provider switch keeps that query.
        val probe = lyricsQuery?.let { (title, artist) -> current.copy(title = title, artist = artist) } ?: current
        DesktopLyricsClient.lookup(
            probe,
            length,
            only = lyricsOnly,
            // Each provider reports itself as the race runs; the callback is on
            // the IO thread, so the map is updated back on the composition's own.
            onSourceResult = { source, found ->
                scope.launch {
                    lyricsProviderResults.value += source to
                        if (found) LyricsProviderState.FOUND else LyricsProviderState.NOT_FOUND
                }
            },
        ).fold(
            onSuccess = { lyrics = it },
            onFailure = { lyricsError = it.message ?: "Lyrics unavailable" },
        )
        lyricsLoading = false
    }

    // A manual ask from the song menu's lyrics drawer, for a track whose automatic
    // lookup came up empty and whose real title or artist the listener knows
    // better than the tag. It just sets the query and nudges the effect above,
    // which owns the loading state and the result — so the drawer's provider
    // list updates through the same path as the automatic lookup.
    fun searchLyrics(title: String, artist: String) {
        lyricsQuery = title to artist
        lyricsSearchTick++
    }

    // The song menu's lyrics offset nudges the whole synced lyric against the clock. It is a
    // per-track adjustment, so a new track starts back at zero; the desktop owns the reset so the
    // phone's sheet, which carries the offset across tracks, is left alone.
    val lyricsOffsetMs by DesktopPlayerSettings.lyricsOffsetMs.collectAsState()
    LaunchedEffect(selectedSong?.videoId) {
        DesktopPlayerSettings.setLyricsOffsetMs(0)
    }

    LaunchedEffect(selectedSong?.videoId, animatedCanvas) {
        val current = selectedSong
        if (current == null || !animatedCanvas) {
            canvas = null
            return@LaunchedEffect
        }
        // Paint what is already known before waiting on anything: re-opening the player on a track
        // resolved a minute ago should not go dark on its way back to the same clip.
        canvas = DesktopCanvasClient.cached(current)
        canvas = withContext(Dispatchers.IO) { DesktopCanvasClient.lookup(current) }
    }

    LaunchedEffect(
        transportPlayback.song?.videoId,
        transportPlayback.isPlaying,
        transportPlayback.durationMs,
        transportPlayback.volume,
        transportPlayback.error,
        shuffle,
        repeatMode,
        playbackSpeed,
    ) {
        mprisController.update(
            playback = transportPlayback,
            shuffle = shuffle,
            loopStatus = when (repeatMode) {
                DesktopRepeatMode.OFF -> "None"
                DesktopRepeatMode.ALL -> "Playlist"
                DesktopRepeatMode.ONE -> "Track"
            },
            rate = playbackSpeed.toDouble(),
        )
    }
    LaunchedEffect(playback.song?.videoId, playback.isPlaying, playback.durationMs) {
        DesktopScrobbling.onPlaybackStateChanged(playback)
    }
    LaunchedEffect(transportPlayback.song?.videoId, transportPlayback.isPlaying, transportPlayback.durationMs) {
        DesktopWindowsMedia.publish(transportPlayback)
    }
    // Refetched on opening the page rather than cached for the session: the
    // point of it is that something played elsewhere shows up here.
    LaunchedEffect(destination, youtubeSignedIn, sessionRevision) {
        if (destination != DesktopDestination.HISTORY || !youtubeSignedIn) return@LaunchedEffect
        DesktopSearchClient.history().onSuccess { remoteHistory = it }
    }
    LaunchedEffect(selectedSong?.videoId, overlays.nowPlaying) {
        trackLinks = null
        val current = selectedSong ?: return@LaunchedEffect
        // Only while the player is up, so playing from the mini player costs
        // nothing, and only when something is actually missing.
        if (!overlays.nowPlaying) return@LaunchedEffect
        if (current.artistId != null && current.albumId != null) return@LaunchedEffect
        trackLinks = DesktopSearchClient.trackLinks(current.videoId).getOrNull()
    }
    // Keyed on the same things, plus the rate: Discord counts the bar down on
    // its own clock, so only a change to what it was told is worth another push.
    // The stream format is keyed too: the decoder measures it a beat after the
    // track starts, and the Discord card's audio-quality line reads off it.
    LaunchedEffect(
        playback.song?.videoId,
        playback.isPlaying,
        playback.durationMs,
        playbackSpeed,
        playback.streamFormat,
    ) {
        DesktopDiscordRpc.onPlaybackStateChanged(playback, playbackSpeed)
    }
    // The account's own history on YouTube Music, which is what the home feed is built out of.
    DisposableEffect(playback.song?.videoId) {
        val previous = playbackEngine.state.value.positionMs / 1_000
        onDispose { DesktopPlaybackTracker.onTrackChanged(previous) }
    }
    LaunchedEffect(playback.song?.videoId, playback.isPlaying) {
        val songId = playback.song?.videoId ?: return@LaunchedEffect
        if (!playback.isPlaying) return@LaunchedEffect
        DesktopPlaybackTracker.onPlaying(songId)
        while (currentCoroutineContext().isActive) {
            delay(HISTORY_SAMPLE_MS)
            val snapshot = playbackEngine.state.value
            if (snapshot.song?.videoId != songId) break
            DesktopPlaybackTracker.onProgress(songId, snapshot.positionMs / 1_000)
        }
    }
    LaunchedEffect(playback.song?.videoId, playback.isPlaying) {
        val songId = playback.song?.videoId ?: return@LaunchedEffect
        if (!playback.isPlaying) return@LaunchedEffect
        var previousPosition = playback.positionMs
        var countedAsPlay = songId in statsCountedSongs
        while (currentCoroutineContext().isActive) {
            delay(STATS_SAMPLE_MS)
            val snapshot = playbackEngine.state.value
            if (!snapshot.isPlaying || snapshot.song?.videoId != songId) break
            val position = snapshot.positionMs
            val delta = (position - previousPosition).takeIf { it in 1L..STATS_MAX_DELTA_MS } ?: 0L
            if (delta > 0L) {
                val threshold = snapshot.durationMs.takeIf { it > 0L }?.let { minOf(it / 2L, SCROBBLE_THRESHOLD_MS) }
                    ?: SCROBBLE_THRESHOLD_MS
                val reachedThreshold = !countedAsPlay && position >= threshold
                DesktopListeningStats.record(snapshot.song, delta, reachedThreshold)
                if (reachedThreshold) {
                    countedAsPlay = true
                    statsCountedSongs = statsCountedSongs + songId
                }
                statsRevision++
            }
            previousPosition = position
        }
    }
    LaunchedEffect(statsRevision) {
        replaySummary = withContext(Dispatchers.IO) { DesktopListeningStats.summary(replayPeriod) }
    }
    DisposableEffect(playbackEngine) {
        onDispose { playbackEngine.release() }
    }

    /** Opens an artist's page, from a card, a search row or the player's credit. */
    fun openArtist(browseId: String, name: String) {
        val target = DesktopArtistTarget(browseId, name)
        if (openedArtist == target && artistState is UiState.Success) return
        openedCollection = null
        overlays.replay = false
        overlays.settingsPage = null
        openedArtist = target
        artistState = UiState.Loading
        artistReloads++
    }

    /**
     * The "…" menu for one row, wherever it is drawn.
     *
     * Built once so a row on Downloads or Local Music offers what the player's own "…" offers —
     * Android opens the same `SongActionsSheet` from every one of them.
     */
    fun songActionsFor(song: Song) = DesktopSongActions(
        signedIn = youtubeSignedIn,
        disliked = song.videoId in dislikedIds,
        downloaded = downloads.any { it.videoId == song.videoId },
        downloadInProgress = song.videoId in downloadInProgress,
        sleepTimerMinutes = sleepTimerMinutes,
        sleepAfterTrack = sleepAfterTrack,
        onToggleDislike = ::toggleDislike,
        onAddToPlaylist = { playlistTarget = it },
        onDownload = ::downloadSong,
        onRemoveDownload = ::removeDownload,
        onStartRadio = ::startRadio,
        onPlayNext = ::playNext,
        onAddToQueue = ::addToQueue,
        onOpenAlbum = ::openAlbum,
        onOpenArtist = { id -> openArtist(id, song.artist) },
        onSleepTimer = { minutes ->
            if (minutes == null) DesktopSleepTimer.cancel() else DesktopSleepTimer.start(minutes)
        },
        onSleepAfterTrack = { DesktopSleepTimer.startAfterTrack() },
        onShare = ::shareSong,
    )

    /** The "…" a song row hangs off, built the same way on every page that offers one. */
    @Composable
    fun songMenu(song: Song) = DesktopSongMenuAnchor(
        song = song,
        liked = song.videoId in likedIds,
        actions = songActionsFor(song),
        onToggleLike = { toggleLike(song) },
        onRevertToOriginal = null,
        onUpgradeQuality = null,
    )

    /** Replay counts artists by name, so the page has to be found from the name alone. */
    fun openArtistByName(name: String) {
        scope.launch {
            val match = DesktopSearchClient.search(name, SearchFilter.ARTISTS)
                .getOrDefault(emptyList())
                .filterIsInstance<SearchResult.Browse>()
                .map { it.item }
                .firstOrNull { it.browseId.isNotBlank() }
                ?: return@launch
            openArtist(match.browseId, match.title)
        }
    }

    /** The same for an album: a row names it, and the source has to be asked which page that is. */
    fun openAlbumByName(album: String?, artist: String?) {
        val name = album?.trim().orEmpty()
        if (name.isEmpty()) return
        scope.launch {
            val match = DesktopSearchClient.search("$name ${artist.orEmpty()}".trim(), SearchFilter.ALBUMS)
                .getOrDefault(emptyList())
                .filterIsInstance<SearchResult.Browse>()
                .map { it.item }
                .firstOrNull { it.browseId.isNotBlank() }
                ?: return@launch
            openCollection(match.browseId, match)
        }
    }

    LaunchedEffect(openedArtist, artistReloads) {
        val target = openedArtist ?: return@LaunchedEffect
        if (artistState !is UiState.Loading) return@LaunchedEffect
        DesktopSearchClient.artistPage(target.browseId).fold(
            onSuccess = { artistState = UiState.Success(it) },
            onFailure = { artistState = UiState.Error(it.message ?: "Could not open ${target.name}") },
        )
    }

    fun toggleSubscription(subscription: SubscriptionState) {
        val target = openedArtist ?: return
        val next = !subscription.subscribed
        // Painted before the network answers and put back if it refuses.
        artistState = (artistState as? UiState.Success)?.let { current ->
            UiState.Success(current.data.copy(subscription = subscription.copy(subscribed = next)))
        } ?: artistState
        scope.launch {
            DesktopSearchClient.setSubscribed(subscription.channelId, next).onFailure { failure ->
                DesktopTrackLog.log("subscription: ${target.name} failed: ${failure.message}")
                artistState = (artistState as? UiState.Success)?.let { current ->
                    UiState.Success(current.data.copy(subscription = subscription))
                } ?: artistState
            }
        }
    }

    /** A search hit or a recent search that is a page: an artist's own, or a collection's. */
    fun openBrowseItem(item: BrowseItem) {
        when {
            // Apple's own page, brought in by the search results beside the source's: it opens
            // the same page every Apple tile on Home opens, not a source collection of that id.
            item.browseId.startsWith(DesktopAppleBrowseClient.PAGE_PREFIX) -> {
                openedCollection = null
                openedArtist = null
                selectedMoodGenre = MoodGenre(item.title, item.browseId, null, item.thumbnailUrl)
                applePageCredit = item.subtitle
                moodGenreShelves = UiState.Loading
            }
            item.type == BrowseType.ARTIST -> openArtist(item.browseId, item.title)
            else -> openCollection(item.browseId, item)
        }
    }

    fun resolveAppleSong(item: ShelfItem, onResolved: (Song) -> Unit) {
        scope.launch {
            DesktopAppleBrowseClient.resolveSong(item).fold(
                onSuccess = onResolved,
                onFailure = { appleActionError = it.message ?: "Could not find this song" },
            )
        }
    }

    /** The same album or playlist on the current music source, found by its title and credits. */
    suspend fun appleCatalogMatch(item: ShelfItem): BrowseItem? {
        val playlist = item.browseId.orEmpty().contains("/playlist/")
        return DesktopSearchClient.search(
            "${item.title} ${item.subtitle}".trim(),
            if (playlist) SearchFilter.PLAYLISTS else SearchFilter.ALBUMS,
        ).getOrDefault(emptyList())
            .filterIsInstance<SearchResult.Browse>()
            .map { it.item }
            .firstOrNull { it.browseId.isNotBlank() }
    }

    fun playAppleCollection(item: ShelfItem, shuffle: Boolean) {
        scope.launch {
            val match = appleCatalogMatch(item)
            if (match == null) {
                appleActionError = "${item.title} is not available from the current music source"
                return@launch
            }
            DesktopSearchClient.browse(match.browseId, match).fold(
                onSuccess = { collection ->
                    val songs = collection.songs
                    if (songs.isEmpty()) appleActionError = "${item.title} has no playable tracks"
                    else playSongs(if (shuffle) songs.shuffled() else songs, 0, collectionSource(collection))
                },
                onFailure = { appleActionError = it.message ?: "Could not play ${item.title}" },
            )
        }
    }

    fun openAppleCatalog(item: ShelfItem) {
        scope.launch {
            val match = appleCatalogMatch(item)
            if (match == null) appleActionError = "${item.title} is not available from the current music source"
            else openCollection(match.browseId, match)
        }
    }

    fun openShelfMenu(item: ShelfItem) {
        if (item.isAppleSong()) resolveAppleSong(item) { pageMenu(it) }
        else if (item.browseId?.startsWith(DesktopAppleBrowseClient.PAGE_PREFIX) == true) appleCollectionMenu(item)
        else if (item.videoId != null) pageMenu(item.toSong())
    }

    fun openShelfItem(item: ShelfItem, shelfTitle: String? = null) {
        val videoId = item.videoId
        val browseId = item.browseId
        when {
            DesktopRadioClient.isStation(item) -> {
                val station = DesktopRadioClient.station(item)
                if (station == null) {
                    appleActionError = "${item.title} is not available in BitChord."
                } else if (DesktopListenTogether.state.value.inParty) {
                    appleActionError = "Leave Listen Together before starting live radio."
                } else {
                    playbackEngine.pause()
                    overlays.nowPlaying = false
                    overlays.sidePanel = null
                    radioPlayer.setVolume(volume)
                    radioPlayer.start(station)
                }
            }
            item.isAppleSong() -> resolveAppleSong(item) { song ->
                playSong(song, source = shelfTitle?.let { DesktopQueueSource(it, PlaybackSourceType.HOME) })
            }
            browseId?.startsWith(DesktopAppleBrowseClient.PAGE_PREFIX) == true -> {
                openedCollection = null
                openedArtist = null
                selectedMoodGenre = MoodGenre(item.title, browseId, null, item.thumbnailUrl)
                applePageCredit = item.subtitle
                moodGenreShelves = UiState.Loading
            }
            videoId != null -> {
                val source = shelfTitle?.let { DesktopQueueSource(it, PlaybackSourceType.HOME) }
                playSong(item.toSong(), source = source)
            }
            // An artist is a page of its own, not a list of tracks with a photograph on top.
            browseTypeOf(browseId.orEmpty()) == BrowseType.ARTIST ->
                openArtist(browseId!!, item.title)
            browseId != null -> openCollection(
                browseId = browseId,
                fallback = BrowseItem(
                    browseId = browseId,
                    title = item.title,
                    subtitle = item.subtitle,
                    thumbnailUrl = item.thumbnailUrl,
                    type = BrowseType.OTHER,
                ),
            )
        }
    }

    fun openMenu(song: Song) = pageMenu(song)

    /** What the phone records for a search hit: the thing picked, not the words typed to find it. */
    fun recordSongSearch(song: Song) = DesktopSearchHistory.record(
        SearchHistoryEntity(
            id = song.videoId,
            title = song.title,
            subtitle = song.artist,
            artworkUrl = song.thumbnailUrl,
            entityType = EntityType.TRACK,
        ),
    )

    fun recordBrowseSearch(item: BrowseItem) = DesktopSearchHistory.record(
        SearchHistoryEntity(
            id = item.browseId,
            title = item.title,
            subtitle = item.subtitle,
            artworkUrl = item.thumbnailUrl,
            entityType = when (item.type) {
                BrowseType.ALBUM -> EntityType.ALBUM
                BrowseType.ARTIST -> EntityType.ARTIST
                BrowseType.PLAYLIST -> EntityType.PLAYLIST
                else -> EntityType.TRACK
            },
        ),
    )

    /** Typing, from the page's field or the sidebar's: the typeahead follows, and an empty field goes back to recents. */
    fun editQuery(text: String) {
        query = text
        searchTyping = true
        if (text.isBlank()) {
            searchCommitted = false
            searchRows = emptyList()
            searchError = null
        }
    }

    fun search() {
        if (query.isBlank() || searchLoading) return
        overlays.settingsPage = null
        destination = DesktopDestination.SEARCH
        searchTyping = false
        searchSuggestions = emptyList()
        // Submitting is picking the first track the typeahead offered, as on the phone — or, with
        // none, the first the results bring back.
        val typedHit = searchTypeahead.firstNotNullOfOrNull { row ->
            when (row) {
                is SearchResult.TopTrack -> row.song
                is SearchResult.Track -> row.song
                else -> null
            }
        }
        typedHit?.let(::recordSongSearch)
        searchCommitted = true
        searchScrollReset++
        searchLoading = true
        searchError = null
        val term = query
        scope.launch {
            try {
                // Apple's playlists are a second request beside the source's, not a slower first
                // one: both are asked at once and merged, so the page is never held back for them.
                // [DesktopMusicSources.search] answers in a Result already; wrapping it again
                // would leave a Result inside a Result to unwrap below.
                val source = async { DesktopMusicSources.search(term, searchFilter) }
                val wantsApple = searchFilter == SearchFilter.ALL || searchFilter == SearchFilter.PLAYLISTS
                val apple = async {
                    if (!wantsApple) return@async emptyList()
                    runCatching { DesktopApplePageSearch.playlists(term) }.getOrDefault(emptyList())
                }
                val result = source.await()
                val rows = result.getOrNull()
                if (rows == null) {
                    searchError = result.exceptionOrNull()?.message ?: "Search failed"
                } else if (query == term) {
                    searchRows = rows + apple.await().map { SearchResult.Browse(it) }
                    if (typedHit == null) {
                        rows.firstNotNullOfOrNull { row ->
                            when (row) {
                                is SearchResult.TopTrack -> row.song
                                is SearchResult.Track -> row.song
                                else -> null
                            }
                        }?.let(::recordSongSearch)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                searchError = failure.message ?: "Search failed"
            } finally {
                // OBS and other desktop capture tools can briefly interrupt focus/coroutines.
                searchLoading = false
            }
        }
    }

    /** The typeahead, one request behind the keystrokes rather than one per. */
    LaunchedEffect(query, searchTyping) {
        if (!searchTyping || query.isBlank()) {
            searchSuggestions = emptyList()
            return@LaunchedEffect
        }
        delay(SUGGESTION_DEBOUNCE_MS)
        DesktopSearchClient.searchSuggestions(query)
            .onSuccess { if (searchTyping) searchSuggestions = it }
            .onFailure { searchSuggestions = emptyList() }
    }

    // Its own pass, on its own delay: a search costs far more than a completion, so it waits for a
    // longer pause in the typing rather than riding the same one.
    LaunchedEffect(query, searchTyping) {
        if (!searchTyping || query.isBlank()) {
            searchTypeahead = emptyList()
            return@LaunchedEffect
        }
        delay(TYPEAHEAD_MEDIA_DEBOUNCE_MS)
        DesktopSearchClient.searchTypeahead(query)
            .onSuccess { if (searchTyping) searchTypeahead = it.take(TYPEAHEAD_MEDIA_LIMIT) }
            .onFailure { searchTypeahead = emptyList() }
    }

    fun runSearch(term: String) {
        query = term
        searchTyping = false
        searchSuggestions = emptyList()
        searchTypeahead = emptyList()
        search()
    }

    fun selectDestination(next: DesktopDestination) {
        if (next == DesktopDestination.LIBRARY && libraryStale) {
            libraryStale = false
            reloadLibrary()
        }
        openedArtist = null
        openedCollection = null
        overlays.replay = false
        overlays.settingsPage = null
        selectedMoodGenre = null
        libraryShowAll = null
        libraryShortcut = null
        if (next == DesktopDestination.SEARCH) searchFocusRequested = true
        destination = next
    }

    /**
     * One of the sidebar's Library shortcuts — Artists, Albums, Songs, Made for
     * You. Each is the same grid the Library page's own "Show all" opens, fed
     * from whichever part of the app already holds those rows.
     */
    fun openLibraryShortcut(title: String) {
        openedArtist = null
        openedCollection = null
        overlays.replay = false
        overlays.settingsPage = null
        selectedMoodGenre = null
        libraryShortcut = title
        destination = DesktopDestination.LIBRARY
    }

    /** The rows behind the open shortcut, re-read as the feeds it borrows arrive. */
    fun libraryShortcutItems(title: String): List<ShelfItem> = when (title) {
        "Artists", "Albums" ->
            (libraryState as? UiState.Success)?.data?.shelves?.firstOrNull { it.title == title }?.items.orEmpty()
        "Songs" -> (libraryState as? UiState.Success)?.data?.let { page ->
            (page.librarySongs + page.likedSongs).distinctBy(Song::videoId).map { song ->
                ShelfItem(song.title, song.artist, song.thumbnailUrl, song.videoId, null)
            }
        }.orEmpty()
        else -> (homeState as? UiState.Success)?.data
            ?.filter { shelf ->
                val name = shelf.title.lowercase()
                name.contains("for you") || name.contains("mix") || name.contains("recommended")
            }
            ?.flatMap { it.items }
            ?.distinctBy { it.browseId ?: it.videoId }
            .orEmpty()
    }

    // The shortcut's page follows its source: an account whose library is still
    // loading fills the grid in rather than leaving an empty page behind.
    LaunchedEffect(libraryShortcut, libraryState, homeState) {
        val title = libraryShortcut ?: return@LaunchedEffect
        libraryShowAll = HomeShelf(title, libraryShortcutItems(title))
    }

    // Settings' search and scroll outlive a visit to one of its pages, and start over each time
    // Settings itself is opened.
    var settingsSession by remember { mutableStateOf(0) }
    var settingsQuery by remember(settingsSession) { mutableStateOf("") }
    val settingsListState = remember(settingsSession) { LazyListState() }

    /** Settings in place of the page — from the sidebar, the account switcher or the tray. */
    fun openSettings() {
        overlays.nowPlaying = false
        if (overlays.settingsPage == null) settingsSession++
        overlays.settingsPage = DesktopSettingsPage.MAIN
    }

    /** The Library's folder rows: the two folders this computer has. */
    val libraryLinks = remember {
        listOf(
            LibraryLink(
                item = ShelfItem(
                    title = DesktopStrings["downloads", "Downloads"],
                    subtitle = DesktopStrings["downloaded_songs", "Downloaded songs"],
                    thumbnailUrl = null,
                    videoId = null,
                    browseId = LOCAL_DOWNLOADS_ID,
                ),
                icon = BitChordIcons.Download,
            ),
            LibraryLink(
                item = ShelfItem(
                    title = DesktopStrings["local_music", "Local Music"],
                    subtitle = DesktopStrings["d_audio_files_on_this_computer", "Audio files on this computer"],
                    thumbnailUrl = null,
                    videoId = null,
                    browseId = LOCAL_MUSIC_ID,
                ),
                icon = BitChordIcons.Folder,
            ),
        )
    }

    /**
     * The Library's "On device" shelf: the playlists kept on this computer rather than on the
     * account — the desktop's counterpart of the phone's downloaded releases, and like them
     * reachable from nowhere else.
     */
    val libraryDeviceItems = remember(playlists) {
        playlists.map { playlist ->
            ShelfItem(
                title = playlist.title,
                subtitle = DesktopStrings.format(
                    "d_playlist_songs",
                    playlist.songs.size,
                    fallback = "Playlist • ${playlist.songs.size} songs",
                ),
                thumbnailUrl = playlist.songs.firstOrNull()?.thumbnailUrl,
                videoId = null,
                browseId = LOCAL_PLAYLIST_PREFIX + playlist.id,
            )
        }
    }

    /** A Library card: a folder is a page of this app's, a local playlist is opened from here. */
    fun openLibraryItem(item: ShelfItem) {
        val browseId = item.browseId.orEmpty()
        when {
            browseId == LOCAL_DOWNLOADS_ID -> selectDestination(DesktopDestination.DOWNLOADS)
            browseId == LOCAL_MUSIC_ID -> selectDestination(DesktopDestination.LOCAL_MUSIC)
            browseId.startsWith(LOCAL_PLAYLIST_PREFIX) ->
                playlists.firstOrNull { it.id == browseId.removePrefix(LOCAL_PLAYLIST_PREFIX) }?.let(::openPlaylist)
            else -> openShelfItem(item)
        }
    }

    /**
     * Adds shelves to the page as they arrive, skipping any heading already on it and any shelf
     * that repeats one's releases — the phone's `publishHomeShelves`. Recently Played is
     * [prepend]ed and replaces any stale copy the core feed carried.
     */
    fun publishHomeShelves(shelves: List<HomeShelf>, prepend: Boolean = false) {
        val existing = (homeState as? UiState.Success)?.data.orEmpty()
        if (prepend) {
            val replacing = shelves.map { it.title.lowercase() }.toSet()
            homeSeenTitles.addAll(replacing)
            homeState = UiState.Success(shelves + existing.filterNot { it.title.lowercase() in replacing })
            return
        }
        val added = shelves.withoutRepeatsOf(existing)
            .filter { it.items.isNotEmpty() && homeSeenTitles.add(it.title.lowercase()) }
        if (added.isNotEmpty()) homeState = UiState.Success(existing + added)
    }

    /**
     * The phone's Home: the core feed, the account's own Recently Played at its head, and the
     * supplementary browse feeds, all asked for at once and laid down as each lands. Signed out
     * there is no Recently Played to ask for, which is also how the phone has it.
     */
    fun loadHome() {
        if (!youtubeSourceEnabled) {
            homeState = UiState.Error("YouTube Music is disabled")
            return
        }
        val generation = ++homeGeneration
        val signedIn = DesktopYouTubeAuth.isSignedIn
        homeIdentity = "$signedIn/${DesktopAccounts.activeAccountId()}"
        homeState = UiState.Loading
        homeContinuation = null
        homeSeenTitles.clear()
        homeLoadingMore = false
        homeRecentsLoading = signedIn
        scope.launch {
            launch {
                DesktopSearchClient.home().fold(
                    onSuccess = { feed ->
                        if (generation != homeGeneration) return@fold
                        homeContinuation = feed.continuation
                        publishHomeShelves(feed.shelves)
                    },
                    onFailure = {
                        if (generation == homeGeneration && homeState !is UiState.Success) {
                            homeState = UiState.Error(it.message ?: "Could not load Home")
                        }
                    },
                )
            }
            if (signedIn) {
                launch {
                    YtMusicRepository.homeRecentlyPlayed()
                        .onSuccess { shelf ->
                            if (generation != homeGeneration) return@onSuccess
                            homeRecentsLoading = false
                            shelf?.let { publishHomeShelves(listOf(it), prepend = true) }
                        }
                        .onFailure { if (generation == homeGeneration) homeRecentsLoading = false }
                }
            }
            DesktopSearchClient.HOME_SUPPLEMENT_BROWSE_IDS.forEach { browseId ->
                launch {
                    DesktopSearchClient.homeSupplement(browseId).onSuccess { shelves ->
                        if (generation == homeGeneration) publishHomeShelves(shelves)
                    }
                }
            }
        }
    }

    /** The next page of the core feed, as the list nears its end; a no-op once it has run dry. */
    fun loadMoreHome() {
        val token = homeContinuation ?: return
        if (homeLoadingMore || homeState !is UiState.Success) return
        val generation = homeGeneration
        homeLoadingMore = true
        scope.launch {
            DesktopSearchClient.moreHome(token).fold(
                onSuccess = { feed ->
                    if (generation != homeGeneration) return@fold
                    val before = homeSeenTitles.size
                    publishHomeShelves(feed.shelves)
                    // A page with nothing new is the feed looping back on itself, not running dry.
                    homeContinuation = feed.continuation.takeIf { homeSeenTitles.size > before }
                },
                onFailure = { homeContinuation = null },
            )
            homeLoadingMore = false
        }
    }

    fun openPlayerFromUser() {
        if (radioPlayer.state.value.station != null) selectDestination(DesktopDestination.RADIO)
        else overlays.nowPlaying = true
    }

    // The tray icon speaks StatusNotifierItem rather than going through AWT, which only implements
    // the XEmbed tray a Wayland session does not have.
    val tray = remember {
        DesktopStatusNotifierController(
            // Clicking the icon is how the window comes back once it has been closed to the tray,
            // so it has to raise the window before it does anything inside it.
            onActivate = {
                DesktopWindowVisibility.show()
                openPlayerFromUser()
            },
            onPlayPause = { if (selectedSong != null || radioPlayer.state.value.station != null) togglePlayPauseFromUser() },
        )
    }
    DisposableEffect(tray, trayIconEnabled) {
        if (trayIconEnabled) tray.start()
        onDispose { tray.stop() }
    }
    // Deliberately not a key on the effect above.
    DisposableEffect(trayIconEnabled, closeToTray) {
        // The listener's choice, with the tray as its precondition: hiding with no tray to come
        // back from leaves an audible process and no window.
        DesktopWindowVisibility.keepRunningWhenClosed = trayIconEnabled && closeToTray
        onDispose { DesktopWindowVisibility.keepRunningWhenClosed = false }
    }
    LaunchedEffect(tray, trayIconEnabled) {
        if (!trayIconEnabled) return@LaunchedEffect
        // The logo travels to the tray as pixels: a themed icon name would be whatever the user's
        // theme draws for a generic music player.
        runCatching { Res.readBytes("drawable/logo.svg") }.onSuccess(tray::setIcon)
    }
    LaunchedEffect(tray) {
        DesktopTrayMenu.bind(
            onPlayPause = { if (selectedSong != null || radioPlayer.state.value.station != null) togglePlayPauseFromUser() },
            onNext = ::playNext,
            onPrevious = ::playPrevious,
            onOpenPlayer = {
                DesktopWindowVisibility.show()
                openPlayerFromUser()
            },
            onOpenSettings = {
                DesktopWindowVisibility.show()
                openSettings()
            },
            onQuit = {
                // Native work first: the audio thread and the media controls are inside FFmpeg,
                // WASAPI and WinRT, and exitProcess would unload those libraries under them.
                radioPlayer.release()
                playbackEngine.shutdown()
                DesktopWindowsMedia.stop()
                exitProcess(0)
            },
        )
    }
    LaunchedEffect(transportPlayback.song, transportPlayback.isPlaying) {
        val title = transportPlayback.song?.let { "${it.title} — ${it.artist}" }
        tray.update(title = title, isPlaying = transportPlayback.isPlaying)
        DesktopTrayMenu.publish(title = title, playing = transportPlayback.isPlaying)
    }

    LaunchedEffect(Unit) {
        // Unpacks the analyser and its models on first run.
        DesktopAnalysisRuntime.ensureStarted()
        DesktopHorizontalScroll.install()
        localSongs = withContext(Dispatchers.IO) { DesktopLocalMusic.scan() }
        // A download record is a claim about a folder this app does not own, so it can outlive the
        // file it names. Checked once at startup rather than trusted: a stale entry showed the
        // track as downloaded, refused to fetch it again, and then would not play.
        val onDisk = withContext(Dispatchers.IO) { DesktopDownloadManager.verified(downloads) }
        if (onDisk.size != downloads.size) {
            DesktopTrackLog.log(
                "downloads: ${downloads.size - onDisk.size} recorded file(s) are gone; forgetting them",
            )
            downloads = onDisk
            persistence.saveDownloads(onDisk)
        }
        loadHome()
    }

    // The feed is the account's: signing in, out or across to another channel is a different Home,
    // and Recently Played only exists with an account behind it.
    LaunchedEffect(youtubeSignedIn, activeAccountId) {
        val identity = "${DesktopYouTubeAuth.isSignedIn}/${DesktopAccounts.activeAccountId()}"
        if (homeIdentity != null && identity != homeIdentity) loadHome()
    }

    // The mark on a downloaded row, for the shared pages.
    LaunchedEffect(downloads) {
        DesktopAppUiHost.downloaded.value = downloads.mapTo(HashSet(), Song::videoId)
    }

    // What reached disk goes into the library's own record, which is what the Downloads page and
    // every row's tick read from.
    LaunchedEffect(Unit) {
        DesktopDownloadQueue.finished.collect { saved ->
            downloads = (listOf(saved) + downloads.filterNot { it.videoId == saved.videoId }).take(200)
            persistence.saveDownloads(downloads)
        }
    }

    // The charts are period-filtered at read time, so a different period is a different read.
    LaunchedEffect(replayPeriod) {
        replaySummary = withContext(Dispatchers.IO) { DesktopListeningStats.summary(replayPeriod) }
    }

    // Re-scanned when the filter or the folder changes, never on first composition.
    LaunchedEffect(localMusicRevision) {
        if (localMusicRevision == 0) return@LaunchedEffect
        localSongs = withContext(Dispatchers.IO) { DesktopLocalMusic.scan() }
        DesktopLocalMusicWatcher.restart()
    }

    // A track dropped into the folder appears without a relaunch.
    LaunchedEffect(Unit) {
        DesktopLocalMusicWatcher.restart()
        DesktopLocalMusicWatcher.changes.collect {
            localSongs = withContext(Dispatchers.IO) { DesktopLocalMusic.scan() }
        }
    }

    suspend fun loadExplore() {
        if (!youtubeSourceEnabled) {
            exploreState = UiState.Error("YouTube Music is disabled")
            return
        }
        exploreState = UiState.Loading
        DesktopSearchClient.moodAndGenres().fold(
            onSuccess = { sections ->
                exploreState = if (sections.isEmpty()) {
                    UiState.Error("Nothing to explore right now")
                } else {
                    UiState.Success(sections)
                }
            },
            onFailure = { exploreState = UiState.Error(it.message ?: "Could not load Explore") },
        )
    }

    LaunchedEffect(destination, exploreReloads) {
        if (destination in listOf(DesktopDestination.EXPLORE, DesktopDestination.SEARCH, DesktopDestination.RADIO) &&
            (exploreState is UiState.Loading || exploreReloads > 0)
        ) {
            loadExplore()
        }
    }

    // Category buttons carry no artwork of their own, so a few are resolved at a time from the
    // shelves they open.
    LaunchedEffect(exploreState) {
        val sections = (exploreState as? UiState.Success)?.data ?: return@LaunchedEffect
        if (sections.all { section -> section.items.all { it.thumbnailUrl != null } }) return@LaunchedEffect
        val limiter = Semaphore(4)
        coroutineScope {
            sections.flatMap(MoodGenreSection::items)
                .filter { it.thumbnailUrl == null }
                .forEach { item ->
                    launch {
                        val artwork = limiter.withPermit {
                            DesktopSearchClient.moodGenreArtwork(item.browseId, item.params).getOrNull()
                        } ?: return@launch
                        val current = (exploreState as? UiState.Success)?.data ?: return@launch
                        exploreState = UiState.Success(
                            current.map { section ->
                                section.copy(
                                    items = section.items.map { entry ->
                                        if (entry.browseId == item.browseId && entry.params == item.params) {
                                            entry.copy(thumbnailUrl = artwork)
                                        } else {
                                            entry
                                        }
                                    },
                                )
                            },
                        )
                    }
                }
        }
    }

    /** The next page of the open collection, appended in place. */
    fun loadMoreCollectionSongs() {
        val collection = openedCollection ?: return
        val token = collection.continuation ?: return
        if (collectionLoadingMore) return
        collectionLoadingMore = true
        scope.launch {
            DesktopSearchClient.moreCollectionSongs(token, collection.songs.firstOrNull()?.artist)
                .onSuccess { (songs, next) ->
                    val current = openedCollection
                    if (current != null && current.browseId == collection.browseId) {
                        openedCollection = current.copy(
                            songs = (current.songs + songs).distinctBy(Song::videoId),
                            continuation = next,
                        )
                    }
                }
                .onFailure {
                    DesktopTrackLog.log("collection: no more rows for ${collection.browseId}: ${it.message}")
                    val current = openedCollection
                    if (current != null && current.browseId == collection.browseId) {
                        // Dropped rather than retried on scroll for ever: the trailing row is what
                        // asks, and leaving the token in place would have it ask again on every
                        // frame.
                        openedCollection = current.copy(continuation = null)
                    }
                }
            collectionLoadingMore = false
        }
    }

    fun renameOpenPlaylist(title: String) {
        val collection = openedCollection ?: return
        playlistBusy = true
        playlistError = null
        scope.launch {
            DesktopSearchClient.renamePlaylist(collection.playlistId, title)
                .onSuccess {
                    // Renamed in place as well as re-fetched: the page that ordered it is still on
                    // screen showing the old name, and the library reload behind it takes a moment.
                    openedCollection = openedCollection
                        ?.takeIf { it.browseId == collection.browseId }
                        ?.copy(title = title)
                        ?: openedCollection
                    editPlaylistShelf { items ->
                        items.map { if (it.browseId == collection.browseId) it.copy(title = title) else it }
                    }
                    closePlaylistDialogs()
                    libraryStale = true
                }
                .onFailure {
                    playlistBusy = false
                    playlistError = it.message ?: "Could not rename the playlist"
                }
        }
    }

    fun deleteOpenPlaylist() {
        val collection = openedCollection ?: return
        playlistBusy = true
        playlistError = null
        scope.launch {
            DesktopSearchClient.deletePlaylist(collection.playlistId)
                .onSuccess {
                    closePlaylistDialogs()
                    // Its page is the one open, and a deleted playlist has nothing left to show.
                    if (openedCollection?.browseId == collection.browseId) openedCollection = null
                    editPlaylistShelf { items -> items.filterNot { it.browseId == collection.browseId } }
                    libraryStale = true
                }
                .onFailure {
                    playlistBusy = false
                    playlistError = it.message ?: "Could not delete the playlist"
                }
        }
    }

    /** Takes one row back out of the playlist it is being read in. */
    fun removeFromOpenPlaylist(song: Song) {
        val collection = openedCollection ?: return
        val setVideoId = song.setVideoId ?: return
        scope.launch {
            DesktopSearchClient.removeFromPlaylist(
                collection.playlistId,
                listOf(setVideoId to song.videoId),
            ).onSuccess {
                val current = openedCollection
                if (current != null && current.browseId == collection.browseId) {
                    openedCollection = current.copy(
                        songs = current.songs.filterNot { it.setVideoId == setVideoId },
                    )
                }
            }.onFailure {
                DesktopTrackLog.log("playlist: could not remove ${song.videoId}: ${it.message}")
            }
        }
    }

    /**
     * Sends a new order for the playlist being read. [original] and [reordered] are the same whole
     * list of entries — as it was and as the user left it — since a reorder is sent as moves relative
     * to neighbours and needs every entry to move against.
     */
    fun reorderOpenPlaylist(original: List<Song>, reordered: List<Song>) {
        val collection = openedCollection ?: return
        playlistBusy = true
        playlistError = null
        scope.launch {
            DesktopSearchClient.reorderPlaylist(
                collection.playlistId,
                current = original.mapNotNull { it.setVideoId },
                target = reordered.mapNotNull { it.setVideoId },
            ).onSuccess {
                val current = openedCollection
                if (current != null && current.browseId == collection.browseId) {
                    // The whole list arrived to be reordered, so the page now shows all of it and has
                    // no further page to ask for.
                    openedCollection = current.copy(songs = reordered, continuation = null)
                }
                closePlaylistDialogs()
                libraryStale = true
            }.onFailure {
                playlistBusy = false
                playlistError = it.message ?: "Could not reorder the playlist"
            }
        }
    }

    /**
     * The account's library, re-read whenever the session behind it changes — signing in, switching
     * channel, signing out.
     */
    LaunchedEffect(activeAccountId, activeProfileId, sessionRevision) {
        youtubeSignedIn = DesktopYouTubeAuth.isSignedIn
        if (!DesktopYouTubeAuth.isSignedIn) {
            libraryState = UiState.Success(LibraryPage(emptyList(), emptyList(), emptyList()))
            return@LaunchedEffect
        }
        libraryState = UiState.Loading
        DesktopSearchClient.library().fold(
            onSuccess = { page ->
                // The continuation is a job to run, not page state — it is consumed below.
                libraryState = UiState.Success(page.copy(likedContinuation = null))
                DesktopTrackLog.log(
                    "library: ${page.likedSongs.size} liked, ${page.librarySongs.size} added, " +
                        "shelves ${page.shelves.joinToString { "${it.title}=${it.items.size}" }}",
                )
                val fromAccount = page.likedSongs.mapTo(HashSet(), Song::videoId)
                if (!likedIds.containsAll(fromAccount)) {
                    likedIds = likedIds + fromAccount
                    // Off the drawing thread: an account with a long Liked Music writes a few tens
                    // of kilobytes through the preference store, and that is a disk flush.
                    withContext(Dispatchers.IO) { persistence.saveLikedIds(likedIds) }
                }
                // Liked Music is published to the tab a page budget deep; the rest is followed here
                // for its ids alone, so a liked track past the budget still reads as liked.
                // Tied to the account it was fetched for: the liked set is one shared map, and a
                // sync still running after a switch would seed it with a stranger's likes.
                val syncingFor = activeAccountId
                page.likedContinuation?.let { token ->
                    likedSyncJob?.cancel()
                    likedSyncJob = scope.launch {
                        val extra = HashSet<String>()
                        DesktopSearchClient.syncLikedIds(token, onIds = { ids -> extra += ids })
                        if (syncingFor != DesktopAccounts.activeAccountId()) return@launch
                        if (!likedIds.containsAll(extra)) {
                            likedIds = likedIds + extra
                            withContext(Dispatchers.IO) { persistence.saveLikedIds(likedIds) }
                            DesktopTrackLog.log("library: liked sync added ${extra.size} ids past the first pages")
                        }
                    }
                }
            },
            onFailure = { libraryState = UiState.Error(it.message ?: "Could not load your library") },
        )
        // A session refused mid-flight is dropped by the request itself, so the answer to "are we
        // signed in" is only settled once the call is over.
        youtubeSignedIn = DesktopYouTubeAuth.isSignedIn
    }

    fun here() = DesktopNavEntry(
        destination = destination,
        artist = openedArtist,
        collection = openedCollection,
        mood = selectedMoodGenre,
        showAll = libraryShowAll,
        replay = overlays.replay,
        settings = overlays.settingsPage,
    )

    // Every move between places is a visit, however it was made — the sidebar, a card, a search
    // hit, the player's credits. The place left is what back returns to.
    LaunchedEffect(Unit) {
        var last: DesktopNavEntry? = null
        snapshotFlow { here() }.collect { now ->
            val before = last
            if (before != null && before.key != now.key) {
                if (navRestoring[0]) {
                    navRestoring[0] = false
                } else {
                    navHistory.add(before)
                    if (navHistory.size > NAV_HISTORY_LIMIT) navHistory.removeAt(0)
                }
            }
            // The newest copy of the same place, so an album that has paged in more tracks comes
            // back with them.
            last = now
        }
    }

    /** Back to the place before this one — the top bar's arrow, the mouse's back button, Alt+Left. */
    fun goBack() {
        val entry = navHistory.removeLastOrNull() ?: return
        if (entry.key == here().key) return
        navRestoring[0] = true
        destination = entry.destination
        overlays.replay = entry.replay
        overlays.settingsPage = entry.settings
        collectionError = null
        openedCollection = entry.collection
        libraryShowAll = entry.showAll
        libraryShortcut = null
        if (entry.mood != selectedMoodGenre) {
            selectedMoodGenre = entry.mood
            moodGenreShelves = UiState.Loading
        }
        if (entry.artist != openedArtist) {
            openedArtist = entry.artist
            if (entry.artist != null) {
                artistState = UiState.Loading
                artistReloads++
            }
        }
    }

    fun openMoodGenre(item: MoodGenre) {
        val appleCategories = (appleBrowseState as? UiState.Success)?.data?.let(DesktopAppleBrowseClient::categories).orEmpty()
        selectedMoodGenre = appleCategories.firstOrNull { it.title.equals(item.title, true) } ?: item
        moodGenreShelves = UiState.Loading
    }

    LaunchedEffect(selectedMoodGenre, moodGenreReloads) {
        val item = selectedMoodGenre ?: return@LaunchedEffect
        if (moodGenreShelves !is UiState.Loading) return@LaunchedEffect
        val result = if (item.browseId.startsWith(DesktopAppleBrowseClient.PAGE_PREFIX)) {
            DesktopAppleBrowseClient.page(item.browseId.removePrefix(DesktopAppleBrowseClient.PAGE_PREFIX), item.title)
        } else {
            DesktopSearchClient.moodGenreShelves(item.browseId, item.params)
        }
        result.fold(
            onSuccess = { shelves ->
                moodGenreShelves = if (shelves.isEmpty()) {
                    UiState.Error("Nothing to explore here yet")
                } else {
                    UiState.Success(shelves)
                }
            },
            onFailure = { moodGenreShelves = UiState.Error(it.message ?: "Could not load this category") },
        )
    }

    // The player's song menu, raised by a right-click on the player itself.
    var playerMenuOpen by remember(selectedSong?.videoId) { mutableStateOf(false) }
    // The lyric sheet and the queue, toggled from rows on the song menu rather
    // than glyphs in the overflow. Flipped here, consumed and cleared by the
    // shared player.
    val playerLyricsToggle = remember(selectedSong?.videoId) { mutableStateOf(false) }
    val playerQueueToggle = remember(selectedSong?.videoId) { mutableStateOf(false) }
    // The translate and romanise toggles, likewise flipped here and run/cleared
    // by the shared player, whose current state comes back through the second so
    // these menu rows can show their checkmarks.
    val playerTranslateToggle = remember(selectedSong?.videoId) { mutableStateOf(false) }
    val playerShowingTranslation = remember(selectedSong?.videoId) { mutableStateOf(false) }
    val playerRomanizeToggle = remember(selectedSong?.videoId) { mutableStateOf(false) }
    val playerShowingRomanization = remember(selectedSong?.videoId) { mutableStateOf(false) }
    // Where the player's own "…" sits, reported up by the shared player: the
    // sleeve's width and the words' column ride on the window's size, so the menu
    // has to follow the glyph rather than sit at a fixed corner. A right-click
    // opens the same menu, at the same place.
    val playerMenuBounds = remember(selectedSong?.videoId) { mutableStateOf<Rect?>(null) }

    /** The song menu's verbs for [song], as the player sheet offers them. */
    fun playerSongActions(song: Song) = DesktopSongActions(
        signedIn = youtubeSignedIn,
        disliked = song.videoId in dislikedIds,
        downloaded = downloads.any { it.videoId == song.videoId },
        downloadInProgress = song.videoId in downloadInProgress,
        sleepTimerMinutes = sleepTimerMinutes,
        sleepAfterTrack = sleepAfterTrack,
        onToggleDislike = ::toggleDislike,
        onAddToPlaylist = { playlistTarget = it },
        onDownload = ::downloadSong,
        onRemoveDownload = ::removeDownload,
        onStartRadio = ::startRadio,
        onPlayNext = ::playNext,
        onAddToQueue = ::addToQueue,
        // Both leave the player, the way opening a page from Android's sheet collapses it.
        onOpenAlbum = { id ->
            overlays.nowPlaying = false
            openAlbum(id)
        },
        onOpenArtist = { id ->
            overlays.nowPlaying = false
            openArtist(id, song.artist)
        },
        onSleepTimer = { minutes ->
            if (minutes == null) DesktopSleepTimer.cancel() else DesktopSleepTimer.start(minutes)
        },
        onSleepAfterTrack = { DesktopSleepTimer.startAfterTrack() },
        onShare = ::shareSong,
    )

    // What the shared player reads as settings, kept in step with the window's own.
    //
    // This watches them from a snapshot flow rather than a SideEffect. A SideEffect body's
    // reads do not subscribe anything, and the effect only runs again when *this* scope
    // recomposes — while every one of these values is read deeper down, in the Settings page's
    // own scope. So a toggle the rest of the window never reads, Stats for nerds among them,
    // would flip, the page would show it flipped, and the flow the player reads would stay
    // exactly as it was when the window opened.
    LaunchedEffect(Unit) {
        snapshotFlow {
            DesktopPlayerSettingsMirror(
                animatedCanvas = animatedCanvas,
                fullBleedArtwork = fullBleedArtwork,
                legacyMeshGradient = legacyMeshGradient,
                lyricsBlur = lyricsBlur,
                syncedLyrics = syncedLyrics,
                showNerdStats = showNerdStats,
                smartFadeEnabled = automix,
                smartAnalysis = playback.smartAnalysis,
                smartMixInProgress = playback.mixing,
                smartTransitionWindow = playback.transitionWindow,
                lyricsSourceOrder = DesktopLyricsClient.enabledSources(lyricsOrder, lyricsOn)
                    .mapNotNull(::lyricsSourceNamed),
                volume = volume,
            )
        }.collect { mirror ->
            DesktopPlayerSettings.animatedCanvas.value = mirror.animatedCanvas
            DesktopPlayerSettings.fullBleedArtwork.value = mirror.fullBleedArtwork
            DesktopPlayerSettings.legacyMeshGradient.value = mirror.legacyMeshGradient
            DesktopPlayerSettings.lyricsBlur.value = mirror.lyricsBlur
            DesktopPlayerSettings.syncedLyrics.value = mirror.syncedLyrics
            DesktopPlayerSettings.showNerdStats.value = mirror.showNerdStats
            DesktopPlayerSettings.smartFadeEnabled.value = mirror.smartFadeEnabled
            DesktopPlayerSettings.smartAnalysis.value = mirror.smartAnalysis
            DesktopPlayerSettings.smartMixInProgress.value = mirror.smartMixInProgress
            DesktopPlayerSettings.smartTransitionWindow.value = mirror.smartTransitionWindow
            DesktopPlayerSettings.lyricsSourceOrder.value = mirror.lyricsSourceOrder
            DesktopPlayerHost.volumeLevel.value = mirror.volume
            DesktopPlayerHost.onVolumeChange = {
                volume = it
                persistence.saveString("volume", it.toString())
            }
        }
    }

    // The engine's measured stream, as the player's quality badge and stats line read it.
    LaunchedEffect(playback.streamFormat, playback.streamSourceId) {
        NerdStats.current.value = playback.streamFormat?.let { format ->
            val codec = format.codec
                ?.substringAfterLast('/')
                ?.substringBefore(';')
                ?.trim()
                ?.lowercase()
                // The shared badge gate knows E-AC-3 as `eac3`, which is what the decoder calls
                // itself; `ec-3` is FFmpeg's name for the same bitstream and what a manifest
                // fourcc says. Without this an Atmos stream that was playing happily wore no badge.
                ?.let { if (it == "ec-3") "eac3" else it }
            NerdStats.Snapshot(
                mimeType = codec?.let { "audio/$it" },
                bitrateKbps = format.kbps,
                sampleRateHz = format.sampleRateHz,
                channels = format.channels,
                bitDepth = format.bitDepth,
                sourceName = playback.streamSourceId,
            )
        }
    }
    LaunchedEffect(playback.searchingBetter, selectedSong?.videoId) {
        NerdStats.racingLossless.value = selectedSong?.videoId
            ?.takeIf { playback.searchingBetter }
            ?.let(::setOf)
            .orEmpty()
    }
    LaunchedEffect(playback.streamFormat, playback.isPlaying) {
        DesktopPlayerHost.pipeline.value = playbackEngine.pipeline()
    }
    // One panel, whichever route reaches it: the player's quality badge through the shared player,
    // or the output row of Settings. Re-built on every composition, so the engine state it reports
    // is never the state of the one that first opened it.
    val trackInfoPanel: @Composable (onDismiss: () -> Unit) -> Unit = { onDismiss ->
        DesktopAudioPipelineDialog(
            song = playback.song,
            format = playback.streamFormat,
            sourceName = playback.streamSourceId?.let { id ->
                sourceConfigs.firstOrNull { it.id == id }?.displayName
            },
            pipeline = playbackEngine.pipeline(),
            onDismiss = onDismiss,
        )
    }
    DesktopPlayerHost.pipelineDialog = trackInfoPanel

    // What the player and the lyrics column beside the page both read, worked out once for the two.
    val sharedLyrics = lyrics?.lines
    val sharedLyricsSource = remember(lyrics) { lyrics?.source?.let(::lyricsSourceNamed) }
    val sharedLyricsUnavailable = !lyricsLoading && sharedLyrics.isNullOrEmpty()
    val providerStates = remember(lyricsOrder, lyricsOn, sharedLyricsSource, lyricsLoading) {
        DesktopLyricsClient.enabledSources(lyricsOrder, lyricsOn)
            .mapNotNull(::lyricsSourceNamed)
            .associateWith { source ->
                when {
                    source == sharedLyricsSource -> LyricsProviderState.FOUND
                    lyricsProviderResults.value[source] != null -> lyricsProviderResults.value.getValue(source)
                    lyricsLoading -> LyricsProviderState.FETCHING
                    else -> LyricsProviderState.NOT_FETCHED
                }
            }
    }

    // Every provider the song menu's lyrics drawer lists — all of them, in the
    // saved order, not just the enabled ones — each carrying what its last race
    // turned up so the row can read "Found", "Not found" or "Searching…". The
    // winner is the one the sheet shows now.
    val menuLyricsProviders = remember(
        lyricsOrder,
        lyricsOn,
        lyricsOnly,
        lyricsLoading,
        lyrics?.source,
        lyricsProviderResults.value,
    ) {
        val currentLabel = lyrics?.source
        val asked = lyricsOnly?.let { listOf(it) } ?: DesktopLyricsClient.enabledSources(lyricsOrder, lyricsOn)
        lyricsOrder.map { label ->
            val source = lyricsSourceNamed(label)
            val state = when {
                label == currentLabel -> LyricsProviderState.FOUND
                source != null && lyricsProviderResults.value[source] != null -> lyricsProviderResults.value.getValue(source)
                lyricsLoading && label in asked -> LyricsProviderState.FETCHING
                else -> LyricsProviderState.NOT_FETCHED
            }
            DesktopLyricsProviderRow(label, state, label == currentLabel)
        }
    }

    // The queue's edits, for the player's queue and the queue column alike.
    fun removeFromQueue(at: Int) {
        if (DesktopListenTogether.state.value.controlsLocked) return
        val edited = liveQueue.removeAt(at)
        if (edited !== liveQueue) {
            liveQueue = edited
            saveQueue()
            partySyncHolder[0]?.onLocalIntent()
        }
    }

    fun moveInQueue(from: Int, to: Int) {
        if (DesktopListenTogether.state.value.controlsLocked) return
        val edited = liveQueue.move(from, to)
        if (edited !== liveQueue) {
            liveQueue = edited
            saveQueue()
            partySyncHolder[0]?.onLocalIntent()
        }
    }

    // Clears what the listener queued by hand, as the phone's Clear does; the album and AutoPlay
    // stay where they are.
    fun clearQueue() {
        if (DesktopListenTogether.state.value.controlsLocked) return
        val edited = liveQueue.withoutUserQueue()
        if (edited === liveQueue) return
        liveQueue = edited
        saveQueue()
        partySyncHolder[0]?.onLocalIntent()
    }

    fun seekPlayer(target: Long) {
        if (radioPlayer.state.value.station != null) return
        if (DesktopListenTogether.state.value.controlsLocked) return
        val duration = playback.durationMs
        playbackEngine.seekTo(
            if (duration > 0) target.coerceIn(0L, duration) else target.coerceAtLeast(0L),
        )
        partySyncHolder[0]?.onLocalIntent()
    }

    MaterialTheme(
        colorScheme = desktopColorScheme(),
        typography = desktopTypography(),
    ) {
        val ambientBackdrop by DesktopAppearanceSettings.ambientBackdrop.collectAsState()
        val windowActions = LocalDesktopWindowActions.current
        val lyricsFont = desktopLyricsFont()
        CompositionLocalProvider(
            LocalContentColor provides Color.White,
            LocalNowPlaying provides selectedSong,
            // Every lyric sheet in the window — the home page's column, the player's own — reads
            // its face from here, because sharedUi cannot reach this module's resources.
            LocalLyricsFontFamily provides lyricsFont,
        ) {
            DesktopFrame(
                // The phone's page is black, not the near-black of its cards.
                containerColor = DesktopBackground,
                // The list the page is scrolling, so the frame can hang one overlay bar at the edge
                // of the page area rather than every page asking for its own. Ordered the same way
                // as the content `when` that picks the page below it, so the bar always follows the
                // list actually shown. The pages that build their list inside a scaffold of their
                // own — an artist, a release, history — are left out here and carry the bar with
                // the scaffold.
                pageScroll = when {
                    overlays.settingsPage != null -> settingsListState
                    overlays.replay -> replayListState
                    openedArtist != null || openedCollection != null -> null
                    selectedMoodGenre != null -> moodGenreListState
                    destination == DesktopDestination.LISTEN_NOW -> homeListState
                    destination == DesktopDestination.EXPLORE -> exploreListState
                    destination == DesktopDestination.RADIO -> radioListState
                    destination == DesktopDestination.LIBRARY && libraryShowAll == null -> libraryListState
                    else -> null
                },
                // One backdrop for the whole window, so every page — not just Replay — sits on
                // the colour of what is playing. Off in Appearance brings the plain surface back.
                backdrop = { transparentBase ->
                    val ambientArtworkUrl = replaySummary.songs.firstOrNull()?.song?.thumbnailUrl
                        .takeIf { overlays.replay }
                        ?: selectedSong?.thumbnailUrl.takeIf { ambientBackdrop }
                    DesktopPageBackdrop(
                        artworkUrl = ambientArtworkUrl,
                        transparentBase = transparentBase,
                    )
                },
                modifier = Modifier.onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyUp) return@onPreviewKeyEvent false
                    when {
                        event.key == Key.MediaPlayPause -> {
                            if (selectedSong != null || radioPlayer.state.value.station != null) togglePlayPauseFromUser()
                            true
                        }
                        event.key == Key.MediaNext -> {
                            playNext()
                            true
                        }
                        event.key == Key.MediaPrevious -> {
                            playPrevious()
                            true
                        }
                        event.key == Key.F11 -> {
                            DesktopWindowMode.toggle()
                            true
                        }
                        event.key == Key.DirectionLeft && event.isAltPressed && !overlays.nowPlaying -> {
                            goBack()
                            true
                        }
                        // The palette and the sheet that lists every key: the command key on a
                        // Mac, Ctrl everywhere else. Read on the key coming up, so a held key
                        // does not open and close it over and over.
                        (event.isCtrlPressed || event.isMetaPressed) && event.key == Key.K -> {
                            overlays.shortcuts = false
                            overlays.quickSearch = !overlays.quickSearch
                            true
                        }
                        (event.isCtrlPressed || event.isMetaPressed) && event.key == Key.Slash -> {
                            overlays.quickSearch = false
                            overlays.shortcuts = !overlays.shortcuts
                            true
                        }
                        (event.isCtrlPressed || event.isMetaPressed) && event.key == Key.F &&
                            !overlays.quickSearch && !overlays.shortcuts && !overlays.nowPlaying -> {
                            selectDestination(DesktopDestination.SEARCH)
                            true
                        }
                        (event.isCtrlPressed || event.isMetaPressed) && event.key == Key.Comma -> {
                            overlays.quickSearch = false
                            overlays.shortcuts = false
                            openSettings()
                            true
                        }
                        DesktopPlatform.isMac && event.isMetaPressed && event.key == Key.W -> {
                            windowActions?.close?.invoke()
                            true
                        }
                        DesktopPlatform.isMac && event.isMetaPressed && event.key == Key.M -> {
                            windowActions?.minimize?.invoke()
                            true
                        }
                        // One layer at a time, innermost first: the player's own side panel, then
                        // the player.
                        event.key == Key.Escape -> when {
                            // The palette sits over everything, so it is the first thing Esc puts
                            // away.
                            overlays.quickSearch -> {
                                overlays.quickSearch = false
                                true
                            }
                            overlays.shortcuts -> {
                                overlays.shortcuts = false
                                true
                            }
                            // The player's own layers first — the lyrics, the queue, a
                            // drawer — in the order Android's back reaches them.
                            overlays.nowPlaying && PlayerBack.dispatch() -> true
                            overlays.nowPlaying -> {
                                overlays.nowPlaying = false
                                true
                            }
                            overlays.sidePanel != null -> {
                                overlays.sidePanel = null
                                true
                            }
                            // A page of Settings steps back the way the back button does, unless
                            // one of its prompts is up over it.
                            overlays.settingsPage != null &&
                                !overlays.lastfmLogin && !overlays.listenBrainzToken && !overlays.discordToken -> {
                                goBack()
                                true
                            }
                            else -> false
                        }
                        else -> false
                    }
                },
                topBar = { compact ->
                    DesktopAppleFloatingPlayer(
                        song = radioSong ?: selectedSong,
                        isPlaying = transportPlayback.isPlaying,
                        isLoading = transportPlayback.isLoading,
                        previousEnabled = !liveRadio && (liveQueue.hasPrevious ||
                            playback.positionMs > BACK_RESTARTS_AFTER_MS),
                        progress = if (!liveRadio && playback.durationMs > 0L) {
                            playback.positionMs.toFloat() / playback.durationMs.toFloat()
                        } else {
                            0f
                        },
                        durationMs = if (liveRadio) 0L else playback.durationMs,
                        volume = volume,
                        shuffle = shuffle,
                        repeatMode = repeatMode,
                        liked = selectedSong?.videoId?.let { it in likedIds } == true,
                        onToggleLike = selectedSong?.let { current -> { toggleLike(current) } },
                        onPlayPause = { if (selectedSong != null || liveRadio) togglePlayPauseFromUser() },
                        onPrevious = ::playPrevious,
                        onNext = ::playNext,
                        onShuffleChange = ::setShuffle,
                        onRepeatModeChange = {
                            repeatMode = it
                            persistence.saveString("repeat_mode", it.name)
                        },
                        onSeek = ::seekPlayer,
                        onOpenNowPlaying = ::openPlayerFromUser,
                        onVolumeChange = {
                            volume = it
                            persistence.saveString("volume", it.toString())
                        },
                        onOpenAudioOutput = { overlays.audioOutput = true },
                        // A second click on the same button puts the column away, as in Apple Music.
                        onOpenLyrics = { overlays.toggleSidePanel(DesktopSidePanel.LYRICS) },
                        onOpenQueue = { overlays.toggleSidePanel(DesktopSidePanel.QUEUE) },
                        sidePanel = overlays.sidePanel,
                        liveRadio = liveRadio,
                        onStopRadio = { radioPlayer.stop() },
                    )
                },
                pageNavigation = { compact ->
                    // Back lives in the sidebar's corner now, so this row only carries what the
                    // narrow window has no room for anywhere else.
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        if (compact) {
                            Spacer(Modifier.weight(1f))
                            DesktopToolbarButton(::openSettings, size = 30.dp) {
                                Icon(BitChordIcons.CogIcon, "Settings", tint = DesktopAppleText, modifier = Modifier.size(18.dp))
                            }
                            DesktopAccountButton(activeAccount?.avatar, onClick = { overlays.accounts = true })
                        }
                    }
                },
                sidebar = {
                    DesktopAppleSidebar(
                        destination = destination,
                        settingsOpen = overlays.settingsPage != null,
                        accountPlaylists = sidebarAccountPlaylists,
                        localPlaylists = playlists,
                        openedCollectionId = openedCollection?.browseId,
                        libraryShelf = libraryShowAll?.title,
                        onDestinationSelected = ::selectDestination,
                        onOpenLibraryShelf = ::openLibraryShortcut,
                        onOpenAccountPlaylist = { openShelfItem(it, PLAYLISTS_SHELF) },
                        onOpenLocalPlaylist = ::openPlaylist,
                        onOpenSettings = ::openSettings,
                        signedIn = youtubeSignedIn,
                        accountAvatar = activeAccount?.avatar ?: activeAccount?.profiles?.firstOrNull()?.avatar,
                        onOpenAccounts = { overlays.accounts = true },
                        canGoBack = navHistory.isNotEmpty(),
                        onBack = ::goBack,
                    )
                },
                bottomBar = {
                    Box(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                        DesktopFloatingNavigation(destination, ::selectDestination)
                    }
                },
                trailing = {
                    val current = selectedSong
                    DesktopSidePanelColumn(
                        panel = overlays.sidePanel.takeIf { current != null },
                        onClose = { overlays.sidePanel = null },
                    ) { which ->
                        if (current == null) return@DesktopSidePanelColumn
                        when (which) {
                            DesktopSidePanel.LYRICS -> LyricsSidePanel(
                                song = current,
                                isPlaying = playback.isPlaying,
                                position = playerPosition,
                                lyrics = sharedLyrics,
                                lyricsSource = sharedLyricsSource,
                                lyricsProviderStates = providerStates,
                                onSelectLyricsProvider = { source -> lyricsOnly = source.label },
                                lyricsUnavailable = sharedLyricsUnavailable,
                                onSeek = ::seekPlayer,
                                modifier = Modifier.fillMaxSize(),
                            )
                            DesktopSidePanel.QUEUE -> QueueSidePanel(
                                queue = liveQueue.songs,
                                queueIndex = liveQueue.index,
                                autoplayEnabled = DesktopAutoplay.enabled(partyState, autoplay),
                                onJumpTo = ::playQueueIndex,
                                onRemove = ::removeFromQueue,
                                onMove = ::moveInQueue,
                                onClear = ::clearQueue,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                },
                overlay = {
                    DesktopPlayerSheet(
                        visible = overlays.nowPlaying && selectedSong != null,
                        onDismiss = {
                            DesktopWindowMode.exit()
                            overlays.nowPlaying = false
                        },
                    ) { windowWidth, windowHeight ->
                        val current = selectedSong ?: return@DesktopPlayerSheet
                        val playerSong = trackLinks?.takeIf { it.videoId == current.videoId }
                            ?.let { extra ->
                                current.copy(
                                    artistId = current.artistId ?: extra.artistId,
                                    // Same as on the phone: the watch-queue lookup
                                    // is what carries a channel per credited
                                    // artist, so it is what makes every name in
                                    // the credit line openable.
                                    artists = current.artists.ifEmpty { extra.artists },
                                    albumId = current.albumId ?: extra.albumId,
                                    albumName = current.albumName ?: extra.albumName,
                                )
                            }
                            ?: current
                        NowPlayingScreen(
                            song = playerSong,
                            isPlaying = playback.isPlaying,
                            isLoading = playback.isLoading,
                            position = playerPosition,
                            durationMs = playback.durationMs,
                            audioVersionSwitching = false,
                            qualityUpgraded = false,
                            queue = liveQueue.songs,
                            queueIndex = liveQueue.index,
                            hasPrevious = liveQueue.hasPrevious,
                            hasNext = liveQueue.hasNext,
                            repeatMode = when (repeatMode) {
                                DesktopRepeatMode.OFF -> RepeatModes.OFF
                                DesktopRepeatMode.ONE -> RepeatModes.ONE
                                DesktopRepeatMode.ALL -> RepeatModes.ALL
                            },
                            shuffleEnabled = shuffle,
                            autoplayEnabled = DesktopAutoplay.enabled(partyState, autoplay),
                            signedIn = youtubeSignedIn,
                            accountName = activeAccount?.name?.takeIf(String::isNotBlank),
                            likeStatus = when (current.videoId) {
                                in likedIds -> LikeStatus.LIKE
                                in dislikedIds -> LikeStatus.DISLIKE
                                else -> LikeStatus.INDIFFERENT
                            },
                            onToggleLike = { toggleLike(current) },
                            onPlayPause = ::togglePlayPauseFromUser,
                            onNext = ::playNext,
                            onPrevious = ::playPrevious,
                            onBlockedControl = {
                                DesktopPlayerHost.showMessage("Only the party host can change playback")
                            },
                            onSeek = ::seekPlayer,
                            onSeekFraction = { fraction ->
                                val duration = playbackEngine.state.value.durationMs
                                if (duration > 0) seekPlayer((fraction * duration).toLong())
                            },
                            onToggleShuffle = { setShuffle(!shuffle) },
                            // The phone's order: off, all, one.
                            onCycleRepeat = {
                                repeatMode = when (repeatMode) {
                                    DesktopRepeatMode.OFF -> DesktopRepeatMode.ALL
                                    DesktopRepeatMode.ALL -> DesktopRepeatMode.ONE
                                    DesktopRepeatMode.ONE -> DesktopRepeatMode.OFF
                                }
                                persistence.saveString("repeat_mode", repeatMode.name)
                            },
                            onToggleAutoplay = {
                                setAutoplay(!DesktopAutoplay.enabled(DesktopListenTogether.state.value, autoplay))
                            },
                            onJumpTo = ::playQueueIndex,
                            onRemoveFromQueue = ::removeFromQueue,
                            onMoveInQueue = ::moveInQueue,
                            onClearQueue = ::clearQueue,
                            onOpenMenu = { playerMenuOpen = true },
                            playerMenuBounds = playerMenuBounds,
                            onOpenAlbum = { id ->
                                overlays.nowPlaying = false
                                openAlbum(id)
                            },
                            // The lead credit carries the track's own channel
                            // id and opens straight away; anyone else on the
                            // line has no id, so the page is found from the
                            // name — and only when the answer is that name
                            // exactly, so a fragment like "2115" opens nothing
                            // rather than White 2115.
                            onOpenArtist = { id, name ->
                                overlays.nowPlaying = false
                                if (id != null) {
                                    openArtist(id, name)
                                } else {
                                    scope.launch {
                                        YtMusicRepository.findArtistPageId(name).getOrNull()
                                            ?.let { found -> openArtist(found, name) }
                                    }
                                }
                            },
                            onOpenPlaybackSource = {
                                val id = playerSong.playbackSourceId
                                overlays.nowPlaying = false
                                if (playerSong.playbackSourceType == PlaybackSourceType.BROWSE && id != null) {
                                    openAlbum(id)
                                }
                            },
                            onListenTogether = {
                                overlays.nowPlaying = false
                                overlays.listenTogether = true
                            },
                            lyrics = sharedLyrics,
                            lyricsSource = sharedLyricsSource,
                            lyricsProviderStates = providerStates,
                            onSelectLyricsProvider = { source -> lyricsOnly = source.label },
                            lyricsUnavailable = sharedLyricsUnavailable,
                            lyricsOffsetOpen = false,
                            onDismissLyricsOffset = {},
                            windowWidth = windowWidth,
                            windowHeight = windowHeight,
                            playerLyricsToggle = playerLyricsToggle,
                            playerQueueToggle = playerQueueToggle,
                            playerTranslateToggle = playerTranslateToggle,
                            playerShowingTranslation = playerShowingTranslation,
                            playerRomanizeToggle = playerRomanizeToggle,
                            playerShowingRomanization = playerShowingRomanization,
                            onDismissPlayer = {
                                DesktopWindowMode.exit()
                                overlays.nowPlaying = false
                            },
                            // The two corners the phone keeps in its "…" menu are
                            // drawn here instead: this is a window with a screen to
                            // fill and words that want to be read at size.
                            onToggleFullScreen = DesktopWindowMode::toggle,
                            fullScreen = DesktopWindowMode.fullScreen.collectAsState().value,
                            // The window's right-click raises the same song menu the
                            // player's "…" does, so the two paths cannot disagree
                            // about where it belongs. Consumed on the initial pass so
                            // the sheet's own gestures never see it.
                            modifier = Modifier.pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent(PointerEventPass.Initial)
                                        if (event.type == PointerEventType.Press &&
                                            event.buttons.isSecondaryPressed
                                        ) {
                                            playerMenuOpen = true
                                            event.changes.forEach { it.consume() }
                                        }
                                    }
                                }
                            },
                        )
                        // The lyrics toggle sits in the corner, on the player itself: a glyph that
                        // raises the words, and goes once they are up — the ⋯ menu keeps its own
                        // copy for the times the glyph is not where the pointer is. No backing
                        // disc: on a sleeve it read as a button painted onto the artwork rather
                        // than as part of it, and the hover wash below is the only ground it needs.
                        if (!playerLyricsToggle.value) {
                            Box(
                                Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(start = 24.dp, bottom = 24.dp)
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .desktopHoverWash()
                                    .clickable { playerLyricsToggle.value = true },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    // The outlined bubble the web player puts up when the words are
                                    // down; the filled one belongs to the state where they are not,
                                    // and this glyph is only drawn while they are down.
                                    BitChordIcons.LyricsOff,
                                    DesktopStrings["open_lyrics", "Lyrics"],
                                    tint = Color.White,
                                    modifier = Modifier.size(15.dp),
                                )
                            }
                        }
                        if (playerMenuOpen) {
                            // Pinned to the player's own "…", whose position the
                            // shared player reports up: the sleeve's width, and
                            // the words' column, both move with the window.
                            val bounds = playerMenuBounds.value
                            Box(
                                Modifier
                                    .align(Alignment.TopStart)
                                    .offset {
                                        IntOffset(
                                            x = bounds?.left?.roundToInt() ?: 0,
                                            y = bounds?.top?.roundToInt() ?: 0,
                                        )
                                    }
                                    .size(32.dp),
                            ) {
                                DesktopSongMenuFor(
                                    song = playerSong,
                                    liked = current.videoId in likedIds,
                                    actions = playerSongActions(current),
                                    onToggleLike = { toggleLike(current) },
                                    onRevertToOriginal = {
                                        DesktopOriginalVersion.pin(current.videoId)
                                        playbackEngine.reloadCurrent()
                                    }.takeIf {
                                        playback.streamSourceId != null &&
                                            playback.streamSourceId != "youtube" &&
                                            DesktopMusicSources.hasYouTubeOriginal(current) &&
                                            !DesktopOriginalVersion.isPinned(current.videoId)
                                    },
                                    onUpgradeQuality = {
                                        DesktopOriginalVersion.clear(current.videoId)
                                        playbackEngine.reloadCurrent(forceSourceRefresh = true)
                                    }.takeIf { DesktopOriginalVersion.isPinned(current.videoId) },
                                    onShowLyrics = {
                                        playerMenuOpen = false
                                        playerLyricsToggle.value = true
                                    },
                                    onShowQueue = {
                                        playerMenuOpen = false
                                        playerQueueToggle.value = true
                                    },
                                    onTranslate = {
                                        playerMenuOpen = false
                                        playerTranslateToggle.value = true
                                    },
                                    showingTranslation = playerShowingTranslation.value,
                                    translateEnabled = !sharedLyrics.isNullOrEmpty(),
                                    onRomanize = {
                                        playerMenuOpen = false
                                        playerRomanizeToggle.value = true
                                    },
                                    showingRomanization = playerShowingRomanization.value,
                                    romanizeEnabled = !sharedLyrics.isNullOrEmpty(),
                                    fromPlayer = true,
                                    lyricsProviders = menuLyricsProviders,
                                    onSelectLyricsProvider = { label -> lyricsOnly = label },
                                    onSearchLyrics = { title, artist -> searchLyrics(title, artist) },
                                    lyricsOffsetMs = lyricsOffsetMs,
                                    onSetLyricsOffset = { DesktopPlayerSettings.setLyricsOffsetMs(it) },
                                    onDismiss = { playerMenuOpen = false },
                                )
                            }
                        }
                        val message by DesktopPlayerHost.messages.collectAsState()
                        LaunchedEffect(message) {
                            if (message != null) {
                                delay(2_500)
                                DesktopPlayerHost.messages.value = null
                            }
                        }
                        message?.let { text ->
                            Text(
                                text = text,
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 36.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(Color.Black.copy(alpha = 0.72f))
                                    .padding(horizontal = 18.dp, vertical = 10.dp),
                            )
                        }
                    }
                    if (overlays.accounts) {
                        DesktopAccountSelector(
                            accounts = accounts,
                            activeAccountId = activeAccountId,
                            activeProfileId = activeProfileId,
                            busy = signInBusy != null,
                            onSelect = { account, profile ->
                                DesktopAccounts.select(account.accountId, profile.profileId)
                                activeAccountId = account.accountId
                                activeProfileId = profile.profileId
                            },
                            onAddAccount = { overlays.accounts = false; overlays.signIn = true },
                            onRemoveAccount = { account ->
                                scope.launch {
                                    accounts = withContext(Dispatchers.IO) {
                                        DesktopAccounts.remove(account.accountId)
                                        DesktopAccounts.accounts()
                                    }
                                    activeAccountId = DesktopAccounts.activeAccountId()
                                    activeProfileId = DesktopAccounts.activeProfileId()
                                }
                            },
                            onOpenSettings = { overlays.accounts = false; openSettings() },
                            onDismiss = { overlays.accounts = false },
                        )
                    }

                    if (overlays.signIn) {
                        DesktopSignInDialog(
                            busy = signInBusy,
                            error = signInError,
                            interactiveBrowser = interactiveSignInBrowser,
                            onBrowserSignIn = { browser ->
                                browserSignInJob?.cancel()
                                browserSignInJob = scope.launch {
                                    signInError = null
                                    signInBusy = browser.label
                                    try {
                                        val cookie = DesktopBrowserSignIn.capture(browser)
                                        signIn(cookie, browser.label)
                                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                        throw cancelled
                                    } catch (failure: Exception) {
                                        signInError = failure.message ?: "Could not complete browser sign-in."
                                    } finally {
                                        signInBusy = null
                                        browserSignInJob = null
                                    }
                                }
                            },
                            onImport = { profile ->
                                scope.launch {
                                    when (val found = withContext(Dispatchers.IO) { DesktopBrowserCookies.read(profile) }) {
                                        is DesktopBrowserCookies.Result.Session ->
                            signIn(found.cookie, profile.label, profile.database.toString())
                                        DesktopBrowserCookies.Result.SignedOut ->
                                            signInError = "${profile.label} is not signed in."
                                        is DesktopBrowserCookies.Result.Unavailable ->
                                            signInError = "${profile.label}: ${found.reason}."
                                    }
                                }
                            },
                            onPaste = { pasted ->
                                scope.launch {
                                    if (!DesktopBrowserCookies.hasSigningSecret(pasted)) {
                                        signInError = "That cookie has no signing secret in it — copy the whole header."
                                    } else {
                                        signIn(pasted, "the cookie you pasted")
                                    }
                                }
                            },
                            onDismiss = {
                                browserSignInJob?.cancel()
                                overlays.signIn = false
                                signInError = null
                            },
                        )
                    }
                    if (overlays.playlistDialog || playlistTarget != null) {
                        DesktopPlaylistDialog(
                            song = playlistTarget,
                            accountPlaylists = accountPlaylists,
                            localPlaylists = playlists,
                            signedIn = youtubeSignedIn,
                            canUseAccount = playlistTarget
                                ?.let { DesktopSearchClient.isVideoId(it.videoId) } ?: true,
                            busy = playlistBusy,
                            error = playlistError,
                            onPickAccount = ::addToAccountPlaylist,
                            onPickLocal = ::addToPlaylist,
                            onCreate = ::createPlaylist,
                            onDismiss = ::closePlaylistDialogs,
                        )
                    }
                    if (overlays.rename) {
                        DesktopRenamePlaylistDialog(
                            current = openedCollection?.title.orEmpty(),
                            busy = playlistBusy,
                            error = playlistError,
                            onRename = ::renameOpenPlaylist,
                            onDismiss = ::closePlaylistDialogs,
                        )
                    }
                    if (overlays.delete) {
                        DesktopDeletePlaylistDialog(
                            title = openedCollection?.title.orEmpty(),
                            busy = playlistBusy,
                            error = playlistError,
                            onDelete = ::deleteOpenPlaylist,
                            onDismiss = ::closePlaylistDialogs,
                        )
                    }
                    val reorderCollection = openedCollection?.takeIf { overlays.reorder }
                    if (reorderCollection != null) {
                        DesktopReorderPlaylistDialog(
                            browseId = reorderCollection.browseId,
                            busy = playlistBusy,
                            error = playlistError,
                            onSave = ::reorderOpenPlaylist,
                            onDismiss = ::closePlaylistDialogs,
                            songContextMenu = { song, onDismiss ->
                                DesktopSongMenuFor(
                                    song = song,
                                    liked = song.videoId in likedIds,
                                    actions = songActionsFor(song),
                                    onToggleLike = { toggleLike(song) },
                                    onRevertToOriginal = null,
                                    onUpgradeQuality = null,
                                    onDismiss = onDismiss,
                                )
                            },
                        )
                    }
                    if (overlays.downloadManager) {
                        DesktopDownloadManagerDialog(onDismiss = { overlays.downloadManager = false })
                    }
                    if (overlays.quickSearch) {
                        DesktopQuickSearchOverlay(
                            onDismiss = { overlays.quickSearch = false },
                            onPlaySong = { song ->
                                recordSongSearch(song)
                                playSong(
                                    song,
                                    source = DesktopQueueSource(
                                        DesktopStrings["search", "Search"],
                                        PlaybackSourceType.SEARCH,
                                    ),
                                )
                            },
                            onOpenBrowse = { item ->
                                recordBrowseSearch(item)
                                openBrowseItem(item)
                            },
                            onOpenRecent = { entity ->
                                // Going back to a recent does not record it again, as on the page.
                                when (entity.entityType) {
                                    EntityType.TRACK -> playSong(
                                        Song(
                                            videoId = entity.id,
                                            title = entity.title,
                                            artist = entity.subtitle,
                                            thumbnailUrl = entity.artworkUrl,
                                        ),
                                        source = DesktopQueueSource(entity.title, PlaybackSourceType.SEARCH),
                                    )
                                    EntityType.ARTIST -> openArtist(entity.id, entity.title)
                                    EntityType.ALBUM, EntityType.PLAYLIST -> openBrowseItem(
                                        BrowseItem(
                                            browseId = entity.id,
                                            title = entity.title,
                                            subtitle = entity.subtitle,
                                            thumbnailUrl = entity.artworkUrl,
                                            type = if (entity.entityType == EntityType.ALBUM) {
                                                BrowseType.ALBUM
                                            } else {
                                                BrowseType.PLAYLIST
                                            },
                                        ),
                                    )
                                }
                            },
                            onSearchAll = { term ->
                                selectDestination(DesktopDestination.SEARCH)
                                runSearch(term)
                            },
                        )
                    }
                    if (overlays.shortcuts) {
                        DesktopShortcutsDialog(onDismiss = { overlays.shortcuts = false })
                    }
                    appleActionError?.let { message ->
                        AlertDialog(
                            onDismissRequest = { appleActionError = null },
                            title = { Text(DesktopStrings["d_playback_unavailable", "Playback unavailable"]) },
                            text = { Text(message) },
                            confirmButton = { TextButton(onClick = { appleActionError = null }) { Text(DesktopStrings["d_ok", "OK"]) } },
                        )
                    }
                    if (overlays.lastfmLogin) {
                        DesktopLastfmLoginDialog(onDismiss = { overlays.lastfmLogin = false })
                    }
                    if (overlays.listenBrainzToken) {
                        DesktopListenBrainzTokenDialog(onDismiss = { overlays.listenBrainzToken = false })
                    }
                    if (overlays.discordToken) {
                        DesktopDiscordTokenDialog(onDismiss = { overlays.discordToken = false })
                    }
                    if (overlays.listenTogether) {
                        DesktopListenTogetherDialog(
                            autoplayEnabled = autoplay,
                            onDismiss = { overlays.listenTogether = false },
                        )
                    }
                    if (overlays.audioOutput) {
                        DesktopAudioOutputDialog(onDismiss = { overlays.audioOutput = false })
                    }
                    if (overlays.pipeline) {
                        trackInfoPanel { overlays.pipeline = false }
                    }
                },
            ) { contentPadding ->
                // Desktop pages start at the same top edge as Library. Only preserve space for the
                // floating player/navigation bar at the bottom in compact windows.
                val sharedPagePadding = PaddingValues(bottom = contentPadding.calculateBottomPadding())
                val appleShelves = (appleBrowseState as? UiState.Success)?.data.orEmpty()
                // Apple's charts and its editorial playlists belong on the home page; the
                // music source's own mix and playlist rows are dropped in their place,
                // since they are the same kind of row with art and titles of another
                // service's.
                val homeWithCharts = (homeState as? UiState.Success)?.let {
                    UiState.Success(
                        it.data.filterNot { shelf -> shelf.isAppleChartShelf() || shelf.isSourcePlaylistShelf() } +
                            appleShelves.filter { shelf -> shelf.isAppleChartShelf() || shelf.isApplePlaylistShelf() },
                    )
                } ?: homeState
                val appleCategories = DesktopAppleBrowseClient.categories(appleShelves)
                val browseCategories = if (appleCategories.isNotEmpty()) {
                    val sections = (exploreState as? UiState.Success)?.data.orEmpty().map { section ->
                        section.copy(items = section.items.filterNot { item -> appleCategories.any { it.title.equals(item.title, true) } })
                    }.filter { it.items.isNotEmpty() }
                    UiState.Success(listOf(MoodGenreSection("Charts & Trending", appleCategories)) + sections)
                } else exploreState
                CompositionLocalProvider(
                    LocalPullToRefreshEnabled provides false,
                    LocalShelfRowChrome provides DesktopShelfRowChrome,
                ) {
                DesktopPointerMenuHost<ShelfItem>(
                    menu = { item, onDismiss ->
                        DesktopAppleCollectionMenu(
                            playlist = item.browseId.orEmpty().contains("/playlist/"),
                            onPlay = { playAppleCollection(item, false) },
                            onShuffle = { playAppleCollection(item, true) },
                            onOpen = { openShelfItem(item) },
                            onFind = { openAppleCatalog(item) },
                            onDismiss = onDismiss,
                        )
                    },
                ) { openCollectionMenu ->
                appleCollectionMenu = openCollectionMenu
                DesktopPointerMenuHost<Song>(
                    menu = { song, onDismiss ->
                        DesktopSongMenuFor(
                            song = song,
                            liked = song.videoId in likedIds,
                            actions = songActionsFor(song),
                            onToggleLike = { toggleLike(song) },
                            onRevertToOriginal = null,
                            onUpgradeQuality = null,
                            onDismiss = onDismiss,
                        )
                    },
                ) { openMenu ->
                pageMenu = openMenu
                CompositionLocalProvider(LocalDesktopContextMenu provides openMenu) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    if (event.type == PointerEventType.Press && event.buttons.isBackPressed) goBack()
                                }
                            }
                        },
                ) {
                    when {
                        // Settings and its pages stand in for the page, held to the width the
                        // Settings card had. Its prompts are still cards, over the page.
                        overlays.settingsPage != null -> Box(
                            Modifier.fillMaxSize().padding(bottom = contentPadding.calculateBottomPadding()),
                        ) {
                            CompositionLocalProvider(LocalDesktopPanelIsPage provides true) {
                            when (overlays.settingsPage!!) {
                                DesktopSettingsPage.MAIN,
                                DesktopSettingsPage.PLAYBACK,
                                DesktopSettingsPage.AUDIO,
                                DesktopSettingsPage.APPEARANCE,
                                DesktopSettingsPage.LYRICS,
                                DesktopSettingsPage.SOURCES,
                                DesktopSettingsPage.STORAGE,
                                DesktopSettingsPage.ACCOUNT,
                                DesktopSettingsPage.GENERAL,
                                -> DesktopSettingsScreen(
                                    page = overlays.settingsPage!!,
                                    onOpenPage = { overlays.settingsPage = it },
                                    onBack = ::goBack,
                                    query = settingsQuery,
                                    onQueryChange = { settingsQuery = it },
                                    listState = settingsListState,
                                    autoplay = autoplay,
                                    onAutoplayChange = ::setAutoplay,
                                    automix = automix,
                                    onAutomixChange = {
                                        automix = it
                                        persistence.saveBoolean("automix", it)
                                    },
                                    automixOnSkip = automixOnSkip,
                                    onAutomixOnSkipChange = {
                                        automixOnSkip = it
                                        persistence.saveBoolean("automix_on_skip", it)
                                    },
                                    instantSkipSeconds = instantSkipSeconds,
                                    onInstantSkipSecondsChange = {
                                        instantSkipSeconds = it
                                        persistence.saveString(KEY_INSTANT_SKIP_SECONDS, it.toString())
                                    },
                                    automixPerformance = automixPerformance,
                                    onAutomixPerformanceChange = {
                                        automixPerformance = it
                                        persistence.saveString("automix_performance", it.name)
                                    },
                                    automixPhraseBars = automixPhraseBars,
                                    onAutomixPhraseBarsChange = {
                                        automixPhraseBars = it
                                        persistence.saveString("automix_phrase_bars", it.toString())
                                    },
                                    shuffle = shuffle,
                                    onShuffleChange = ::setShuffle,
                                    repeatMode = repeatMode,
                                    onRepeatModeChange = {
                                        repeatMode = it
                                        persistence.saveString("repeat_mode", it.name)
                                    },
                                    playbackSpeed = playbackSpeed,
                                    onPlaybackSpeedChange = {
                                        playbackSpeed = it
                                        persistence.saveString("playback_speed", it.toString())
                                    },
                                    crossfadeSeconds = crossfadeSeconds,
                                    onCrossfadeSecondsChange = {
                                        crossfadeSeconds = it
                                        persistence.saveString("crossfade_seconds", it.toString())
                                    },
                                    animatedCanvas = animatedCanvas,
                                    showNerdStats = showNerdStats,
                                    fullBleedArtwork = fullBleedArtwork,
                                    onAnimatedCanvasChange = {
                                        animatedCanvas = it
                                        persistence.saveBoolean("animated_canvas", it)
                                    },
                                    dontRepeatSuggestions = dontRepeatSuggestions,
                                    onDontRepeatSuggestionsChange = {
                                        dontRepeatSuggestions = it
                                        persistence.saveBoolean(KEY_DONT_REPEAT_SUGGESTIONS, it)
                                    },
                                    onChooseLocalMusicFolder = { onChosen ->
                                        DesktopLocalMusic.chooseFolder()?.let { chosen ->
                                            DesktopLocalMusic.setFolder(chosen)
                                            onChosen()
                                            localMusicRevision++
                                        }
                                    },
                                    onLocalMusicFolderChanged = { localMusicRevision++ },
                                    filterNonMusicAudio = filterNonMusicAudio,
                                    onFilterNonMusicAudioChange = {
                                        filterNonMusicAudio = it
                                        persistence.saveBoolean(DesktopLocalMusic.KEY_FILTER_NON_MUSIC_AUDIO, it)
                                        // The scan's result changes with it, so it has to be taken again.
                                        localMusicRevision++
                                    },
                                    syncedLyrics = syncedLyrics,
                                    onSyncedLyricsChange = {
                                        syncedLyrics = it
                                        persistence.saveBoolean(DesktopLyricsClient.KEY_SYNCED_LYRICS, it)
                                    },
                                    lyricsBlur = lyricsBlur,
                                    onLyricsBlurChange = {
                                        lyricsBlur = it
                                        persistence.saveBoolean(DesktopLyricsClient.KEY_LYRICS_BLUR, it)
                                    },
                                    enabledLyricsSources = DesktopLyricsClient.enabledSources(lyricsOrder, lyricsOn),
                                    onOpenLyricsSources = { overlays.settingsPage = DesktopSettingsPage.LYRICS_SOURCES },
                                    onOpenTranslationLanguage = { overlays.settingsPage = DesktopSettingsPage.TRANSLATION_LANGUAGE },
                                    onOpenEqualizer = { overlays.settingsPage = DesktopSettingsPage.EQUALIZER },
                                    onOpenAudioOutput = { overlays.settingsPage = DesktopSettingsPage.AUDIO_OUTPUT },
                                    onOpenListenTogether = { overlays.settingsPage = DesktopSettingsPage.LISTEN_TOGETHER },
                                    onShowNerdStatsChange = {
                                        showNerdStats = it
                                        persistence.saveBoolean("show_nerd_stats", it)
                                    },
                                    onFullBleedArtworkChange = {
                                        fullBleedArtwork = it
                                        persistence.saveBoolean("full_bleed_artwork", it)
                                    },
                                    legacyMeshGradient = legacyMeshGradient,
                                    onLegacyMeshGradientChange = {
                                        legacyMeshGradient = it
                                        persistence.saveBoolean("legacy_mesh_gradient", it)
                                    },
                                    spatialAudio = spatialAudio,
                                    onSpatialAudioChange = {
                                        spatialAudio = it
                                        persistence.saveBoolean("spatial_audio", it)
                                    },
                                    dolbyAtmos = dolbyAtmos,
                                    onDolbyAtmosChange = {
                                        dolbyAtmos = it
                                        DesktopAddonSettings.dolbyAtmosEnabled = it
                                        persistence.saveBoolean("dolby_atmos", it)
                                        DesktopAddonSource.clearCompletedTrackCalls()
                                    },
                                    skipSilence = skipSilence,
                                    onSkipSilenceChange = {
                                        skipSilence = it
                                        persistence.saveBoolean("skip_silence", it)
                                    },
                                    outputPrecision = outputPrecision,
                                    onOutputPrecisionChange = {
                                        outputPrecision = it
                                        persistence.saveString("output_precision", it)
                                    },
                                    outputSummary = playbackEngine.outputSummary(),
                                    trayIconEnabled = trayIconEnabled,
                                    closeToTray = closeToTray,
                                    onCloseToTrayChange = {
                                        closeToTray = it
                                        persistence.saveBoolean("close_to_tray", it)
                                    },
                                    onTrayIconChange = {
                                        trayIconEnabled = it
                                        persistence.saveBoolean("tray_icon", it)
                                    },
                                    downloadQuality = downloadQuality,
                                    onDownloadQualityChange = {
                                        downloadQuality = it
                                        persistence.saveString("download_quality", it)
                                    },
                                    audioQuality = audioQuality,
                                    onAudioQualityChange = {
                                        audioQuality = it
                                        persistence.saveAudioQuality(it)
                                    },
                                    sleepTimerMinutes = sleepTimerMinutes,
                                    sleepAfterTrack = sleepAfterTrack,
                                    sleepRemainingMs = sleepRemainingMs,
                                    onSleepTimerCycle = ::cycleSleepTimer,
                                    sourceConfigs = sourceConfigs,
                                    sourceStatus = sourceStatus,
                                    onSourceEnabledChange = { config, enabled ->
                                        val next = sourceConfigs.map {
                                            if (it.id == config.id && it.kind != DesktopSourceKind.YOUTUBE) {
                                                it.copy(enabled = enabled)
                                            } else {
                                                it
                                            }
                                        }
                                        persistence.saveSourceConfigs(next)
                                        sourceConfigs = persistence.sourceConfigs()
                                        DesktopModuleSource.reload()
                                    },
                                    onSaveSource = { saved ->
                                        // Replaced by id, so any number of addons can be configured.
                                        val without = sourceConfigs.filterNot {
                                            it.id == saved.id ||
                                                (saved.kind == DesktopSourceKind.CUSTOM_MODULE &&
                                                    it.kind == DesktopSourceKind.CUSTOM_MODULE)
                                        }
                                        persistence.saveSourceConfigs((without + saved).inSourceOrder())
                                        sourceConfigs = persistence.sourceConfigs()
                                        // Whatever was held for this entry describes a server that may no
                                        // longer be the one selected.
                                        DesktopAddonSource.forget(saved.id)
                                        DesktopModuleSource.reload()
                                    },
                                    onRemoveSource = { config ->
                                        if (config.isUserAdded) {
                                            persistence.saveSourceConfigs(sourceConfigs.filterNot { it.id == config.id })
                                            sourceConfigs = persistence.sourceConfigs()
                                            DesktopAddonSource.forget(config.id)
                                            DesktopModuleSource.reload()
                                        }
                                    },
                                    onMoveSource = { config, delta ->
                                        val next = moveUserSource(sourceConfigs, config.id, delta)
                                        persistence.saveSourceConfigs(next)
                                        sourceConfigs = persistence.sourceConfigs()
                                    },
                                    onTestSource = { candidate ->
                                        sourceStatus = sourceStatus + (candidate.id to "Checking source…")
                                        scope.launch {
                                            val health = if (candidate.kind == DesktopSourceKind.ADDON) {
                                                DesktopAddonSource.health(candidate)
                                            } else {
                                                DesktopModuleSource.health(candidate)
                                            }
                                            health.fold(
                                                onSuccess = { sourceStatus = sourceStatus + (candidate.id to it) },
                                                onFailure = {
                                                    sourceStatus = sourceStatus +
                                                        (candidate.id to (it.message ?: "Source unavailable"))
                                                },
                                            )
                                        }
                                    },
                                    onOpenIntegrations = { overlays.settingsPage = DesktopSettingsPage.INTEGRATIONS },
                                    onOpenLicenses = { overlays.settingsPage = DesktopSettingsPage.LICENSES },
                                    onOpenReplay = {
                                        overlays.settingsPage = null
                                        overlays.replay = true
                                    },
                                )
                                DesktopSettingsPage.EQUALIZER -> DesktopEqualizerDialog(onDismiss = ::goBack)
                                DesktopSettingsPage.AUDIO_OUTPUT -> DesktopAudioOutputDialog(onDismiss = ::goBack)
                                DesktopSettingsPage.LISTEN_TOGETHER -> DesktopListenTogetherDialog(
                                    autoplayEnabled = autoplay,
                                    onDismiss = ::goBack,
                                )
                                DesktopSettingsPage.LYRICS_SOURCES -> DesktopLyricsSourcesDialog(
                                    order = lyricsOrder,
                                    enabled = lyricsOn,
                                    prioritizeSyllables = prioritizeSyllables,
                                    onReorder = {
                                        lyricsOrder = it
                                        persistence.saveLyricsSourceOrder(it)
                                    },
                                    onToggle = { name ->
                                        lyricsOn = if (name in lyricsOn) lyricsOn - name else lyricsOn + name
                                        persistence.saveLyricsEnabledSources(lyricsOn)
                                    },
                                    onPrioritizeSyllables = {
                                        prioritizeSyllables = it
                                        persistence.saveBoolean(DesktopLyricsClient.KEY_PRIORITIZE_SYLLABLES, it)
                                    },
                                    onReset = {
                                        lyricsOrder = DesktopLyricsClient.sources.map { it.name }
                                        lyricsOn = lyricsOrder.toSet()
                                        prioritizeSyllables = false
                                        persistence.saveLyricsSourceOrder(lyricsOrder)
                                        persistence.saveLyricsEnabledSources(lyricsOn)
                                        persistence.saveBoolean(DesktopLyricsClient.KEY_PRIORITIZE_SYLLABLES, false)
                                    },
                                    onDismiss = ::goBack,
                                )
                                DesktopSettingsPage.TRANSLATION_LANGUAGE -> DesktopTranslationLanguageDialog(onDismiss = ::goBack)
                                DesktopSettingsPage.INTEGRATIONS -> DesktopIntegrationsDialog(
                                    song = playback.song,
                                    audioQuality = desktopDiscordAudioQualityLine(playback.streamFormat),
                                    onOpenLastfm = { overlays.lastfmLogin = true },
                                    onOpenListenBrainz = { overlays.listenBrainzToken = true },
                                    onOpenDiscordToken = { overlays.discordToken = true },
                                    onDismiss = ::goBack,
                                )
                                DesktopSettingsPage.LICENSES -> DesktopLicensesPage(onDismiss = ::goBack)
                            }
                            }
                        }
                        overlays.replay -> DesktopReplayPage(
                            summary = replaySummary,
                            period = replayPeriod,
                            holder = replayHolder,
                            onPeriodChange = { replayPeriod = it },
                            onPlaySong = { playSong(it) },
                            onOpenArtist = ::openArtistByName,
                            contentPadding = contentPadding,
                            listState = replayListState,
                        )
                        openedArtist != null -> DesktopArtistPage(
                            state = artistState,
                            fallbackName = openedArtist!!.name,
                            onRetry = {
                                artistState = UiState.Loading
                                artistReloads++
                            },
                            onPlaySongs = { songs, index ->
                                playSongs(songs, index, openedArtist?.let { artistSource(it) })
                            },
                            onShuffle = { songs ->
                                playSongs(songs, 0, openedArtist?.let { artistSource(it) })
                            },
                            onOpenMenu = { song -> openMenu(song) },
                            onShelfItemClick = ::openShelfItem,
                            // A channel subscription is the account's, so a guest is never shown
                            // the button.
                            onToggleSubscription = if (youtubeSignedIn) ::toggleSubscription else null,
                            contentPadding = contentPadding,
                        )
                        openedCollection?.loading == true -> DesktopPageScaffold(contentPadding) {
                            Box(Modifier.fillMaxSize()) {
                                val error = collectionError
                                if (error != null) {
                                    DesktopErrorPage(error) { collectionReloads++ }
                                } else {
                                    val title = openedCollection?.title.orEmpty()
                                    DesktopLoadingPage(if (title.isBlank()) "Loading…" else "Loading $title…")
                                }
                            }
                        }
                        openedCollection != null -> DesktopCollectionPage(
                            collection = openedCollection!!,
                            loadingMore = collectionLoadingMore,
                            onLoadMore = ::loadMoreCollectionSongs,
                            onRename = { overlays.rename = true }.takeIf { openedCollection?.owned == true },
                            onShare = openedCollection
                                ?.takeIf { it.type == BrowseType.ALBUM || it.type == BrowseType.PLAYLIST }
                                ?.let { collection -> { shareCollection(collection) } },
                            onDelete = { overlays.delete = true }.takeIf { openedCollection?.owned == true },
                            onReorder = { overlays.reorder = true }.takeIf {
                                openedCollection?.owned == true && openedCollection?.type == BrowseType.PLAYLIST
                            },
                            onRemoveFromPlaylist = ::removeFromOpenPlaylist
                                .takeIf { openedCollection?.owned == true },
                            likedIds = likedIds,
                            onPlaySongs = { songs, index ->
                                playSongs(songs, index, openedCollection?.let { collectionSource(it) })
                            },
                            onShuffle = { songs ->
                                playSongs(songs.shuffled(), 0, openedCollection?.let { collectionSource(it) })
                            },
                            onDownloadAll = ::downloadAll,
                            animatedCanvas = animatedCanvas,
                            onToggleLike = { song ->
                                toggleLike(song)
                            },
                            onAddToPlaylist = { playlistTarget = it },
                            onDownload = ::downloadSong,
                            downloadedIds = downloads.map(Song::videoId).toSet(),
                            downloadInProgress = downloadInProgress,
                            contentPadding = contentPadding,
                            onArtistClick = ::openArtistByName,
                            onAlbumClick = ::openAlbumByName,
                        )
                        selectedMoodGenre != null -> {
                            val genre = selectedMoodGenre!!
                            // An album's or playlist's own page is the release: its cover, its credit
                            // and the tracks as a table, the way Music draws one. A mood or a
                            // category keeps the shelves it always had.
                            val release = genre.browseId.startsWith(DesktopAppleBrowseClient.PAGE_PREFIX) &&
                                (genre.browseId.contains("/playlist/") || genre.browseId.contains("/album/"))
                            if (release) {
                                val opened = ShelfItem(
                                    title = genre.title,
                                    subtitle = applePageCredit.orEmpty(),
                                    thumbnailUrl = genre.thumbnailUrl,
                                    videoId = null,
                                    browseId = genre.browseId,
                                )
                                // The last segment of the page's own URL is the release's Apple id,
                                // and Apple files its motion artwork under that rather than its name.
                                val appleId = genre.browseId
                                    .removePrefix(DesktopAppleBrowseClient.PAGE_PREFIX)
                                    .substringBefore('#').substringBefore('?')
                                    .trimEnd('/').substringAfterLast('/')
                                DesktopAppleCollectionPage(
                                    title = genre.title,
                                    credit = applePageCredit,
                                    coverUrl = genre.thumbnailUrl,
                                    appleId = appleId,
                                    state = moodGenreShelves,
                                    listState = moodGenreListState,
                                    contentPadding = sharedPagePadding,
                                    animatedCanvas = animatedCanvas,
                                    onPlay = { playAppleCollection(opened, shuffle = false) },
                                    onShuffle = { playAppleCollection(opened, shuffle = true) },
                                    onItemClick = ::openShelfItem,
                                    onItemMenu = ::openShelfMenu,
                                    onRetry = { moodGenreShelves = UiState.Loading; moodGenreReloads++ },
                                    onArtistClick = ::openArtistByName,
                                    // The album cell opens the release it names: the page's own
                                    // link when the table carried one, and Apple's search for
                                    // the album when it did not.
                                    onAlbumClick = { track ->
                                        val detail = DesktopAppleBrowseClient.trackDetail(track)
                                        val album = detail?.album
                                        val url = detail?.albumUrl
                                        if (!album.isNullOrBlank()) {
                                            val open = { page: String ->
                                                openShelfItem(
                                                    ShelfItem(
                                                        title = album,
                                                        subtitle = track.subtitle,
                                                        thumbnailUrl = track.thumbnailUrl,
                                                        videoId = null,
                                                        browseId = DesktopAppleBrowseClient.PAGE_PREFIX + page,
                                                    ),
                                                )
                                            }
                                            if (url != null) {
                                                open(url)
                                            } else {
                                                scope.launch {
                                                    DesktopApplePageSearch.albumPage(album, track.subtitle)
                                                        ?.let(open)
                                                }
                                            }
                                        }
                                    },
                                )
                            } else {
                                DesktopAppleFeedPage(
                                    title = genre.title,
                                    state = moodGenreShelves,
                                    listState = moodGenreListState,
                                    onItemClick = ::openShelfItem,
                                    onItemMenu = ::openShelfMenu,
                                    onRetry = { moodGenreShelves = UiState.Loading; moodGenreReloads++ },
                                    contentPadding = sharedPagePadding,
                                )
                            }
                        }
                        destination == DesktopDestination.LISTEN_NOW -> DesktopAppleFeedPage(
                            state = homeWithCharts,
                            listState = homeListState,
                            onItemClick = ::openShelfItem,
                            onRetry = { loadHome(); appleBrowseReloads++ },
                            contentPadding = sharedPagePadding,
                            title = DesktopStrings["d_home", "Home"],
                            onItemMenu = ::openShelfMenu,
                            onLoadMore = ::loadMoreHome,
                            loadingMore = homeLoadingMore,
                            recentlyPlayedLoading = homeRecentsLoading,
                        )
                        destination == DesktopDestination.EXPLORE -> DesktopAppleFeedPage(
                            title = DesktopStrings["d_new", "New"],
                            state = appleBrowseState,
                            listState = exploreListState,
                            contentPadding = sharedPagePadding,
                            onItemClick = ::openShelfItem,
                            onRetry = { appleBrowseReloads++ },
                            onItemMenu = ::openShelfMenu,
                        )
                        destination == DesktopDestination.RADIO -> DesktopAppleRadioPage(
                            shelves = appleRadioState,
                            listState = radioListState,
                            contentPadding = sharedPagePadding,
                            onItemClick = ::openShelfItem,
                            onItemMenu = ::openShelfMenu,
                            onRetry = { appleRadioReloads++ },
                            playingRadioKey = radioPlayback.station?.id,
                        )
                        destination == DesktopDestination.SEARCH -> Column(Modifier.fillMaxSize().background(DesktopPageGround())) {
                            val fieldFocus = remember { FocusRequester() }
                            LaunchedEffect(searchFocusRequested) {
                                if (searchFocusRequested) {
                                    fieldFocus.requestFocus()
                                    searchFocusRequested = false
                                }
                            }
                            Box(Modifier.fillMaxWidth().padding(top = 32.dp, bottom = 28.dp), contentAlignment = Alignment.Center) {
                                DesktopSearchField(query, ::editQuery, ::search, Modifier.widthIn(max = 347.dp), focusRequester = fieldFocus)
                            }
                            if (query.isBlank()) AppleScrollbarBox(searchListState, Modifier.fillMaxSize()) {
                                DesktopAppleCategories(
                                    state = browseCategories,
                                    listState = searchListState,
                                    contentPadding = sharedPagePadding,
                                    onCategoryClick = ::openMoodGenre,
                                    onRetry = { exploreReloads++ },
                                )
                            } else SearchScreen(
                            query = query,
                            onQueryChange = ::editQuery,
                            filter = searchFilter,
                            // The playing row's indicator, as the phone passes it: the engine's
                            // live state, not the selection a track change publishes behind it.
                            currentSong = playback.song,
                            isPlaying = playback.isPlaying,
                            onFilterChange = {
                                searchFilter = it
                                if (query.isNotBlank()) search()
                            },
                            results = when {
                                searchLoading -> UiState.Loading
                                searchError != null -> UiState.Error(searchError!!)
                                searchCommitted -> UiState.Success(searchRows)
                                else -> null
                            },
                            loadingMore = false,
                            onLoadMore = {},
                            listState = searchListState,
                            scrollResetTrigger = searchScrollReset,
                            focusRequested = searchFocusRequested,
                            onFocusHandled = { searchFocusRequested = false },
                            onSongClick = { songs, index ->
                                songs.getOrNull(index)?.let { song ->
                                    recordSongSearch(song)
                                    playSong(song, source = DesktopQueueSource(DesktopStrings["search", "Search"], PlaybackSourceType.SEARCH))
                                }
                            },
                            onSongLongPress = ::openMenu,
                            onSongSwipe = { song ->
                                if (DesktopAppUiHost.swipeToPlayNext.value) playNext(song) else addToQueue(song)
                            },
                            onTopResultPlay = { song ->
                                recordSongSearch(song)
                                playSong(song, source = DesktopQueueSource(DesktopStrings["search", "Search"], PlaybackSourceType.SEARCH))
                            },
                            onTopResultPlaylist = { song ->
                                recordSongSearch(song)
                                playlistTarget = song
                            },
                            onBrowseClick = { item ->
                                recordBrowseSearch(item)
                                openBrowseItem(item)
                            },
                            history = searchHistory,
                            // The typed text leads the list, put there by the keystroke rather than
                            // taken from the response — the page drops it, as the phone's does.
                            suggestions = if (searchTyping && query.isNotBlank()) {
                                listOf(query) +
                                    searchSuggestions.filterNot { it.equals(query, ignoreCase = true) }
                            } else {
                                emptyList()
                            },
                            typeaheadResults = if (searchTyping && query.isNotBlank()) searchTypeahead else emptyList(),
                            onSubmit = ::search,
                            onSuggestionClick = ::runSearch,
                            onHistoryClick = { entity ->
                                // Going back to a recent does not record it again, as on the phone.
                                when (entity.entityType) {
                                    EntityType.TRACK -> playSong(
                                        Song(
                                            videoId = entity.id,
                                            title = entity.title,
                                            artist = entity.subtitle,
                                            thumbnailUrl = entity.artworkUrl,
                                        ),
                                        source = DesktopQueueSource(entity.title, PlaybackSourceType.SEARCH),
                                    )
                                    EntityType.ARTIST -> openArtist(entity.id, entity.title)
                                    EntityType.ALBUM, EntityType.PLAYLIST -> openBrowseItem(
                                        BrowseItem(
                                            browseId = entity.id,
                                            title = entity.title,
                                            subtitle = entity.subtitle,
                                            thumbnailUrl = entity.artworkUrl,
                                            type = if (entity.entityType == EntityType.ALBUM) BrowseType.ALBUM else BrowseType.PLAYLIST,
                                        ),
                                    )
                                }
                            },
                            onHistoryRemove = DesktopSearchHistory::remove,
                            onHistoryClear = DesktopSearchHistory::clear,
                            onTypeaheadLongPress = ::openMenu,
                            contentPadding = sharedPagePadding,
                            topPadding = 0.dp,
                            showField = false,
                        )
                        }
                        destination == DesktopDestination.LIBRARY && libraryShowAll != null -> Column(Modifier.fillMaxWidth().fillMaxSize()) {
                            // Every page's header, in every state of this one: the sidebar
                            // shortcut names the page after itself, a shelf's "Show all" after
                            // the shelf. The phone keeps the grid's sort beside it, in the bar
                            // above; here it sits at the end of the title row.
                            AppleBrowsePageTitle(libraryShortcut ?: libraryShowAll!!.title) { DesktopLibrarySortMenu() }
                            AppleScrollbarBox(libraryGridState, Modifier.weight(1f).fillMaxWidth()) {
                                LibraryGridPage(
                                    shelf = libraryShowAll!!,
                                    gridState = libraryGridState,
                                    onItemClick = ::openLibraryItem,
                                    onItemLongPress = { item -> if (item.videoId != null) openMenu(item.toSong()) },
                                    contentPadding = PaddingValues(bottom = sharedPagePadding.calculateBottomPadding()),
                                    modifier = Modifier.fillMaxSize(),
                                    onNewPlaylist = { overlays.playlistDialog = true }
                                        .takeIf { libraryShowAll?.title == PLAYLISTS_SHELF },
                                )
                            }
                        }
                        destination == DesktopDestination.LIBRARY -> LibraryScreen(
                            signedIn = youtubeSignedIn,
                            state = libraryState,
                            listState = libraryListState,
                            onShelfItemClick = ::openLibraryItem,
                            onShelfItemLongPress = { item -> if (item.videoId != null) openMenu(item.toSong()) },
                            onNewPlaylist = { overlays.playlistDialog = true },
                            onSignIn = { overlays.accounts = true },
                            onRetry = ::reloadLibrary,
                            refreshing = false,
                            onRefresh = ::reloadLibrary,
                            pullState = rememberPullToRefreshState(),
                            contentPadding = PaddingValues(bottom = sharedPagePadding.calculateBottomPadding()),
                            // Library's own gutter is the measure every other page
                            // uses, so the page takes no inset of its own.
                            modifier = Modifier,
                            links = libraryLinks,
                            deviceItems = libraryDeviceItems,
                            titleRow = { AppleBrowsePageTitle(DesktopStrings["library", "Library"]) },
                        )
                        destination == DesktopDestination.HISTORY -> DesktopHistoryPage(
                            // The account's history when there is one, and what
                            // this computer played when there is not.
                            history = remoteHistory.ifEmpty { history },
                            onSongClick = { playSong(it, source = DesktopQueueSource(DesktopStrings["history", "History"], PlaybackSourceType.HISTORY)) },
                            contentPadding = contentPadding,
                            menu = { song -> songMenu(song) },
                        )
                        destination == DesktopDestination.DOWNLOADS -> Box(Modifier.fillMaxSize()) {
                            DesktopDownloadsPage(
                                downloads = downloads,
                                onSongClick = { playSong(it, source = DesktopQueueSource(DesktopStrings["downloads", "Downloads"], PlaybackSourceType.BROWSE)) },
                                contentPadding = contentPadding,
                                menu = { song -> songMenu(song) },
                            )
                            // Only while there is a queue to look at.
                            if (downloadInProgress.isNotEmpty()) {
                                DesktopActionButton(
                                    "Queue · ${downloadInProgress.size}",
                                    BitChordIcons.Download,
                                    onClick = { overlays.downloadManager = true },
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(end = 28.dp, bottom = 28.dp + contentPadding.calculateBottomPadding()),
                                )
                            }
                        }
                        destination == DesktopDestination.LOCAL_MUSIC -> DesktopLocalMusicPage(
                            songs = localSongs,
                            onSongClick = { playSong(it, source = DesktopQueueSource(DesktopStrings["local_music", "Local Music"], PlaybackSourceType.BROWSE)) },
                            contentPadding = contentPadding,
                            menu = { song -> songMenu(song) },
                        )
                    }
                }
                }
                }
                }

            }
        }
        }
    }

}

/**
 * The window's player settings, gathered for one read.
 *
 * [snapshotFlow] needs a value it can compare with the last — a data class changes when any
 * setting does and not when none does, so the publish below runs exactly as often as the player
 * has something new to hear about.
 */
private data class DesktopPlayerSettingsMirror(
    val animatedCanvas: Boolean,
    val fullBleedArtwork: Boolean,
    val legacyMeshGradient: Boolean,
    val lyricsBlur: Boolean,
    val syncedLyrics: Boolean,
    val showNerdStats: Boolean,
    val smartFadeEnabled: Boolean,
    val smartAnalysis: SmartAnalysis,
    val smartMixInProgress: Boolean,
    val smartTransitionWindow: TransitionWindow?,
    val lyricsSourceOrder: List<LyricsSource>,
    val volume: Float,
)

@Composable
internal fun DesktopToolbarButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    size: Dp = 36.dp,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .desktopHoverWash()
            .clickable(
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/** The small caps headings Apple Music groups its sidebar under — Library, Playlists. */
@Composable
internal fun DesktopSidebarSection(label: String) {
    Text(
        label,
        color = Color.White.copy(alpha = 0.64f),
        fontSize = 12.sp,
        lineHeight = 15.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

/**
 * Apple's own chrome box, and the size the web player draws its sidebar marks in.
 *
 * Apple sizes each silhouette *inside* this box — the house nearly fills it, the
 * magnifier does not — so a column of them keeps an even optical rhythm even
 * though every glyph is drawn at the same number.
 */
internal val DesktopAppleGlyph = 24.dp

/**
 * The size the web player draws its Lucide marks, in the same 24 box: a step
 * smaller, because a round-capped pen spreads past the geometry it traces where a
 * filled shape does not, and 24 of one next to 24 of the other reads as a column
 * of icons two sizes apart.
 */
internal val DesktopLucideGlyph = 18.dp

@Composable
internal fun DesktopSidebarItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    /** Which family [icon] is drawn from — see [DesktopAppleGlyph] and [DesktopLucideGlyph]. */
    iconSize: Dp = DesktopAppleGlyph,
    /** When set, a right-click offers to hide the row — see [DesktopSidebarVisibility]. */
    onHide: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .height(36.dp)
                .desktopHoverFill(corner = 8.dp, alpha = 0.05f)
                .clip(RoundedCornerShape(8.dp))
                .background(if (selected) Color.White.copy(alpha = 0.05f) else Color.Transparent)
                .clickable(onClick = onClick)
                .then(
                    if (onHide == null) Modifier
                    else Modifier.pointerInput(onHide) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                                    menuOpen = true
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        }
                    },
                )
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                Icon(icon, label, tint = if (selected) DesktopAccent else DesktopSecondary, modifier = Modifier.size(iconSize))
            }
            Spacer(Modifier.width(8.dp))
            Text(
                label,
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onHide != null) {
            DesktopDropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(DesktopStrings["hide_from_sidebar", "Hide from sidebar"]) },
                    onClick = {
                        menuOpen = false
                        onHide()
                    },
                )
            }
        }
    }
}


/**
 * The "N hidden" row that brings tucked-away things back: a menu of everything
 * hidden, each restoring on its own, plus a "Show all". Only drawn while
 * something is hidden, so a page never shows it for nothing.
 *
 * The radio page's stations hide behind this; the sidebar rows, which used to
 * carry it at their foot, are restored from Settings → Appearance instead.
 */
@Composable
internal fun DesktopHiddenRestoreRow(
    hidden: Map<String, String>,
    onShow: (String) -> Unit,
    onShowAll: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .desktopHoverFill()
                .clip(RoundedCornerShape(7.dp))
                .clickable { open = true }
                .padding(horizontal = 9.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                BitChordIcons.Minus,
                null,
                tint = DesktopSecondary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                DesktopStrings.format("n_hidden", hidden.size, fallback = "%1\$d hidden"),
                color = DesktopSecondary,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        DesktopDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            hidden.forEach { (key, label) ->
                DropdownMenuItem(
                    text = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = {
                        onShow(key)
                        if (hidden.size == 1) open = false
                    },
                )
            }
            if (hidden.size > 1) {
                DropdownMenuItem(
                    text = { Text(DesktopStrings["show_all", "Show all"]) },
                    onClick = {
                        onShowAll()
                        open = false
                    },
                )
            }
        }
    }
}

@Composable
private fun DesktopFloatingNavigation(
    destination: DesktopDestination,
    onDestinationSelected: (DesktopDestination) -> Unit,
) {
    DesktopStrings.language.collectAsState()
    val tabs = listOf(
        DesktopDestination.LISTEN_NOW to BitChordIcons.Home,
        DesktopDestination.EXPLORE to BitChordIcons.Explore,
        DesktopDestination.RADIO to BitChordIcons.Radio,
        DesktopDestination.LIBRARY to BitChordIcons.Library,
        DesktopDestination.SEARCH to BitChordIcons.Search,
    )
    val selected = tabs.firstOrNull { it.first == destination }?.first ?: DesktopDestination.LIBRARY
    val shape = RoundedCornerShape(percent = 50)
    Surface(
        modifier = Modifier
            .widthIn(max = 440.dp)
            .fillMaxWidth()
            .clip(shape)
            .desktopBarGlass(shape),
        shape = shape,
        color = Color.Transparent,
        tonalElevation = 0.dp,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            tabs.forEach { (item, icon) ->
                val isSelected = item == selected
                val label = item.label
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(if (isSelected) DesktopAccent.copy(alpha = 0.18f) else Color.Transparent)
                        .clickable { onDestinationSelected(item) }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(icon, label, tint = if (isSelected) DesktopAccent else DesktopSecondary, modifier = Modifier.size(20.dp))
                    Text(label, color = if (isSelected) Color.White else DesktopSecondary, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
internal fun DesktopSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = remember { FocusRequester() },
    onFocusChanged: (Boolean) -> Unit = {},
    placeholder: String = DesktopStrings["search", "Search"],
) {
    val searchShape = CircleShape
    val windowInfo = LocalWindowInfo.current
    var focused by remember { mutableStateOf(false) }
    var restoreFocus by remember { mutableStateOf(false) }
    LaunchedEffect(windowInfo.isWindowFocused) {
        if (!windowInfo.isWindowFocused) restoreFocus = focused
        else if (restoreFocus) {
            yield()
            focusRequester.requestFocus()
            restoreFocus = false
        }
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(38.dp)
            .clip(searchShape)
            .background(Color.White.copy(alpha = 0.025f))
            .border(0.5.dp, Color.White.copy(alpha = 0.15f), searchShape)
            .clickable { focusRequester.requestFocus() }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onFocusChanged { focused = it.isFocused; onFocusChanged(it.isFocused) }
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                        onSearch()
                        true
                    } else {
                        false
                    }
                },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
            cursorBrush = SolidColor(DesktopAccent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            decorationBox = { innerTextField ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        BitChordIcons.Search,
                        contentDescription = null,
                        tint = DesktopSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f)) {
                        if (query.isBlank()) {
                            Text(placeholder, color = DesktopSecondary, maxLines = 1)
                        }
                        innerTextField()
                    }
                }
            },
        )
    }
}

@Composable
private fun DesktopFrame(
    containerColor: Color,
    modifier: Modifier = Modifier,
    /**
     * The open page's own colours, painted across the whole window rather than only the content
     * area, so the title bar, top bar and sidebar take their tint from the page they are framing
     * instead of staying a flat slab beside it.
     */
    /** Receives true when its solid base should be left clear for the system material. */
    backdrop: @Composable (transparentBase: Boolean) -> Unit,
    topBar: @Composable (Boolean) -> Unit,
    sidebar: @Composable () -> Unit,
    bottomBar: @Composable (Boolean) -> Unit,
    pageNavigation: @Composable (Boolean) -> Unit,
    /** The column on the far side of the page — the lyrics or the queue, when one is open. */
    trailing: @Composable () -> Unit = {},
    /** The page's own list, so its overlay scrollbar can follow it without being handed by each page. */
    pageScroll: LazyListState? = null,
    overlay: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    // Held out here rather than inside the constraints box.
    val haze = remember { HazeState() }
    // With a system material active, the window stays clear under its chrome. The app-background
    // preference decides whether the page and side column also stay clear or retain their solid
    // gray ground.
    val material by DesktopWindowBackdrop.active.collectAsState()
    val appBackground by DesktopWindowBackdrop.appBackground.collectAsState()
    val glass = material != DesktopBackdrop.OFF
    val materialBehindApp = glass && appBackground
    val maximized by DesktopWindowMode.maximized.collectAsState()
    val roundCorners = DesktopPlatform.isMac && !maximized
    CompositionLocalProvider(LocalDesktopHaze provides haze) {
        Box(
            modifier
                .fillMaxSize()
                .then(if (roundCorners) Modifier.clip(RoundedCornerShape(10.dp)) else Modifier)
                .then(if (glass) Modifier else Modifier.background(containerColor)),
        ) {
            // Both sources of the same state: the chrome blurs the backdrop behind it, and the
            // floating bottom bar blurs the page scrolling under it.
            if (!glass) Box(Modifier.fillMaxSize().hazeSource(haze)) { backdrop(false) }
            Column(Modifier.fillMaxSize()) {
                // Above everything, and outside the box the rest of the window is drawn in, because
                // that is what a title bar is.
                DesktopTitleBar()
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        val compact = maxWidth < 900.dp
                        val titleBarEnabled by DesktopTitleBarSetting.enabled.collectAsState()
                        Row(Modifier.fillMaxSize()) {
                            if (!compact) sidebar()
                            Column(Modifier.weight(1f).fillMaxHeight()) {
                                if (compact && DesktopPlatform.drawsOwnWindowFrame && !titleBarEnabled) {
                                    DesktopTitleBarDragArea(Modifier.fillMaxWidth().height(40.dp)) {
                                        Box(Modifier.fillMaxSize().padding(start = 10.dp), contentAlignment = Alignment.CenterStart) { DesktopWindowButtons() }
                                    }
                                }
                                Row(Modifier.fillMaxWidth().weight(1f)) {
                                    Box(
                                        Modifier.weight(1f).fillMaxHeight()
                                            .then(if (glass && !materialBehindApp) Modifier.background(containerColor) else Modifier),
                                    ) {
                                        Box(Modifier.fillMaxSize().hazeSource(haze)) {
                                            if (glass) backdrop(materialBehindApp)
                                            // The room the floating bottom bar leaves at the foot of
                                            // the page, handed to the page as its own bottom padding.
                                            val pageInsets = PaddingValues(bottom = if (compact) 154.dp else 90.dp)
                                            if (pageScroll != null) {
                                                AppleScrollbarBox(pageScroll, Modifier.fillMaxSize()) { content(pageInsets) }
                                            } else {
                                                content(pageInsets)
                                            }
                                        }
                                        // The strip the page's navigation floats in is window
                                        // furniture as much as the sidebar's caption row is: together
                                        // they carry the whole top edge of the window, so the pointer
                                        // can pick BitChord up anywhere along it and the system can
                                        // snap it to an edge. The gesture sits underneath the content,
                                        // so Back, the account button and anything else in the strip
                                        // keeps its own clicks.
                                        DesktopTitleBarDragArea(
                                            Modifier.align(Alignment.TopStart).fillMaxWidth()
                                                .padding(horizontal = 10.dp, vertical = 10.dp),
                                        ) { pageNavigation(compact) }
                                        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = if (compact) 82.dp else 10.dp)) { topBar(compact) }
                                        if (compact) Box(Modifier.align(Alignment.BottomCenter)) { bottomBar(true) }
                                    }
                                    Box(
                                        Modifier.fillMaxHeight()
                                            .then(if (glass && !materialBehindApp) Modifier.background(containerColor) else Modifier),
                                    ) { trailing() }
                                }
                            }
                        }
                    }
                    overlay()
                }
            }
        }
    }
}

/**
 * What [DesktopFrame] paints behind the window.
 *
 * [artworkUrl] is the page's lead artwork when it has one, and the mesh built from it is laid under
 * ink so a page of text stays readable over it.
 */
@Composable
private fun DesktopPageBackdrop(artworkUrl: String?, transparentBase: Boolean) {
    Box(
        Modifier
            .fillMaxSize()
            .then(if (transparentBase) Modifier else Modifier.background(DesktopBackground)),
    ) {
        if (artworkUrl == null) return@Box
        DesktopMesh(artworkUrl)
        Box(
            Modifier.fillMaxSize().background(
                // Light enough that the sleeve's colour still reads through the page — the
                // wash was being scrimmed to near-black, which is why it looked like the
                // backdrop only worked on Replay — and dark enough for white text.
                Brush.verticalGradient(
                    listOf(
                        Color.Black.copy(alpha = 0.18f),
                        Color.Black.copy(alpha = 0.52f),
                        Color.Black.copy(alpha = 0.74f),
                    ),
                ),
            ),
        )
    }
}

/** What the mesh is averaged from — coarse by design. */
/** Whether AutoPlay may offer a song this session has already played or suggested. */
internal const val KEY_DONT_REPEAT_SUGGESTIONS = "dont_repeat_suggestions"

internal const val KEY_LIBRARY_SORT = "library_sort"

private const val MESH_SOURCE_PX = 120



/** Enough to scroll through, short of turning the shelf into the history page. */
/** The Library's "On device" cards — the phone's ids for the two folders, so their cards draw alike. */
private const val LOCAL_DOWNLOADS_ID = "local:downloads"
private const val LOCAL_MUSIC_ID = "local:all"
private const val LOCAL_PLAYLIST_PREFIX = "desktop-playlist:"



/**
 * An artist: their picture, the numbers under it, their top songs, and the carousels of what they
 * have released.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DesktopArtistPage(
    state: UiState<ArtistPage>,
    fallbackName: String,
    onRetry: () -> Unit,
    onPlaySongs: (List<Song>, Int) -> Unit,
    onShuffle: (List<Song>) -> Unit,
    /** The ⋮ on a top song, or a right-click on it. */
    onOpenMenu: (Song) -> Unit,
    onShelfItemClick: (ShelfItem, String?) -> Unit,
    /** Null for a guest: a channel subscription is the account's. */
    onToggleSubscription: ((SubscriptionState) -> Unit)?,
    contentPadding: PaddingValues,
) {
    val name = canonicalAppleArtistName((state as? UiState.Success)?.data?.name?.takeIf(String::isNotBlank) ?: fallbackName)
    val artistKey = name.filter(Char::isLetterOrDigit).lowercase()
    var appleArt by remember(artistKey) { mutableStateOf(DesktopAppleArtistArtRepository.cached(name)) }
    LaunchedEffect(artistKey) { appleArt = DesktopAppleArtistArtRepository.artFor(name) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    DesktopPageScaffold(contentPadding, listState) {
        when (state) {
            UiState.Loading -> DesktopLoadingPage("Loading $fallbackName…")
            is UiState.Error -> DesktopErrorPage(state.message, onRetry)
            is UiState.Success -> {
                val artist = state.data
                val top = artist.songs.take(MAX_ARTIST_SONGS)
                val names = mapOf("Videos" to "Music Videos", "Featured on" to "Appears On", "Fans might also like" to "Similar Artists", "Playlists" to "Artist Playlists", "Singles" to "Singles & EPs")
                val order = listOf("Essential Albums", "Albums", "Music Videos", "Artist Playlists", "Singles & EPs", "Live Albums", "Compilations", "Appears On", "More To Hear", "More To See", "Similar Artists")
                val shelves = artist.sections.map { it.copy(title = names[it.title] ?: it.title) }
                    .sortedBy { order.indexOf(it.title).let { rank -> if (rank < 0) order.lastIndex - 1 else rank } }
                val latest = shelves.filter { it.title in listOf("Albums", "Singles & EPs") }.flatMap { it.items }
                    .maxByOrNull { Regex("\\b(?:19|20)\\d{2}\\b").find(it.subtitle)?.value?.toIntOrNull() ?: 0 }
                val hasSongsBlock = top.isNotEmpty() || latest != null
                val aboutIndex = 1 + (if (hasSongsBlock) 1 else 0) + shelves.size
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().background(appleArtistWash(appleArt?.background)),
                    contentPadding = pagePadding(bottom = 32.dp),
                ) {
                    item(key = "artist-header") {
                        val subscription = artist.subscription
                        DesktopAppleArtistHeader(
                            name = name,
                            fallbackImageUrl = artist.thumbnailUrl,
                            art = appleArt,
                            favourite = subscription?.subscribed,
                            onPlay = { if (top.isNotEmpty()) onPlaySongs(top, 0) },
                            onShuffle = { if (top.isNotEmpty()) onShuffle(top) },
                            onToggleFavourite = if (subscription != null && onToggleSubscription != null) {
                                { onToggleSubscription(subscription) }
                            } else null,
                            onInfo = { scope.launch { listState.animateScrollToItem(aboutIndex) } },
                        )
                    }
                    if (hasSongsBlock) item(key = "artist-top-songs") {
                        // Apple's artist page is two columns here: the latest release on the left,
                        // top songs beside it — the pair fills the page instead of leaving the
                        // right half empty and a five-wide shelf running off the edge.
                        Row(
                            Modifier.fillMaxWidth()
                                .padding(start = DesktopPageGutter, end = DesktopPageGutter, top = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(40.dp),
                        ) {
                            if (latest != null) {
                                Box(Modifier.width(320.dp)) {
                                    DesktopAppleLatestRelease(latest) { onShelfItemClick(latest, "Latest Release") }
                                }
                            }
                            if (top.isNotEmpty()) {
                                DesktopAppleTopSongs(
                                    top,
                                    onPlay = { onPlaySongs(top, it) },
                                    onOpenMenu = onOpenMenu,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                    shelves.forEachIndexed { index, shelf ->
                        item(key = "artist-shelf-$index") { DesktopAppleShelf(shelf, onShelfItemClick) }
                    }
                    item(key = "artist-about") {
                        DesktopAppleAboutCard(name, appleArt?.bio ?: artist.description, artist.subscriberCountText, artist.monthlyListenerCount)
                    }
                    item(key = "artist-signature") { DesktopArtistSignature(name) }
                    if (top.isEmpty() && shelves.isEmpty()) item {
                        DesktopEmptyPage(BitChordIcons.Person, "Nothing to show for $name", "YouTube Music has no songs or releases on this page.")
                    }
                }
            }
        }
    }
}

/** The artist page that is open, and the name to bill it under until it loads. */
/** The page an artist's tracks were started from. */
private fun artistSource(target: DesktopArtistTarget) =
    DesktopQueueSource(target.name, PlaybackSourceType.BROWSE, target.browseId)

/** The album or playlist a queue was started from. */
private fun collectionSource(collection: DesktopCollection) =
    DesktopQueueSource(collection.title, PlaybackSourceType.BROWSE, collection.browseId)

/** Where a queue was started from, for the player's "Playing from" caption. */
internal data class DesktopQueueSource(
    val title: String,
    val type: PlaybackSourceType,
    val id: String? = null,
)

/**
 * Where this row says it was played from, as the shared queue rules take it — the phone's
 * `play()` makes the same one from the first row of what it was handed.
 */
private fun Song.queueSource(): QueueSource = QueueSource(
    title = playbackSource ?: albumName ?: DesktopStrings["queue", "Queue"],
    type = playbackSourceType ?: PlaybackSourceType.QUEUE,
    id = playbackSourceId,
)

/** Stamps [source] onto a row, leaving one that already names its origin alone. */
private fun Song.withSource(source: DesktopQueueSource?): Song = when {
    source == null || playbackSource != null -> this
    else -> copy(
        playbackSource = source.title,
        playbackSourceType = source.type,
        playbackSourceId = source.id,
    )
}

private data class DesktopArtistTarget(val browseId: String, val name: String)

/** One place the window has been: which page, and whatever was open on it. */
private data class DesktopNavEntry(
    val destination: DesktopDestination,
    val artist: DesktopArtistTarget?,
    val collection: DesktopCollection?,
    val mood: MoodGenre?,
    val showAll: HomeShelf?,
    val replay: Boolean,
    val settings: DesktopSettingsPage?,
) {
    /** What makes two entries the same place: an album that paged in more tracks has not moved. */
    val key: List<Any?>
        get() = listOf(destination, artist?.browseId, collection?.browseId, mood?.browseId, mood?.params, showAll?.title, replay, settings)
}

/** Deep enough for any way back anyone takes; the oldest fall off. */
private const val NAV_HISTORY_LIMIT = 50

/** The strip at the head of the sidebar that holds the window's buttons and can be dragged by. */
private val SIDEBAR_CAPTION_HEIGHT = 34.dp


@Composable
private fun DesktopHistoryPage(
    history: List<Song>,
    onSongClick: (Song) -> Unit,
    contentPadding: PaddingValues,
    menu: (@Composable (Song) -> Unit)? = null,
) {
    val listState = rememberLazyListState()
    DesktopPageScaffold(contentPadding, listState) {
        Column(Modifier.fillMaxSize()) {
            // Every other page opens with its name over a rule; this one was a bare list, and the
            // row buttons made it read as a toolbar rather than a history.
            AppleBrowsePageTitle(DesktopDestination.HISTORY.label)
            LazyColumn(
                state = listState,
                contentPadding = pagePadding(start = DesktopPageGutter, end = DesktopPageGutter, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (history.isEmpty()) item { DesktopEmptyPage(BitChordIcons.Clock, "Nothing played yet", "Songs you play will show up here.") }
                else items(history, key = Song::videoId) {
                    DesktopSongRow(
                        song = it,
                        liked = false,
                        onClick = onSongClick,
                        onToggleLike = null,
                        menu = menu,
                    )
                }
            }
        }
    }
}

@Composable
private fun DesktopDownloadsPage(
    downloads: List<Song>,
    onSongClick: (Song) -> Unit,
    contentPadding: PaddingValues,
    menu: (@Composable (Song) -> Unit)? = null,
) {
    DesktopLocalMusicPage(
        songs = downloads,
        onSongClick = onSongClick,
        contentPadding = contentPadding,
        menu = menu,
        title = DesktopStrings["downloads", "Downloads"],
        subtitle = DesktopStrings["d_available_offline", "Available offline"],
        emptyIcon = BitChordIcons.Download,
        emptyTitle = DesktopStrings["d_no_downloads", "No downloads"],
        emptyDescription = DesktopStrings["d_downloaded_songs_will_appear_here", "Downloaded songs will appear here."],
        persistenceKey = "downloaded",
    )
}

@Composable
private fun DesktopLocalMusicPage(
    songs: List<Song>,
    onSongClick: (Song) -> Unit,
    contentPadding: PaddingValues,
    title: String = "Local Music",
    subtitle: String = "Audio files found in Music and Downloads",
    emptyIcon: androidx.compose.ui.graphics.vector.ImageVector = BitChordIcons.MusicNote,
    emptyTitle: String = "No local music",
    emptyDescription: String = "Put audio files in your Music folder and reopen this page.",
    persistenceKey: String = "local",
    menu: (@Composable (Song) -> Unit)? = null,
) {
    val persistence = remember { DesktopPersistence() }
    var selectedTab by remember(persistenceKey) { mutableStateOf(0) }
    var searchQuery by remember(persistenceKey) { mutableStateOf("") }
    var view by remember(persistenceKey) {
        mutableStateOf(
            if (persistence.string("${persistenceKey}_view", "LIST") == "GRID") {
                DesktopLibraryView.GRID
            } else {
                DesktopLibraryView.LIST
            },
        )
    }
    var sort by remember(persistenceKey) {
        mutableStateOf(
            runCatching {
                DesktopLibrarySort.valueOf(persistence.string("${persistenceKey}_sort", "TITLE_ASC"))
            }.getOrDefault(DesktopLibrarySort.TITLE_ASC),
        )
    }
    var drillDown by remember(persistenceKey) { mutableStateOf<Pair<String, List<Song>>?>(null) }
    val filteredSongs = remember(songs, sort, searchQuery) {
        songs.sortedForDesktopLibrary(sort).filter { song ->
            searchQuery.isBlank() || song.title.contains(searchQuery, true) || song.artist.contains(searchQuery, true) ||
                song.albumName.orEmpty().contains(searchQuery, true)
        }
    }
    val artists = remember(filteredSongs) {
        filteredSongs.groupBy { it.artist.ifBlank { "On This Computer" } }
            .entries.sortedBy { it.key.lowercase() }
    }
    val albums = remember(filteredSongs) {
        filteredSongs.groupBy { it.albumName?.takeIf(String::isNotBlank) ?: "Unknown Album" }
            .entries.sortedBy { it.key.lowercase() }
    }
    DesktopPageScaffold(contentPadding) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = DesktopPageGutter),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DesktopSearchField(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onSearch = {},
                    modifier = Modifier.weight(1f),
                    placeholder = DesktopStrings["d_search_your_music", "Search your music"],
                )
                Spacer(Modifier.width(10.dp))
                TextButton(onClick = {
                    sort = when (sort) {
                        DesktopLibrarySort.TITLE_ASC -> DesktopLibrarySort.TITLE_DESC
                        DesktopLibrarySort.TITLE_DESC -> DesktopLibrarySort.DATE_ADDED
                        DesktopLibrarySort.DATE_ADDED -> DesktopLibrarySort.DATE_MODIFIED
                        DesktopLibrarySort.DATE_MODIFIED -> DesktopLibrarySort.TITLE_ASC
                    }
                    persistence.saveString("${persistenceKey}_sort", sort.name)
                }) {
                    Text(sort.label())
                }
                IconButton(onClick = {
                    view = if (view == DesktopLibraryView.LIST) DesktopLibraryView.GRID else DesktopLibraryView.LIST
                    persistence.saveString("${persistenceKey}_view", view.name)
                }) {
                    Icon(if (view == DesktopLibraryView.LIST) BitChordIcons.GridView else BitChordIcons.ListView, "Change view")
                }
            }
            Spacer(Modifier.height(10.dp))
            TabRow(selectedTabIndex = selectedTab, containerColor = Color.Transparent) {
                listOf("Songs", "Artists", "Albums").forEachIndexed { index, label ->
                    Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(label) })
                }
            }
            Spacer(Modifier.height(8.dp))
            if (songs.isEmpty()) {
                DesktopEmptyPage(emptyIcon, emptyTitle, emptyDescription)
            } else if (drillDown != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { drillDown = null }) { Icon(BitChordIcons.ChevronLeft, DesktopStrings["back", "Back"]) }
                    Text(drillDown!!.first, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(6.dp))
                DesktopLibrarySongContent(drillDown!!.second, view, onSongClick, menu)
            } else when (selectedTab) {
                0 -> DesktopLibrarySongContent(filteredSongs, view, onSongClick, menu)
                1 -> DesktopLibraryGroupingContent(artists, view, onGroupClick = { drillDown = it })
                else -> DesktopLibraryGroupingContent(albums, view, onGroupClick = { drillDown = it })
            }
        }
    }
}

private fun DesktopLibrarySort.label(): String = when (this) {
    DesktopLibrarySort.TITLE_ASC -> "A–Z"
    DesktopLibrarySort.TITLE_DESC -> "Z–A"
    DesktopLibrarySort.DATE_ADDED -> "Added"
    DesktopLibrarySort.DATE_MODIFIED -> "Modified"
}

@Composable
private fun DesktopLibrarySongContent(
    songs: List<Song>,
    view: DesktopLibraryView,
    onSongClick: (Song) -> Unit,
    menu: (@Composable (Song) -> Unit)? = null,
) {
    if (songs.isEmpty()) {
        DesktopEmptyPage(BitChordIcons.Search, "No matching music", "Try a different search.")
    } else if (view == DesktopLibraryView.GRID) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = pagePadding(bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            gridItems(songs, key = Song::videoId) { song ->
                Column(Modifier.fillMaxWidth().desktopQuietClick { onSongClick(song) }) {
                    DesktopArtwork(song.thumbnailUrl, Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(12.dp)).desktopHoverFill(corner = 12.dp), px = CARD_ART_PX)
                    Spacer(Modifier.height(7.dp))
                    Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                    Text(song.artist, color = DesktopSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = pagePadding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(songs, key = Song::videoId) { song ->
                DesktopSongRow(
                    song,
                    liked = false,
                    onClick = onSongClick,
                    onToggleLike = null,
                    downloaded = song.localPath != null,
                    menu = menu,
                )
            }
        }
    }
}

@Composable
private fun DesktopLibraryGroupingContent(
    groups: List<Map.Entry<String, List<Song>>>,
    view: DesktopLibraryView,
    onGroupClick: (Pair<String, List<Song>>) -> Unit,
) {
    if (groups.isEmpty()) {
        DesktopEmptyPage(BitChordIcons.Search, "Nothing here yet", "Music will be grouped as it is added.")
    } else if (view == DesktopLibraryView.GRID) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = pagePadding(bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            gridItems(groups, key = { it.key }) { group ->
                Column(Modifier.fillMaxWidth().desktopQuietClick { onGroupClick(group.key to group.value) }) {
                    DesktopArtwork(group.value.firstOrNull()?.thumbnailUrl, Modifier.fillMaxWidth().height(150.dp).clip(CircleShape).desktopHoverFill(), px = CARD_ART_PX)
                    Spacer(Modifier.height(7.dp))
                    Text(group.key, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                    Text(DesktopStrings.format("d_count_songs", group.value.size, fallback = "${group.value.size} songs"), color = DesktopSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = pagePadding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(groups, key = { it.key }) { group ->
                Row(
                    Modifier.fillMaxWidth().clickable { onGroupClick(group.key to group.value) }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DesktopArtwork(group.value.firstOrNull()?.thumbnailUrl, Modifier.size(54.dp).clip(CircleShape))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(group.key, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                        Text(DesktopStrings.format("d_count_songs", group.value.size, fallback = "${group.value.size} songs"), color = DesktopSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(BitChordIcons.Play, DesktopStrings["open", "Open"], tint = DesktopSecondary)
                }
            }
        }
    }
}

/**
 * A titled block of rows, and the page of Settings it sits on. Settings' main page lists one row per
 * [DesktopSettingsPage] rather than every control, so these blocks only compose once one of those
 * rows is tapped — except while the search field has text in it, when all of them show as they did
 * on the single long page.
 */
private enum class DesktopSettingsGroup(val page: DesktopSettingsPage) {
    PLAYBACK(DesktopSettingsPage.PLAYBACK),
    AUDIO(DesktopSettingsPage.AUDIO),
    APPEARANCE(DesktopSettingsPage.APPEARANCE),
    LYRICS(DesktopSettingsPage.LYRICS),
    LOCAL_MUSIC(DesktopSettingsPage.SOURCES),
    SOURCES(DesktopSettingsPage.SOURCES),
    STORAGE(DesktopSettingsPage.STORAGE),
    ACCOUNT(DesktopSettingsPage.ACCOUNT),
    GENERAL(DesktopSettingsPage.GENERAL),
}

/** The eight rows the main page offers, each opening one of Settings' pages. */
private enum class DesktopSettingsEntry(val page: DesktopSettingsPage) {
    PLAYBACK(DesktopSettingsPage.PLAYBACK),
    AUDIO(DesktopSettingsPage.AUDIO),
    APPEARANCE(DesktopSettingsPage.APPEARANCE),
    LYRICS(DesktopSettingsPage.LYRICS),
    SOURCES(DesktopSettingsPage.SOURCES),
    STORAGE(DesktopSettingsPage.STORAGE),
    ACCOUNT(DesktopSettingsPage.ACCOUNT),
    GENERAL(DesktopSettingsPage.GENERAL),
}

/**
 * Settings as a page of inset groups, the way Android presents it, held to the width the old card
 * had so the rows do not stretch across a wide window. Its query and scroll belong to the caller,
 * so they are where they were when back returns from one of the pages it opens.
 */
@Composable
private fun DesktopSettingsScreen(
    page: DesktopSettingsPage,
    onOpenPage: (DesktopSettingsPage) -> Unit,
    onBack: () -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    listState: LazyListState,
    autoplay: Boolean,
    onAutoplayChange: (Boolean) -> Unit,
    automix: Boolean,
    onAutomixChange: (Boolean) -> Unit,
    automixOnSkip: Boolean,
    onAutomixOnSkipChange: (Boolean) -> Unit,
    /** Skips made before this many seconds have played cut instead of blending; 0 = never. */
    instantSkipSeconds: Int,
    onInstantSkipSecondsChange: (Int) -> Unit,
    automixPerformance: AutomixPerformanceMode,
    onAutomixPerformanceChange: (AutomixPerformanceMode) -> Unit,
    /** Apple-style Automix phrase length in bars: 0 = adaptive, 8 or 16 = fixed. */
    automixPhraseBars: Int,
    onAutomixPhraseBarsChange: (Int) -> Unit,
    shuffle: Boolean,
    onShuffleChange: (Boolean) -> Unit,
    repeatMode: DesktopRepeatMode,
    onRepeatModeChange: (DesktopRepeatMode) -> Unit,
    playbackSpeed: Float,
    onPlaybackSpeedChange: (Float) -> Unit,
    crossfadeSeconds: Int,
    onCrossfadeSecondsChange: (Int) -> Unit,
    animatedCanvas: Boolean,
    onAnimatedCanvasChange: (Boolean) -> Unit,
    dontRepeatSuggestions: Boolean,
    onDontRepeatSuggestionsChange: (Boolean) -> Unit,
    filterNonMusicAudio: Boolean,
    onFilterNonMusicAudioChange: (Boolean) -> Unit,
    onChooseLocalMusicFolder: (onChosen: () -> Unit) -> Unit,
    onLocalMusicFolderChanged: () -> Unit,
    syncedLyrics: Boolean,
    onSyncedLyricsChange: (Boolean) -> Unit,
    lyricsBlur: Boolean,
    onLyricsBlurChange: (Boolean) -> Unit,
    /** The sources that will actually be asked, in the order they are asked. */
    enabledLyricsSources: List<String>,
    onOpenLyricsSources: () -> Unit,
    onOpenTranslationLanguage: () -> Unit,
    onOpenEqualizer: () -> Unit,
    /** Opens the output-device picker. */
    onOpenAudioOutput: () -> Unit,
    /** Opens the listening-together party. */
    onOpenListenTogether: () -> Unit,
    showNerdStats: Boolean,
    onShowNerdStatsChange: (Boolean) -> Unit,
    fullBleedArtwork: Boolean,
    onFullBleedArtworkChange: (Boolean) -> Unit,
    legacyMeshGradient: Boolean,
    onLegacyMeshGradientChange: (Boolean) -> Unit,
    trayIconEnabled: Boolean,
    closeToTray: Boolean,
    onCloseToTrayChange: (Boolean) -> Unit,
    onTrayIconChange: (Boolean) -> Unit,
    spatialAudio: Boolean,
    onSpatialAudioChange: (Boolean) -> Unit,
    dolbyAtmos: Boolean,
    onDolbyAtmosChange: (Boolean) -> Unit,
    skipSilence: Boolean,
    onSkipSilenceChange: (Boolean) -> Unit,
    outputPrecision: String,
    onOutputPrecisionChange: (String) -> Unit,
    outputSummary: String,
    downloadQuality: String,
    onDownloadQualityChange: (String) -> Unit,
    audioQuality: DesktopAudioQuality,
    onAudioQualityChange: (DesktopAudioQuality) -> Unit,
    sleepTimerMinutes: Int?,
    sleepAfterTrack: Boolean,
    sleepRemainingMs: Long?,
    onSleepTimerCycle: () -> Unit,
    sourceConfigs: List<DesktopSourceConfig>,
    sourceStatus: Map<String, String>,
    onSourceEnabledChange: (DesktopSourceConfig, Boolean) -> Unit,
    onSaveSource: (DesktopSourceConfig) -> Unit,
    onRemoveSource: (DesktopSourceConfig) -> Unit,
    onMoveSource: (DesktopSourceConfig, Int) -> Unit,
    onTestSource: (DesktopSourceConfig) -> Unit,
    onOpenIntegrations: () -> Unit,
    onOpenLicenses: () -> Unit,
    onOpenReplay: () -> Unit,
) {
    var editingSource by remember { mutableStateOf<DesktopSourceConfig?>(null) }
    var backupMessage by remember { mutableStateOf<String?>(null) }
    var backupFailed by remember { mutableStateOf(false) }
    var signatureDialogOpen by remember { mutableStateOf(false) }
    val backupScope = rememberCoroutineScope()
    val sourceProbeKey = sourceConfigs
        .filter { it.kind.needsServer && it.isComplete }
        .joinToString { "${it.id}@${it.baseUrl}" }
    LaunchedEffect(sourceProbeKey) {
        sourceConfigs
            .filter { it.kind.needsServer && it.isComplete }
            .forEach(onTestSource)
    }
    DesktopSettingsPageFrame {
            Column(Modifier.fillMaxSize()) {
                val settingsQuery = query.trim()
                Row(
                    Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // No back arrow of its own: opening a sub-page pushes onto the same history
                        // the top bar's back button pops, and a second arrow beside it only made the
                        // header look like two ways out that did the same thing.
                        Text(
                            page.settingsTitle(),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    DesktopSearchField(
                        query = query,
                        onQueryChange = onQueryChange,
                        onSearch = {},
                        placeholder = DesktopStrings["settings_search_hint", "Search settings"],
                        modifier = Modifier.padding(start = 24.dp).width(280.dp),
                    )
                }
                // The list is the caller's and is shared by every page, so a page that opens below a
                // scrolled one has to start at its own top.
                LaunchedEffect(page) { listState.scrollToItem(0) }
                CompositionLocalProvider(LocalSettingsQuery provides settingsQuery) {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    state = listState,
                    contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp),
                ) {
            // The main page is eight rows. Typing in the field brings back the one long page, so a
            // search still reaches every control rather than only the group names.
            if (page == DesktopSettingsPage.MAIN && settingsQuery.isEmpty()) {
                DesktopSettingsEntry.entries.forEach { entry ->
                    item(key = "entry-${entry.name}") {
                        SettingsRow(entry.icon(), entry.title(), entry.summary(), onClick = { onOpenPage(entry.page) })
                    }
                }
            }
            DesktopSettingsGroup.entries.filter { group ->
                settingsQuery.isNotBlank() || group.page == page
            }.forEach { section ->
            if (section == DesktopSettingsGroup.PLAYBACK) item {
                    SettingsGroup(DesktopStrings["playback", "Playback"]) {
                    SettingsToggle(
                        BitChordIcons.Infinity,
                        DesktopStrings["autoplay", "Autoplay"],
                        DesktopStrings["d_keep_the_music_going_with_similar_songs", "Keep the music going with similar songs"],
                        autoplay,
                        onAutoplayChange,
                    )
                    SettingsToggle(
                        BitChordIcons.Shuffle,
                        DesktopStrings["shuffle", "Shuffle"],
                        DesktopStrings["d_mix_the_order_of_the_current_queue", "Mix the order of the current queue"],
                        shuffle,
                        onShuffleChange,
                    )
                    SettingsRow(
                        BitChordIcons.Repeat,
                        DesktopStrings["d_repeat", "Repeat"],
                        repeatMode.label(),
                    ) { onRepeatModeChange(repeatMode.next()) }
                    SettingsRow(
                        BitChordIcons.Performance,
                        DesktopStrings["d_playback_speed", "Playback speed"],
                        "${"%.2f".format(playbackSpeed)}×",
                    ) {
                        val next = when {
                            playbackSpeed < 0.76f -> 1.0f
                            playbackSpeed < 1.01f -> 1.25f
                            playbackSpeed < 1.26f -> 1.5f
                            playbackSpeed < 1.51f -> 2.0f
                            else -> 0.5f
                        }
                        onPlaybackSpeedChange(next)
                    }
                    SettingsRow(
                        BitChordIcons.Moon,
                        DesktopStrings["d_sleep_timer", "Sleep timer"],
                        when {
                            sleepRemainingMs != null -> "${formatSleepTimer(sleepRemainingMs)} until playback pauses"
                            sleepAfterTrack -> "Pausing when this song ends"
                            else -> "Pause playback after a while"
                        },
                    ) { onSleepTimerCycle() }
                    if (!automix) {
                        val crossfadeTitle = DesktopStrings["crossfade", "Crossfade"]
                        val crossfadeValue = if (crossfadeSeconds == 0) {
                            DesktopStrings["d_off", "Off"]
                        } else {
                            "${crossfadeSeconds}s"
                        }
                        if (settingsRowVisible(crossfadeTitle, crossfadeValue)) {
                        Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                            SettingsBlockHeading(BitChordIcons.Clock, crossfadeTitle, crossfadeValue)
                            DesktopBareSlider(
                                value = crossfadeSeconds.toFloat(),
                                onValueChange = { onCrossfadeSecondsChange(it.toInt()) },
                                valueRange = 0f..12f,
                                steps = 11,
                            )
                        }
                        }
                    }
                    SettingsToggle(
                        BitChordIcons.Sliders,
                        DesktopStrings["automix", "Automix [BETA]"],
                        if (automix) {
                            DesktopStrings["d_blends_each_song_into_the_next", "Blends each song into the next"]
                        } else {
                            DesktopStrings["d_times_the_mix_without_a_crossfade_slider", "Times the mix without a crossfade slider"]
                        },
                        automix,
                        onAutomixChange,
                    )
                    SettingsToggle(
                        BitChordIcons.TransportNext,
                        DesktopStrings["automix_on_skip", "Automix on skip"],
                        DesktopStrings[
                            "d_mix_instead_of_cutting_when_you_skip",
                            "Mixes into the next song when you skip ahead",
                        ],
                        automixOnSkip,
                        onAutomixOnSkipChange,
                    )
                    if (automixOnSkip) {
                        val instantTitle = DesktopStrings["instant_skip_window", "Instant skip window"]
                        val instantDetail = DesktopStrings[
                            "d_skips_this_early_cut_straight_to_the_next_song",
                            "Skips this early cut straight to the next song instead of blending",
                        ]
                        val instantValue = if (instantSkipSeconds == 0) {
                            DesktopStrings["d_off", "Off"]
                        } else {
                            "${instantSkipSeconds}s"
                        }
                        if (settingsRowVisible(instantTitle, instantDetail)) {
                            Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                                SettingsBlockHeading(BitChordIcons.FastForward, instantTitle, instantValue)
                                Spacer(Modifier.height(10.dp))
                                DesktopBareSlider(
                                    value = instantSkipSeconds.toFloat(),
                                    onValueChange = { onInstantSkipSecondsChange(it.toInt()) },
                                    valueRange = 0f..MAX_INSTANT_SKIP_SECONDS.toFloat(),
                                    steps = 5,
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    instantDetail,
                                    color = DesktopSecondary,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                    if (automix) {
                        val phraseTitle = DesktopStrings["automix_phrase_length", "Automix phrase length"]
                        val phraseSubtitle =
                            DesktopStrings["d_a_fixed_number_of_bars_beat_aligned", "A fixed number of bars, beat-aligned"]
                        if (settingsRowVisible(phraseTitle, phraseSubtitle)) {
                            Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                                SettingsBlockHeading(BitChordIcons.Ratio, phraseTitle, phraseSubtitle)
                                Spacer(Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(0, 8, 16).forEach { bars ->
                                        FilterChip(
                                            colors = desktopChipColors(),
                                            selected = automixPhraseBars == bars,
                                            onClick = { onAutomixPhraseBarsChange(bars) },
                                            label = { Text(phraseBarsLabel(bars)) },
                                        )
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    phraseBarsDetail(automixPhraseBars),
                                    color = DesktopSecondary,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                    val performanceTitle = DesktopStrings["automix_performance", "Automix performance"]
                    val performanceSubtitle = DesktopStrings["d_cpu_the_background_analysis_may_use", "CPU the background analysis may use"]
                    if (settingsRowVisible(performanceTitle, performanceSubtitle)) {
                        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                            SettingsBlockHeading(BitChordIcons.Performance, performanceTitle, performanceSubtitle)
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AutomixPerformanceMode.entries.forEach { mode ->
                                    FilterChip(
                                        colors = desktopChipColors(),
                                        selected = automixPerformance == mode,
                                        onClick = { onAutomixPerformanceChange(mode) },
                                        label = { Text(mode.label()) },
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                automixPerformance.detail(),
                                color = DesktopSecondary,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    SettingsToggle(
                        BitChordIcons.FastForward,
                        DesktopStrings["skip_silence", "Skip silence"],
                        DesktopStrings["d_shorten_long_gaps_rather_than_playing_them_out", "Shorten long gaps rather than playing them out"],
                        skipSilence,
                        onSkipSilenceChange,
                    )
                    val sponsorOn by DesktopSponsorBlock.enabled.collectAsState()
                    SettingsToggle(
                        BitChordIcons.Ban,
                        DesktopStrings["sponsorblock", "SponsorBlock"],
                        DesktopStrings["d_skips_intros_outros_and_other_non_music_parts", "Skips intros, outros and other non-music parts"],
                        sponsorOn,
                        { DesktopSponsorBlock.setEnabled(it) },
                    )
                    SettingsToggle(
                        BitChordIcons.TransportRepeatOne,
                        DesktopStrings["d_dont_repeat_songs_in_current_session", "Don’t repeat songs in current session"],
                        DesktopStrings["d_autoplay_wont_repeat_a_song_from_this_session", "AutoPlay won’t repeat a song from this session"],
                        dontRepeatSuggestions,
                        onDontRepeatSuggestionsChange,
                    )
                }
            }
            if (section == DesktopSettingsGroup.LYRICS) item {
                SettingsGroup(DesktopStrings["open_lyrics", "Lyrics"]) {
                    SettingsToggle(
                        BitChordIcons.Lyrics,
                        DesktopStrings["synced_lyrics", "Synced lyrics"],
                        DesktopStrings["d_lights_up_the_words_as_they_are_sung", "Lights up the words as they are sung"],
                        syncedLyrics,
                        onSyncedLyricsChange,
                    )
                    // Nothing to choose between while the feature is off, and the sources are
                    // third-party services reached on the listener's connection.
                    if (syncedLyrics) {
                        SettingsToggle(
                            BitChordIcons.Blur,
                            DesktopStrings["d_blur_unfocused_lyrics", "Blur unfocused lyrics"],
                            DesktopStrings["d_keeps_the_spotlight_on_the_current_line", "Keeps the spotlight on the current line"],
                            lyricsBlur,
                            onLyricsBlurChange,
                        )
                        SettingsNavigationRow(
                            BitChordIcons.Queue,
                            title = DesktopStrings["lyrics_sources", "Lyrics sources"],
                            subtitle = enabledLyricsSources
                                .joinToString(", ")
                                .ifEmpty { "None. Lyrics will not be fetched." },
                            onClick = onOpenLyricsSources,
                        )
                        val translationLanguage by DesktopTranslationSetting.language.collectAsState()
                        SettingsNavigationRow(
                            BitChordIcons.Languages,
                            title = DesktopStrings["translation_language", "Translation language"],
                            subtitle = DesktopTranslationSetting.describe(translationLanguage),
                            onClick = onOpenTranslationLanguage,
                        )
                    }
                }
            }
            if (section == DesktopSettingsGroup.STORAGE) item {
                SettingsGroup(DesktopStrings["storage", "Storage"]) {
                    var limitMb by remember { mutableStateOf(DesktopMediaCache.limitMb()) }
                    var cleared by remember { mutableStateOf<String?>(null) }
                    val cacheTitle = DesktopStrings["song_cache_limit", "Song cache limit"]
                    val cacheSubtitle = if (limitMb > DesktopMediaCache.WARNING_MB) {
                        "Holds up to ${formatCacheSize(limitMb)} of audio"
                    } else {
                        DesktopStrings["d_keeps_audio_on_disk_for_instant_replays", "Keeps audio on disk for instant replays"]
                    }
                    if (settingsRowVisible(cacheTitle, cacheSubtitle)) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                        SettingsBlockHeading(BitChordIcons.HardDrive, cacheTitle, cacheSubtitle)
                        DesktopBareSlider(
                            value = limitMb.toFloat(),
                            onValueChange = { limitMb = it.roundToInt() },
                            onValueChangeFinished = { DesktopMediaCache.setLimitMb(limitMb) },
                            valueRange = DesktopMediaCache.MIN_LIMIT_MB.toFloat()..
                                DesktopMediaCache.MAX_LIMIT_MB.toFloat(),
                            steps = 18,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(formatCacheSize(limitMb), color = DesktopSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                    }
                    SettingsRow(
                        BitChordIcons.Trash,
                        DesktopStrings["d_clear_song_cache", "Clear song cache"],
                        cleared ?: DesktopStrings["d_frees_downloaded_audio", "Frees the space downloaded audio takes"],
                    ) {
                        DesktopMediaCache.clear()
                        cleared = DesktopStrings["d_song_cache_cleared", "Song cache cleared"]
                    }
                    var imagesCleared by remember { mutableStateOf(false) }
                    SettingsRow(
                        BitChordIcons.Picture,
                        DesktopStrings["d_clear_image_cache", "Clear image cache"],
                        if (imagesCleared) {
                            DesktopStrings["d_image_cache_cleared", "Image cache cleared"]
                        } else {
                            DesktopStrings["d_frees_album_artwork", "Frees the space album artwork takes"]
                        },
                    ) {
                        DesktopArtworkCache.clear()
                        imagesCleared = true
                    }
                }
            }
            if (section == DesktopSettingsGroup.LOCAL_MUSIC) item {
                SettingsGroup(DesktopStrings["local_music", "Local Music"]) {
                    var folderLabel by remember { mutableStateOf(DesktopLocalMusic.folderLabel()) }
                    SettingsRow(
                        BitChordIcons.Folder,
                        DesktopStrings["d_local_music_folder", "Local music folder"],
                        folderLabel,
                    ) {
                        onChooseLocalMusicFolder { folderLabel = DesktopLocalMusic.folderLabel() }
                    }
                    if (DesktopLocalMusic.folder() != null) {
                        SettingsRow(
                            BitChordIcons.Library,
                            DesktopStrings["use_all_audio_folders", "Use all audio folders"],
                            DesktopStrings["d_scans_music_across_the_device", "Scans for music across the device"],
                        ) {
                            DesktopLocalMusic.setFolder(null)
                            folderLabel = DesktopLocalMusic.folderLabel()
                            onLocalMusicFolderChanged()
                        }
                    }
                    SettingsToggle(
                        BitChordIcons.Funnel,
                        DesktopStrings["filter_non_music_audio", "Filter non-music audio"],
                        DesktopStrings["d_hides_short_clips_voice_notes_and_recordings", "Hides short clips, voice notes and recordings"],
                        filterNonMusicAudio,
                        onFilterNonMusicAudioChange,
                    )
                }
            }
            if (section == DesktopSettingsGroup.APPEARANCE) item {
                SettingsGroup(DesktopStrings["appearance", "Appearance"]) {
                    val reduceDynamicBlur by DesktopAppearanceSettings.reduceDynamicBlur.collectAsState()
                    SettingsToggle(
                        BitChordIcons.EyeOff,
                        DesktopStrings["reduce_dynamic_blur", "Reduce dynamic blur"],
                        DesktopStrings["d_solid_fills_instead_of_frosted_glass", "Solid fills instead of frosted glass"],
                        reduceDynamicBlur,
                        DesktopAppearanceSettings::setReduceDynamicBlur,
                    )
                    // Window furniture belongs beside the visual settings it changes, rather than
                    // interrupting playback controls. These rows remain Windows-only.
                    if (DesktopPlatform.drawsOwnWindowFrame) {
                        val titleBar by DesktopTitleBarSetting.enabled.collectAsState()
                        SettingsToggle(
                            BitChordIcons.Monitor,
                            DesktopStrings["d_title_bar", "Title bar"],
                            DesktopStrings["d_a_slim_bar_with_the_window_s_own_buttons", "A slim bar with the window's own buttons"],
                            titleBar,
                            DesktopTitleBarSetting::set,
                        )
                    }
                    if (DesktopWindowBackdrop.available) {
                        val backdropChoice by DesktopWindowBackdrop.selected.collectAsState()
                        val backdropActive by DesktopWindowBackdrop.active.collectAsState()
                        val materialTitle = DesktopStrings["d_window_material", "Window material"]
                        val materialSubtitle = DesktopStrings[
                            "d_window_material_subtitle",
                            "Sets the material for the sidebar and top bar",
                        ]
                        if (settingsRowVisible(materialTitle, materialSubtitle)) {
                            Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                                SettingsBlockHeading(BitChordIcons.Brush, materialTitle, materialSubtitle)
                                Spacer(Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    DesktopBackdrop.entries.forEach { option ->
                                        FilterChip(
                                            colors = desktopChipColors(),
                                            selected = backdropChoice == option,
                                            onClick = { DesktopWindowBackdrop.set(option) },
                                            label = { Text(option.label) },
                                        )
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    when {
                                        backdropChoice != DesktopBackdrop.OFF && backdropActive == DesktopBackdrop.OFF ->
                                            if (DesktopPlatform.isMac) "Native vibrancy could not be attached"
                                            else "Needs Windows 11 version 22H2 or later"
                                        backdropChoice == DesktopBackdrop.MICA ->
                                            if (DesktopPlatform.isMac) "A subtle blur of your desktop wallpaper"
                                            else "A soft tint taken from your wallpaper"
                                        backdropChoice == DesktopBackdrop.ACRYLIC ->
                                            "A frosted blur of whatever is behind the window"
                                        else -> "Solid, as the rest of the app"
                                    },
                                    color = DesktopSecondary,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        val appBackground by DesktopWindowBackdrop.appBackground.collectAsState()
                        SettingsToggle(
                            BitChordIcons.Wallpaper,
                            DesktopStrings["d_app_background", "App background"],
                            DesktopStrings["d_the_window_material_behind_the_app", "The window material behind the app"],
                            appBackground,
                            DesktopWindowBackdrop::setAppBackground,
                        )
                        val ambientBackdrop by DesktopAppearanceSettings.ambientBackdrop.collectAsState()
                        SettingsToggle(
                            BitChordIcons.Sun,
                            DesktopStrings["d_ambient_backdrop", "Ambient artwork glow"],
                            DesktopStrings["d_tints_the_glass_with_the_current_artwork", "Tints the glass with the current artwork"],
                            ambientBackdrop,
                            DesktopAppearanceSettings::setAmbientBackdrop,
                        )
                    }
                    SettingsToggle(
                        BitChordIcons.Maximize2,
                        DesktopStrings["full_screen_cover_art", "Full-screen cover art"],
                        DesktopStrings["d_runs_the_cover_to_the_player_s_edges", "Runs the cover to the player's edges"],
                        fullBleedArtwork,
                        onFullBleedArtworkChange,
                    )
                    val turntableArt by DesktopAppearanceSettings.turntableArt.collectAsState()
                    SettingsToggle(
                        BitChordIcons.Disc,
                        DesktopStrings["d_turntable_cover", "Turntable cover"],
                        DesktopStrings["d_spins_the_cover_as_a_record", "Spins the cover as a record on a deck"],
                        turntableArt,
                        DesktopAppearanceSettings::setTurntableArt,
                    )
                    SettingsToggle(
                        BitChordIcons.Palette,
                        DesktopStrings["apple_colour_blobs", "Colour blobs"],
                        DesktopStrings["apple_colour_blobs_subtitle", "Drift the artwork's colours behind the player, as in Apple Music"],
                        legacyMeshGradient,
                        onLegacyMeshGradientChange,
                    )
                    SettingsToggle(
                        BitChordIcons.Film,
                        DesktopStrings["d_animated_canvas", "Animated canvas"],
                        DesktopStrings["d_show_motion_artwork_when_it_is_available", "Show motion artwork when it is available"],
                        animatedCanvas,
                        onAnimatedCanvasChange,
                    )
                    val hideVolumeBar by DesktopAppearanceSettings.hideVolumeBar.collectAsState()
                    SettingsToggle(
                        BitChordIcons.VolumeMute,
                        DesktopStrings["hide_volume_bar", "Hide volume bar"],
                        DesktopStrings["hide_volume_bar_subtitle", "Removes the volume slider from the main player"],
                        hideVolumeBar,
                        DesktopAppearanceSettings::setHideVolumeBar,
                    )
                    // Which sidebar rows are tucked away. Hiding them is a setting rather than
                    // something to leave a row for at the foot of the sidebar, so the list of
                    // what to bring back lives here.
                    val hiddenRows by DesktopSidebarVisibility.hidden.collectAsState()
                    var hiddenRowsOpen by remember { mutableStateOf(false) }
                    val showHiddenRows: () -> Unit = { hiddenRowsOpen = true }
                    Box {
                        SettingsRow(
                            BitChordIcons.ListView,
                            DesktopStrings["hidden_items", "Hidden items"],
                            if (hiddenRows.isEmpty()) {
                                DesktopStrings["d_nothing_hidden_yet", "Nothing is hidden yet"]
                            } else {
                                DesktopStrings.format(
                                    "d_n_rows_tucked_away",
                                    hiddenRows.size,
                                    fallback = "%1\$d rows tucked away from the sidebar",
                                )
                            },
                            onClick = if (hiddenRows.isEmpty()) null else showHiddenRows,
                        )
                        DesktopDropdownMenu(
                            expanded = hiddenRowsOpen,
                            onDismissRequest = { hiddenRowsOpen = false },
                        ) {
                            hiddenRows.forEach { (key, label) ->
                                DropdownMenuItem(
                                    text = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    onClick = {
                                        DesktopSidebarVisibility.show(key)
                                        if (hiddenRows.size == 1) hiddenRowsOpen = false
                                    },
                                )
                            }
                            if (hiddenRows.size > 1) {
                                DropdownMenuItem(
                                    text = { Text(DesktopStrings["show_all", "Show all"]) },
                                    onClick = {
                                        DesktopSidebarVisibility.showAll()
                                        hiddenRowsOpen = false
                                    },
                                )
                            }
                        }
                    }
                }
            }
            if (section == DesktopSettingsGroup.GENERAL) item {
                SettingsGroup(DesktopStrings["d_general", "General"]) {
                    var languageMenuOpen by remember { mutableStateOf(false) }
                    val chosen by DesktopStrings.language.collectAsState()
                    Box {
                        SettingsRow(
                            BitChordIcons.Languages,
                            DesktopStrings["app_language", "App language"],
                            DesktopStrings.languageLabel(),
                        ) { languageMenuOpen = true }
                        DesktopDropdownMenu(languageMenuOpen, onDismissRequest = { languageMenuOpen = false }) {
                            DesktopStrings.available.forEach { language ->
                                DropdownMenuItem(
                                    text = { Text(language.label) },
                                    trailingIcon = if (language.tag == chosen) {
                                        { Icon(BitChordIcons.Check, contentDescription = null) }
                                    } else {
                                        null
                                    },
                                    onClick = {
                                        DesktopStrings.setLanguage(language.tag)
                                        languageMenuOpen = false
                                    },
                                )
                            }
                        }
                    }
                    SettingsToggle(
                        BitChordIcons.Minimize,
                        DesktopStrings["d_tray_icon", "Tray icon"],
                        DesktopStrings[
                            "d_show_bitchord_in_the_system_tray_with_playback_controls",
                            "Show BitChord in the system tray, with playback controls",
                        ],
                        trayIconEnabled,
                        onTrayIconChange,
                    )
                    if (trayIconEnabled) {
                        SettingsToggle(
                            BitChordIcons.Play,
                            DesktopStrings["d_keep_playing_when_closed", "Keep playing when closed"],
                            DesktopStrings[
                                "d_closing_the_window_leaves_bitchord_in_the_tray_instead_o",
                                "Closing the window leaves BitChord in the tray instead of quitting",
                            ],
                            closeToTray,
                            onCloseToTrayChange,
                        )
                    }
                    SettingsToggle(
                        BitChordIcons.BarChart,
                        DesktopStrings["show_nerd_stats", "Show stats for nerds"],
                        DesktopStrings["show_nerd_stats_subtitle", "Codec, bitrate and sample rate on the player"],
                        showNerdStats,
                        onShowNerdStatsChange,
                    )
                    val signatureEndpoint by DesktopSignatureSettings.url.collectAsState()
                    SettingsNavigationRow(
                        BitChordIcons.Pencil,
                        title = DesktopStrings["d_artist_signature_service", "Artist signature service"],
                        subtitle = if (signatureEndpoint.isBlank()) {
                            DesktopStrings["d_nothing_is_fetched_until_you_add_a_link", "Nothing is fetched until you add a link"]
                        } else {
                            signatureEndpoint
                        },
                        onClick = { signatureDialogOpen = true },
                    )
                    SettingsRow(
                        BitChordIcons.Archive,
                        DesktopStrings["d_back_up_settings", "Back up settings"],
                        DesktopStrings["d_writes_your_preferences_to_a_json_file", "Writes your preferences to a JSON file in Downloads"],
                    ) {
                        backupScope.launch {
                            val result = withContext(Dispatchers.IO) { runCatching { DesktopBackup.export() } }
                            backupFailed = result.isFailure
                            backupMessage = result.fold(
                                onSuccess = { file ->
                                    DesktopStrings.format("d_backup_saved_to", file.absolutePath, fallback = "Saved to %1\$s")
                                },
                                onFailure = { error ->
                                    error.message ?: error.javaClass.simpleName
                                },
                            )
                        }
                    }
                }
            }
            if (section == DesktopSettingsGroup.AUDIO) item {
                SettingsGroup(DesktopStrings["d_audio", "Audio"]) {
                    val qualityTitle = DesktopStrings["audio_quality", "Audio quality"]
                    if (settingsRowVisible(qualityTitle, audioQuality.label)) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                        SettingsBlockHeading(BitChordIcons.AudioLines, qualityTitle, audioQuality.label)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DesktopAudioQuality.entries.forEach { rung ->
                                FilterChip(
                                    colors = desktopChipColors(),
                                    selected = audioQuality == rung,
                                    onClick = { onAudioQualityChange(rung) },
                                    label = { Text(rung.label) },
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        // A rung is not a bitrate: it decides which kinds of source are allowed to
                        // answer, so say which.
                        Text(
                            audioQuality.detail,
                            color = DesktopSecondary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    }
                    SettingsToggle(
                        BitChordIcons.AudioWaveform,
                        DesktopStrings["spatial_audio", "Spatial audio"],
                        DesktopStrings["spatial_audio_subtitle", "Widens stereo tracks for a more immersive feel"],
                        spatialAudio,
                        onSpatialAudioChange,
                    )
                    SettingsToggle(
                        BitChordIcons.Clapperboard,
                        DesktopStrings["dolby_atmos", "Dolby Atmos"],
                        if (DesktopCodecs.supportsDolbyAtmos) {
                            DesktopStrings["d_prefers_an_addon_s_immersive_mix", "Prefers an add-on's immersive mix"]
                        } else {
                            DesktopStrings["dolby_atmos_unavailable", "This device can't play Atmos, so these songs play in their usual version"]
                        },
                        dolbyAtmos && DesktopCodecs.supportsDolbyAtmos,
                        { if (DesktopCodecs.supportsDolbyAtmos) onDolbyAtmosChange(it) },
                    )
                    val eqOn by DesktopEqualizerSettings.enabled.collectAsState()
                    SettingsNavigationRow(
                        BitChordIcons.SlidersVertical,
                        title = DesktopStrings["equalizer", "Equalizer"],
                        subtitle = if (eqOn) {
                            DesktopStrings["equalizer_subtitle", "Tone, seven bands and balance"]
                        } else {
                            DesktopStrings["d_off", "Off"]
                        },
                        onClick = onOpenEqualizer,
                    )
                    SettingsNavigationRow(
                        BitChordIcons.Speaker,
                        title = DesktopStrings["pipeline_output_device", "Output device"],
                        subtitle = DesktopAudioDevices.label(),
                        onClick = onOpenAudioOutput,
                    )
                    val precisionTitle = DesktopStrings["d_output_precision", "Output precision"]
                    // What was asked for and what the device agreed to are not the same question, and
                    // a setting that silently did not take is worse than one that says so.
                    val precisionSubtitle = "Playing at $outputSummary"
                    if (settingsRowVisible(precisionTitle, precisionSubtitle)) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                        SettingsBlockHeading(BitChordIcons.Activity, precisionTitle, precisionSubtitle)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("PCM_16" to "16-bit PCM", "FLOAT_32" to "32-bit float").forEach { (value, label) ->
                                FilterChip(
                                    colors = desktopChipColors(),
                                    selected = outputPrecision == value,
                                    onClick = { onOutputPrecisionChange(value) },
                                    label = { Text(label) },
                                )
                            }
                        }
                    }
                    }
                    val downloadsTitle = DesktopStrings["download_channel_name", "Downloads"]
                    val downloadsSubtitle = DesktopStrings["d_quality_of_saved_songs", "Quality of saved songs"]
                    if (settingsRowVisible(downloadsTitle, downloadsSubtitle)) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                        SettingsBlockHeading(BitChordIcons.Download, downloadsTitle, downloadsSubtitle)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("STANDARD" to "Standard", "HIGH" to "High", "LOSSLESS" to "Lossless").forEach { (value, label) ->
                                FilterChip(
                                    colors = desktopChipColors(),
                                    selected = downloadQuality == value,
                                    onClick = { onDownloadQualityChange(value) },
                                    label = { Text(label) },
                                )
                            }
                        }
                    }
                    }
                }
            }
            if (section == DesktopSettingsGroup.SOURCES) item {
                SettingsGroup(DesktopStrings["sources_order_header", "Sources · tried in this order"]) {
                    val sourcesHeading = DesktopStrings["sources_order_header", "Sources · tried in this order"]
                    val userAddedIds = sourceConfigs.inSourceOrder()
                        .filter(DesktopSourceConfig::isUserAdded)
                        .map(DesktopSourceConfig::id)
                    sourceConfigs.inSourceOrder().forEachIndexed { index, config ->
                        if (!settingsRowVisible(sourcesHeading, config.displayName)) return@forEachIndexed
                        DesktopSourceSettingsRow(
                            position = index + 1,
                            config = config,
                            status = sourceStatus[config.id],
                            skippedByQuality = config.enabled && !audioQuality.permits(config.kind),
                            onEdit = if (config.kind.needsServer) {
                                { editingSource = config }
                            } else {
                                null
                            },
                            onToggle = if (config.kind == DesktopSourceKind.YOUTUBE) {
                                null
                            } else {
                                { onSourceEnabledChange(config, it) }
                            },
                            onMoveUp = if (userAddedIds.indexOf(config.id) > 0) {
                                { onMoveSource(config, -1) }
                            } else null,
                            onMoveDown = if (userAddedIds.indexOf(config.id) in 0 until userAddedIds.lastIndex) {
                                { onMoveSource(config, 1) }
                            } else null,
                        )
                    }
                    // One entry point, and it creates an addon.
                    SettingsRow(
                        BitChordIcons.Plus,
                        DesktopStrings["d_add_a_source", "Add a source"],
                        DesktopSourceKind.ADDON.detail,
                    ) {
                        editingSource = DesktopSourceConfig(
                            id = UUID.randomUUID().toString(),
                            kind = DesktopSourceKind.ADDON,
                        )
                    }
                }
            }
            if (section == DesktopSettingsGroup.ACCOUNT) item {
                // One row rather than the four groups this used to be. Every
                // integration option Android has lives behind it; inline, the
                // list was longer than the rest of Settings put together.
                SettingsGroup(DesktopStrings["d_account", "Account"]) {
                    SettingsRow(
                        BitChordIcons.Users,
                        DesktopStrings["account_integrations", "Account & integrations"],
                        DesktopStrings["d_discord_rich_presence_last_fm_and_listenbrainz", "Discord rich presence, Last.fm and ListenBrainz"],
                        onOpenIntegrations,
                    )
                    SettingsNavigationRow(
                        BitChordIcons.Play,
                        DesktopStrings["listen_together", "Listen together"],
                        DesktopStrings["listen_together_create_subtitle", "Start a party and share the code."],
                    ) { onOpenListenTogether() }
                    // Replay moved here from the Library page's head, where it
                    // stood between the folder list and the shelves.
                    SettingsRow(
                        BitChordIcons.Calendar,
                        DesktopStrings["replay", "Replay"],
                        DesktopStrings["d_your_year_in_music", "Your year in music"],
                        onOpenReplay,
                    )
                }
            }
            }
            if (page == DesktopSettingsPage.MAIN) item {
                DesktopSettingsFooter(onLicenses = onOpenLicenses)
            }
        }
        }
            }
    }

    editingSource?.let { config ->
        DesktopSourceEditorDialog(
            config = config,
            configuredSources = sourceConfigs,
            isNew = sourceConfigs.none { it.id == config.id },
            status = sourceStatus[config.id],
            onDismiss = { editingSource = null },
            onSave = {
                onSaveSource(it)
                editingSource = null
            },
            onRemove = {
                onRemoveSource(config)
                editingSource = null
            },
            onTest = onTestSource,
        )
    }
    backupMessage?.let { message ->
        DesktopBackupDialog(message = message, failed = backupFailed) { backupMessage = null }
    }
    if (signatureDialogOpen) {
        DesktopTextValueDialog(
            title = DesktopStrings["d_artist_signature_service", "Artist signature service"],
            message = DesktopStrings[
                "d_artist_signature_dialog",
                "BitChord asks a service of your choosing for a handwritten signature, and draws it " +
                    "in white over the artist header. The link must answer with JSON. Put {artist} " +
                    "where the name should go, or leave it out and the name is appended as " +
                    "?artist=…&type=handwritten. Clear the field to fetch nothing at all.",
            ],
            initial = DesktopSignatureSettings.url.value,
            placeholder = "https://example.com/signature?artist={artist}",
            onSave = {
                DesktopSignatureSettings.setUrl(it)
                signatureDialogOpen = false
            },
            onDismiss = { signatureDialogOpen = false },
        )
    }
}

/** The line at the foot of the settings sheet, as Android has it. */
@Composable
private fun DesktopSettingsFooter(onLicenses: () -> Unit) {
    val version = remember { System.getProperty("bitchord.version") ?: "1.8-beta1" }
    val linkStyles = TextLinkStyles(
        style = SpanStyle(color = DesktopAccent, textDecoration = TextDecoration.Underline),
    )
    Text(
        text = buildAnnotatedString {
            append("bitchord $version  ")
            withLink(LinkAnnotation.Url("https://github.com/jackychan4891/BitChord-PC-Continued", linkStyles)) {
                append("GitHub")
            }
            append("  ")
            withLink(LinkAnnotation.Url("https://github.com/kushagrasinghx", linkStyles)) {
                append("Developer")
            }
            append("  ")
            withLink(LinkAnnotation.Url("https://discord.gg/pDdKfrdHY6", linkStyles)) {
                append("Discord")
            }
            append("  ")
            withLink(LinkAnnotation.Clickable("licenses", linkStyles) { onLicenses() }) {
                append("Licenses")
            }
            append("\n~YouTube Music Backend")
        },
        style = MaterialTheme.typography.labelSmall,
        color = DesktopSecondary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp),
    )
}

/** Everything this build is assembled from, and the terms each part came under. A page of Settings. */
@Composable
private fun DesktopLicensesPage(onDismiss: () -> Unit) {
    DesktopSettingsPageFrame {
        Text(
            DesktopStrings["d_third_party_licenses", "Third-party licenses"],
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
        )
        Text(
            DesktopStrings["d_bitchord_is_free_software_and_so_is_everything_it_is_bui", "BitChord is free software, and so is everything it is built on."],
            color = DesktopSecondary,
            style = MaterialTheme.typography.bodySmall,
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            DesktopLicenses.groups.forEach { group ->
                item(key = group.title) {
                    SettingsGroup(group.title) {
                        group.entries.forEach { entry ->
                            Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(entry.name, fontWeight = FontWeight.Medium)
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        entry.license,
                                        color = DesktopAccent,
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                }
                                if (entry.note.isNotBlank()) {
                                    Text(
                                        entry.note,
                                        color = DesktopSecondary,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                Text(
                                    entry.url,
                                    color = DesktopSecondary.copy(alpha = 0.7f),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(vertical = 18.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = DesktopAccent, contentColor = Color.White),
            ) { Text(DesktopStrings["done", "Done"]) }
        }
    }
}

@Composable
private fun DesktopSourceSettingsRow(
    position: Int,
    config: DesktopSourceConfig,
    status: String?,
    skippedByQuality: Boolean = false,
    onEdit: (() -> Unit)?,
    onToggle: ((Boolean) -> Unit)?,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
) {
    val enabledAlpha = if (config.enabled) 1f else 0.45f
    Row(
        Modifier
            .fillMaxWidth()
            .desktopRowClickable(enabled = onEdit != null) { onEdit?.invoke() }
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$position",
            color = DesktopSecondary,
            modifier = Modifier.width(20.dp).alpha(enabledAlpha),
        )
        Icon(
            when (config.kind) {
                DesktopSourceKind.ADDON -> BitChordIcons.Puzzle
                DesktopSourceKind.CUSTOM_MODULE, DesktopSourceKind.MODULE -> BitChordIcons.Puzzle
                DesktopSourceKind.JIOSAAVN -> BitChordIcons.Equalizer
                DesktopSourceKind.YOUTUBE -> BitChordIcons.CirclePlay
            },
            contentDescription = null,
            tint = DesktopAccent,
            modifier = Modifier.size(21.dp).alpha(enabledAlpha),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).alpha(enabledAlpha)) {
            Text(config.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                when {
                    // The ceiling in force, not the switch, is why this one is being passed over —
                    // worth saying, or the row reads as enabled and silently unused.
                    skippedByQuality -> "Skipped at this audio quality"
                    status != null -> status
                    !config.isComplete -> "Setup required"
                    config.kind.needsServer -> "Checking…"
                    else -> config.kind.detail
                } + if (!config.allowDownloads) {
                    " · " + DesktopStrings["source_downloads_off", "Streaming only"]
                } else {
                    ""
                },
                color = if (skippedByQuality) DesktopAccent else DesktopSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        if (onMoveUp != null) {
            IconButton(onClick = onMoveUp, modifier = Modifier.size(32.dp)) {
                Icon(BitChordIcons.ChevronUp, "Move source earlier")
            }
        }
        if (onMoveDown != null) {
            IconButton(onClick = onMoveDown, modifier = Modifier.size(32.dp)) {
                Icon(BitChordIcons.ChevronDown, "Move source later")
            }
        }
        if (onToggle == null) {
            Text(DesktopStrings["always_on", "Always on"], color = DesktopSecondary, style = MaterialTheme.typography.bodySmall)
        } else {
            Switch(checked = config.enabled, onCheckedChange = onToggle, colors = desktopSwitchColors())
        }
    }
}

@Composable
private fun DesktopSourceEditorDialog(
    config: DesktopSourceConfig,
    configuredSources: List<DesktopSourceConfig>,
    isNew: Boolean,
    status: String?,
    onDismiss: () -> Unit,
    onSave: (DesktopSourceConfig) -> Unit,
    onRemove: () -> Unit,
    onTest: (DesktopSourceConfig) -> Unit,
) {
    var label by remember(config.id) { mutableStateOf(config.label) }
    var baseUrl by remember(config.id) { mutableStateOf(config.baseUrl) }
    var checking by remember(config.id) { mutableStateOf(false) }
    var message by remember(config.id) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val candidate = config.copy(label = label.trim(), baseUrl = baseUrl.trim())

    /** Works out what is actually on the end of the URL before storing it. */
    fun identify(thenSave: Boolean) {
        if (candidate.baseUrl.isBlank() || checking) return
        checking = true
        message = null
        scope.launch {
            val outcome = DesktopSourceFormats.identify(candidate.baseUrl)
            checking = false
            outcome.fold(
                onSuccess = { detected ->
                    when (detected) {
                        is DesktopDetectedFormat.Addon -> {
                            val named = candidate.copy(
                                kind = DesktopSourceKind.ADDON,
                                baseUrl = detected.baseUrl,
                                label = candidate.label.ifBlank { detected.manifest.displayName },
                                allowDownloads = detected.manifest.downloadsAllowed,
                            )
                            val duplicate = configuredSources.duplicateOf(named.baseUrl, exceptId = named.id)
                            if (duplicate != null) {
                                message = DesktopStrings.format(
                                    "d_already_added_as",
                                    duplicate.displayName,
                                    fallback = "Already added as ${duplicate.displayName}",
                                )
                            } else {
                                message = DesktopStrings.format(
                                    "d_addon_detected",
                                    detected.manifest.displayName,
                                    fallback = "Addon · ${detected.manifest.displayName}",
                                )
                                if (thenSave) onSave(named)
                            }
                        }
                        is DesktopDetectedFormat.ModuleIndex -> {
                            val asIndex = candidate.copy(
                                kind = DesktopSourceKind.CUSTOM_MODULE,
                                baseUrl = detected.url.ifBlank { candidate.baseUrl },
                            )
                            message = DesktopStrings.format(
                                "d_module_index_detected",
                                detected.moduleCount,
                                fallback = "Module index · ${detected.moduleCount} modules",
                            )
                            if (thenSave) onSave(asIndex)
                        }
                        is DesktopDetectedFormat.Unsupported -> message = detected.reason
                    }
                },
                onFailure = { message = it.message ?: "Nothing answered at that address" },
            )
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) DesktopStrings["d_add_a_source", "Add a source"] else config.displayName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = {
                        baseUrl = it
                        message = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(DesktopStrings["d_addon_url", "Addon URL"]) },
                    placeholder = { Text("https://example.com/addon") },
                    singleLine = true,
                )
                Text(
                    DesktopStrings[
                        "d_an_addon_server",
                        "An addon server — BitChord asks it over plain HTTP and runs no code from it. " +
                            "A compatible module index also works and is stored as one.",
                    ],
                    color = DesktopSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(DesktopStrings["d_name_optional", "Name (optional)"]) },
                    placeholder = { Text(DesktopStrings["d_my_music_source", "My music source"]) },
                    singleLine = true,
                )
                (message ?: status)?.let {
                    Text(it, color = DesktopSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        dismissButton = {
            Row {
                if (!isNew) TextButton(onClick = onRemove) { Text(DesktopStrings["remove", "Remove"]) }
                TextButton(onClick = onDismiss) { Text(DesktopStrings["cancel", "Cancel"]) }
            }
        },
        confirmButton = {
            Row {
                TextButton(
                    onClick = {
                        onTest(candidate)
                        identify(thenSave = false)
                    },
                    enabled = candidate.baseUrl.isNotBlank() && !checking,
                ) {
                    Text(if (checking) "Checking…" else "Test")
                }
                TextButton(
                    onClick = { identify(thenSave = true) },
                    enabled = candidate.baseUrl.isNotBlank() && !checking,
                ) {
                    Text(DesktopStrings["save", "Save"])
                }
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DesktopCollectionPage(
    collection: DesktopCollection,
    loadingMore: Boolean,
    onLoadMore: () -> Unit,
    /** The three edits only a playlist's owner is offered. */
    onRename: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    /** Opens the drag-into-order sheet; only a playlist the account owns can be rearranged. */
    onReorder: (() -> Unit)?,
    /** Copies the release's own YouTube Music link; null for anything with no link to share. */
    onShare: (() -> Unit)?,
    onRemoveFromPlaylist: ((Song) -> Unit)?,
    likedIds: Set<String>,
    onPlaySongs: (List<Song>, Int) -> Unit,
    onShuffle: (List<Song>) -> Unit,
    onDownloadAll: (List<Song>) -> Unit,
    animatedCanvas: Boolean,
    onToggleLike: (Song) -> Unit,
    onDownload: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit,
    downloadedIds: Set<String>,
    downloadInProgress: Set<String>,
    contentPadding: PaddingValues,
    /** A row's artist or album is a link, as it is on Apple's own tables. */
    onArtistClick: ((String) -> Unit)? = null,
    onAlbumClick: ((String, String) -> Unit)? = null,
) {
    val songs = collection.songs
    // The phone's "search this list": a filter over the rows already loaded.
    var searching by remember(collection.browseId) { mutableStateOf(false) }
    var listQuery by remember(collection.browseId) { mutableStateOf("") }
    val shownSongs = remember(songs, listQuery) {
        val needle = listQuery.trim()
        songs.withIndex().filter { (_, song) ->
            needle.isBlank() || song.title.contains(needle, ignoreCase = true) ||
                song.artist.contains(needle, ignoreCase = true) ||
                song.albumName.orEmpty().contains(needle, ignoreCase = true)
        }
    }
    var moreOpen by remember(collection.browseId) { mutableStateOf(false) }
    val subtitleParts = collection.subtitle
        .split(" • ", " · ", " | ")
        .map(String::trim)
        .filter(String::isNotBlank)
    val credit = subtitleParts.firstOrNull {
        it.lowercase() !in setOf("album", "single", "ep", "playlist", "artist") &&
            !it.matches(Regex("\\d{4}")) &&
            !it.matches(Regex("\\d{1,2}:\\d{2}(?::\\d{2})?")) &&
            !it.matches(Regex("[0-9,.]+\\s*(?:songs?|tracks?)", RegexOption.IGNORE_CASE))
    }.orEmpty()
    val typeLabel = when (collection.type) {
        BrowseType.ALBUM -> "Album"
        BrowseType.PLAYLIST -> "Playlist"
        BrowseType.ARTIST -> "Artist"
        BrowseType.OTHER -> "Collection"
    }
    val metadata = buildList {
        add(typeLabel.uppercase())
        subtitleParts.firstOrNull { it.matches(Regex("\\d{4}")) }?.let(::add)
        if (songs.isNotEmpty()) add("${songs.size} songs")
        val seconds = songs.sumOf { it.durationText.durationMillis() / 1_000L }
        if (seconds > 0) add(formatCollectionDuration(seconds))
    }.joinToString(" • ").uppercase()
    val listState = rememberLazyListState()
    DesktopPageScaffold(contentPadding, listState) {
        LazyColumn(
            state = listState,
            contentPadding = pagePadding(
                start = DesktopPageGutter,
                top = DesktopPageGutter,
                end = DesktopPageGutter,
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 36.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // The services hang motion artwork off the release, so a page for one asks for
                    // it directly rather than picking a track and hoping it sits on the right
                    // edition.
                    val albumCanvas by produceState<DesktopCanvasArtwork?>(null, collection.title, animatedCanvas) {
                        value = if (!animatedCanvas) {
                            null
                        } else {
                            withContext(Dispatchers.IO) {
                                DesktopCanvasClient.lookupAlbum(
                                    collection.title,
                                    songs.firstOrNull()?.artist.orEmpty(),
                                )
                            }
                        }
                    }
                    Box(Modifier.size(286.dp).clip(RoundedCornerShape(8.dp))) {
                        DesktopArtwork(
                            collection.thumbnailUrl ?: songs.firstOrNull()?.thumbnailUrl,
                            Modifier.fillMaxSize(),
                        )
                        albumCanvas?.let {
                            DesktopCanvasView(it.url, Modifier.fillMaxSize(), fallbackUrl = it.fallbackUrl)
                        }
                    }
                    Spacer(Modifier.width(30.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            typeLabel.uppercase(),
                            color = DesktopSecondary,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.2.sp,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            collection.title,
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (credit.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                credit,
                                Modifier.then(
                                    if (onArtistClick != null) {
                                        Modifier.clickable { onArtistClick(credit) }
                                    } else Modifier,
                                ),
                                color = DesktopAccent,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (metadata.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(metadata, color = DesktopSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(Modifier.height(24.dp))
                        // The phone's release controls, in its order: Shuffle, Play, search this
                        // list, and everything else behind the overflow — downloading a release is
                        // done once, finding a track on it is done while reading the page.
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            DesktopCircleButton(BitChordIcons.Shuffle, DesktopStrings["shuffle", "Shuffle"], onClick = { onShuffle(songs) })
                            DesktopPlayPill(
                                label = DesktopStrings["play", "Play"],
                                onClick = { if (songs.isNotEmpty()) onPlaySongs(songs, 0) },
                                iconOnly = true,
                            )
                            DesktopCircleButton(
                                if (searching) BitChordIcons.Close else BitChordIcons.Search,
                                if (searching) DesktopStrings["close_search", "Close search"] else DesktopStrings["search_this_list", "Search this list"],
                                onClick = {
                                    searching = !searching
                                    if (!searching) listQuery = ""
                                },
                            )
                            Box {
                                DesktopCircleButton(BitChordIcons.Ellipsis, DesktopStrings["more", "More"], onClick = { moreOpen = true })
                                DesktopDropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
                                    DropdownMenuItem(
                                        text = { Text(DesktopStrings["download_all", "Download all"]) },
                                        leadingIcon = { Icon(BitChordIcons.Download, null) },
                                        onClick = {
                                            moreOpen = false
                                            onDownloadAll(songs)
                                        },
                                    )
                                    onShare?.let { share ->
                                        DropdownMenuItem(
                                            text = { Text(DesktopStrings["share", "Share"]) },
                                            leadingIcon = { Icon(BitChordIcons.ArrowUpRight, null) },
                                            onClick = {
                                                moreOpen = false
                                                share()
                                            },
                                        )
                                    }
                                    onRename?.let { rename ->
                                        DropdownMenuItem(
                                            text = { Text(DesktopStrings["rename", "Rename"]) },
                                            leadingIcon = { Icon(BitChordIcons.Pencil, null) },
                                            onClick = {
                                                moreOpen = false
                                                rename()
                                            },
                                        )
                                    }
                                    onReorder?.let { reorder ->
                                        DropdownMenuItem(
                                            text = { Text(DesktopStrings["reorder_playlist", "Edit order"]) },
                                            leadingIcon = { Icon(BitChordIcons.Grip, null) },
                                            onClick = {
                                                moreOpen = false
                                                reorder()
                                            },
                                        )
                                    }
                                    onDelete?.let { delete ->
                                        DropdownMenuItem(
                                            text = { Text(DesktopStrings["delete", "Delete"]) },
                                            leadingIcon = { Icon(BitChordIcons.Trash, null) },
                                            onClick = {
                                                moreOpen = false
                                                delete()
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (searching) {
                item(key = "collection-search") {
                    DesktopSearchField(
                        query = listQuery,
                        onQueryChange = { listQuery = it },
                        onSearch = {},
                        modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
                        placeholder = DesktopStrings["search_this_list", "Search this list"],
                    )
                }
            }
            itemsIndexed(shownSongs, key = { _, (index, song) -> "${song.videoId}-$index" }) { position, (index, song) ->
                DesktopCollectionSongRow(
                    // Striped by place on screen, not track number, so a filtered list still
                    // alternates.
                    striped = position % 2 == 1,
                    song = song,
                    collectionType = collection.type,
                    liked = song.videoId in likedIds,
                    onClick = { _ -> onPlaySongs(songs, index) },
                    onToggleLike = onToggleLike,
                    onDownload = onDownload,
                    onAddToPlaylist = onAddToPlaylist,
                    downloaded = song.videoId in downloadedIds,
                    downloadInProgress = song.videoId in downloadInProgress,
                    number = index + 1,
                    onRemove = onRemoveFromPlaylist?.takeIf { song.setVideoId != null },
                    onArtistClick = onArtistClick,
                    onAlbumClick = onAlbumClick,
                )
            }
            if (collection.continuation != null) {
                // Reaching this row is what asks for the next page, the way the home feed pages.
                item(key = "collection-more") {
                    LaunchedEffect(songs.size) { onLoadMore() }
                    Box(
                        Modifier.fillMaxWidth().height(72.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (loadingMore) {
                            CircularProgressIndicator(
                                color = DesktopAccent,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The width of a collection row's action cluster, fixed so the duration column lines up down the
 * table whichever rows can be removed.
 */
private fun collectionActionsWidth(hasRemove: Boolean): Dp =
    if (hasRemove) 136.dp else 102.dp

@Composable
private fun DesktopCollectionSongRow(
    song: Song,
    collectionType: BrowseType,
    liked: Boolean,
    onClick: (Song) -> Unit,
    onToggleLike: ((Song) -> Unit)?,
    onDownload: ((Song) -> Unit)?,
    onAddToPlaylist: ((Song) -> Unit)?,
    downloaded: Boolean,
    downloadInProgress: Boolean,
    number: Int,
    /** Every other row is shaded, so a wide row can be followed across to its buttons. */
    striped: Boolean,
    /** Takes this row out of the playlist being read. */
    onRemove: ((Song) -> Unit)? = null,
    onArtistClick: ((String) -> Unit)? = null,
    onAlbumClick: ((String, String) -> Unit)? = null,
) {
    val openContextMenu = LocalDesktopContextMenu.current
    Row(
        Modifier
            .fillMaxWidth()
            .contextClick { openContextMenu(song) }
            .clip(RoundedCornerShape(6.dp))
            .background(if (striped) Color.White.copy(alpha = 0.04f) else Color.Transparent)
            .desktopHoverFill(corner = 6.dp)
            .clickable { onClick(song) }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val nowPlaying = song.isNowPlaying()
        Text(
            number.toString(),
            Modifier.width(28.dp),
            color = DesktopSecondary,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelSmall,
        )
        Spacer(Modifier.width(12.dp))
        // No sleeve on an album's rows.
        if (collectionType != BrowseType.ALBUM) {
            DesktopArtwork(song.thumbnailUrl, Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)), px = ROW_ART_PX)
            Spacer(Modifier.width(12.dp))
        }
        Text(
            song.title,
            Modifier.weight(0.42f),
            color = if (nowPlaying) Color.White else Color.White.copy(alpha = 0.75f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Medium,
        )
        Text(
            song.artist,
            Modifier.weight(0.20f).padding(horizontal = 8.dp)
                .then(
                    if (onArtistClick != null && song.artist.isNotBlank()) {
                        Modifier.clickable { onArtistClick(song.artist) }
                    } else Modifier,
                ),
            color = DesktopSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // Every row of an album is on that album; the column would only repeat the page's title.
        if (collectionType != BrowseType.ALBUM) {
            Text(
                song.albumName ?: "—",
                Modifier.weight(0.25f).padding(horizontal = 8.dp)
                    .then(
                        if (onAlbumClick != null && !song.albumName.isNullOrBlank()) {
                            Modifier.clickable { onAlbumClick(song.albumName.orEmpty(), song.artist) }
                        } else Modifier,
                    ),
                color = DesktopSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            song.durationText ?: "—",
            Modifier.width(58.dp),
            color = DesktopSecondary,
            textAlign = TextAlign.End,
            style = MaterialTheme.typography.bodySmall,
        )
        Row(
            Modifier.width(collectionActionsWidth(onRemove != null)),
            horizontalArrangement = Arrangement.End,
        ) {
            onRemove?.let { remove ->
                IconButton(onClick = { remove(song) }, modifier = Modifier.size(34.dp)) {
                    Icon(
                        BitChordIcons.Minus,
                        DesktopStrings["d_remove_from_playlist", "Remove from playlist"],
                        tint = DesktopSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            onToggleLike?.let { onLike ->
                IconButton(onClick = { onLike(song) }, modifier = Modifier.size(34.dp)) {
                    Icon(
                        if (liked) BitChordIcons.StarFilled else BitChordIcons.Star,
                        "Favorite",
                        tint = if (liked) DesktopAccent else DesktopSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            onDownload?.let { onSave ->
                IconButton(onClick = { if (!downloaded) onSave(song) }, modifier = Modifier.size(34.dp)) {
                    if (downloadInProgress) {
                        CircularProgressIndicator(Modifier.size(16.dp), color = DesktopAccent, strokeWidth = 2.dp)
                    } else {
                        Icon(
                            if (downloaded) BitChordIcons.Download else BitChordIcons.Download,
                            if (downloaded) "Downloaded" else "Download",
                            tint = if (downloaded) DesktopAccent else DesktopSecondary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            onAddToPlaylist?.let { onAdd ->
                IconButton(onClick = { onAdd(song) }, modifier = Modifier.size(34.dp)) {
                    Icon(BitChordIcons.Plus, DesktopStrings["add_to_playlist", "Add to playlist"], tint = DesktopSecondary, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}


/** The track playing, for rows that want to say so. */
private val LocalNowPlaying = compositionLocalOf<Song?> { null }

/**
 * Opens the app's song menu where the pointer is, for the desktop's own rows.
 *
 * The pages the phone shares already hang right-click off this via their long-press call; a
 * desktop-only row has no such hook, so it reads this and calls it from a [contextClick]. The
 * pages that hold it provide it from the pointer host that plants the menu under the cursor.
 */
internal val LocalDesktopContextMenu = compositionLocalOf<(Song) -> Unit> { {} }
/** Whether this row is the track being played, for the now-playing highlight. */
@Composable
private fun Song.isNowPlaying(): Boolean = isSameTrackAs(LocalNowPlaying.current)

/** The frosted pane the floating bars are made of. */
@Composable
internal fun Modifier.desktopBarGlass(shape: Shape): Modifier =
    desktopFrosted(DesktopBarGlass, tintAlpha = 0.55f).border(0.5.dp, DesktopBarEdge, shape)

/**
 * A card a floating sheet is made of — a palette, a shortcuts list — with the window behind it
 * blurred rather than the sheet painted solid.
 *
 * Only the two overlays the listener summons over the whole app use it. Every other card stays
 * solid: they sit over a page the reader has already chosen to leave, and a page of rows showing
 * through a menu is noise.
 */
@Composable
internal fun Modifier.desktopGlassCard(shape: Shape): Modifier =
    clip(shape)
        .desktopFrosted(DesktopCardFill, tintAlpha = GLASS_CARD_TINT)
        .border(0.5.dp, DesktopCardEdge, shape)

/** The dim a sheet sits in, holding a blur of what is behind it rather than plain black. */
@Composable
internal fun Modifier.desktopFrostedScrim(): Modifier {
    val haze = LocalDesktopHaze.current
    val reduceDynamicBlur by DesktopAppearanceSettings.reduceDynamicBlur.collectAsState()
    if (haze == null || reduceDynamicBlur) return this.background(DesktopScrim)
    return this.hazeEffect(state = haze) {
        blurEnabled = true
        backgroundColor = DesktopSurface
        blurRadius = 24.dp
        tints = listOf(HazeTint(DesktopScrim))
        noiseFactor = 0f
    }
}

/** Dense enough that a column of rows over it stays readable, thin enough that the colour shows. */
private const val GLASS_CARD_TINT = 0.78f

/** The edge of a chrome pane that meets the page, and so has to dissolve into it. */
internal enum class DesktopChromeEdge { NONE, BOTTOM, END }

/**
 * The same pane for the window's own chrome — square-edged, and a shade denser, because the sidebar
 * and top bar carry far more text than a floating bar does.
 *
 * [edge] names the side the page is on. The pane's tint is dissolved across the last [fade] of it so
 * the two meet in a gradient rather than on a line.
 */
@Composable
internal fun Modifier.desktopChromeGlass(
    edge: DesktopChromeEdge = DesktopChromeEdge.NONE,
    fade: Float = 0.2f,
): Modifier = desktopFrosted(
    opaque = DesktopSurface.copy(alpha = 0.88f),
    tintAlpha = 0.46f,
    edge = edge,
    fade = fade,
)

/**
 * The window's own chrome — title bar, top bar, sidebar. Over Windows 11's Mica or Acrylic
 * ([DesktopWindowBackdrop]) it is a light dark tint and nothing else, so DWM's material shows
 * through; the in-app blur would paint the page's backdrop over it. Otherwise the in-app glass.
 *
 * No dissolve over the material: it would fade the tint out into bare material right where the
 * opaque page begins, a lighter stripe along the seam rather than a softer one.
 */
@Composable
internal fun Modifier.desktopWindowGlass(
    edge: DesktopChromeEdge = DesktopChromeEdge.NONE,
    fade: Float = 0.2f,
): Modifier {
    val backdrop by DesktopWindowBackdrop.active.collectAsState()
    return if (backdrop == DesktopBackdrop.OFF) {
        desktopChromeGlass(edge, fade)
    } else {
        val base = background(Color.Black.copy(alpha = WINDOW_GLASS_TINT))
        if (DesktopPlatform.isMac && edge == DesktopChromeEdge.BOTTOM) {
            base.drawBehind {
                // Subtle liquid glass specular highlight along the top edge
                drawLine(
                    color = Color.White.copy(alpha = 0.08f),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1f,
                )
            }
        } else {
            base
        }
    }
}

/** Enough to keep white text readable over a bright wallpaper, little enough to let it through. */
private const val WINDOW_GLASS_TINT = 0.28f

/**
 * The separators in the window's chrome. Acrylic lets the wallpaper through bright, and the solid
 * dark divider cut across it as a black line; a faint white one reads as an edge in the glass
 * instead. Mica is dark enough for the usual one.
 */
@Composable
internal fun desktopChromeDivider(): Color {
    val backdrop by DesktopWindowBackdrop.active.collectAsState()
    return when {
        backdrop == DesktopBackdrop.ACRYLIC -> Color.White.copy(alpha = 0.14f)
        DesktopPlatform.isMac && backdrop != DesktopBackdrop.OFF -> Color.White.copy(alpha = 0.10f)
        else -> DesktopDivider
    }
}

@Composable
private fun Modifier.desktopFrosted(
    opaque: Color,
    tintAlpha: Float,
    edge: DesktopChromeEdge = DesktopChromeEdge.NONE,
    fade: Float = 0f,
): Modifier {
    val haze = LocalDesktopHaze.current
    val reduceDynamicBlur by DesktopAppearanceSettings.reduceDynamicBlur.collectAsState()
    if (haze == null || reduceDynamicBlur) {
        return this.background(edge.dissolve(opaque, fade) ?: SolidColor(opaque))
    }
    val dissolve = edge.dissolve(Color.Black, fade)
    return this.hazeEffect(state = haze) {
        // Spelled out rather than taken from a preset.
        blurEnabled = true
        backgroundColor = DesktopSurface
        blurRadius = 30.dp
        // Dark enough that white text on the pane always wins, translucent enough that the
        // colour of what is behind it still comes through.
        tints = listOf(HazeTint(DesktopSurface.copy(alpha = tintAlpha)))
        noiseFactor = 0.04f
        // Assigned either way: the scope belongs to the node and outlives the draw, so a pane that
        // stops dissolving — the leading stretch of the top bar, when the window grows back out of
        // compact — would otherwise keep the mask it was given in the other layout.
        mask = dissolve
    }
}

/** The gradient that takes [colour] to nothing across the pane's last [fade]. */
private fun DesktopChromeEdge.dissolve(colour: Color, fade: Float): Brush? {
    if (this == DesktopChromeEdge.NONE || fade <= 0f) return null
    val stops = arrayOf(
        0f to colour,
        (1f - fade).coerceIn(0f, 1f) to colour,
        1f to Color.Transparent,
    )
    return if (this == DesktopChromeEdge.BOTTOM) {
        Brush.verticalGradient(*stops)
    } else {
        Brush.horizontalGradient(*stops)
    }
}

private fun AutomixPerformanceMode.label(): String = when (this) {
    AutomixPerformanceMode.EFFICIENT -> "Efficient"
    AutomixPerformanceMode.BALANCED -> "Balanced"
    AutomixPerformanceMode.PERFORMANCE -> "Performance"
}

/** Android's description of each rung. */
private fun AutomixPerformanceMode.detail(): String = when (this) {
    AutomixPerformanceMode.EFFICIENT ->
        "1 thread · lowest heat and battery use · analysis may take longer"
    AutomixPerformanceMode.BALANCED ->
        "2 threads · recommended balance of speed, heat and battery"
    AutomixPerformanceMode.PERFORMANCE ->
        "4 threads · fastest analysis · higher heat and battery use"
}

/** Chip label for an Apple-style Automix phrase length, in bars; 0 is the adaptive default. */
private fun phraseBarsLabel(bars: Int): String = when (bars) {
    8 -> DesktopStrings["automix_phrase_8", "8 bars"]
    16 -> DesktopStrings["automix_phrase_16", "16 bars"]
    else -> DesktopStrings["automix_phrase_auto", "Auto"]
}

/** What the selected phrase length does, shown under the chips. */
private fun phraseBarsDetail(bars: Int): String = when (bars) {
    8 -> DesktopStrings[
        "automix_phrase_8_detail",
        "Mixes over exactly 8 bars, both edges on the beat grid",
    ]
    16 -> DesktopStrings[
        "automix_phrase_16_detail",
        "Mixes over exactly 16 bars, both edges on the beat grid",
    ]
    else -> DesktopStrings[
        "automix_phrase_auto_detail",
        "Chooses each transition's length from the two tracks",
    ]
}

/** Android's wording for the Automix line, so both players read alike. */
private fun TrackAnalysisState.label(): String = when (this) {
    TrackAnalysisState.ANALYSED -> "analysed"
    TrackAnalysisState.REFINING -> "analysed, refining…"
    TrackAnalysisState.ANALYSING -> "analysing…"
    TrackAnalysisState.WAITING -> "waiting"
    TrackAnalysisState.FAILED -> "failed"
}

/**
 * A sideways row with the affordances a desktop needs to move it.
 *
 * A carousel longer than the window has no way to be scrolled with a mouse — the wheel belongs to
 * the page underneath — so this adds the three that work: dragging it, the pointer's horizontal
 * ticks while it is hovered, and a paging arrow at whichever end still has more.
 *
 * Shared rather than repeated: every shelf-shaped row in the app wants the same three.
 */
@Composable
internal fun DesktopScrollableRow(
    gutter: Dp = DesktopPageGutter,
    spacing: Dp = 14.dp,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    val rowState = rememberLazyListState()
    DesktopShelfRowChrome.Wrap(rowState) {
        LazyRow(
            state = rowState,
            contentPadding = PaddingValues(horizontal = gutter),
            horizontalArrangement = Arrangement.spacedBy(spacing),
            content = content,
        )
    }
}

/**
 * What every sideways shelf is wrapped in on the desktop — the shared pages' rows too, through
 * [LocalShelfRowChrome]. A mouse cannot swipe a row, so it gets the arrows it pages with, a drag
 * to pull it by, and the horizontal wheel while the pointer is over it.
 */
internal object DesktopShelfRowChrome : ShelfRowChrome {
    @Composable
    override fun Wrap(state: LazyListState, row: @Composable () -> Unit) {
        val scope = rememberCoroutineScope()
        val interaction = remember { MutableInteractionSource() }
        val hovered by interaction.collectIsHoveredAsState()

        /** One screenful, less a sliver so the card at the edge stays in view. */
        fun page(forward: Boolean) {
            val viewport = state.layoutInfo.let { it.viewportEndOffset - it.viewportStartOffset }
            val distance = (viewport - SHELF_PAGE_OVERLAP_PX).coerceAtLeast(SHELF_PAGE_OVERLAP_PX)
            scope.launch { state.animateScrollBy(if (forward) distance.toFloat() else -distance.toFloat()) }
        }

        // Whichever row the pointer is in takes the horizontal ticks; the page underneath keeps the
        // wheel, as it does everywhere else.
        LaunchedEffect(hovered) {
            if (!hovered) return@LaunchedEffect
            DesktopHorizontalScroll.ticks.collect { tick -> state.scrollBy(tick * SHELF_WHEEL_STEP) }
        }

        // A row whose ends stop dead reads as clipped rather than continuing; the fade says there
        // is more of it. It is a mask rather than a wash to the page's colour, because the page
        // behind a shelf is the artwork glow and there is no single colour to match.
        val fadeWidth = with(LocalDensity.current) { SHELF_FADE_WIDTH.toPx() }
        val fadeStart by animateFloatAsState(if (state.canScrollBackward) 1f else 0f, tween(160), label = "shelfFadeStart")
        val fadeEnd by animateFloatAsState(if (state.canScrollForward) 1f else 0f, tween(160), label = "shelfFadeEnd")

        Box(
            Modifier
                .fillMaxWidth()
                .hoverable(interaction)
                // A row can be taken hold of and pulled, which is the first thing anyone tries.
                .draggable(
                    state = rememberDraggableState { delta -> scope.launch { state.scrollBy(-delta) } },
                    orientation = Orientation.Horizontal,
                ),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        // Only the row is faded; the paging arrows are siblings and stay solid.
                        if (fadeStart > 0.01f) {
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    listOf(Color.Black.copy(alpha = fadeStart), Color.Black),
                                    startX = 0f,
                                    endX = fadeWidth,
                                ),
                                blendMode = BlendMode.DstIn,
                            )
                        }
                        if (fadeEnd > 0.01f) {
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    listOf(Color.Black, Color.Black.copy(alpha = fadeEnd)),
                                    startX = size.width - fadeWidth,
                                    endX = size.width,
                                ),
                                blendMode = BlendMode.DstIn,
                            )
                        }
                    },
            ) { row() }
            DesktopShelfArrow(
                alignment = Alignment.CenterStart,
                icon = BitChordIcons.ChevronLeft,
                description = DesktopStrings["d_scroll_left", "Scroll left"],
                visible = hovered && state.canScrollBackward,
                onClick = { page(forward = false) },
            )
            DesktopShelfArrow(
                alignment = Alignment.CenterEnd,
                icon = BitChordIcons.ChevronRight,
                description = DesktopStrings["d_scroll_right", "Scroll right"],
                visible = hovered && state.canScrollForward,
                onClick = { page(forward = true) },
            )
        }
    }
}

/** How much of the outgoing card stays visible after a paged scroll. */
internal const val SHELF_PAGE_OVERLAP_PX = 120

/** How far the row's ends dissolve into the page when there is more of it off-screen. */
private val SHELF_FADE_WIDTH = 44.dp

/** How far one notch of a horizontal scroll moves a shelf. */
internal const val SHELF_WHEEL_STEP = 90f

@Composable
internal fun BoxScope.DesktopShelfArrow(
    alignment: Alignment,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    visible: Boolean,
    onClick: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(alignment).padding(horizontal = 6.dp),
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, description, tint = Color.White, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun DesktopSongRow(
    song: Song,
    liked: Boolean,
    onClick: (Song) -> Unit,
    onToggleLike: ((Song) -> Unit)?,
    onDownload: ((Song) -> Unit)? = null,
    onAddToPlaylist: ((Song) -> Unit)? = null,
    downloaded: Boolean = false,
    downloadInProgress: Boolean = false,
    number: Int? = null,
    /** The row's own "…", when the page has a menu to give it. */
    menu: (@Composable (Song) -> Unit)? = null,
) {
    val openContextMenu = LocalDesktopContextMenu.current
    Row(
        Modifier
            .fillMaxWidth()
            .contextClick { openContextMenu(song) }
            .clip(RoundedCornerShape(8.dp))
            .desktopHoverFill(corner = 8.dp)
            .clickable { onClick(song) }
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (number != null) {
            Text(
                "$number",
                Modifier.width(28.dp),
                color = DesktopSecondary,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
            )
            Spacer(Modifier.width(12.dp))
        }
        DesktopArtwork(
            song.thumbnailUrl,
            Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)),
            px = ROW_ART_PX,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium,
                color = if (song.isNowPlaying()) Color.White else Color.White.copy(alpha = 0.75f),
            )
            Text(listOfNotNull(song.artist.takeIf(String::isNotBlank), song.durationText).joinToString(" · "), color = DesktopSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        song.sourceQuality?.let { quality ->
            val badge = quality.uppercase().takeIf { value ->
                value in setOf("LOSSLESS", "FLAC", "HI-RES", "HIRES", "HIGH", "320")
            }
            if (badge != null) {
                Text(
                    badge,
                    color = DesktopAccent,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }
        if (onToggleLike != null) {
            IconButton(onClick = { onToggleLike(song) }) { Icon(if (liked) BitChordIcons.StarFilled else BitChordIcons.Star, "Favorite", tint = if (liked) DesktopAccent else DesktopSecondary) }
        }
        if (onDownload != null) {
            if (downloadInProgress) {
                IconButton(onClick = { onDownload(song) }) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = DesktopAccent,
                        strokeWidth = 2.dp,
                    )
                }
            } else {
                IconButton(onClick = { if (!downloaded) onDownload(song) }) {
                    Icon(
                        imageVector = if (downloaded) BitChordIcons.Download else BitChordIcons.Download,
                        contentDescription = if (downloaded) "Downloaded" else "Download",
                        tint = if (downloaded) DesktopAccent else DesktopSecondary,
                    )
                }
            }
        }
        if (onAddToPlaylist != null) {
            IconButton(onClick = { onAddToPlaylist(song) }) {
                Icon(BitChordIcons.Plus, DesktopStrings["add_to_playlist", "Add to playlist"], tint = DesktopSecondary)
            }
        }
        // The same menu every other surface opens, so a row on a list page offers what a row on
        // the player does; the row itself plays.
        menu?.invoke(song)
    }
}

/**
 * A page's box, inset from the shell's chrome.
 *
 * [listState] is the list the page scrolls, when the page has one: handed here, it puts the pane's
 * overlay bar at the edge of the page area rather than at the edge of the inset, which is where
 * macOS puts it.
 */
@Composable
private fun DesktopPageScaffold(
    contentPadding: PaddingValues,
    listState: LazyListState? = null,
    content: @Composable () -> Unit,
) {
    val direction = LocalLayoutDirection.current
    val body: @Composable () -> Unit = {
        Box(
            Modifier.fillMaxSize().padding(
                start = contentPadding.calculateStartPadding(direction),
                end = contentPadding.calculateEndPadding(direction),
                top = contentPadding.calculateTopPadding(),
            ),
        ) {
            CompositionLocalProvider(
                LocalDesktopBottomInset provides contentPadding.calculateBottomPadding(),
            ) {
                content()
            }
        }
    }
    if (listState != null) AppleScrollbarBox(listState, Modifier.fillMaxSize(), body) else body()
}

/** A scrollable's own padding, plus room to clear whatever floats over the page. */
@Composable
private fun pagePadding(
    start: Dp = 0.dp,
    top: Dp = 0.dp,
    end: Dp = 0.dp,
    bottom: Dp = 0.dp,
): PaddingValues = PaddingValues(
    start = start,
    top = top,
    end = end,
    bottom = bottom + LocalDesktopBottomInset.current,
)

@Composable
private fun SectionTitle(
    title: String,
    subtitle: String = "",
    gutter: Dp = DesktopPageGutter,
    /** An action belonging to this heading, drawn at the far end of it. */
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = gutter), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp,
            )
            if (subtitle.isNotBlank()) Text(subtitle, color = DesktopSecondary, style = MaterialTheme.typography.bodySmall)
        }
        trailing?.invoke()
    }
}

@Composable
private fun DesktopLoadingPage(label: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = DesktopAccent)
            Spacer(Modifier.height(14.dp))
            Text(label, color = DesktopSecondary)
        }
    }
}

@Composable
private fun DesktopErrorPage(message: String, onRetry: () -> Unit) {
    DesktopEmptyPage(BitChordIcons.SlidersVertical, "Something went wrong", message, onRetry, "Try again")
}

@Composable
private fun DesktopEmptyPage(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, message: String, onAction: (() -> Unit)? = null, actionLabel: String? = null) {
    Column(Modifier.fillMaxWidth().padding(56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = DesktopAccent, modifier = Modifier.size(42.dp))
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(message, color = DesktopSecondary, textAlign = TextAlign.Center)
        if (onAction != null && actionLabel != null) TextButton(onClick = onAction) { Text(actionLabel) }
    }
}

/** A titled block of settings rows. */
@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {    val query = LocalSettingsQuery.current
    // A group whose own name matches shows whole; otherwise only its matching rows do, and the
    // heading goes with them when none are left.
    val wholeGroup = query.isBlank() || title.contains(query, ignoreCase = true)
    val matched = remember(query) { androidx.compose.runtime.mutableStateListOf<String>() }
    val visible = wholeGroup || matched.isNotEmpty()
    // The gap belongs to the group, not to the list: as list spacing, every filtered-out group
    // still left its 18dp behind and the surviving ones sat under a band of empty space.
    Column(if (visible) Modifier.padding(bottom = 20.dp) else Modifier) {
        if (visible) {
            Text(
                title.uppercase(),
                color = DesktopSecondary,
                style = MaterialTheme.typography.labelSmall,
                // In line with the rows' own text, now that no card sets them in.
                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
            )
        }
        Column {
            CompositionLocalProvider(
                LocalSettingsQuery provides if (wholeGroup) "" else query,
                LocalSettingsMatches provides matched,
            ) {
                content()
            }
        }
    }
}

/** What the settings field is filtering on, or blank when it is empty. */
private val LocalSettingsQuery = compositionLocalOf { "" }

/** Where a row tells its group that it survived the filter, so an empty group can hide its name. */
private val LocalSettingsMatches = compositionLocalOf<MutableList<String>?> { null }

/**
 * Whether a row with this text belongs on screen, and registering it with its group when it does.
 * Rows call this first and return early when it is false.
 */
@Composable
private fun settingsRowVisible(title: String, subtitle: String = ""): Boolean {
    val query = LocalSettingsQuery.current
    if (query.isBlank()) return true
    val hit = title.contains(query, ignoreCase = true) || subtitle.contains(query, ignoreCase = true)
    val matches = LocalSettingsMatches.current
    DisposableEffect(hit, title, matches) {
        if (hit) matches?.add(title)
        onDispose { if (hit) matches?.remove(title) }
    }
    return hit
}

/** The tinted square every settings row starts on, so the eye has one column to run down. */
@Composable
private fun SettingsIcon(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Box(
        Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(Color.White.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = DesktopAccent, modifier = Modifier.size(19.dp))
    }
}

/**
 * The title line of a block whose control is a slider or a row of chips, so it carries an icon like
 * the rows above and below it.
 */
@Composable
private fun SettingsBlockHeading(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        SettingsIcon(icon)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, color = DesktopSecondary, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
        }
    }
}

/** The title a page of Settings shows beside the back arrow; the main page keeps its own. */
@Composable
private fun DesktopSettingsPage.settingsTitle(): String = when (this) {
    DesktopSettingsPage.PLAYBACK -> DesktopStrings["playback", "Playback"]
    DesktopSettingsPage.AUDIO -> DesktopStrings["d_audio", "Audio"]
    DesktopSettingsPage.APPEARANCE -> DesktopStrings["appearance", "Appearance"]
    DesktopSettingsPage.LYRICS -> DesktopStrings["open_lyrics", "Lyrics"]
    DesktopSettingsPage.SOURCES -> DesktopStrings["sources_order_short", "Sources"]
    DesktopSettingsPage.STORAGE -> DesktopStrings["storage", "Storage"]
    DesktopSettingsPage.ACCOUNT -> DesktopStrings["d_account", "Account"]
    DesktopSettingsPage.GENERAL -> DesktopStrings["d_general", "General"]
    else -> DesktopStrings["settings", "Settings"]
}

/** The line a row on Settings' main page draws for one of the pages it opens. */
@Composable
private fun DesktopSettingsEntry.icon(): androidx.compose.ui.graphics.vector.ImageVector = when (page) {
    DesktopSettingsPage.PLAYBACK -> BitChordIcons.Play
    DesktopSettingsPage.AUDIO -> BitChordIcons.Equalizer
    DesktopSettingsPage.APPEARANCE -> BitChordIcons.Palette
    DesktopSettingsPage.LYRICS -> BitChordIcons.LyricsQuote
    DesktopSettingsPage.SOURCES -> BitChordIcons.Library
    DesktopSettingsPage.STORAGE -> BitChordIcons.HardDrive
    DesktopSettingsPage.ACCOUNT -> BitChordIcons.Users
    else -> BitChordIcons.CogIcon
}

@Composable
private fun DesktopSettingsEntry.title(): String = page.settingsTitle()

@Composable
private fun DesktopSettingsEntry.summary(): String = when (page) {
    DesktopSettingsPage.PLAYBACK -> DesktopStrings["d_autoplay_mixing_and_sleep_timer", "Autoplay, mixing and sleep timer"]
    DesktopSettingsPage.AUDIO -> DesktopStrings["d_quality_output_and_equalizer", "Quality, output and equalizer"]
    DesktopSettingsPage.APPEARANCE -> DesktopStrings["d_window_covers_and_motion", "Window, covers and motion"]
    DesktopSettingsPage.LYRICS -> DesktopStrings["d_words_sources_and_translation", "Words, sources and translation"]
    DesktopSettingsPage.SOURCES -> DesktopStrings["d_where_the_songs_come_from", "Where the songs come from"]
    DesktopSettingsPage.STORAGE -> {
        val size = formatCacheSize(DesktopMediaCache.limitMb())
        DesktopStrings.format("d_up_to_size_on_disk", size, fallback = "Up to $size on disk")
    }
    DesktopSettingsPage.ACCOUNT -> DesktopStrings["d_integrations_and_replay", "Integrations and Replay"]
    else -> DesktopStrings["d_language_tray_and_backup", "Language, tray and backup"]
}

/** A settings row that opens something rather than toggling it. */
@Composable
private fun SettingsNavigationRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    if (!settingsRowVisible(title, subtitle)) return
    Row(
        Modifier
            .fillMaxWidth()
            .desktopRowClickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsIcon(icon)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = DesktopSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        Icon(
            BitChordIcons.ChevronRight,
            null,
            tint = DesktopSecondary,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** A settings row whose control is a slider, with its value read out beside the title. */
/** Material's slider with the step ticks and end-stop dot taken off, as Android draws them. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DesktopBareSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val colors = SliderDefaults.colors(
        thumbColor = Color.White,
        activeTrackColor = Color.White,
        inactiveTrackColor = Color.White.copy(alpha = 0.24f),
    )
    Slider(
        value = value,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        valueRange = valueRange,
        steps = steps,
        colors = colors,
        track = { state ->
            SliderDefaults.Track(
                sliderState = state,
                colors = colors,
                drawStopIndicator = null,
                drawTick = { _, _ -> },
            )
        },
        modifier = modifier,
    )
}

@Composable
private fun SettingsToggle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    if (!settingsRowVisible(title, subtitle)) return
    Row(
        Modifier
            .fillMaxWidth()
            .desktopRowClickable { onCheckedChange(!checked) }
            .padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsIcon(icon)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, color = DesktopSecondary, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
        }
        Spacer(Modifier.width(18.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange, colors = desktopSwitchColors())
    }
}

@Composable
private fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
) {
    if (!settingsRowVisible(title, subtitle)) return
    Row(
        Modifier
            .fillMaxWidth()
            .desktopRowClickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsIcon(icon)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, color = DesktopSecondary, style = MaterialTheme.typography.bodyMedium, maxLines = 3)
        }
        if (onClick != null) {
            Spacer(Modifier.width(10.dp))
            Icon(BitChordIcons.ChevronRight, null, tint = DesktopSecondary, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun DesktopActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(modifier.clip(CircleShape).background(DesktopAccent).clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = Color.White, fontWeight = FontWeight.SemiBold)
    }
}

private fun SearchResult.key(): String = when (this) {
    is SearchResult.TopTrack -> "track:${song.videoId}"
    is SearchResult.Track -> "track:${song.videoId}"
    is SearchResult.Browse -> "browse:${item.browseId}"
}

private fun ShelfItem.toSong(): Song {
    val parts = subtitle.split(" • ", " · ", " | ").filter(String::isNotBlank)
    // Android's InnertubeParser uses the first non-type/non-tally segment.
    val artist = parts.firstOrNull {
        !it.matches(SHELF_DURATION) &&
            it.lowercase() !in SHELF_TYPE_WORDS &&
            !it.matches(SHELF_TALLY)
    } ?: "Unknown Artist"
    return Song(
        videoId = videoId.orEmpty(),
        title = title,
        artist = artist,
        thumbnailUrl = thumbnailUrl,
        durationText = parts.firstOrNull { it.matches(SHELF_DURATION) },
    )
}

private fun String.isUnknownArtist(): Boolean =
    trim().equals("unknown", ignoreCase = true) ||
        trim().equals("unknown artist", ignoreCase = true) ||
        trim().equals("unknown artist(s)", ignoreCase = true) ||
        isBlank()

private val SHELF_DURATION = Regex("""\d{1,2}:\d{2}(?::\d{2})?""")
private val SHELF_TALLY = Regex("[0-9,.]+\\s*(?:views?|likes?|songs?)", RegexOption.IGNORE_CASE)
private val SHELF_TYPE_WORDS = setOf("song", "video", "album", "ep", "single", "playlist", "mix")

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1_000L).coerceAtLeast(0L)
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

private fun formatCollectionDuration(totalSeconds: Long): String {
    val minutes = totalSeconds / 60L
    return if (minutes < 60L) {
        "$minutes min"
    } else {
        "${minutes / 60L} hr ${minutes % 60L} min"
    }
}

private fun formatSleepTimer(ms: Long): String = formatTime(ms)
