package com.music.bitchord.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.Song
import com.music.bitchord.ui.components.contextClick
import com.music.bitchord.ui.icons.BitChordIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

internal val DesktopAppleText = Color.White.copy(alpha = 0.92f)
internal val DesktopAppleSecondary = Color(0xFFAAAAAD)

/**
 * Apple's own wash for an artist page, kept dark.
 *
 * The colour the artist record carries is that page's light-mode ground — pure
 * white on a monochrome cover — and this app's artist page lays white ink over
 * it, so only the hue is worth keeping: saturation and lightness are clamped
 * into the range that stays a ground rather than a glare. With nothing to keep,
 * the plain surface comes back.
 */
internal fun appleArtistWash(argb: Int?): Color {
    if (argb == null) return DesktopSurface
    val hsl = FloatArray(3).also { com.music.bitchord.ui.graphics.ColorUtils.colorToHSL(argb, it) }
    if (hsl[1] < 0.08f) return DesktopSurface
    hsl[1] = hsl[1].coerceIn(0.30f, 0.65f)
    hsl[2] = hsl[2].coerceIn(0.08f, 0.18f)
    return Color(com.music.bitchord.ui.graphics.ColorUtils.HSLToColor(hsl))
}

@Composable
private fun artistHeaderImage(url: String?): State<Result<ImageBitmap?>?> = produceState(null, url) {
    value = null
    if (url == null) {
        value = Result.success(null)
        return@produceState
    }
    repeat(2) { attempt ->
        val bitmap = withContext(Dispatchers.IO) { DesktopArtworkCache.load(url) }
        if (bitmap != null) {
            value = Result.success(bitmap)
            return@produceState
        }
        if (attempt == 0) delay(400)
    }
    DesktopTrackLog.log("Apple artist: image could not be fetched or decoded")
    value = Result.failure(IllegalStateException("Artist image unavailable"))
}

