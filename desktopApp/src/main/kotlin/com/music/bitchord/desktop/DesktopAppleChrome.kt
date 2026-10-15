package com.music.bitchord.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VolumeUp
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
    onDestinationSelected: (DesktopDestination) -> Unit,
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
        DesktopTitleBarDragArea(Modifier.fillMaxWidth().height(68.dp)) {
            Row(Modifier.fillMaxSize().padding(start = 26.dp), horizontalArrangement = Arrangement.Start, verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(Res.drawable.logo_mark), "BitChord", Modifier.size(33.dp, 24.dp))
                Text("BitChord", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = DesktopAppleText, modifier = Modifier.padding(start = 8.dp))
            }
        }
        Column(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp)) {
            DesktopSidebarItem(BitChordIcons.Search, "Search", destination == DesktopDestination.SEARCH) { onDestinationSelected(DesktopDestination.SEARCH) }
            DesktopSidebarItem(BitChordIcons.Home, "Home", destination == DesktopDestination.LISTEN_NOW) { onDestinationSelected(DesktopDestination.LISTEN_NOW) }
            DesktopSidebarItem(BitChordIcons.Explore, "New", destination == DesktopDestination.EXPLORE) { onDestinationSelected(DesktopDestination.EXPLORE) }
            DesktopSidebarItem(Icons.Rounded.Radio, "Radio", destination == DesktopDestination.RADIO) { onDestinationSelected(DesktopDestination.RADIO) }
            Spacer(Modifier.height(24.dp))
            DesktopSidebarSection("Library")
            DesktopSidebarItem(BitChordIcons.Library, "Library", destination == DesktopDestination.LIBRARY) { onDestinationSelected(DesktopDestination.LIBRARY) }
            if ("dest:HISTORY" !in hidden) DesktopSidebarItem(BitChordIcons.Clock, "History", destination == DesktopDestination.HISTORY,
                onHide = { DesktopSidebarVisibility.hide("dest:HISTORY", "History") }) { onDestinationSelected(DesktopDestination.HISTORY) }
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
            DesktopSidebarItem(Icons.Rounded.Settings, "Settings", settingsOpen, onClick = onOpenSettings)
        }
        HorizontalDivider(Modifier.padding(top = 8.dp), thickness = 0.5.dp, color = Color.White.copy(alpha = 0.08f))
        Row(Modifier.fillMaxWidth().height(70.dp).padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            if (signedIn) {
                Box(Modifier.size(28.dp).clip(CircleShape).clickable(onClick = onOpenAccounts), contentAlignment = Alignment.Center) {
                    if (accountAvatar != null) DesktopArtwork(accountAvatar, Modifier.fillMaxSize())
                    else Icon(Icons.Rounded.AccountCircle, "Account", tint = DesktopAppleSecondary)
                }
                Text("Account", fontSize = 13.sp, color = DesktopAppleText, modifier = Modifier.padding(start = 10.dp).clickable(onClick = onOpenAccounts))
            } else {
                Box(Modifier.clip(CircleShape).background(DesktopAccent).clickable(onClick = onOpenAccounts).padding(horizontal = 20.dp, vertical = 7.dp)) {
                    Text("Sign In", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                }
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
    volume: Float,
    shuffle: Boolean,
    repeatMode: DesktopRepeatMode,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onShuffleChange: (Boolean) -> Unit,
    onRepeatModeChange: (DesktopRepeatMode) -> Unit,
    onOpenNowPlaying: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onOpenAudioOutput: () -> Unit,
    onOpenLyrics: () -> Unit,
    onOpenQueue: () -> Unit,
    sidePanel: DesktopSidePanel?,
) {
    val mixBlend = DesktopPlayerSettings.smartMixBlend.collectAsState()
    val mixPulse = rememberMixPulse({ mixBlend.value }, enabled = true, slow = true)
    val currentProgress by rememberUpdatedState(progress)
    var volumeMenu by remember { mutableStateOf(false) }
    BoxWithConstraints(Modifier.widthIn(max = 668.dp).fillMaxWidth().padding(horizontal = 6.dp)) {
        val narrow = maxWidth < 550.dp
        Row(
            Modifier.fillMaxWidth().height(56.dp).clip(CircleShape).background(Color(0xF22C2C2E))
                .border(0.5.dp, Color.White.copy(alpha = 0.15f), CircleShape).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!narrow) DesktopToolbarButton({ onShuffleChange(!shuffle) }, size = 28.dp) {
                Icon(BitChordIcons.Shuffle, "Shuffle", tint = if (shuffle) DesktopAccent else DesktopAppleSecondary, modifier = Modifier.size(16.dp))
            }
            DesktopToolbarButton(onPrevious, enabled = previousEnabled && song != null, size = 30.dp) {
                Icon(Icons.Rounded.FastRewind, "Previous", tint = DesktopAppleText, modifier = Modifier.size(21.dp))
            }
            DesktopToolbarButton(onPlayPause, enabled = song != null, size = 34.dp) {
                if (isLoading) CircularProgressIndicator(Modifier.size(17.dp), color = DesktopAppleText, strokeWidth = 2.dp)
                else Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (isPlaying) "Pause" else "Play", tint = DesktopAppleText, modifier = Modifier.size(25.dp))
            }
            DesktopToolbarButton(onNext, enabled = song != null, size = 30.dp) {
                Icon(Icons.Rounded.FastForward, "Next", tint = DesktopAppleText, modifier = Modifier.size(21.dp))
            }
            if (!narrow) DesktopToolbarButton({ onRepeatModeChange(repeatMode.next()) }, size = 28.dp) {
                if (repeatMode == DesktopRepeatMode.ONE) Text("1", fontSize = 12.sp, color = DesktopAccent)
                else Icon(BitChordIcons.Repeat, "Repeat ${repeatMode.label()}", tint = if (repeatMode == DesktopRepeatMode.OFF) DesktopAppleSecondary else DesktopAccent, modifier = Modifier.size(16.dp))
            }
            Box(Modifier.weight(1f).padding(horizontal = 10.dp).height(44.dp).clip(RoundedCornerShape(5.dp))
                .background(Color.Black.copy(alpha = 0.12f)).clickable(onClick = onOpenNowPlaying)) {
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    if (song != null) DesktopArtwork(song.thumbnailUrl, Modifier.size(44.dp), px = 160)
                    else Image(painterResource(Res.drawable.logo_mark), "BitChord", Modifier.padding(10.dp).size(24.dp))
                    Column(Modifier.weight(1f).padding(start = 9.dp, end = 9.dp, bottom = 4.dp)) {
                        Text(song?.title ?: "BitChord", color = DesktopAppleText, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(song?.artist ?: "Not Playing", color = DesktopAppleSecondary, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(2.dp).drawBehind {
                    val base = currentProgress.coerceIn(0f, 1f)
                    val fraction = base + (1f - base) * mixPulse.cover
                    drawRect(Color.White.copy(alpha = 0.12f))
                    drawRect(Color.White.copy(alpha = mixPulse.alpha(0.9f)), size = Size(size.width * fraction, size.height))
                })
            }
            DesktopToolbarButton(onOpenLyrics, size = 28.dp) {
                Icon(BitChordIcons.LyricsQuote, "Lyrics", tint = if (sidePanel == DesktopSidePanel.LYRICS) DesktopAppleText else DesktopAppleSecondary, modifier = Modifier.size(17.dp))
            }
            DesktopToolbarButton(onOpenQueue, size = 28.dp) {
                Icon(BitChordIcons.Queue, "Queue", tint = if (sidePanel == DesktopSidePanel.QUEUE) DesktopAppleText else DesktopAppleSecondary, modifier = Modifier.size(17.dp))
            }
            Box {
                DesktopToolbarButton({ volumeMenu = true }, size = 28.dp) {
                    Icon(Icons.Rounded.VolumeUp, "Volume and audio output", tint = DesktopAppleSecondary, modifier = Modifier.size(17.dp))
                }
                DesktopDropdownMenu(volumeMenu, { volumeMenu = false }) {
                    if (narrow) {
                        DropdownMenuItem(text = { Text(if (shuffle) "Shuffle On" else "Shuffle Off") }, onClick = { onShuffleChange(!shuffle) })
                        DropdownMenuItem(text = { Text("Repeat ${repeatMode.label()}") }, onClick = { onRepeatModeChange(repeatMode.next()) })
                    }
                    Box(Modifier.width(180.dp).padding(horizontal = 16.dp)) {
                        DesktopThinSlider(volume, onVolumeChange, idleHeight = 4.dp, activeHeight = 8.dp)
                    }
                    DropdownMenuItem(text = { Text("Audio Output") }, leadingIcon = { Icon(Icons.Rounded.Headphones, null) }, onClick = { volumeMenu = false; onOpenAudioOutput() })
                }
            }
            if (!narrow) DesktopThinSlider(volume, onVolumeChange, idleHeight = 4.dp, activeHeight = 8.dp, modifier = Modifier.width(66.dp))
        }
    }
}
