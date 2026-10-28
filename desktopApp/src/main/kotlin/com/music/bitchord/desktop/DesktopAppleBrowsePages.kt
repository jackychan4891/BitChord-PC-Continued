package com.music.bitchord.desktop

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import bitchord.desktopapp.generated.resources.Res
import bitchord.desktopapp.generated.resources.logo_mark
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.MoodGenre
import com.music.bitchord.data.model.MoodGenreSection
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.UiState
import com.music.bitchord.ui.components.AppleScrollbarBox
import com.music.bitchord.ui.components.ShimmerBox
import com.music.bitchord.ui.components.contextClick
import com.music.bitchord.ui.components.longPressMenuClickable
import com.music.bitchord.ui.icons.BitChordIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.painterResource
import java.util.Locale

@Composable
internal fun DesktopAppleFeedPage(
    title: String,
    state: UiState<List<HomeShelf>>,
    listState: LazyListState,
    contentPadding: PaddingValues,
    onItemClick: (ShelfItem, String?) -> Unit,
    onRetry: () -> Unit,
    onItemMenu: (ShelfItem) -> Unit,
    onLoadMore: (() -> Unit)? = null,
    loadingMore: Boolean = false,
    recentlyPlayedLoading: Boolean = false,
) {
    val isNew = title.equals("New", ignoreCase = true)
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().background(DesktopPageGround()),
        contentPadding = contentPadding,
    ) {
        item { AppleBrowsePageTitle(title) }
        when (state) {
            UiState.Loading -> {
                if (!isNew) item { AppleBrowseShelfSkeleton() }
                item { AppleBrowseShelfSkeleton(landscape = true) }
                item { AppleBrowseShelfSkeleton() }
            }
            is UiState.Error -> item { AppleBrowseError(state.message, onRetry) }
            is UiState.Success -> {
                if (recentlyPlayedLoading && !isNew) {
                    item { AppleBrowseShelfSkeleton("Recently Played") }
                }
                if (isNew) {
                    appleBrowseNewShelves(state.data, onItemClick, onItemMenu)
                } else {
                    appleBrowseHomeShelves(state.data, onItemClick, onItemMenu)
                }
                if (state.data.isEmpty() && !recentlyPlayedLoading) {
                    item { AppleBrowseEmpty("No music to show yet.") }
                }
                if (loadingMore) item { AppleBrowseShelfSkeleton() }
            }
        }
    }
    val loadMore by rememberUpdatedState(onLoadMore)
    if (onLoadMore != null && state is UiState.Success) {
        LaunchedEffect(listState, loadingMore) {
            snapshotFlow {
                val layout = listState.layoutInfo
                (layout.visibleItemsInfo.lastOrNull()?.index ?: -1) to layout.totalItemsCount
            }.collect { (lastVisible, total) ->
                if (!loadingMore && total > 0 && lastVisible >= total - 3) loadMore?.invoke()
            }
        }
    }
}

private fun LazyListScope.appleBrowseHomeShelves(
    shelves: List<HomeShelf>,
    onItemClick: (ShelfItem, String?) -> Unit,
    onItemMenu: (ShelfItem) -> Unit,
) {
    // The reference site's home is one running order: Recently Played as a run
    // of songs at the head, then the feed's own shelves exactly as it delivered
    // them. No ranking, no editorial shelf lifted out of the middle.
    val recents = shelves.filter { it.title.isAppleBrowseRecent() }
    recents.forEach { shelf ->
        item(key = "home:recent:${shelf.title}") {
            AppleBrowseRecentShelf(shelf, onItemClick, onItemMenu)
        }
    }
    // Apple's home opens with a row of tall picks whose words sit inside the art.
    // A feed that is not Apple's has no such lockup, so its own first cover row
    // takes that place — runs of songs, artist circles and video rows are not
    // picks and keep the shape they already have.
    val portraitAt = shelves.withIndex()
        .filterNot { (_, shelf) -> shelf.title.isAppleBrowseRecent() || shelf.title.isAppleBrowseDropped() }
        .firstOrNull { (_, shelf) -> shelf.wearsPortrait() }
        ?.index
    shelves.forEachIndexed { index, shelf ->
        if (shelf.title.isAppleBrowseRecent() || shelf.title.isAppleBrowseDropped()) return@forEachIndexed
        item(key = "home:$index") {
            DesktopAppleShelf(shelf, onItemClick, onItemMenu, portrait = index == portraitAt)
        }
    }
}

/** Whether this row is covers enough to be drawn as the site's tall (3:4) pick. */
private fun HomeShelf.wearsPortrait(): Boolean =
    items.isNotEmpty() &&
        !isAppleRadioShelf() &&
        !title.contains("video", ignoreCase = true) &&
        items.none { it.isSongLine() || DesktopRadioClient.isStation(it) } &&
        items.none { it.browseId?.startsWith("UC") == true } &&
        feedLayout(false).let { it == null || it == AppleFeedLayout.Square }

