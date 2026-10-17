package com.music.bitchord.desktop

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import bitchord.desktopapp.generated.resources.Res
import bitchord.desktopapp.generated.resources.logo_mark
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.Song
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
) {
    val titleBarEnabled by DesktopTitleBarSetting.enabled.collectAsState()
    val inlineCaption = DesktopPlatform.drawsOwnWindowFrame && !titleBarEnabled
    val hidden by DesktopSidebarVisibility.hidden.collectAsState()
    Column(
        Modifier.width(260.dp).fillMaxHeight().background(Color(0x99262628))
            .border(0.5.dp, Color.White.copy(alpha = 0.05f)),
    ) {
        if (inlineCaption) DesktopTitleBarDragArea(Modifier.fillMaxWidth().height(40.dp)) {
            Box(Modifier.fillMaxSize().padding(start = 10.dp), contentAlignment = Alignment.CenterStart) { DesktopWindowButtons() }
        }
        Column(Modifier.fillMaxWidth().weight(1f).padding(start = 16.dp, top = 8.dp, end = 16.dp)) {
            DesktopSidebarItem(BitChordIcons.Search, "Search", destination == DesktopDestination.SEARCH) { onDestinationSelected(DesktopDestination.SEARCH) }
            DesktopSidebarItem(BitChordIcons.Home, "Home", destination == DesktopDestination.LISTEN_NOW) { onDestinationSelected(DesktopDestination.LISTEN_NOW) }
            DesktopSidebarItem(BitChordIcons.Explore, "New", destination == DesktopDestination.EXPLORE) { onDestinationSelected(DesktopDestination.EXPLORE) }
            DesktopSidebarItem(Icons.Rounded.Radio, "Radio", destination == DesktopDestination.RADIO) { onDestinationSelected(DesktopDestination.RADIO) }
            Spacer(Modifier.height(24.dp))
            DesktopSidebarSection("Library")
            DesktopSidebarItem(BitChordIcons.Library, "Library", destination == DesktopDestination.LIBRARY && libraryShelf == null) {
                onDestinationSelected(DesktopDestination.LIBRARY)
            }
            if ("dest:HISTORY" !in hidden) DesktopSidebarItem(BitChordIcons.Clock, "Recently Played", destination == DesktopDestination.HISTORY,
                onHide = { DesktopSidebarVisibility.hide("dest:HISTORY", "Recently Played") }) { onDestinationSelected(DesktopDestination.HISTORY) }
            // The four shortcuts are the page's own shelf list, so they hide like destinations do.
            if ("shelf:Artists" !in hidden) DesktopSidebarItem(Icons.Outlined.Mic, "Artists", destination == DesktopDestination.LIBRARY && libraryShelf == "Artists",
                onHide = { DesktopSidebarVisibility.hide("shelf:Artists", "Artists") }) { onOpenLibraryShelf("Artists") }
            if ("shelf:Albums" !in hidden) DesktopSidebarItem(Icons.Outlined.Album, "Albums", destination == DesktopDestination.LIBRARY && libraryShelf == "Albums",
                onHide = { DesktopSidebarVisibility.hide("shelf:Albums", "Albums") }) { onOpenLibraryShelf("Albums") }
            if ("shelf:Songs" !in hidden) DesktopSidebarItem(BitChordIcons.MusicNote, "Songs", destination == DesktopDestination.LIBRARY && libraryShelf == "Songs",
                onHide = { DesktopSidebarVisibility.hide("shelf:Songs", "Songs") }) { onOpenLibraryShelf("Songs") }
            if ("shelf:Made for You" !in hidden) DesktopSidebarItem(Icons.Outlined.Person, "Made for You", destination == DesktopDestination.LIBRARY && libraryShelf == "Made for You",
                onHide = { DesktopSidebarVisibility.hide("shelf:Made for You", "Made for You") }) { onOpenLibraryShelf("Made for You") }
            val downloads by DesktopDownloadQueue.active.collectAsState()
            if ("dest:DOWNLOADS" !in hidden) DesktopSidebarItem(BitChordIcons.Download, if (downloads.isEmpty()) "Downloads" else "Downloads · ${downloads.size}", destination == DesktopDestination.DOWNLOADS,
                onHide = { DesktopSidebarVisibility.hide("dest:DOWNLOADS", "Downloads") }) { onDestinationSelected(DesktopDestination.DOWNLOADS) }
            if ("dest:LOCAL_MUSIC" !in hidden) DesktopSidebarItem(BitChordIcons.Library, "Local Music", destination == DesktopDestination.LOCAL_MUSIC,
                onHide = { DesktopSidebarVisibility.hide("dest:LOCAL_MUSIC", "Local Music") }) { onDestinationSelected(DesktopDestination.LOCAL_MUSIC) }
            Spacer(Modifier.height(24.dp))
            DesktopSidebarSection("Playlists")
            LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(bottom = 12.dp)) {
                items(accountPlaylists.filterNot { "account:${it.browseId}" in hidden }, key = { "account:${it.browseId}" }) { playlist ->
                    DesktopSidebarItem(Icons.AutoMirrored.Rounded.PlaylistPlay, playlist.title, openedCollectionId == playlist.browseId,
                        onHide = { DesktopSidebarVisibility.hide("account:${playlist.browseId}", playlist.title) }) { onOpenAccountPlaylist(playlist) }
                }
                items(localPlaylists.filterNot { "local:${it.id}" in hidden }, key = { "local:${it.id}" }) { playlist ->
                    DesktopSidebarItem(Icons.AutoMirrored.Rounded.PlaylistPlay, playlist.title, openedCollectionId == playlist.id,
                        onHide = { DesktopSidebarVisibility.hide("local:${playlist.id}", playlist.title) }) { onOpenLocalPlaylist(playlist) }
                }
            }
            if (hidden.isNotEmpty()) DesktopSidebarRestoreRow(hidden)
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
                        else Icon(Icons.Rounded.AccountCircle, null, tint = DesktopAppleSecondary)
                    }
                    Text("Account", Modifier.weight(1f).padding(start = 8.dp), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = DesktopAppleText, maxLines = 1)
                    Icon(BitChordIcons.ChevronRight, null, tint = DesktopAppleSecondary, modifier = Modifier.size(16.dp))
                }
            } else {
                Box(Modifier.clip(RoundedCornerShape(16.dp)).background(DesktopAccent).clickable(onClick = onOpenAccounts).padding(horizontal = 14.dp, vertical = 5.dp)) {
                    Text("Sign In", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                }
                Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.width(8.dp))
            val settingsHover = remember { MutableInteractionSource() }
            val overSettings by settingsHover.collectIsHoveredAsState()
            Box(
                Modifier.size(28.dp).clip(RoundedCornerShape(8.dp))
                    .background(if (overSettings || settingsOpen) Color.White.copy(alpha = 0.05f) else Color.Transparent)
                    .hoverable(settingsHover).clickable(onClick = onOpenSettings),
                contentAlignment = Alignment.Center,
            ) {
                Icon(BitChordIcons.CogIcon, "Settings", tint = if (settingsOpen) DesktopAccent else if (overSettings) DesktopAppleText else DesktopAppleSecondary, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
internal fun DesktopAppleFloatingPlayer(
    song: Song?,
    isPlaying: Boolean,
    isLoading: Boolean,
    previousEnabled: Boolean,
    progress: Float,
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
    onVolumeChange: (Float) -> Unit,
    onOpenAudioOutput: () -> Unit,
    onOpenLyrics: () -> Unit,
    onOpenQueue: () -> Unit,
    sidePanel: DesktopSidePanel?,
    liveRadio: Boolean,
    onStopRadio: () -> Unit,
) {
    val mixBlend = DesktopPlayerSettings.smartMixBlend.collectAsState()
    val mixPulse = rememberMixPulse({ mixBlend.value }, enabled = true, slow = true)
    val currentProgress by rememberUpdatedState(progress)
    var volumeMenu by remember { mutableStateOf(false) }
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
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (!narrow) MiniPlayerButton({ onShuffleChange(!shuffle) }, enabled = !liveRadio, width = 24.dp) {
                        Icon(BitChordIcons.Shuffle, "Shuffle", tint = if (shuffle) DesktopAccent else DesktopAppleSecondary, modifier = Modifier.size(16.dp))
                    }
                    MiniPlayerButton(onPrevious, enabled = previousEnabled && song != null) {
                        Icon(BitChordIcons.TransportPrevious, "Previous", tint = DesktopAppleText, modifier = Modifier.size(24.dp))
                    }
                    MiniPlayerButton(onPlayPause, enabled = song != null) {
                        if (isLoading) CircularProgressIndicator(Modifier.size(19.dp), color = DesktopAppleText, strokeWidth = 2.dp)
                        else Icon(if (isPlaying) BitChordIcons.TransportPause else BitChordIcons.TransportPlay, if (isPlaying) "Pause" else "Play", tint = DesktopAppleText, modifier = Modifier.size(26.dp))
                    }
                    MiniPlayerButton(if (liveRadio) onStopRadio else onNext, enabled = song != null) {
                        Icon(if (liveRadio) Icons.Rounded.Stop else BitChordIcons.TransportNext, if (liveRadio) "Stop radio" else "Next", tint = DesktopAppleText, modifier = Modifier.size(24.dp))
                    }
                    if (!narrow) MiniPlayerButton({ onRepeatModeChange(repeatMode.next()) }, enabled = !liveRadio, width = 24.dp) {
                        if (repeatMode == DesktopRepeatMode.ONE) Text("1", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = DesktopAccent)
                        else Icon(BitChordIcons.Repeat, "Repeat ${repeatMode.label()}", tint = if (repeatMode == DesktopRepeatMode.OFF) DesktopAppleSecondary else DesktopAccent, modifier = Modifier.size(16.dp))
                    }
                }
                MiniPlayerLcd(
                    song = song,
                    progress = { currentProgress },
                    mixPulse = mixPulse,
                    durationMs = durationMs,
                    seekable = !liveRadio && song != null && durationMs > 0L,
                    onSeek = onSeek,
                    onOpenNowPlaying = onOpenNowPlaying,
                    modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                )
                Box(contentAlignment = Alignment.CenterEnd) {
                    val actionsHover = remember { MutableInteractionSource() }
                    val volumeHover = remember { MutableInteractionSource() }
                    val overActions by actionsHover.collectIsHoveredAsState()
                    val overVolume by volumeHover.collectIsHoveredAsState()
                    var sliderOpen by remember { mutableStateOf(false) }
                    LaunchedEffect(overVolume, overActions) {
                        if (overVolume) sliderOpen = true else if (!overActions) sliderOpen = false
                    }
                    val reveal by animateFloatAsState(if (sliderOpen) 1f else 0f, tween(150), label = "miniVolume")
                    Row(
                        Modifier.hoverable(actionsHover),
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val others = Modifier.graphicsLayer { alpha = 1f - reveal }
                        MiniPlayerButton({ onToggleLike?.invoke() }, enabled = onToggleLike != null && song != null && !liveRadio, width = 24.dp, modifier = others) {
                            Icon(if (liked) BitChordIcons.StarFilled else BitChordIcons.Star, if (liked) "Remove from Favourites" else "Favourite",
                                tint = if (liked) DesktopAccent else DesktopAppleSecondary, modifier = Modifier.size(18.dp))
                        }
                        MiniPlayerButton(onOpenLyrics, enabled = !liveRadio, width = 24.dp, modifier = others) {
                            Icon(BitChordIcons.LyricsQuote, "Lyrics", tint = if (sidePanel == DesktopSidePanel.LYRICS) DesktopAccent else DesktopAppleSecondary, modifier = Modifier.size(17.dp))
                        }
                        MiniPlayerButton(onOpenQueue, enabled = !liveRadio, width = 24.dp, modifier = others) {
                            Icon(BitChordIcons.Queue, "Up Next", tint = if (sidePanel == DesktopSidePanel.QUEUE) DesktopAccent else DesktopAppleSecondary, modifier = Modifier.size(17.dp))
                        }
                        Box {
                            MiniPlayerButton({ volumeMenu = true }, width = 24.dp, modifier = Modifier.hoverable(volumeHover)) {
                                Icon(BitChordIcons.TransportVolume(volume), "Volume and audio output", tint = if (sliderOpen) DesktopAppleText else DesktopAppleSecondary, modifier = Modifier.size(18.dp))
                            }
                            DesktopDropdownMenu(volumeMenu, { volumeMenu = false }) {
                                if (narrow && !liveRadio) {
                                    DropdownMenuItem(text = { Text(if (shuffle) "Shuffle On" else "Shuffle Off") }, onClick = { onShuffleChange(!shuffle) })
                                    DropdownMenuItem(text = { Text("Repeat ${repeatMode.label()}") }, onClick = { onRepeatModeChange(repeatMode.next()) })
                                }
                                Box(Modifier.width(180.dp).padding(horizontal = 16.dp)) {
                                    DesktopThinSlider(volume, onVolumeChange, idleHeight = 4.dp, activeHeight = 8.dp)
                                }
                                DropdownMenuItem(text = { Text(if (liveRadio) "Radio uses system default output" else "Audio Output") }, enabled = !liveRadio,
                                    leadingIcon = { Icon(Icons.Rounded.Headphones, null) }, onClick = { volumeMenu = false; onOpenAudioOutput() })
                            }
                        }
                    }
                    if (reveal > 0f) {
                        DesktopThinSlider(
                            volume, onVolumeChange,
                            idleHeight = 4.dp, activeHeight = 4.dp,
                            modifier = Modifier.padding(end = 33.dp).width(96.dp).hoverable(actionsHover)
                                .graphicsLayer { alpha = reveal; translationX = (1f - reveal) * 8.dp.toPx() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniPlayerButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    width: Dp = 28.dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier.size(width, 28.dp).clip(RoundedCornerShape(4.dp))
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
    seekable: Boolean,
    onSeek: (Long) -> Unit,
    onOpenNowPlaying: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val stripHover = remember { MutableInteractionSource() }
    val overStrip by stripHover.collectIsHoveredAsState()
    var scrub by remember { mutableStateOf<Float?>(null) }
    val scrubbing = seekable && (overStrip || scrub != null)
    val focus by animateFloatAsState(if (scrubbing) 1f else 0f, tween(100), label = "lcdScrub")
    Box(modifier.height(44.dp).clip(RoundedCornerShape(6.dp)).clickable(onClick = onOpenNowPlaying)) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            if (song != null) DesktopArtwork(song.thumbnailUrl, Modifier.size(34.dp).clip(RoundedCornerShape(6.dp)), px = 160)
            else Image(painterResource(Res.drawable.logo_mark), "BitChord", Modifier.padding(5.dp).size(24.dp))
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                Text(song?.title ?: "BitChord", color = DesktopAppleText, fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song?.artist ?: "Not Playing", color = DesktopAppleSecondary, fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
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
                    val barHeight = (2.dp + 2.dp * focus).toPx()
                    val top = size.height - barHeight
                    val width = size.width
                    val base = (scrub ?: progress()).coerceIn(0f, 1f)
                    val fill = base + (1f - base) * mixPulse.cover
                    val radius = androidx.compose.ui.geometry.CornerRadius(barHeight / 2f)
                    drawRoundRect(Color.White.copy(alpha = 0.16f), Offset(0f, top), Size(width, barHeight), radius)
                    drawRoundRect(Color.White.copy(alpha = mixPulse.alpha(0.64f)), Offset(0f, top), Size(width * fill, barHeight), radius)
                },
        )
    }
}
