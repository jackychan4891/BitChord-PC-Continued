package com.music.bitchord.desktop

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import bitchord.desktopapp.generated.resources.Res
import bitchord.desktopapp.generated.resources.logo_mark
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.Song
import com.music.bitchord.ui.components.AppleScrollbarBox
import com.music.bitchord.ui.icons.BitChordIcons
import com.music.bitchord.ui.player.rememberMixPulse
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun DesktopAppleSidebar(
    destination: DesktopDestination,
    settingsOpen: Boolean,
    accountPlaylists: List<ShelfItem>,
    localPlaylists: List<DesktopPlaylist>,
    openedCollectionId: String?,
    /** Which Library shortcut is open on the page, so its row reads as selected. */
    libraryShelf: String?,
    onDestinationSelected: (DesktopDestination) -> Unit,
    onOpenLibraryShelf: (String) -> Unit,
    onOpenAccountPlaylist: (ShelfItem) -> Unit,
    onOpenLocalPlaylist: (DesktopPlaylist) -> Unit,
    onOpenSettings: () -> Unit,
    signedIn: Boolean,
    accountAvatar: String?,
    onOpenAccounts: () -> Unit,
    /** Where the app's one back button stands: nothing to pop greys it out. */
    canGoBack: Boolean,
    onBack: () -> Unit,
) {
    val inlineCaption = DesktopPlatform.drawsOwnWindowFrame
    val hidden by DesktopSidebarVisibility.hidden.collectAsState()
    val collapsed by DesktopSidebarVisibility.collapsed.collectAsState()
    // The copy below comes from DesktopStrings rather than Compose resources, so nothing in this
    // scope would otherwise notice the language changing.
    DesktopStrings.language.collectAsState()
    if (collapsed) {
        DesktopAppleSidebarRail(
            destination = destination,
            settingsOpen = settingsOpen,
            signedIn = signedIn,
            accountAvatar = accountAvatar,
            hidden = hidden,
            onDestinationSelected = onDestinationSelected,
            onOpenSettings = onOpenSettings,
            onOpenAccounts = onOpenAccounts,
        )
        return
    }
    Column(
        Modifier.width(260.dp).fillMaxHeight().background(Color(0x99262628))
            .border(0.5.dp, Color.White.copy(alpha = 0.05f)),
    ) {
        // The sidebar owns the caption row when the app draws the frame and the system title bar is
        // off, so it carries the buttons and the collapse control. With a title bar above it, the
        // control moves down to the footer rather than leaving an empty band.
        if (inlineCaption) {
            Box(Modifier.fillMaxWidth().height(40.dp)) {
                DesktopTitleBarDragArea(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().padding(start = 10.dp), contentAlignment = Alignment.CenterStart) { DesktopWindowButtons() }
                }
                Box(Modifier.align(Alignment.CenterEnd).padding(end = 8.dp)) {
                    DesktopSidebarBackButton(canGoBack, onBack)
                }
            }
        }
        Column(Modifier.fillMaxWidth().weight(1f).padding(start = 16.dp, top = if (inlineCaption) 0.dp else 8.dp, end = 16.dp)) {
            DesktopSidebarItem(BitChordIcons.Search, DesktopDestination.SEARCH.label, destination == DesktopDestination.SEARCH) { onDestinationSelected(DesktopDestination.SEARCH) }
            DesktopSidebarItem(BitChordIcons.Home, DesktopDestination.LISTEN_NOW.label, destination == DesktopDestination.LISTEN_NOW) { onDestinationSelected(DesktopDestination.LISTEN_NOW) }
            DesktopSidebarItem(BitChordIcons.Explore, DesktopDestination.EXPLORE.label, destination == DesktopDestination.EXPLORE) { onDestinationSelected(DesktopDestination.EXPLORE) }
            DesktopSidebarItem(BitChordIcons.Radio, DesktopDestination.RADIO.label, destination == DesktopDestination.RADIO, iconSize = DesktopLucideGlyph) { onDestinationSelected(DesktopDestination.RADIO) }
            Spacer(Modifier.height(24.dp))
            DesktopSidebarSection(DesktopStrings["library", "Library"])
            DesktopSidebarItem(BitChordIcons.Library, DesktopDestination.LIBRARY.label, destination == DesktopDestination.LIBRARY && libraryShelf == null) {
                onDestinationSelected(DesktopDestination.LIBRARY)
            }
            if ("dest:HISTORY" !in hidden) DesktopSidebarItem(BitChordIcons.Clock, DesktopDestination.HISTORY.label, destination == DesktopDestination.HISTORY,
                onHide = { DesktopSidebarVisibility.hide("dest:HISTORY", DesktopDestination.HISTORY.label) }) { onDestinationSelected(DesktopDestination.HISTORY) }
            // The four shortcuts are the page's own shelf list, so they hide like destinations do.
            // The shelf itself is named in English — only the copy shown to the user is translated.
            if ("shelf:Artists" !in hidden) DesktopSidebarItem(BitChordIcons.Mic, DesktopStrings["artists", "Artists"], destination == DesktopDestination.LIBRARY && libraryShelf == "Artists",
                onHide = { DesktopSidebarVisibility.hide("shelf:Artists", DesktopStrings["artists", "Artists"]) }) { onOpenLibraryShelf("Artists") }
            if ("shelf:Albums" !in hidden) DesktopSidebarItem(BitChordIcons.Album, DesktopStrings["albums", "Albums"], destination == DesktopDestination.LIBRARY && libraryShelf == "Albums",
                onHide = { DesktopSidebarVisibility.hide("shelf:Albums", DesktopStrings["albums", "Albums"]) }) { onOpenLibraryShelf("Albums") }
            if ("shelf:Songs" !in hidden) DesktopSidebarItem(BitChordIcons.MusicNote, DesktopStrings["songs", "Songs"], destination == DesktopDestination.LIBRARY && libraryShelf == "Songs",
                onHide = { DesktopSidebarVisibility.hide("shelf:Songs", DesktopStrings["songs", "Songs"]) }) { onOpenLibraryShelf("Songs") }
            if ("shelf:Made for You" !in hidden) DesktopSidebarItem(BitChordIcons.Person, DesktopStrings["d_made_for_you", "Made for You"], destination == DesktopDestination.LIBRARY && libraryShelf == "Made for You",
                onHide = { DesktopSidebarVisibility.hide("shelf:Made for You", DesktopStrings["d_made_for_you", "Made for You"]) }) { onOpenLibraryShelf("Made for You") }
            val downloads by DesktopDownloadQueue.active.collectAsState()
            val downloadsLabel = DesktopStrings["downloads", "Downloads"]
            if ("dest:DOWNLOADS" !in hidden) DesktopSidebarItem(BitChordIcons.Download, if (downloads.isEmpty()) downloadsLabel else "$downloadsLabel · ${downloads.size}", destination == DesktopDestination.DOWNLOADS,
                iconSize = DesktopLucideGlyph,
                onHide = { DesktopSidebarVisibility.hide("dest:DOWNLOADS", downloadsLabel) }) { onDestinationSelected(DesktopDestination.DOWNLOADS) }
            if ("dest:LOCAL_MUSIC" !in hidden) DesktopSidebarItem(BitChordIcons.Library, DesktopDestination.LOCAL_MUSIC.label, destination == DesktopDestination.LOCAL_MUSIC,
                onHide = { DesktopSidebarVisibility.hide("dest:LOCAL_MUSIC", DesktopDestination.LOCAL_MUSIC.label) }) { onDestinationSelected(DesktopDestination.LOCAL_MUSIC) }
            Spacer(Modifier.height(24.dp))
            DesktopSidebarSection(DesktopStrings["playlists", "Playlists"])
            val playlistsState = rememberLazyListState()
            AppleScrollbarBox(playlistsState, Modifier.fillMaxWidth().weight(1f)) {
                LazyColumn(Modifier.fillMaxSize(), state = playlistsState, contentPadding = PaddingValues(bottom = 12.dp)) {
                    items(accountPlaylists.filterNot { "account:${it.browseId}" in hidden }, key = { "account:${it.browseId}" }) { playlist ->
                        DesktopSidebarItem(BitChordIcons.Playlist, playlist.title, openedCollectionId == playlist.browseId,
                            onHide = { DesktopSidebarVisibility.hide("account:${playlist.browseId}", playlist.title) }) { onOpenAccountPlaylist(playlist) }
                    }
                    items(localPlaylists.filterNot { "local:${it.id}" in hidden }, key = { "local:${it.id}" }) { playlist ->
                        DesktopSidebarItem(BitChordIcons.Playlist, playlist.title, openedCollectionId == playlist.id,
                            onHide = { DesktopSidebarVisibility.hide("local:${playlist.id}", playlist.title) }) { onOpenLocalPlaylist(playlist) }
                    }
                }
            }
        }
        HorizontalDivider(Modifier.padding(top = 8.dp), thickness = 0.5.dp, color = Color.White.copy(alpha = 0.08f))
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, top = 12.dp, end = 12.dp, bottom = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            if (signedIn) {
                Row(
                    Modifier.weight(1f).desktopHoverFill(corner = 8.dp, alpha = 0.05f).clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onOpenAccounts).padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(28.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
                        if (accountAvatar != null) DesktopArtwork(accountAvatar, Modifier.fillMaxSize())
                        else Icon(BitChordIcons.PersonCircle, null, tint = DesktopAppleSecondary)
                    }
                    Text(DesktopStrings["d_account", "Account"], Modifier.weight(1f).padding(start = 8.dp), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = DesktopAppleText, maxLines = 1)
                    Icon(BitChordIcons.ChevronRight, null, tint = DesktopAppleSecondary, modifier = Modifier.size(16.dp))
                }
            } else {
                Box(Modifier.clip(RoundedCornerShape(16.dp)).background(DesktopAccent).clickable(onClick = onOpenAccounts).padding(horizontal = 14.dp, vertical = 5.dp)) {
                    Text(DesktopStrings["sign_in", "Sign in"], fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                }
                Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.width(8.dp))
            if (!inlineCaption) {
                DesktopSidebarBackButton(canGoBack, onBack)
                Spacer(Modifier.width(2.dp))
            }
            val settingsHover = remember { MutableInteractionSource() }
            val overSettings by settingsHover.collectIsHoveredAsState()
            Box(
                Modifier.size(28.dp).clip(RoundedCornerShape(8.dp))
                    .background(if (overSettings || settingsOpen) Color.White.copy(alpha = 0.05f) else Color.Transparent)
                    .hoverable(settingsHover).clickable(onClick = onOpenSettings),
                contentAlignment = Alignment.Center,
            ) {
                Icon(BitChordIcons.CogIcon, DesktopDestination.SETTINGS.label, tint = if (settingsOpen) DesktopAccent else if (overSettings) DesktopAppleText else DesktopAppleSecondary, modifier = Modifier.size(17.dp))
            }
        }
    }
}