private fun LazyListScope.appleBrowseNewShelves(
    source: List<HomeShelf>,
    onItemClick: (ShelfItem, String?) -> Unit,
    onItemMenu: (ShelfItem) -> Unit,
) {
    // New is Home without the recents: the feed's own order, one row per shelf.
    source.forEachIndexed { index, shelf ->
        if (shelf.title.isAppleBrowseRecent() || shelf.isAppleRadioShelf() ||
            shelf.title.isAppleBrowseDropped()
        ) {
            return@forEachIndexed
        }
        item(key = "new:$index") { DesktopAppleShelf(shelf, onItemClick, onItemMenu) }
    }
}

@Composable
internal fun DesktopAppleCategories(
    state: UiState<List<MoodGenreSection>>,
    listState: LazyListState,
    contentPadding: PaddingValues,
    onCategoryClick: (MoodGenre) -> Unit,
    onRetry: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize().background(DesktopPageGround())) {
        val columns = if (maxWidth < 650.dp) 2 else 3
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
        ) {
            item {
                DesktopAppleHeading(
                    "Browse Categories",
                    Modifier.padding(horizontal = DesktopPageGutter).padding(top = 18.dp, bottom = 20.dp),
                )
            }
            when (state) {
                UiState.Loading -> items(4) { AppleBrowseCategorySkeleton(columns) }
                is UiState.Error -> item { AppleBrowseError(state.message, onRetry) }
                is UiState.Success -> {
                    val categories = state.data.flatMap { it.items }.distinctBy { it.browseId to it.params }
                    items(categories.chunked(columns)) { row ->
                        AppleBrowseCategoryRow(row, columns, onCategoryClick)
                    }
                    if (categories.isEmpty()) item { AppleBrowseEmpty("No categories to show yet.") }
                }
            }
        }
    }
}

