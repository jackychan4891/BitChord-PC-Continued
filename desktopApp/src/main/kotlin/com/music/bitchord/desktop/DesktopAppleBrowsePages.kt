package com.music.bitchord.desktop

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
    /** The station on air in the radio player, so its tile on the New page can be marked. */
    playingRadioKey: String? = null,
    /** A page a heading's chevron leads to: its rows are laid out as grids down the page. */
    gridded: Boolean = false,
) {
    val isNew = title.equals("New", ignoreCase = true)
    // Stations show only where Apple puts them — a row in the New feed — and only the ones this
    // player can start. Hiding happens on a tile, so the page is what watches the store: only a
    // read here recomposes the rows that follow it.
    val hiddenStations by DesktopRadioVisibility.hidden.collectAsState()
    val shelves = remember(state, hiddenStations) {
        (state as? UiState.Success)?.data.orEmpty().mapNotNull { shelf ->
            if (shelf.isAppleRadioShelf()) DesktopRadioClient.playableStations(shelf) else shelf
        }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().background(DesktopPageGround()),
        contentPadding = contentPadding,
    ) {
        // The shelves below open with their own gap, so the title leaves it to them rather than
        // stacking a second one on top and pushing the page's content down.
        item { AppleBrowsePageTitle(title, bottomPadding = 0.dp) }
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
                    appleBrowseNewShelves(shelves, onItemClick, onItemMenu, playingRadioKey, gridded)
                } else {
                    appleBrowseHomeShelves(shelves, onItemClick, onItemMenu, gridded)
                }
                if (shelves.isEmpty() && !recentlyPlayedLoading) {
                    item { AppleBrowseEmpty("No music to show yet.") }
                }
                if (isNew && hiddenStations.isNotEmpty()) item {
                    Box(Modifier.padding(horizontal = DesktopPageGutter + 8.dp, vertical = 18.dp)) {
                        DesktopHiddenRestoreRow(
                            hidden = hiddenStations,
                            onShow = DesktopRadioVisibility::show,
                            onShowAll = DesktopRadioVisibility::showAll,
                        )
                    }
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
    gridded: Boolean = false,
) {
    // The reference site's home is one running order: the picks built for this listener as the tall
    // row, Recently Played next, then the moods, then the feed's own shelves exactly as it delivered
    // them. No ranking, no editorial shelf lifted out of the middle.
    val picks = shelves.filter { it.isApplePicksShelf() }
    val recents = shelves.filter { it.title.isAppleBrowseRecent() }
    val moods = shelves.filter { it.isAppleMoodsShelf() }
    val rest = shelves.filterNot {
        it.isApplePicksShelf() || it.isAppleMoodsShelf() ||
            it.title.isAppleBrowseRecent() || it.title.isAppleBrowseDropped()
    }
    if (!gridded) {
        picks.forEach { shelf ->
            item(key = "home:picks:${shelf.title}") {
                DesktopAppleShelf(shelf, onItemClick, onItemMenu, portrait = true)
            }
        }
        recents.forEach { shelf ->
            item(key = "home:recent:${shelf.title}") {
                AppleBrowseRecentShelf(shelf, onItemClick, onItemMenu)
            }
        }
        // A mood plays instead of opening, and a tile that plays has nothing to put in a menu.
        moods.forEach { shelf ->
            item(key = "home:moods:${shelf.title}") {
                DesktopAppleShelf(shelf, onItemClick, null)
            }
        }
    }
    // Apple's home opens its cover rows in the site's tall picks; a feed that is not Apple's names
    // no such lockup, so its own first cover row takes that place — runs of songs, artist circles
    // and video rows are not picks and keep the shape they already have. The rows built for this
    // listener are exempt: one already wears the shape, and the other is a cover row of its own.
    val portraitAt = rest.withIndex().firstOrNull { (_, shelf) -> shelf.wearsPortrait() }?.index
    if (gridded) {
        (picks + moods).forEach { shelf ->
            item(key = "home:shelf:${shelf.title}") {
                DesktopAppleShelf(
                    shelf, onItemClick,
                    if (shelf.isAppleMoodsShelf()) null else onItemMenu,
                    gridded = true,
                )
            }
        }
    }
    rest.forEachIndexed { index, shelf ->
        item(key = "home:$index") {
            DesktopAppleShelf(
                shelf, onItemClick, onItemMenu,
                portrait = !gridded && index == portraitAt,
                gridded = gridded,
            )
        }
    }
}

/** The row built for this listener, drawn as the site's tall picks. */
internal fun HomeShelf.isApplePicksShelf(): Boolean = layout == DesktopApplePersonalFeed.PICKS_LAYOUT

/** The row of Apple's mood playlists. */
internal fun HomeShelf.isAppleMoodsShelf(): Boolean = layout == DesktopApplePersonalFeed.MOODS_LAYOUT

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
    playingRadioKey: String?,
    gridded: Boolean = false,
) {
    // New is Home without the recents: the feed's own order, one row per shelf — including the
    // stations, which Apple lays in the running order here rather than on a page of their own.
    source.forEachIndexed { index, shelf ->
        if (shelf.title.isAppleBrowseRecent() || shelf.title.isAppleBrowseDropped()) {
            return@forEachIndexed
        }
        item(key = "new:$index") {
            DesktopAppleShelf(shelf, onItemClick, onItemMenu, playingRadioKey, gridded = gridded)
        }
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
internal fun AppleBrowsePageTitle(
    title: String,
    /** The gap under the title, left to the content when that content opens with its own. */
    bottomPadding: Dp = 18.dp,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        // On the same left edge as the rows below.
        Modifier.fillMaxWidth().padding(horizontal = DesktopPageGutter)
            .padding(top = 32.dp, bottom = bottomPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            color = DesktopAppleText,
            fontSize = 34.sp,
            lineHeight = 41.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        trailing()
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

internal val AppleShelfGap = 20.dp

internal enum class AppleFeedLayout { Tracks, Square, Circle, Video, Editorial, Horizontal, Portrait }

/**
 * The site's shelf shape for a source shelf, when the source names one: Apple's `itemKind` maps
 * one-for-one onto the site's lockups. Null for a source that doesn't say, which leaves the
 * caller's own reading of the items in charge.
 *
 * Portrait is not an Apple kind from the New feed: a feed has to be told which of its rows is the
 * pick row, either by the page's own reading of its cover rows or by a row built for the purpose —
 * see [DesktopApplePersonalFeed.PICKS_LAYOUT].
 */
internal fun HomeShelf.feedLayout(circles: Boolean): AppleFeedLayout? = when (layout) {
    "trackLockup" -> AppleFeedLayout.Tracks
    "verticalVideoLockup" -> AppleFeedLayout.Video
    "horizontalLockup" -> AppleFeedLayout.Horizontal
    "flowcaseLockup" -> AppleFeedLayout.Editorial
    "portraitLockup" -> AppleFeedLayout.Portrait
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
internal fun appleFeedColumns(layout: AppleFeedLayout, contentWidth: Dp): Int {
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
internal fun appleFeedTileWidth(layout: AppleFeedLayout, contentWidth: Dp, columns: Int): Dp {
    if (contentWidth < 1024.dp) return layout.xsWidth
    val available = contentWidth - DesktopPageGutter * 2
    return ((available - AppleShelfGap * (columns - 1)) / columns).coerceAtLeast(layout.xsWidth)
}

/**
 * A shelf's name in the site's type: 17/22 bold, with its chevron when the row leads anywhere.
 *
 * The site's headings are links: the name carries a › and opens the page that lists the row out in
 * full. A shelf the source gives no page keeps the bare name, which is then only a label.
 */
@Composable
internal fun AppleFeedShelfHeading(
    title: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    if (onClick == null) {
        Text(
            title,
            color = DesktopAppleText,
            fontSize = 17.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = modifier.fillMaxWidth().padding(bottom = 14.dp),
        )
        return
    }
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        modifier.padding(bottom = 14.dp)
            .hoverable(interaction)
            .clickable(interaction, indication = null, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            color = DesktopAppleText,
            fontSize = 17.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Icon(
            BitChordIcons.ChevronRight,
            null,
            tint = if (hovered) DesktopAppleText else DesktopAppleSecondary,
            modifier = Modifier.padding(start = 5.dp).size(15.dp),
        )
    }
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
    /** Where the row's own page is, when the source names one; null leaves the name a label. */
    onOpenHeading: (() -> Unit)? = null,
) {
    if (lines.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(top = topPadding)) {
        if (showHeading) {
            AppleFeedShelfHeading(
                title,
                Modifier.padding(horizontal = DesktopPageGutter),
                onOpenHeading,
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
    /** Where the row's own page is, when the source names one; null leaves the name a label. */
    onOpenHeading: (() -> Unit)? = null,
) {
    if (items.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(top = topPadding)) {
        if (showHeading) {
            AppleFeedShelfHeading(
                title,
                Modifier.padding(horizontal = DesktopPageGutter),
                onOpenHeading,
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

/**
 * A page of covers laid out as the site lays out a page of covers: a grid that runs down the
 * page rather than a row that runs off its side. Only a page opened from a shelf's chevron wears
 * it — elsewhere the same covers are a shelf, and a shelf is one row.
 */
@Composable
internal fun AppleFeedCoverGrid(
    title: String,
    layout: AppleFeedLayout,
    items: List<ShelfItem>,
    onClick: (ShelfItem) -> Unit,
    onMenu: ((ShelfItem) -> Unit)?,
    showHeading: Boolean = true,
    topPadding: Dp = 36.dp,
    onOpenHeading: (() -> Unit)? = null,
) {
    if (items.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(top = topPadding)) {
        if (showHeading) {
            AppleFeedShelfHeading(
                title,
                Modifier.padding(horizontal = DesktopPageGutter),
                onOpenHeading,
            )
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = appleFeedColumns(layout, maxWidth)
            val width = appleFeedTileWidth(layout, maxWidth, columns)
            Column(
                Modifier.fillMaxWidth().padding(horizontal = DesktopPageGutter),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                items.chunked(columns).forEach { line ->
                    Row(horizontalArrangement = Arrangement.spacedBy(AppleShelfGap)) {
                        line.forEach { item -> AppleFeedCoverCell(item, layout, width, onClick, onMenu) }
                    }
                }
            }
        }
    }
}

/**
 * The same page of covers as [AppleFeedCoverGrid], built from lazily composed cells instead of a
 * column of rows: the site's measured square columns, captioned underneath, for a page that can
 * run long — a history, say — where every tile must not be built at once.
 */
@Composable
internal fun AppleFeedLazyCoverGrid(
    items: List<ShelfItem>,
    gridState: LazyGridState,
    contentPadding: PaddingValues,
    onClick: (ShelfItem) -> Unit,
    onMenu: ((ShelfItem) -> Unit)?,
) {
    if (items.isEmpty()) return
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns = appleFeedColumns(AppleFeedLayout.Square, maxWidth)
        val width = appleFeedTileWidth(AppleFeedLayout.Square, maxWidth, columns)
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(AppleShelfGap),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.fillMaxSize().padding(horizontal = DesktopPageGutter),
        ) {
            itemsIndexed(
                items,
                key = { index, item -> "${item.videoId ?: item.browseId ?: item.title}#$index" },
            ) { _, item ->
                AppleFeedCoverCell(item, AppleFeedLayout.Square, width, onClick, onMenu)
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
        if (item.isAppleMoodTile()) {
            AppleFeedMoodArt(item, onClick, onMenu)
        } else {
            DesktopArtwork(
                item.thumbnailUrl,
                Modifier.width(width).aspectRatio(aspect).clip(RoundedCornerShape(7.dp))
                    .desktopArtHoverShade().clickable(onClick = onClick)
                    .then(if (onMenu == null) Modifier else Modifier.contextClick { onMenu() }),
                px = if (width > 240.dp) 800 else 400,
            )
        }
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

/**
 * A mood station's cover, which moves.
 *
 * Only under the pointer — Apple animates one of these at a time too, and here it is a matter of
 * cost as much as taste: every clip is an HLS loop decoded in software, so a row of seven playing
 * side by side is seven decoders running for a page nobody is watching closely.
 */
@Composable
private fun AppleFeedMoodArt(item: ShelfItem, onClick: () -> Unit, onMenu: (() -> Unit)?) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val animated by DesktopPlayerSettings.animatedCanvas.collectAsState()
    val clip = if (animated) DesktopApplePersonalFeed.motionFor(item) else null
    Box(
        Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(7.dp))
            .hoverable(interaction)
            .clickable(interaction, null, onClick = onClick)
            .desktopArtShade(hovered)
            .then(if (onMenu == null) Modifier else Modifier.contextClick { onMenu() }),
    ) {
        DesktopArtwork(item.thumbnailUrl, Modifier.matchParentSize(), px = 400)
        if (hovered && clip != null) {
            DesktopCanvasView(clip, Modifier.matchParentSize(), publishBackdrop = false)
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
 * Song, Artist, Album, Time. A release page is the album its rows sit on, so its list leaves that
 * column out; a playlist's rows come from all over and keep it.
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
    /** The album in a track row that named no release of its own, found from its name. */
    onAlbumByName: ((String, String) -> Unit)? = null,
    /**
     * Whether the rows get an Album column. A release page is the album its rows sit on; a
     * playlist's rows come from all over, which is when the site shows where each one is from.
     */
    showsAlbum: Boolean = false,
) {
    val shelves = (state as? UiState.Success)?.data.orEmpty()
    val tracks = shelves.flatMap { it.items }.filter { it.isAppleSong() }
    // A release page still carries shelves that are not its track list — the rest of an album's
    // releases, more by the artist — and those keep the cover-row shape below the table.
    val extras = shelves.map { shelf -> shelf.copy(items = shelf.items.filterNot { it.isAppleSong() }) }
        .filter { it.items.isNotEmpty() }
    val tint = rememberDesktopArtworkTint(coverUrl)
    // The column only stands when the rows can name an album: one of dashes says less than none.
    val albumColumn = showsAlbum && tracks.any { !DesktopAppleBrowseClient.trackDetail(it)?.album.isNullOrBlank() }
    // The length of every row is already kept for the table, and the site adds them up to say how
    // long the release runs for under it.
    val totalMs = tracks.mapNotNull { DesktopAppleBrowseClient.trackDetail(it)?.durationMs }.sum()
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
                // The sleeve and its copy share a baseline: the buttons under the title sit level
                // with the bottom of the cover rather than being spaced from the text above them.
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = DesktopPageGutter).padding(top = 28.dp, bottom = 30.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    AppleReleaseCover(title, credit, coverUrl, appleId, tracks.firstOrNull()?.subtitle, animatedCanvas)
                    Spacer(Modifier.width(50.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            title,
                            color = DesktopAppleText,
                            fontSize = 26.sp,
                            lineHeight = 30.sp,
                            fontWeight = FontWeight.W700,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!credit.isNullOrBlank()) {
                            Text(
                                credit,
                                color = AppleReleaseMeta,
                                fontSize = 12.sp,
                                lineHeight = 15.sp,
                                fontWeight = FontWeight.W600,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 12.dp),
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(top = 32.dp),
                        ) {
                            AppleReleasePlay("Play", BitChordIcons.TransportPlay, onClick = onPlay)
                            AppleReleaseRound("Shuffle", BitChordIcons.TransportShuffle, onClick = onShuffle)
                        }
                    }
                }
            }
        }
        when (state) {
            UiState.Loading -> items(8) { AppleBrowseTrackRowSkeleton() }
            is UiState.Error -> item { AppleBrowseError(state.message, onRetry) }
            is UiState.Success -> {
                item(key = "table-head") { AppleTrackTableHead(tint, albumColumn) }
                items(tracks, key = { it.browseId.orEmpty() + "|" + it.title }) { track ->
                    AppleTrackTableRow(track, title, tint, onItemClick, onItemMenu, onArtistClick, albumColumn, onAlbumByName)
                }
                if (tracks.isEmpty()) item { AppleBrowseEmpty("No music to show yet.") }
                // The count and the running time, in the same colour the artwork gave the table.
                if (tracks.isNotEmpty()) item(key = "release-footer") {
                    Text(
                        appleReleaseFooter(tracks.size, totalMs),
                        color = tint,
                        fontSize = 13.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(horizontal = DesktopPageGutter + 7.dp).padding(top = 20.dp, bottom = 8.dp),
                    )
                }
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
    Box(Modifier.size(238.dp)) {
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

/** The release's own button: a white stadium with black in it, fixed at the site's 130×36. */
@Composable
internal fun AppleReleasePlay(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.size(width = 130.dp, height = 36.dp).clip(CircleShape).background(Color.White)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, label, tint = Color.Black, modifier = Modifier.size(18.dp))
        Text(label, color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.W600, modifier = Modifier.padding(start = 8.dp))
    }
}

/** The round button that follows it, barely lighter than the page it sits on. */
@Composable
internal fun AppleReleaseRound(label: String, icon: ImageVector, onClick: () -> Unit) {
    Box(
        Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.04f))
            .clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = DesktopAppleText, modifier = Modifier.size(17.dp))
    }
}

@Composable
internal fun AppleTrackTableHead(tint: Color, showsAlbum: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = DesktopPageGutter).height(32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppleTrackHeading("Song", if (showsAlbum) 42f else 50f, tint, Modifier.padding(start = 7.dp))
        AppleTrackHeading("Artist", if (showsAlbum) 26f else 32f, tint, Modifier.padding(end = 10.dp))
        if (showsAlbum) AppleTrackHeading("Album", 20f, tint)
        AppleTrackHeading("Time", if (showsAlbum) 12f else 18f, tint, Modifier.fillMaxWidth().padding(end = 18.dp), TextAlign.End)
    }
}

@Composable
private fun RowScope.AppleTrackHeading(
    label: String,
    /** An extension on [RowScope] because the column is a share of the row's width. */
    share: Float,
    tint: Color,
    modifier: Modifier = Modifier,
    align: TextAlign = TextAlign.Start,
) {
    Text(
        label,
        // The site colours a release's whole table from its cover, headings included, rather than
        // leaving them the page's generic gray.
        color = tint,
        fontSize = 12.sp,
        lineHeight = 15.sp,
        fontWeight = FontWeight.W600,
        maxLines = 1,
        textAlign = align,
        modifier = Modifier.weight(share).then(modifier),
    )
}

/**
 * One row of a release's track table, in Apple's columns.
 *
 * The web list answers the pointer in two places at once: the row takes a wash, a play triangle
 * comes up over the cover, and the length in the last column is replaced by a ⋯ that opens the same
 * menu a right-click does. A playlist's rows come from all over, so the album each one sits on is a
 * column of its own; a release page is the album its rows sit on and leaves it out.
 */
@Composable
private fun AppleTrackTableRow(
    track: ShelfItem,
    shelfTitle: String,
    tint: Color,
    onItemClick: (ShelfItem, String?) -> Unit,
    onItemMenu: (ShelfItem) -> Unit,
    onArtistClick: ((String) -> Unit)? = null,
    showsAlbum: Boolean = false,
    onAlbumByName: ((String, String) -> Unit)? = null,
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
    Column(Modifier.padding(horizontal = DesktopPageGutter)) {
        // The hairline above a row, which goes when the pointer is on it, so a hovered row reads as
        // one block rather than two halves.
        Box(
            Modifier.fillMaxWidth().height(0.5.dp).background(Color.White.copy(alpha = 0.12f * (1f - reveal))),
        )
        Row(
            Modifier.fillMaxWidth().height(TRACK_ROW_HEIGHT)
                .background(Color.White.copy(alpha = APPLE_ROW_WASH_ALPHA * reveal))
                .hoverable(interaction)
                .longPressMenuClickable(onClick = { onItemClick(track, shelfTitle) }, onLongClick = menu)
                .contextClick(menu),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.weight(if (showsAlbum) 42f else 50f),
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
            // The Artist cell is a link, as it is on Apple's own table: the inner clickable takes
            // the press, so the row's own play action is left alone.
            AppleTableLink(
                artistName,
                tint,
                canOpen = onArtistClick != null && artistName.isNotBlank(),
                onClick = { onArtistClick?.invoke(artistName) },
                modifier = Modifier.weight(if (showsAlbum) 26f else 32f).padding(end = 10.dp),
            )
            if (showsAlbum) {
                val album = detail?.album?.takeIf(String::isNotBlank)
                val albumUrl = detail?.albumUrl
                // A release the row names a page for opens it directly; one named only by title
                // is found from its name, so the cell is a link either way.
                val openByName = onAlbumByName
                val byName = album != null && albumUrl == null && openByName != null
                AppleTableLink(
                    album ?: "—",
                    tint,
                    canOpen = (album != null && albumUrl != null) || byName,
                    onClick = {
                        when {
                            album != null && albumUrl != null -> onItemClick(
                                ShelfItem(
                                    title = album,
                                    subtitle = artistName,
                                    thumbnailUrl = track.thumbnailUrl,
                                    videoId = null,
                                    browseId = DesktopAppleBrowseClient.PAGE_PREFIX + albumUrl,
                                ),
                                shelfTitle,
                            )
                            byName -> openByName?.invoke(album.orEmpty(), artistName)
                        }
                    },
                    modifier = Modifier.weight(20f).padding(end = 10.dp),
                )
            }
            Box(Modifier.weight(if (showsAlbum) 12f else 18f).fillMaxHeight()) {
                Text(
                    appleTrackTime(detail?.durationMs),
                    color = tint,
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
 * itself, so a click here opens the artist rather than playing the row.
 */
@Composable
internal fun AppleTableLink(
    text: String,
    tint: Color,
    canOpen: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val lit = hovered && canOpen
    Text(
        text,
        color = if (lit) DesktopAppleText else tint,
        fontSize = 13.sp,
        lineHeight = 16.sp,
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

/** What a release's table signs off with: `8 songs, 27 minutes`. */
internal fun appleReleaseFooter(songs: Int, totalMs: Long): String {
    val minutes = ((totalMs + 30_000L) / 60_000L).toInt()
    return "$songs ${if (songs == 1) "song" else "songs"}, $minutes ${if (minutes == 1) "minute" else "minutes"}"
}

/** The gray a release page writes its update line in, whatever its cover looks like. */
internal val AppleReleaseMeta = Color(0xFFA1A1A6)

private const val TRACK_REVEAL_MS = 140
internal val TRACK_ROW_HEIGHT = 54.dp

/** The wash a track row takes under the pointer — the row lifting off the page, not a grey block. */
private const val APPLE_ROW_WASH_ALPHA = 0.045f
private const val TABULAR_FIGURES = "tnum"

@Composable
private fun AppleBrowseTrackRowSkeleton() {
    Row(
        Modifier.fillMaxWidth().height(TRACK_ROW_HEIGHT).padding(horizontal = DesktopPageGutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(50f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ShimmerBox(Modifier.padding(start = 7.dp).size(40.dp).clip(RoundedCornerShape(5.dp)))
            ShimmerBox(Modifier.weight(1f).height(12.dp))
        }
        ShimmerBox(Modifier.weight(32f).height(12.dp))
        ShimmerBox(Modifier.weight(18f).height(12.dp).padding(end = 18.dp))
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

internal fun String.isAppleBrowseRecent(): Boolean = trim().lowercase(Locale.ROOT) in setOf(
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