/**
 * The app's back button, in the corner the sidebar's collapse control used to hold.
 *
 * Two arrows that popped the same history read as two different ways out, so the one in the page
 * header went and this took its place. With nothing behind it the glyph dims rather than vanishing,
 * because the button is the anchor of that corner and a hole in it moves the settings gear.
 */
@Composable
private fun DesktopSidebarBackButton(canGoBack: Boolean, onBack: () -> Unit) {
    DesktopToolbarButton(onBack, enabled = canGoBack, size = 28.dp) {
        Icon(
            BitChordIcons.ChevronLeft,
            DesktopStrings["back", "Back"],
            tint = if (canGoBack) DesktopAppleText else DesktopAppleSecondary.copy(alpha = 0.4f),
            modifier = Modifier.size(16.dp),
        )
    }
}

/**
 * The sidebar folded down to its glyphs, for a window that is wide enough for a rail but not for
 * the full 260dp list. Every destination that has a glyph is still one click away; the playlist
 * rows are the part that waits for the sidebar to be opened again.
 */
@Composable
private fun DesktopAppleSidebarRail(
    destination: DesktopDestination,
    settingsOpen: Boolean,
    signedIn: Boolean,
    accountAvatar: String?,
    hidden: Map<String, String>,
    onDestinationSelected: (DesktopDestination) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAccounts: () -> Unit,
) {
    val inlineCaption = DesktopPlatform.drawsOwnWindowFrame
    DesktopStrings.language.collectAsState()
    Column(
        Modifier.width(72.dp).fillMaxHeight().background(Color(0x99262628))
            .border(0.5.dp, Color.White.copy(alpha = 0.05f)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (inlineCaption) {
            DesktopTitleBarDragArea(Modifier.fillMaxWidth().height(40.dp)) {
                Box(Modifier.fillMaxSize().padding(start = 4.dp), contentAlignment = Alignment.CenterStart) { DesktopWindowButtons() }
            }
        } else {
            Spacer(Modifier.height(12.dp))
        }
        Column(
            Modifier.fillMaxWidth().weight(1f).padding(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            DesktopSidebarRailItem(BitChordIcons.Search, DesktopDestination.SEARCH.label, destination == DesktopDestination.SEARCH) {
                onDestinationSelected(DesktopDestination.SEARCH)
            }
            DesktopSidebarRailItem(BitChordIcons.Home, DesktopDestination.LISTEN_NOW.label, destination == DesktopDestination.LISTEN_NOW) {
                onDestinationSelected(DesktopDestination.LISTEN_NOW)
            }
            DesktopSidebarRailItem(BitChordIcons.Explore, DesktopDestination.EXPLORE.label, destination == DesktopDestination.EXPLORE) {
                onDestinationSelected(DesktopDestination.EXPLORE)
            }
            DesktopSidebarRailItem(BitChordIcons.Radio, DesktopDestination.RADIO.label, destination == DesktopDestination.RADIO) {
                onDestinationSelected(DesktopDestination.RADIO)
            }
            Spacer(Modifier.height(14.dp))
            DesktopSidebarRailItem(BitChordIcons.Library, DesktopDestination.LIBRARY.label, destination == DesktopDestination.LIBRARY) {
                onDestinationSelected(DesktopDestination.LIBRARY)
            }
            if ("dest:HISTORY" !in hidden) {
                DesktopSidebarRailItem(BitChordIcons.Clock, DesktopDestination.HISTORY.label, destination == DesktopDestination.HISTORY) {
                    onDestinationSelected(DesktopDestination.HISTORY)
                }
            }
            if ("dest:DOWNLOADS" !in hidden) {
                DesktopSidebarRailItem(BitChordIcons.Download, DesktopDestination.DOWNLOADS.label, destination == DesktopDestination.DOWNLOADS) {
                    onDestinationSelected(DesktopDestination.DOWNLOADS)
                }
            }
            if ("dest:LOCAL_MUSIC" !in hidden) {
                DesktopSidebarRailItem(BitChordIcons.Library, DesktopDestination.LOCAL_MUSIC.label, destination == DesktopDestination.LOCAL_MUSIC) {
                    onDestinationSelected(DesktopDestination.LOCAL_MUSIC)
                }
            }
        }
        Column(
            Modifier.padding(bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            DesktopSidebarRailItem(
                BitChordIcons.ChevronRight,
                DesktopStrings["d_expand_sidebar", "Expand sidebar"],
                selected = false,
            ) { DesktopSidebarVisibility.setCollapsed(false) }
            DesktopSidebarRailItem(BitChordIcons.CogIcon, DesktopDestination.SETTINGS.label, settingsOpen, onOpenSettings)
            Box(
                Modifier.size(42.dp).clip(CircleShape).clickable(onClick = onOpenAccounts),
                contentAlignment = Alignment.Center,
            ) {
                if (signedIn) {
                    Box(Modifier.size(26.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
                        if (accountAvatar != null) DesktopArtwork(accountAvatar, Modifier.fillMaxSize())
                        else Icon(BitChordIcons.PersonCircle, null, tint = DesktopSecondary, modifier = Modifier.size(24.dp))
                    }
                } else {
                    Icon(
                        BitChordIcons.PersonCircle,
                        DesktopStrings["sign_in", "Sign in"],
                        tint = DesktopSecondary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

/** One glyph in the rail: the row [DesktopSidebarItem] draws, with the label left to its tooltip. */
@Composable
private fun DesktopSidebarRailItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(42.dp)
            .desktopHoverFill(corner = 10.dp, alpha = 0.05f)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Color.White.copy(alpha = 0.06f) else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = if (selected) DesktopAccent else DesktopSecondary, modifier = Modifier.size(21.dp))
    }
}

@Composable
internal fun DesktopAppleFloatingPlayer(
    song: Song?,
    isPlaying: Boolean,
    isLoading: Boolean,
    previousEnabled: Boolean,
    progress: Float,
    positionMs: Long,
    durationMs: Long,
    volume: Float,
    shuffle: Boolean,
    repeatMode: DesktopRepeatMode,
    liked: Boolean,
    onToggleLike: (() -> Unit)?,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onShuffleChange: (Boolean) -> Unit,
    onRepeatModeChange: (DesktopRepeatMode) -> Unit,
    onSeek: (Long) -> Unit,
    onOpenNowPlaying: () -> Unit,
    onOpenLyrics: () -> Unit,
    onOpenQueue: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onOpenAudioOutput: () -> Unit,
    sidePanel: DesktopSidePanel?,
    liveRadio: Boolean,
    onStopRadio: () -> Unit,
) {
    val mixBlend = DesktopPlayerSettings.smartMixBlend.collectAsState()
    val mixPulse = rememberMixPulse({ mixBlend.value }, enabled = true, slow = true)
    val currentProgress by rememberUpdatedState(progress)
    var volumeMenu by remember { mutableStateOf(false) }
    // Recompose when the app language changes; see the note in DesktopAppleSidebar.
    DesktopStrings.language.collectAsState()
    BoxWithConstraints(Modifier.widthIn(max = 668.dp).fillMaxWidth().padding(horizontal = 6.dp)) {
        val narrow = maxWidth < 550.dp
        // Frosted from the page scrolling under it, the way the other floating
        // bars are — not a blur of the sleeve, which made the pill read as a
        // second copy of the artwork rather than a window over the app.
        Box(
            Modifier.fillMaxWidth().height(56.dp)
                .shadow(20.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.2f), spotColor = Color.Black.copy(alpha = 0.2f))
                .clip(CircleShape).desktopBarGlass(CircleShape),
        ) {
            Row(
                Modifier.matchParentSize().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The site's transport column: shuffle and the loop pin the two
                // ends of a fixed 184px run, the skips and play sit between them.
                // Every box is 28 square on a 6px corner with no hover fill —
                // the glyphs alone carry the state, secondary until pressed pink.
                Row(
                    Modifier.then(if (narrow) Modifier else Modifier.width(184.dp)).padding(end = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!narrow) PillTransportButton({ onShuffleChange(!shuffle) }, enabled = !liveRadio) {
                        Icon(BitChordIcons.TransportShuffle, DesktopStrings["shuffle", "Shuffle"], tint = if (shuffle) DesktopAccent else WebSecondary, modifier = Modifier.size(28.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        PillTransportButton(onPrevious, enabled = previousEnabled && song != null) {
                            Icon(BitChordIcons.TransportPrevious, DesktopStrings["widget_previous", "Previous"], tint = WebPrimary, modifier = Modifier.size(26.dp))
                        }
                        PillTransportButton(onPlayPause, enabled = song != null) {
                            // The site keeps the pause glyph up through buffering
                            // instead of swapping it for a spinner.
                            Icon(if (isPlaying || isLoading) BitChordIcons.TransportPause else BitChordIcons.TransportPlay,
                                if (isPlaying) DesktopStrings["pause", "Pause"] else DesktopStrings["play", "Play"],
                                tint = WebPrimary, modifier = Modifier.size(28.dp))
                        }
                        PillTransportButton(if (liveRadio) onStopRadio else onNext, enabled = song != null) {
                            Icon(if (liveRadio) BitChordIcons.Stop else BitChordIcons.TransportNext,
                                if (liveRadio) DesktopStrings["d_stop_radio", "Stop radio"] else DesktopStrings["widget_next", "Next"],
                                tint = WebPrimary, modifier = Modifier.size(if (liveRadio) 24.dp else 26.dp))
                        }
                    }
                    if (!narrow) PillTransportButton({ onRepeatModeChange(repeatMode.next()) }, enabled = !liveRadio) {
                        Icon(
                            imageVector = if (repeatMode == DesktopRepeatMode.ONE) BitChordIcons.TransportRepeatOne else BitChordIcons.TransportRepeat,
                            contentDescription = repeatMode.menuLabel(),
                            tint = if (repeatMode == DesktopRepeatMode.OFF) WebSecondary else DesktopAccent,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
                MiniPlayerLcd(
                    song = song,
                    progress = { currentProgress },
                    mixPulse = mixPulse,
                    durationMs = durationMs,
                    positionMs = positionMs,
                    seekable = !liveRadio && song != null && durationMs > 0L,
                    onSeek = onSeek,
                    onOpenNowPlaying = onOpenNowPlaying,
                    modifier = Modifier.weight(1f),
                )
                Box(Modifier.width(132.dp), contentAlignment = Alignment.CenterEnd) {
                    // The volume slider's hover has to come from the volume button
                    // and the slider alone: hanging it off the whole row is what
                    // made the slider cover the other buttons the moment the
                    // pointer was anywhere near them.
                    val volumeHover = remember { MutableInteractionSource() }
                    val sliderHover = remember { MutableInteractionSource() }
                    val overVolume by volumeHover.collectIsHoveredAsState()
                    val overSlider by sliderHover.collectIsHoveredAsState()
                    var sliderOpen by remember { mutableStateOf(false) }
                    LaunchedEffect(overVolume, overSlider) {
                        sliderOpen = overVolume || overSlider
                    }
                    val reveal by animateFloatAsState(if (sliderOpen) 1f else 0f, tween(150), label = "miniVolume")
                    Row(
                        Modifier.align(Alignment.CenterEnd),
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // While the slider is open it covers the other three, so
                        // they fade under it the way the site's do.
                        val others = Modifier.graphicsLayer { alpha = 1f - reveal }
                        // Always drawn at full strength, with or without a song:
                        // a star that dims or vanishes with the track makes the
                        // pill's right end jump about as tracks come and go.
                        PillActionButton({ if (onToggleLike != null && song != null && !liveRadio) onToggleLike.invoke() }, modifier = others) {
                            Icon(if (liked) BitChordIcons.StarFilled else BitChordIcons.Star,
                                if (liked) DesktopStrings["d_remove_from_favourites", "Remove from Favourites"] else DesktopStrings["d_favourite", "Favourite"],
                                tint = if (liked) DesktopAccent else WebPrimary, modifier = Modifier.size(18.dp))
                        }
                        PillActionButton(onOpenLyrics, enabled = !liveRadio, modifier = others) {
                            Icon(if (sidePanel == DesktopSidePanel.LYRICS) BitChordIcons.Lyrics else BitChordIcons.LyricsOff,
                                DesktopStrings["open_lyrics", "Lyrics"],
                                tint = if (sidePanel == DesktopSidePanel.LYRICS) DesktopAccent else WebPrimary, modifier = Modifier.size(18.dp))
                        }
                        PillActionButton(onOpenQueue, enabled = !liveRadio, modifier = others) {
                            Icon(BitChordIcons.Queue,
                                DesktopStrings["up_next", "Up Next"],
                                tint = if (sidePanel == DesktopSidePanel.QUEUE) DesktopAccent else WebPrimary, modifier = Modifier.size(18.dp))
                        }
                        Box {
                            PillActionButton({ volumeMenu = true }, modifier = Modifier.hoverable(volumeHover)) {
                                val icon = when {
                                    volume <= 0f -> Icons.AutoMirrored.Rounded.VolumeOff
                                    volume < 0.5f -> Icons.AutoMirrored.Rounded.VolumeDown
                                    else -> Icons.AutoMirrored.Rounded.VolumeUp
                                }
                                Icon(icon,
                                    DesktopStrings["volume_and_audio_output", "Volume and audio output"],
                                    tint = WebPrimary, modifier = Modifier.size(18.dp))
                            }
                            DesktopDropdownMenu(volumeMenu, { volumeMenu = false }) {
                                if (narrow && !liveRadio) {
                                    DropdownMenuItem(text = { Text(if (shuffle) DesktopStrings["shuffle_on", "Shuffle On"] else DesktopStrings["shuffle_off", "Shuffle Off"]) }, onClick = { onShuffleChange(!shuffle) })
                                    DropdownMenuItem(text = { Text(repeatMode.menuLabel()) }, onClick = { onRepeatModeChange(repeatMode.next()) })
                                }
                                Box(Modifier.width(180.dp).padding(horizontal = 16.dp)) {
                                    DesktopThinSlider(volume, onVolumeChange, idleHeight = 4.dp, activeHeight = 8.dp)
                                }
                                DropdownMenuItem(
                                    text = { Text(if (liveRadio) DesktopStrings["d_radio_system_output", "Radio uses system default output"] else DesktopStrings["audio_output", "Audio Output"]) },
                                    enabled = !liveRadio,
                                    leadingIcon = { Icon(Icons.Rounded.Headphones, null) },
                                    onClick = { volumeMenu = false; onOpenAudioOutput() },
                                )
                            }
                        }
                    }
                    if (reveal > 0f) {
                        DesktopThinSlider(
                            volume, onVolumeChange,
                            idleHeight = 4.dp, activeHeight = 4.dp,
                            modifier = Modifier.padding(end = 33.dp).width(96.dp).hoverable(sliderHover)
                                .graphicsLayer { alpha = reveal; translationX = (1f - reveal) * 8.dp.toPx() },
                        )
                    }
                }
            }
        }
    }
}

/** A transport glyph's box: 28 square on a 6px corner, no hover fill. */
@Composable
private fun PillTransportButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier.size(28.dp).clip(RoundedCornerShape(6.dp))
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** An action glyph's box: 24×28 on a 4px corner, no hover fill, white until pressed pink. */
@Composable
private fun PillActionButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier.size(24.dp, 28.dp).clip(RoundedCornerShape(4.dp))
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun MiniPlayerLcd(
    song: Song?,
    progress: () -> Float,
    mixPulse: com.music.bitchord.ui.player.MixPulse,
    durationMs: Long,
    positionMs: Long,
    seekable: Boolean,
    onSeek: (Long) -> Unit,
    onOpenNowPlaying: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val stripHover = remember { MutableInteractionSource() }
    val overStrip by stripHover.collectIsHoveredAsState()
    var scrub by remember { mutableStateOf<Float?>(null) }
    DesktopStrings.language.collectAsState()
    val scrubbing = seekable && (overStrip || scrub != null)
    val focus by animateFloatAsState(if (scrubbing) 1f else 0f, tween(100), label = "lcdScrub")
    // The cover's own hover darkens the sleeve and fades in the expand glyph —
    // the cue that the artwork is the way up into the full player.
    val coverHover = remember { MutableInteractionSource() }
    val overCover by coverHover.collectIsHoveredAsState()
    val expand by animateFloatAsState(if (overCover) 1f else 0f, tween(200), label = "lcdExpand")
    Box(modifier.clip(RoundedCornerShape(6.dp))) {
        Row(
            Modifier.fillMaxSize().padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .hoverable(coverHover)
                    .clickable(onClick = onOpenNowPlaying),
            ) {
                if (song != null) {
                    // Two sleeves crossfade on a skip: the outgoing one holds
                    // its frame while the incoming one dissolves over it, so a
                    // change of song is one continuous blend rather than a pop
                    // to the placeholder and back.
                    var coverUrl by remember { mutableStateOf<String?>(null) }
                    var coverOld by remember { mutableStateOf<String?>(null) }
                    val coverFade = remember { Animatable(1f) }
                    LaunchedEffect(song.thumbnailUrl) {
                        val url = song.thumbnailUrl
                        if (url != coverUrl) {
                            coverOld = coverUrl
                            coverUrl = url
                            coverFade.snapTo(0f)
                            coverFade.animateTo(1f, tween(600, easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)))
                            coverOld = null
                        }
                    }
                    Box(Modifier.fillMaxSize()) {
                        coverOld?.let { DesktopArtwork(it, Modifier.fillMaxSize(), px = 160) }
                        coverUrl?.let {
                            DesktopArtwork(it, Modifier.fillMaxSize().graphicsLayer { alpha = coverFade.value }, px = 160)
                        }
                    }
                } else Image(painterResource(Res.drawable.logo_mark), "BitChord", Modifier.padding(5.dp).size(24.dp))
                // The site's hover: the sleeve drops under a half-black scrim
                // while its own 14 dp white maximize-2 (2.25 pen) fades in at
                // the centre — the cue that the art is the way up.
                Box(
                    Modifier.fillMaxSize()
                        .graphicsLayer { alpha = expand }
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        BitChordIcons.Maximize2, DesktopStrings["d_now_playing", "Now playing"],
                        tint = Color.White, modifier = Modifier.size(14.dp),
                    )
                }
            }
            // Hovering the strip dims the titles to 30% and blurs them, the
            // site's group/scrub trick for handing the eye to the times.
            Column(
                Modifier.weight(1f).padding(start = 8.dp)
                    .graphicsLayer { alpha = 1f - 0.7f * focus }
                    .then(if (focus > 0.01f) Modifier.blur(3.dp * focus) else Modifier),
            ) {
                Text(song?.title ?: "BitChord", color = WebPrimary, fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song?.artist ?: DesktopStrings["d_not_playing", "Not Playing"], color = WebSecondary, fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        // The hover times: elapsed on the left, the remaining as a minus time on
        // the right, both reading the drag position while a scrub is in flight.
        if (seekable && focus > 0f) {
            val shownMs = (scrub?.let { (it * durationMs).toLong() } ?: positionMs).coerceAtLeast(0L)
            Text(
                webTime(shownMs),
                modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 11.dp).graphicsLayer { alpha = focus },
                color = WebPrimary, fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.SemiBold,
            )
            Text(
                "-" + webTime(durationMs - shownMs),
                modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 11.dp).graphicsLayer { alpha = focus },
                color = WebPrimary, fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.SemiBold,
            )
        }
        // The bar rides the bottom of the pill rather than the middle of the
        // LCD: laid over the sleeve it read as a scratch across the artwork.
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(14.dp)
                .hoverable(stripHover, enabled = seekable)
                .pointerInput(seekable, durationMs) {
                    if (!seekable) return@pointerInput
                    // Full width, so the line begins at the sleeve's left edge
                    // and ends at the LCD's right one instead of stopping short
                    // of the artwork it sits under.
                    fun fraction(x: Float) = (x / size.width.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
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
                }
                .drawBehind {
                    // 2px on the 6px line at rest; 4px dropped to the 5px line
                    // while the pointer is on it — the site's hover growth.
                    val barHeight = (2.dp + 2.dp * focus).toPx()
                    val bottomInset = (6.dp - 1.dp * focus).toPx()
                    val top = size.height - bottomInset - barHeight
                    val width = size.width
                    val base = (scrub ?: progress()).coerceIn(0f, 1f)
                    val fill = base + (1f - base) * mixPulse.cover
                    val radius = androidx.compose.ui.geometry.CornerRadius(barHeight / 2f)
                    drawRoundRect(WebPageQuaternary, Offset(0f, top), Size(width, barHeight), radius)
                    drawRoundRect(Color.White.copy(alpha = mixPulse.alpha(0.64f)), Offset(0f, top), Size(width * fill, barHeight), radius)
                },
        )
    }
}