@Composable
internal fun DesktopAppleRadioPage(
    shelves: UiState<List<HomeShelf>>,
    listState: LazyListState,
    contentPadding: PaddingValues,
    onItemClick: (ShelfItem, String?) -> Unit,
    onItemMenu: (ShelfItem) -> Unit,
    onRetry: () -> Unit,
    playingRadioKey: String?,
) {
    // A station hidden from its tile is left off the page, and the row at the foot names everything
    // hidden so nothing is stranded. The page is what watches the store: hiding happens on a tile, and
    // only a read here recomposes the shelves that follow it.
    val hiddenStations by DesktopRadioVisibility.hidden.collectAsState()
    val rows = remember(shelves, hiddenStations) {
        (shelves as? UiState.Success)?.let { DesktopRadioClient.visible(it.data) }.orEmpty()
    }
    AppleScrollbarBox(listState, Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().background(DesktopPageGround()),
            contentPadding = contentPadding,
        ) {
            item { AppleBrowsePageTitle(DesktopStrings["d_radio", "Radio"]) }
            when (shelves) {
                UiState.Loading -> item { AppleBrowseShelfSkeleton() }
                is UiState.Error -> item { AppleBrowseError(shelves.message, onRetry) }
                is UiState.Success -> {
                    // The stations start straight under the page's own name. The shelf Apple labels
                    // this feed with is a banner, not a heading, and the page already says Radio.
                    items(rows) { shelf ->
                        DesktopAppleShelf(shelf, onItemClick, onItemMenu, playingRadioKey, showHeading = false)
                    }
                    if (rows.isEmpty()) item {
                        AppleBrowseEmpty(
                            if (hiddenStations.isEmpty()) "No radio content to show yet."
                            else DesktopStrings["d_radio_all_hidden", "Every station on this page is hidden."],
                        )
                    }
                    if (hiddenStations.isNotEmpty()) item {
                        Box(Modifier.padding(horizontal = DesktopPageGutter + 8.dp, vertical = 18.dp)) {
                            DesktopHiddenRestoreRow(
                                hidden = hiddenStations,
                                onShow = DesktopRadioVisibility::show,
                                onShowAll = DesktopRadioVisibility::showAll,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun AppleBrowsePageTitle(title: String, trailing: @Composable () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(horizontal = DesktopPageGutter)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, top = 24.dp, bottom = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(painterResource(Res.drawable.logo_mark), null, Modifier.size(30.dp, 23.dp))
            Text(
                title,
                color = DesktopAppleText,
                fontSize = 34.sp,
                lineHeight = 41.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 12.dp).weight(1f),
            )
            trailing()
        }
        HorizontalDivider(thickness = 0.5.dp, color = Color.White.copy(alpha = 0.1f))
    }
}

/**
 * The reference site's shelf measures, read off its stylesheet.
 *
 * Its rows come in six shapes — four rows of songs, or one row of squares,
 * circles, videos or editorial stills — and on a wide page each shape fits a
 * fixed number of columns across the content. The numbers below are its own;
 * [APPLE_FEED_SIDEBAR] puts this window's content back on the site's viewport
 * scale, since the site's breakpoints include its navigation column and this
 * page is already past the sidebar when it measures itself.
 */
private const val APPLE_FEED_SIDEBAR = 260f

private val AppleShelfGap = 20.dp

internal enum class AppleFeedLayout { Tracks, Square, Circle, Video, Editorial, Horizontal, Portrait }

/**
 * The site's shelf shape for a source shelf, when the source names one: Apple's `itemKind` maps
 * one-for-one onto the site's lockups. Null for a source that doesn't say, which leaves the
 * caller's own reading of the items in charge.
 *
 * Portrait is not an Apple kind: a feed that is not Apple's has no "Top Picks" lockup, so the
 * page decides which of its own cover rows wears one. See `AppleFeedLayout.Portrait`.
 */
internal fun HomeShelf.feedLayout(circles: Boolean): AppleFeedLayout? = when (layout) {
    "trackLockup" -> AppleFeedLayout.Tracks
    "verticalVideoLockup" -> AppleFeedLayout.Video
    "horizontalLockup" -> AppleFeedLayout.Horizontal
    "flowcaseLockup" -> AppleFeedLayout.Editorial
    "squareLockup" -> if (circles) AppleFeedLayout.Circle else AppleFeedLayout.Square
    else -> null
}

private val AppleFeedLayout.xsWidth: Dp
    get() = when (this) {
        AppleFeedLayout.Tracks -> 270.dp
        AppleFeedLayout.Square -> 144.dp
        AppleFeedLayout.Circle -> 118.dp
        AppleFeedLayout.Video -> 200.dp
        AppleFeedLayout.Editorial -> 298.dp
        AppleFeedLayout.Horizontal -> 298.dp
        AppleFeedLayout.Portrait -> 262.dp
    }

/** How many columns of [layout] the site fits across at this content width. */
private fun appleFeedColumns(layout: AppleFeedLayout, contentWidth: Dp): Int {
    val viewport = contentWidth.value + APPLE_FEED_SIDEBAR
    return when (layout) {
        AppleFeedLayout.Tracks -> if (viewport < 1260f) 2 else if (viewport < 1580f) 3 else 4
        AppleFeedLayout.Square -> if (viewport < 1260f) 4 else if (viewport < 1580f) 5 else 6
        AppleFeedLayout.Circle -> if (viewport < 1260f) 6 else if (viewport < 1580f) 8 else 10
        AppleFeedLayout.Video -> if (viewport < 1260f) 3 else if (viewport < 1580f) 4 else 5
        AppleFeedLayout.Editorial -> if (viewport < 1580f) 2 else 3
        AppleFeedLayout.Horizontal -> if (viewport < 1260f) 2 else if (viewport < 1580f) 3 else 4
        AppleFeedLayout.Portrait -> if (viewport < 1260f) 2 else if (viewport < 1580f) 3 else 4
    }
}

/** A column's width: fixed on a narrow page, and exactly a share of the content on a wide one. */
private fun appleFeedTileWidth(layout: AppleFeedLayout, contentWidth: Dp, columns: Int): Dp {
    if (contentWidth < 1024.dp) return layout.xsWidth
    val available = contentWidth - DesktopPageGutter * 2
    return ((available - AppleShelfGap * (columns - 1)) / columns).coerceAtLeast(layout.xsWidth)
}

/**
 * A shelf's name in the site's type: 17/22 bold. A label, not a control — the site's headings
 * name the row and do nothing else, so there is no chevron and nothing to click.
 */
@Composable
internal fun AppleFeedShelfHeading(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        title,
        color = DesktopAppleText,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier.fillMaxWidth().padding(bottom = 14.dp),
    )
}

/**
 * The site's run-of-songs shelf: four rows down, columns measured to the page,
 * scrolled sideways when there are more songs than fit. The row itself is a
 * plain [AppleSongLine]; the menu is the right-click, as the site's own rows
 * are opened from their surrounding list.
 */
@Composable
internal fun AppleFeedTracksShelf(
    title: String,
    lines: List<AppleSongLine>,
    onPlay: (Int) -> Unit,
    onOpenMenu: ((Int) -> Unit)?,
    showHeading: Boolean = true,
    topPadding: Dp = 36.dp,
    /** The source's own depth for this run of songs; four rows when it doesn't say. */
    rows: Int = 4,
) {
    if (lines.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(top = topPadding)) {
        if (showHeading) {
            AppleFeedShelfHeading(
                title,
                Modifier.padding(horizontal = DesktopPageGutter),
            )
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = appleFeedColumns(AppleFeedLayout.Tracks, maxWidth)
            val width = appleFeedTileWidth(AppleFeedLayout.Tracks, maxWidth, columns)
            val chunks = lines.indices.chunked(rows.coerceIn(1, 8))
            DesktopScrollableRow(gutter = DesktopPageGutter, spacing = AppleShelfGap) {
                items(chunks) { chunk ->
                    Column(Modifier.width(width)) {
                        chunk.forEach { index ->
                            AppleFeedTrackRow(
                                lines[index],
                                onClick = { onPlay(index) },
                                onMenu = onOpenMenu?.let { menu -> { menu(index) } },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** One 55dp song line of a site tracks shelf: 40dp sleeve, 13/16 title, 12/15 credit. */
@Composable
private fun AppleFeedTrackRow(
    line: AppleSongLine,
    onClick: () -> Unit,
    onMenu: (() -> Unit)?,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        Modifier.fillMaxWidth().height(55.dp).hoverable(interaction)
            .then(if (onMenu != null) Modifier.contextClick { onMenu() } else Modifier)
            .clickable(onClick = onClick)
            .padding(end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp)) {
            DesktopArtwork(line.artworkUrl, Modifier.size(40.dp).clip(RoundedCornerShape(5.dp)), px = 160)
            if (hovered) {
                Box(
                    Modifier.matchParentSize().clip(RoundedCornerShape(5.dp))
                        .background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(BitChordIcons.TransportPlay, null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }
        Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
            Text(line.title, color = DesktopAppleText, fontSize = 13.sp, lineHeight = 16.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (line.subtitle.isNotBlank()) {
                Text(line.subtitle, color = DesktopAppleSecondary, fontSize = 12.sp, lineHeight = 15.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * The site's cover shelves: one row of square covers, circles or 16:9 stills,
 * each sized to its share of the page and captioned underneath in 12/15.
 */
@Composable
internal fun AppleFeedCoverShelf(
    title: String,
    layout: AppleFeedLayout,
    items: List<ShelfItem>,
    onClick: (ShelfItem) -> Unit,
    onMenu: ((ShelfItem) -> Unit)?,
    showHeading: Boolean = true,
    topPadding: Dp = 36.dp,
    /** The source's own depth for this shelf: one row unless Apple stacks it two or more deep. */
    rows: Int = 1,
) {
    if (items.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(top = topPadding)) {
        if (showHeading) {
            AppleFeedShelfHeading(
                title,
                Modifier.padding(horizontal = DesktopPageGutter),
            )
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = appleFeedColumns(layout, maxWidth)
            val width = appleFeedTileWidth(layout, maxWidth, columns)
            // The horizontal lockup has always been two deep, whatever the source says; every
            // other shelf is as deep as the source lays it out. A portrait card is nearly half
            // again as tall as it is wide, so it is never stacked.
            val depth = when (layout) {
                AppleFeedLayout.Horizontal -> rows.coerceAtLeast(2)
                AppleFeedLayout.Portrait -> 1
                else -> rows.coerceAtLeast(1)
            }
            DesktopScrollableRow(gutter = DesktopPageGutter, spacing = AppleShelfGap) {
                // Columns fill downwards, the way the source's own grid flows.
                items(items.chunked(depth)) { column ->
                    if (depth == 1) {
                        AppleFeedCoverCell(column.first(), layout, width, onClick, onMenu)
                    } else {
                        Column(Modifier.width(width), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                            column.forEach { item -> AppleFeedCoverCell(item, layout, width, onClick, onMenu) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppleFeedCoverCell(
    item: ShelfItem,
    layout: AppleFeedLayout,
    width: Dp,
    onClick: (ShelfItem) -> Unit,
    onMenu: ((ShelfItem) -> Unit)?,
) {
    when (layout) {
        AppleFeedLayout.Circle -> AppleFeedCircle(
            item, width,
            onClick = { onClick(item) },
            onMenu = onMenu?.let { menu -> { menu(item) } },
        )
        // The site's two story cards — a 3:4 pick and a 16:9 editorial still — carry
        // their words inside the art, over a scrim at its foot rather than under it.
        AppleFeedLayout.Portrait -> AppleFeedArtCard(
            item, width,
            aspect = 3f / 4f,
            onClick = { onClick(item) },
            onMenu = onMenu?.let { menu -> { menu(item) } },
        )
        AppleFeedLayout.Editorial -> AppleFeedArtCard(
            item, width,
            aspect = 16f / 9f,
            onClick = { onClick(item) },
            onMenu = onMenu?.let { menu -> { menu(item) } },
        )
        AppleFeedLayout.Horizontal -> AppleFeedHorizontalRow(
            item,
            onClick = { onClick(item) },
            onMenu = onMenu?.let { menu -> { menu(item) } },
        )
        else -> AppleFeedCover(
            item,
            width,
            aspect = if (layout == AppleFeedLayout.Square) 1f else 16f / 9f,
            onClick = { onClick(item) },
            onMenu = onMenu?.let { menu -> { menu(item) } },
        )
    }
}

/**
 * The site's horizontal lockup: a 97dp sleeve with the name at 15/20 and the credit beneath it,
 * both beside the art rather than under it.
 */
@Composable
private fun AppleFeedHorizontalRow(
    item: ShelfItem,
    onClick: () -> Unit,
    onMenu: (() -> Unit)?,
) {
    Row(
        Modifier.fillMaxWidth()
            .clickable(onClick = onClick)
            .then(if (onMenu == null) Modifier else Modifier.contextClick { onMenu() }),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        DesktopArtwork(
            item.thumbnailUrl,
            Modifier.size(97.dp).clip(RoundedCornerShape(5.dp))
                .desktopArtHoverShade().clickable(onClick = onClick),
            px = 400,
        )
        Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
            Text(
                item.title,
                color = DesktopAppleText,
                fontSize = 15.sp,
                lineHeight = 20.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.subtitle.isNotBlank()) {
                Text(
                    item.subtitle,
                    color = DesktopAppleSecondary,
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * The site's story card: the art is the card, and the words sit inside it at the foot over a
 * scrim — the credit as a small kicker, the name in bold white under it. Two sizes wear it, the
 * 3:4 pick on the home page and the 16:9 editorial still.
 */
@Composable
private fun AppleFeedArtCard(
    item: ShelfItem,
    width: Dp,
    aspect: Float,
    onClick: () -> Unit,
    onMenu: (() -> Unit)?,
) {
    Box(
        Modifier.width(width).aspectRatio(aspect).clip(RoundedCornerShape(10.dp))
            .desktopArtHoverShade().clickable(onClick = onClick)
            .then(if (onMenu == null) Modifier else Modifier.contextClick { onMenu() }),
    ) {
        DesktopArtwork(
            item.thumbnailUrl,
            Modifier.matchParentSize(),
            px = if (width > 240.dp) 800 else 500,
        )
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.45f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.82f),
                ),
            ),
        )
        Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            if (item.subtitle.isNotBlank()) {
                Text(
                    item.subtitle,
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                item.title,
                color = Color.White,
                fontSize = 15.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}

@Composable
private fun AppleFeedCover(
    item: ShelfItem,
    width: Dp,
    aspect: Float,
    onClick: () -> Unit,
    onMenu: (() -> Unit)?,
) {
    Column(Modifier.width(width)) {
        DesktopArtwork(
            item.thumbnailUrl,
            Modifier.width(width).aspectRatio(aspect).clip(RoundedCornerShape(7.dp))
                .desktopArtHoverShade().clickable(onClick = onClick)
                .then(if (onMenu == null) Modifier else Modifier.contextClick { onMenu() }),
            px = if (width > 240.dp) 800 else 400,
        )
        Text(
            item.title,
            color = DesktopAppleText,
            fontSize = 12.sp,
            lineHeight = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (item.subtitle.isNotBlank()) {
            Text(
                item.subtitle,
                color = DesktopAppleSecondary,
                fontSize = 12.sp,
                lineHeight = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AppleFeedCircle(
    item: ShelfItem,
    width: Dp,
    onClick: () -> Unit,
    onMenu: (() -> Unit)?,
) {
    Column(Modifier.width(width), horizontalAlignment = Alignment.CenterHorizontally) {
        DesktopArtwork(
            item.thumbnailUrl,
            Modifier.size(width).clip(CircleShape)
                .desktopArtHoverShade().clickable(onClick = onClick)
                .then(if (onMenu == null) Modifier else Modifier.contextClick { onMenu() }),
            px = 400,
        )
        Text(
            item.title,
            color = DesktopAppleText,
            fontSize = 13.sp,
            lineHeight = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        )
    }
}

@Composable
private fun AppleBrowseRecentShelf(
    shelf: HomeShelf,
    onItemClick: (ShelfItem, String?) -> Unit,
    onItemMenu: (ShelfItem) -> Unit,
) {
    // Recently Played is a run of songs on the site, not a row of covers: the
    // same four-row shelf every other run of songs gets.
    AppleFeedTracksShelf(
        title = shelf.title,
        lines = shelf.items.map { AppleSongLine(it.title, it.subtitle, it.thumbnailUrl) },
        onPlay = { index -> onItemClick(shelf.items[index], shelf.title) },
        onOpenMenu = { index -> onItemMenu(shelf.items[index]) },
    )
}

/**
 * An album's or playlist's own page, in Apple's shape: the sleeve large on the left with what
 * moves behind it, the name and credits beside it, and the tracks below as a table read down —
 * Song, Artist, Album, Time — rather than as shelves of covers.
 */
@Composable
internal fun DesktopAppleCollectionPage(
    title: String,
    credit: String?,
    coverUrl: String?,
    /** The release's Apple id, which is what its motion artwork is filed under. */
    appleId: String?,
    state: UiState<List<HomeShelf>>,
    listState: LazyListState,
    contentPadding: PaddingValues,
    animatedCanvas: Boolean,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onItemClick: (ShelfItem, String?) -> Unit,
    onItemMenu: (ShelfItem) -> Unit,
    onRetry: () -> Unit,
    /** The artist name in a track row, which opens their page — as Apple's table does. */
    onArtistClick: ((String) -> Unit)? = null,
    /** The track whose Album cell was picked; the caller resolves the release page. */
    onAlbumClick: ((ShelfItem) -> Unit)? = null,
) {
    val shelves = (state as? UiState.Success)?.data.orEmpty()
    val tracks = shelves.flatMap { it.items }.filter { it.isAppleSong() }
    // A release page still carries shelves that are not its track list — the rest of an album's
    // releases, more by the artist — and those keep the cover-row shape below the table.
    val extras = shelves.map { shelf -> shelf.copy(items = shelf.items.filterNot { it.isAppleSong() }) }
        .filter { it.items.isNotEmpty() }
    val tint = rememberDesktopArtworkPalette(coverUrl).secondary
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().background(DesktopPageGround()),
        contentPadding = contentPadding,
    ) {
        item(key = "release-header") {
            // Apple tints the top of a release page with its own cover; the wash is the average of
            // that cover, laid over the page ground and gone by the first row.
            Box(
                Modifier.fillMaxWidth().background(
                    Brush.verticalGradient(
                        listOf(tint.copy(alpha = 0.5f), tint.copy(alpha = 0f)),
                    ),
                ),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = DesktopPageGutter).padding(top = 28.dp, bottom = 30.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppleReleaseCover(title, credit, coverUrl, appleId, tracks.firstOrNull()?.subtitle, animatedCanvas)
                    Spacer(Modifier.width(30.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            title,
                            color = DesktopAppleText,
                            fontSize = 32.sp,
                            lineHeight = 38.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!credit.isNullOrBlank()) {
                            Text(
                                credit,
                                color = DesktopAppleText,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                        if (tracks.isNotEmpty()) {
                            Text(
                                "${tracks.size} SONGS",
                                color = DesktopAppleSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 0.6.sp,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(top = 20.dp),
                        ) {
                            AppleReleasePill("Play", BitChordIcons.TransportPlay, filled = true, onClick = onPlay)
                            AppleReleasePill("Shuffle", BitChordIcons.TransportShuffle, filled = false, onClick = onShuffle)
                        }
                    }
                }
            }
        }
        when (state) {
            UiState.Loading -> items(8) { AppleBrowseTrackRowSkeleton() }
            is UiState.Error -> item { AppleBrowseError(state.message, onRetry) }
            is UiState.Success -> {
                item(key = "table-head") { AppleTrackTableHead() }
                items(tracks, key = { it.browseId.orEmpty() + "|" + it.title }) { track ->
                    AppleTrackTableRow(track, title, onItemClick, onItemMenu, onArtistClick, onAlbumClick)
                }
                if (tracks.isEmpty()) item { AppleBrowseEmpty("No music to show yet.") }
                extras.forEachIndexed { index, shelf ->
                    item(key = "release-extra:$index") { DesktopAppleShelf(shelf, onItemClick, onItemMenu) }
                }
            }
        }
    }
}

@Composable
private fun AppleReleaseCover(
    title: String,
    credit: String?,
    coverUrl: String?,
    appleId: String?,
    firstArtist: String?,
    animatedCanvas: Boolean,
) {
    // The clip belongs to the release, not to whichever track happens to be first on it. The credit
    // under the title is the curator of a playlist rather than a recording artist, so the id is
    // what the clip is actually filed under and the names only back it up.
    val canvas by produceState<DesktopCanvasArtwork?>(null, title, credit, appleId, animatedCanvas) {
        value = if (!animatedCanvas) {
            null
        } else {
            withContext(Dispatchers.IO) {
                DesktopCanvasClient.lookupAlbum(
                    title,
                    credit.orEmpty().ifBlank { firstArtist.orEmpty() },
                    appleId,
                )
            }
        }
    }
    Box(Modifier.size(290.dp).clip(RoundedCornerShape(10.dp))) {
        DesktopArtwork(coverUrl, Modifier.fillMaxSize(), px = 600)
        canvas?.let {
            DesktopCanvasView(
                it.url,
                Modifier.fillMaxSize(),
                fallbackUrl = it.fallbackUrl,
                publishBackdrop = false,
            )
        }
    }
}

@Composable
private fun AppleReleasePill(label: String, icon: ImageVector, filled: Boolean, onClick: () -> Unit) {
    val ink = if (filled) Color.Black else Color.White
    Row(
        Modifier.clip(RoundedCornerShape(50.dp)).clickable(onClick = onClick)
            .background(if (filled) Color.White else Color.White.copy(alpha = 0.14f))
            .padding(horizontal = 20.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, label, tint = ink, modifier = Modifier.size(18.dp))
        Text(
            label,
            color = ink,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun AppleTrackTableHead() {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = DesktopPageGutter).height(32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppleTrackHeading("Song", 36.5f, Modifier.padding(start = 7.dp))
        AppleTrackHeading("Artist", 25.25f, Modifier.padding(end = 10.dp))
        AppleTrackHeading("Album", 25.25f, Modifier.padding(end = 16.dp))
        AppleTrackHeading("Time", 13f, Modifier.fillMaxWidth().padding(end = 18.dp), TextAlign.End)
    }
}

@Composable
private fun RowScope.AppleTrackHeading(
    label: String,
    /** An extension on [RowScope] because the column is a share of the row's width. */
    share: Float,
    modifier: Modifier = Modifier,
    align: TextAlign = TextAlign.Start,
) {
    Text(
        label,
        color = DesktopAppleSecondary,
        fontSize = 12.sp,
        lineHeight = 15.sp,
        fontWeight = FontWeight.W600,
        maxLines = 1,
        textAlign = align,
        modifier = Modifier.weight(share).then(modifier),
    )
}

/**
 * One row of a release's track table, in Apple's four columns.
 *
 * The web list answers the pointer in two places at once: a play triangle comes up over the cover,
 * and the length in the last column is replaced by a ⋯ that opens the same menu a right-click
 * does. Both are driven by the one hover value so they arrive together. The row itself keeps its
 * ground — only the name under the pointer underlines, the way a link does.
 */
@Composable
private fun AppleTrackTableRow(
    track: ShelfItem,
    shelfTitle: String,
    onItemClick: (ShelfItem, String?) -> Unit,
    onItemMenu: (ShelfItem) -> Unit,
    onArtistClick: ((String) -> Unit)? = null,
    onAlbumClick: ((ShelfItem) -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val reveal by animateFloatAsState(
        targetValue = if (hovered) 1f else 0f,
        animationSpec = tween(TRACK_REVEAL_MS),
        label = "trackReveal",
    )
    val detail = DesktopAppleBrowseClient.trackDetail(track)
    val menu = { onItemMenu(track) }
    val artistName = track.subtitle
    val albumName = detail?.album.orEmpty()
    Column(Modifier.padding(horizontal = DesktopPageGutter)) {
        // The hairline above a row, which goes when the pointer is on it, so a hovered row reads as
        // one block rather than two halves.
        Box(
            Modifier.fillMaxWidth().height(0.5.dp).background(Color.White.copy(alpha = 0.12f * (1f - reveal))),
        )
        Row(
            Modifier.fillMaxWidth().height(TRACK_ROW_HEIGHT).hoverable(interaction)
                .longPressMenuClickable(onClick = { onItemClick(track, shelfTitle) }, onLongClick = menu)
                .contextClick(menu),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.weight(36.5f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.padding(start = 7.dp).size(40.dp).clip(RoundedCornerShape(5.dp))) {
                    DesktopArtwork(track.thumbnailUrl, Modifier.fillMaxSize(), px = 160)
                    if (reveal > 0.01f) {
                        Box(
                            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f * reveal)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                BitChordIcons.TransportPlay,
                                "Play",
                                tint = Color.White.copy(alpha = reveal),
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
                Text(
                    track.title,
                    color = DesktopAppleText,
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                )
            }
            // The Artist and Album cells are links, as they are on Apple's own table: the inner
            // clickable takes the press, so the row's own play action is left alone.
            AppleTableLink(
                artistName,
                canOpen = onArtistClick != null && artistName.isNotBlank(),
                onClick = { onArtistClick?.invoke(artistName) },
                modifier = Modifier.weight(25.25f).padding(end = 10.dp),
            )
            AppleTableLink(
                albumName,
                canOpen = onAlbumClick != null && albumName.isNotBlank(),
                onClick = { onAlbumClick?.invoke(track) },
                modifier = Modifier.weight(25.25f).padding(end = 16.dp),
            )
            Box(Modifier.weight(13f).fillMaxHeight()) {
                Text(
                    appleTrackTime(detail?.durationMs),
                    color = DesktopAppleSecondary,
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    style = TextStyle(fontFeatureSettings = TABULAR_FIGURES),
                    maxLines = 1,
                    textAlign = TextAlign.End,
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxWidth().padding(end = 18.dp)
                        .alpha(1f - reveal),
                )
                if (reveal > 0.01f) {
                    Box(
                        Modifier.align(Alignment.CenterEnd).padding(end = 10.dp).size(28.dp)
                            .clip(CircleShape).background(Color.White.copy(alpha = 0.07f * reveal))
                            .clickable(onClickLabel = "Song menu", onClick = menu),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            BitChordIcons.Ellipsis,
                            "Song menu",
                            tint = DesktopAccent.copy(alpha = reveal),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * A link in the track table: under the pointer it lightens to white and underlines, the way
 * Apple's own table answers a name — and the row itself keeps its ground. It takes the press
 * itself, so a click here opens the artist or release rather than playing the row.
 */
@Composable
private fun AppleTableLink(
    text: String,
    canOpen: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val lit = hovered && canOpen
    Text(
        text,
        color = if (lit) DesktopAppleText else DesktopAppleSecondary,
        fontSize = 12.sp,
        lineHeight = 15.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textDecoration = if (lit) TextDecoration.Underline else null,
        modifier = if (canOpen) {
            modifier.hoverable(interaction)
                .clickable(interaction, indication = null, onClick = onClick)
        } else {
            modifier
        },
    )
}

/** Apple keeps the length of a track in milliseconds and shows it as `3:28`. */
private fun appleTrackTime(durationMs: Long?): String {
    val seconds = durationMs?.let { it / 1000L } ?: return ""
    return "${seconds / 60}:${"%02d".format(seconds % 60)}"
}

private const val TRACK_REVEAL_MS = 140
private val TRACK_ROW_HEIGHT = 54.dp
private const val TABULAR_FIGURES = "tnum"

@Composable
private fun AppleBrowseTrackRowSkeleton() {
    Row(
        Modifier.fillMaxWidth().height(TRACK_ROW_HEIGHT).padding(horizontal = DesktopPageGutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(36.5f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ShimmerBox(Modifier.padding(start = 7.dp).size(40.dp).clip(RoundedCornerShape(5.dp)))
            ShimmerBox(Modifier.weight(1f).height(12.dp))
        }
        ShimmerBox(Modifier.weight(25.25f).height(12.dp))
        ShimmerBox(Modifier.weight(25.25f).height(12.dp))
        ShimmerBox(Modifier.weight(13f).height(12.dp))
    }
}

@Composable
private fun AppleBrowseStations(seeds: List<Song>, onStartStation: (Song) -> Unit) {
    Column(Modifier.padding(top = 20.dp, bottom = 6.dp)) {
        DesktopAppleHeading("Stations for You", Modifier.padding(horizontal = DesktopPageGutter, vertical = 12.dp))
        DesktopScrollableRow(spacing = 20.dp) {
            items(seeds, key = { it.videoId }) { song ->
                Column(
                    Modifier.width(172.dp).desktopQuietClick { onStartStation(song) },
                ) {
                    DesktopArtwork(
                        song.thumbnailUrl,
                        Modifier.size(172.dp).clip(RoundedCornerShape(8.dp)).desktopArtHoverShade(),
                        px = 400,
                    )
                    AppleBrowseCaptions("${song.artist} Station", song.title)
                }
            }
        }
    }
}

@Composable
private fun AppleBrowseCaptions(title: String, subtitle: String) {
    Text(title, color = DesktopAppleText, fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Normal,
        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
    if (subtitle.isNotBlank()) {
        Text(subtitle, color = DesktopAppleSecondary, fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Normal,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun AppleBrowseCategoryRow(row: List<MoodGenre>, columns: Int, onCategoryClick: (MoodGenre) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = DesktopPageGutter).padding(bottom = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        row.forEach { category ->
            val tone = category.stripeColor?.let { Color(it.toInt()) } ?: appleBrowseCategoryColor(category.title)
            // These cards are a third of whatever the page is, so a fixed artwork
            // size is wrong on a wide window: the charts tile came out softer than
            // the page it opens. Measured, the cover asked for is at least as big
            // as the box it is drawn in, rounded up so resizing the window does
            // not fetch a new one every pixel.
            var artPx by remember { mutableStateOf(0) }
            Box(
                Modifier
                    .weight(1f)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(tone)
                    .clickable { onCategoryClick(category) }
                    .onSizeChanged { artPx = ((it.width + 99) / 100) * 100 },
            ) {
                if (!category.thumbnailUrl.isNullOrBlank() && artPx > 0) {
                    DesktopArtwork(category.thumbnailUrl, Modifier.fillMaxSize(), px = artPx)
                }
                Box(
                    Modifier.matchParentSize().background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))),
                    ).desktopArtHoverShade(),
                )
                Text(
                    category.title,
                    color = Color.White,
                    fontSize = 15.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.BottomStart).padding(14.dp),
                )
            }
        }
        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
    }
}

@Composable
private fun AppleBrowseShelfSkeleton(title: String? = null, landscape: Boolean = false) {
    Column(Modifier.padding(top = 24.dp, bottom = 6.dp)) {
        if (title != null) {
            DesktopAppleHeading(title, Modifier.padding(horizontal = DesktopPageGutter, vertical = 12.dp))
        } else {
            ShimmerBox(Modifier.padding(start = DesktopPageGutter, bottom = 16.dp).width(160.dp).height(18.dp))
        }
        DesktopScrollableRow(spacing = 20.dp) {
            items(if (landscape) 3 else 6) {
                Column(Modifier.width(if (landscape) 457.dp else 172.dp)) {
                    ShimmerBox(
                        Modifier.fillMaxWidth().height(if (landscape) 261.dp else 172.dp),
                        RoundedCornerShape(8.dp),
                    )
                    ShimmerBox(Modifier.padding(top = 9.dp).width(132.dp).height(12.dp))
                    ShimmerBox(Modifier.padding(top = 5.dp).width(92.dp).height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun AppleBrowseCategorySkeleton(columns: Int) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = DesktopPageGutter).padding(bottom = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        repeat(columns) {
            ShimmerBox(Modifier.weight(1f).aspectRatio(16f / 9f), RoundedCornerShape(10.dp))
        }
    }
}

@Composable
private fun AppleBrowseError(message: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = DesktopPageGutter, vertical = 28.dp)) {
        Text(message, color = DesktopAppleSecondary, fontSize = 13.sp)
        TextButton(onClick = onRetry, modifier = Modifier.padding(top = 8.dp)) {
            Text(DesktopStrings["retry", "Retry"], color = DesktopAppleText, fontSize = 13.sp)
        }
    }
}

@Composable
private fun AppleBrowseEmpty(message: String) {
    Text(message, color = DesktopAppleSecondary, fontSize = 13.sp,
        modifier = Modifier.padding(horizontal = DesktopPageGutter, vertical = 28.dp))
}

private fun String.isAppleBrowseRecent(): Boolean = trim().lowercase(Locale.ROOT) in setOf(
    "recents", "recently played", "recentlyplayed", "gần đây", "最近",
)

/**
 * Feed rows Apple's own home has no equivalent of, so the page drops them rather
 * than carrying the backend's taxonomy onto a screen that is meant to read as
 * Apple Music.
 */
private fun String.isAppleBrowseDropped(): Boolean {
    val value = trim().lowercase(Locale.ROOT)
    return value.contains("fresh finds") || value.contains("old favourite") || value.contains("old favorite")
}

/**
 * Shelf labels that say nothing the screen has not already said: the backend's generic
 * "Albums & singles" (plural or not, "new" or not), and a "New" that only repeats the
 * page's own title. The rows stay; only the lines naming them go.
 */
internal fun String.isQuietShelfTitle(): Boolean {
    val value = trim().lowercase(Locale.ROOT).replace(" ", "")
    return value == "new" || value == "albums&singles" ||
        value == "newalbum&singles" || value == "newalbums&singles"
}

private fun appleBrowseCategoryColor(title: String): Color = when ((title.hashCode() and Int.MAX_VALUE) % 6) {
    0 -> Color(0xFF96576D)
    1 -> Color(0xFF476D93)
    2 -> Color(0xFF7D619E)
    3 -> Color(0xFF887044)
    4 -> Color(0xFF4F7C75)
    else -> Color(0xFF9C584E)
}