@Composable
internal fun DesktopAppleArtistHeader(
    name: String,
    fallbackImageUrl: String?,
    art: DesktopAppleArtistArt?,
    favourite: Boolean?,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onToggleFavourite: (() -> Unit)?,
    onInfo: () -> Unit,
) {
    val background = appleArtistWash(art?.background)
    val controlColor = art?.keyColor?.let { Color(it) } ?: Color.White
    val heroResult by artistHeaderImage(art?.heroUrl ?: fallbackImageUrl)
    val logoResult by artistHeaderImage(art?.logoUrl)
    val portraitResult by artistHeaderImage(art?.portraitUrl)
    val hero = heroResult?.getOrNull()
    val logo = logoResult?.getOrNull()
    val portrait = portraitResult?.getOrNull()
    var menu by remember { mutableStateOf(false) }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val headerWidth = maxWidth
        val compact = headerWidth < 650.dp
        val circular = art?.portraitUrl != null
        val headerHeight = if (circular) {
            if (compact) 400.dp else 486.dp
        } else (headerWidth / 2.119f).coerceIn(470.dp, 705.dp)
        Box(Modifier.fillMaxWidth().height(headerHeight).background(background).clip(RoundedCornerShape(0.dp))) {
            if (circular) {
                hero?.let {
                    Image(it, null, Modifier.matchParentSize().blur(80.dp).graphicsLayer { alpha = 0.45f }, contentScale = ContentScale.Crop)
                }
                Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(background.copy(alpha = 0.3f), background.copy(alpha = 0.8f)))))
                portrait?.let {
                    Image(it, name, Modifier.align(Alignment.TopCenter).padding(top = if (compact) 40.dp else 94.dp)
                        .size(if (compact) 174.dp else 190.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                }
            } else {
                hero?.let { Image(it, null, Modifier.matchParentSize(), alignment = Alignment.TopCenter, contentScale = ContentScale.Crop) }
                val video = art?.wideVideoUrl ?: art?.squareVideoUrl
                if (video != null) {
                    DesktopCanvasView(
                        url = video,
                        modifier = Modifier.matchParentSize(),
                        fallbackUrl = art?.squareVideoUrl?.takeIf { it != video },
                        maxEdge = 1280,
                        publishBackdrop = false,
                    )
                }
                hero?.let { bitmap ->
                    Image(
                        bitmap, null,
                        Modifier.matchParentSize()
                            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                            .drawWithContent {
                                drawContent()
                                drawRect(Brush.verticalGradient(0f to Color.Transparent, 0.64f to Color.Transparent, 1f to Color.White), blendMode = BlendMode.DstIn)
                            }
                            .blur(30.dp),
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.TopCenter,
                    )
                }
                Box(Modifier.matchParentSize().background(Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.64f to Color.Transparent,
                    0.8f to background.copy(alpha = 0.1f),
                    1f to background.copy(alpha = 0.92f),
                )))
            }
            Box(Modifier.align(Alignment.TopEnd).padding(top = 11.dp, end = 12.dp)) {
                DesktopAppleArtistButton(30, "More", onClick = { menu = true }) {
                    Icon(BitChordIcons.Ellipsis, null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
                DesktopDropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text(DesktopStrings["play", "Play"]) }, onClick = { menu = false; onPlay() })
                    DropdownMenuItem(text = { Text(DesktopStrings["shuffle", "Shuffle"]) }, onClick = { menu = false; onShuffle() })
                    if (onToggleFavourite != null) {
                        DropdownMenuItem(text = { Text(if (favourite == true) DesktopStrings["d_remove_from_favourites", "Remove from Favourites"] else DesktopStrings["d_favourite", "Favourite"]) }, onClick = { menu = false; onToggleFavourite() })
                    }
                }
            }
            Column(
                Modifier.align(Alignment.BottomCenter).padding(start = 40.dp, end = 40.dp, bottom = 23.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val mark = logo
                val logoMissing = art?.logoUrl == null || logoResult?.isFailure == true
                val signature by produceState<Result<ImageBitmap?>?>(null, name, logoMissing) {
                    value = if (logoMissing) Result.success(DesktopArtistSignatures.signatureFor(name)) else null
                }
                val ink = signature?.getOrNull()
                if (mark != null) {
                    val aspect = mark.width.toFloat() / mark.height
                    val width = minOf(headerWidth * 0.45f, 282.dp, 94.dp * aspect)
                    Image(mark, name, Modifier.width(width).height(width / aspect), contentScale = ContentScale.Fit)
                } else if (logoMissing && ink != null) {
                    val aspect = ink.width.toFloat() / ink.height
                    val width = minOf(headerWidth * 0.5f, 320.dp, 110.dp * aspect)
                    Image(ink, name, Modifier.width(width).height(width / aspect), contentScale = ContentScale.Fit,
                        colorFilter = ColorFilter.tint(Color.White, BlendMode.SrcIn))
                } else if (logoMissing && signature != null) {
                    Text(canonicalAppleArtistName(name), fontSize = 44.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center, maxLines = 2)
                } else {
                    Spacer(Modifier.height(94.dp))
                }
                Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    DesktopAppleArtistButton(36, "Artist information", onClick = onInfo) {
                        Text("i", fontSize = 20.sp, fontWeight = FontWeight.Medium, color = controlColor)
                    }
                    Canvas(
                        Modifier.size(56.dp).graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.clip(CircleShape).clickable(onClick = onPlay),
                    ) {
                        drawCircle(controlColor)
                        val triangle = Path().apply {
                            moveTo(size.width * 0.41f, size.height * 0.29f)
                            lineTo(size.width * 0.73f, size.height * 0.5f)
                            lineTo(size.width * 0.41f, size.height * 0.71f)
                            close()
                        }
                        drawPath(triangle, Color.Transparent, blendMode = BlendMode.Clear)
                    }
                    DesktopAppleArtistButton(36, "Favourite artist", enabled = onToggleFavourite != null, onClick = { onToggleFavourite?.invoke() }) {
                        Icon(if (favourite == true) BitChordIcons.StarFilled else BitChordIcons.Star, null, tint = controlColor, modifier = Modifier.size(19.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun DesktopAppleArtistButton(size: Int, label: String, enabled: Boolean = true, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.size(size.dp).semantics { contentDescription = label }.clip(CircleShape).background(Color.Black.copy(alpha = 0.15f))
            .border(0.67.dp, Color.White.copy(alpha = 0.26f), CircleShape)
            .desktopHoverWash().clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
internal fun DesktopAppleHeading(title: String, modifier: Modifier = Modifier, chevron: Boolean = false) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = DesktopAppleText, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        if (chevron) Icon(BitChordIcons.ChevronRight, null, tint = DesktopAppleSecondary, modifier = Modifier.padding(start = 3.dp).size(14.dp))
    }
}

@Composable
internal fun DesktopAppleSongRow(
    song: Song,
    subtitle: String = song.artist,
    onClick: () -> Unit,
    onOpenMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().height(59.dp).hoverable(interaction).desktopHoverFill()
                .clickable(onClick = onClick).contextClick(onOpenMenu),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DesktopArtwork(song.thumbnailUrl, Modifier.size(40.dp).clip(RoundedCornerShape(4.dp)), px = 160)
            Column(Modifier.weight(1f).padding(start = 16.dp, end = 8.dp)) {
                Text(song.title, color = DesktopAppleText, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, color = DesktopAppleSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(Modifier.size(30.dp)) {
                if (hovered) DesktopToolbarButton(onOpenMenu, size = 30.dp) {
                    Icon(BitChordIcons.Ellipsis, "Song menu", tint = DesktopAppleSecondary, modifier = Modifier.size(18.dp))
                }
            }
        }
        HorizontalDivider(Modifier.padding(start = 56.dp), thickness = 0.5.dp, color = Color.White.copy(alpha = 0.1f))
    }
}

/** One line of a song shelf: the compact row Apple stacks four to a column. */
internal data class AppleSongLine(
    val title: String,
    val subtitle: String,
    val artworkUrl: String?,
)

/**
 * Apple's song shelf — "Best New Songs", a chart, an artist's top songs: the
 * tracks read down in short columns, and the columns scroll sideways.
 *
 * Separate from [DesktopAppleSongRow] on purpose. A release's or playlist's own
 * tracks stay one long list read down, which is what opening one is for; this is
 * the shelf that says *here is a run of songs*, where the whole page has to be
 * scanned sideways.
 *
 * Three rows to a column, Apple's own measure: 55 tall in a 298-wide column on a
 * browse page, 50 in a 335-wide one on an artist page.
 */
@Composable
internal fun DesktopAppleSongGrid(
    title: String,
    lines: List<AppleSongLine>,
    onPlay: (Int) -> Unit,
    onOpenMenu: (Int) -> Unit,
    chevron: Boolean = false,
    onHeadingClick: (() -> Unit)? = null,
    rowsPerColumn: Int = 3,
    columnWidth: Dp = 298.dp,
    rowHeight: Dp = 55.dp,
) {
    if (lines.isEmpty()) return
    val columns = lines.mapIndexed { index, line -> index to line }.chunked(rowsPerColumn)
    Column(Modifier.padding(top = 25.dp, bottom = 5.dp)) {
        DesktopAppleHeading(
            title,
            Modifier.padding(horizontal = DesktopPageGutter, vertical = 12.dp)
                .then(if (onHeadingClick != null) Modifier.clickable { onHeadingClick() } else Modifier),
            chevron = chevron,
        )
        DesktopScrollableRow(gutter = DesktopPageGutter, spacing = 20.dp) {
            items(columns) { column ->
                Column(Modifier.width(columnWidth)) {
                    column.forEach { (index, line) ->
                        AppleSongGridLine(line.title, line.subtitle, line.artworkUrl, rowHeight,
                            onClick = { onPlay(index) }, onOpenMenu = { onOpenMenu(index) })
                    }
                }
            }
        }
    }
}

@Composable
private fun AppleSongGridLine(
    title: String,
    subtitle: String,
    artworkUrl: String?,
    height: Dp,
    onClick: () -> Unit,
    onOpenMenu: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        Modifier.fillMaxWidth().height(height).hoverable(interaction).desktopHoverFill()
            .clickable(onClick = onClick).contextClick(onOpenMenu),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp)) {
            DesktopArtwork(artworkUrl, Modifier.size(40.dp).clip(RoundedCornerShape(4.dp)), px = 160)
            if (hovered) {
                Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
                    Icon(BitChordIcons.TransportPlay, null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }
        Column(Modifier.weight(1f).padding(start = 12.dp, end = 8.dp)) {
            Text(title, color = DesktopAppleText, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = DesktopAppleSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(
            BitChordIcons.Ellipsis,
            "Song menu",
            tint = DesktopAppleSecondary,
            modifier = Modifier.size(18.dp).clickable(onClick = onOpenMenu),
        )
    }
}

/**
 * An artist's top songs, as Apple lays them out: three rows down, the columns measured to the
 * space they are given rather than to a fixed width that runs off the edge of the page.
 *
 * Apple puts this beside Latest Release, so it is a block rather than a shelf that scrolls
 * sideways — nine songs, which is what fits without clipping the last column.
 */
@Composable
internal fun DesktopAppleTopSongs(
    songs: List<Song>,
    onPlay: (Int) -> Unit,
    onOpenMenu: (Song) -> Unit,
    modifier: Modifier = Modifier,
    rowsPerColumn: Int = 3,
    maxSongs: Int = 9,
) {
    if (songs.isEmpty()) return
    val shown = songs.take(maxSongs)
    val columns = shown.indices.chunked(rowsPerColumn)
    Column(modifier) {
        DesktopAppleHeading("Top Songs", Modifier.padding(bottom = 15.dp), chevron = true)
        BoxWithConstraints {
            val spacing = 20.dp
            val columnWidth = ((maxWidth - spacing * (columns.size - 1)) / columns.size)
                .coerceAtLeast(120.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                columns.forEach { column ->
                    Column(Modifier.width(columnWidth)) {
                        column.forEach { index ->
                            val song = shown[index]
                            AppleSongGridLine(
                                title = song.title,
                                subtitle = song.albumName ?: song.artist,
                                artworkUrl = song.thumbnailUrl,
                                height = 50.dp,
                                onClick = { onPlay(index) },
                                onOpenMenu = { onOpenMenu(song) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun DesktopAppleLatestRelease(item: ShelfItem, onClick: () -> Unit) {
    Column {
        DesktopAppleHeading("Latest Release")
        Row(Modifier.padding(top = 15.dp).desktopQuietClick(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
            DesktopArtwork(item.thumbnailUrl, Modifier.size(160.dp).clip(RoundedCornerShape(8.dp)).desktopHoverFill(corner = 8.dp))
            Column(Modifier.padding(start = 20.dp, end = 12.dp)) {
                val year = Regex("\\b(?:19|20)\\d{2}\\b").find(item.subtitle)?.value
                if (year != null) Text(year, fontSize = 11.sp, color = DesktopAppleSecondary)
                Text(item.title, fontSize = 15.sp, color = DesktopAppleText, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(vertical = 5.dp))
                Text(item.subtitle, fontSize = 15.sp, color = DesktopAppleSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
internal fun DesktopAppleShelf(
    shelf: HomeShelf,
    onItemClick: (ShelfItem, String?) -> Unit,
    onItemMenu: ((ShelfItem) -> Unit)? = null,
    /** The station on air in the radio player, so its tile can be marked. */
    playingRadioKey: String? = null,
    /** Off for a page that already names itself, so the shelf's own title is left out. */
    showHeading: Boolean = true,
) {
    val circles = shelf.items.isNotEmpty() && shelf.items.all { it.browseId?.startsWith("UC") == true }
    val videos = shelf.title.contains("video", ignoreCase = true)
    val more = shelf.moreBrowseId?.takeIf { it.startsWith(DesktopAppleBrowseClient.PAGE_PREFIX) }
    val heading: @Composable () -> Unit = {
        if (showHeading) DesktopAppleHeading(shelf.title, Modifier.padding(horizontal = DesktopPageGutter, vertical = 12.dp)
            .then(if (more != null) Modifier.clickable { onItemClick(ShelfItem(shelf.title, "", null, null, more), shelf.title) } else Modifier), chevron = true)
    }
    // Apple's track grid — the columns of short rows that read down three at a
    // time — is what a *run of songs* looks like on its pages: the charts, a
    // "Best New Songs", an artist's top tracks. The source's other song rows
    // ("Quick picks", "Covers and remixes") are covers, and stay tiles.
    val songGrid = shelf.items.isNotEmpty() && !circles && !videos && shelf.items.all { it.isSongLine() } &&
        (shelf.items.all { it.isAppleSong() } || shelf.isAppleChartShelf() ||
            shelf.title.contains("song", ignoreCase = true) || shelf.title.equals("Listen again", ignoreCase = true))
    if (songGrid) {
        DesktopAppleSongGrid(
            shelf.title,
            shelf.items.map { AppleSongLine(it.title, it.subtitle, it.thumbnailUrl) },
            onPlay = { onItemClick(shelf.items[it], shelf.title) },
            onOpenMenu = { i -> onItemMenu?.invoke(shelf.items[i]) },
            chevron = more != null,
            onHeadingClick = more?.let { id -> { onItemClick(ShelfItem(shelf.title, "", null, null, id), shelf.title) } },
        )
        return
    }
    // Apple's radio page is a grid of squares with what each one is carrying underneath. Apple names
    // no programme in any feed a listener may ask for, so that line is read off the stream itself.
    if (shelf.items.isNotEmpty() && shelf.items.all { DesktopRadioClient.isStation(it) }) {
        val offAir by DesktopRadioNowPlaying.lines.collectAsState()
        val itemOf = { tile: DesktopRadioTile -> shelf.items.firstOrNull { DesktopRadioClient.keyOf(it) == tile.key } }
        DesktopRadioTileGrid(
            if (showHeading) shelf.title else null,
            shelf.items.map { item ->
                val key = DesktopRadioClient.keyOf(item)
                DesktopRadioTile(
                    key = key,
                    title = item.title,
                    subtitle = offAir[key] ?: item.subtitle,
                    artworkUrl = item.thumbnailUrl,
                    playing = key == playingRadioKey,
                )
            },
            onOpen = { tile -> itemOf(tile)?.let { onItemClick(it, shelf.title) } },
            onHide = { tile -> DesktopRadioVisibility.hide(tile.key, tile.title) },
        )
        return
    }
    // Apple's own tile measures: 171 to the square, 219 to a 16:9 video, 97 to a
    // circle, all 20 apart.
    val width = if (circles) 97.dp else if (videos) 219.dp else 171.dp
    Column(Modifier.padding(top = 25.dp, bottom = 5.dp)) {
        heading()
        DesktopScrollableRow(gutter = DesktopPageGutter, spacing = 20.dp) {
            items(shelf.items) { item ->
                val shape = if (circles) CircleShape else RoundedCornerShape(8.dp)
                Column(Modifier.width(width)) {
                    DesktopArtwork(
                        item.thumbnailUrl,
                        Modifier.width(width).aspectRatio(if (videos) 16f / 9f else 1f).clip(shape)
                            .desktopHoverFill().clickable { onItemClick(item, shelf.title) }
                            .then(if (onItemMenu == null) Modifier else Modifier.contextClick { onItemMenu(item) }),
                        px = if (videos) 800 else 400,
                    )
                    Text(item.title, fontSize = 13.sp, color = DesktopAppleText, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        textAlign = if (circles) TextAlign.Center else TextAlign.Start,
                        modifier = Modifier.fillMaxWidth().padding(top = 7.dp))
                    if (!circles) Text(item.subtitle, fontSize = 12.sp, color = DesktopAppleSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/**
 * The artist's own hand at the foot of their page.
 *
 * The signature is drawn as ink rather than as a picture: the source art carries a paper
 * background, so the image is tinted through its own alpha and laid on the wash at low opacity,
 * the way a page signs itself off. Nothing is drawn when the artist has no signature on file.
 */
@Composable
internal fun DesktopArtistSignature(name: String) {
    val signature by produceState<ImageBitmap?>(null, name) {
        value = DesktopArtistSignatures.signatureFor(name)
    }
    val ink = signature ?: return
    if (ink.width <= 0 || ink.height <= 0) return
    val aspect = ink.width.toFloat() / ink.height
    val width = minOf(300.dp, 96.dp * aspect)
    Column(
        Modifier.fillMaxWidth().padding(start = DesktopPageGutter, end = DesktopPageGutter, top = 40.dp, bottom = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            ink,
            null,
            Modifier.width(width).height(width / aspect),
            contentScale = ContentScale.Fit,
            colorFilter = ColorFilter.tint(Color.White.copy(alpha = 0.55f), BlendMode.SrcIn),
        )
        // The hand on its own reads as decoration; the line underneath makes it a credit.
        Text(
            DesktopStrings.format("d_signed_by", name, fallback = "Signed by %1\$s"),
            color = DesktopAppleSecondary.copy(alpha = 0.78f),
            fontSize = 12.sp,
            letterSpacing = 0.5.sp,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
internal fun DesktopAppleAboutCard(name: String, bio: String?, subscribers: String?, listeners: String?) {
    var expanded by remember(name) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = DesktopPageGutter, vertical = 36.dp)) {
        DesktopAppleHeading(DesktopStrings.format("d_about_named", name, fallback = "About %1\$s"))
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
            Column(Modifier.weight(1f)) {
                if (!bio.isNullOrBlank()) {
                    Text(bio, color = DesktopAppleSecondary, fontSize = 14.sp, lineHeight = 21.sp, maxLines = if (expanded) Int.MAX_VALUE else 6, overflow = TextOverflow.Ellipsis)
                    TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) DesktopStrings["less", "Less"] else DesktopStrings["more", "More"], color = DesktopAppleText, fontSize = 13.sp) }
                }
            }
            if (subscribers != null || listeners != null) {
                Column(Modifier.width(170.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    subscribers?.let {
                        Text(DesktopStrings["d_subscribers", "SUBSCRIBERS"], fontSize = 10.sp, color = DesktopAppleSecondary)
                        Text(it.substringBefore(' '), fontSize = 17.sp, color = DesktopAppleText)
                    }
                    listeners?.let {
                        Text(DesktopStrings["d_monthly_listeners", "MONTHLY LISTENERS"], fontSize = 10.sp, color = DesktopAppleSecondary)
                        Text(it.substringBefore(' '), fontSize = 17.sp, color = DesktopAppleText)
                    }
                }
            }
        }
    }
}
