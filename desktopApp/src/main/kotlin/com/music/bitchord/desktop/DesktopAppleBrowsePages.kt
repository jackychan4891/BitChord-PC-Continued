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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
    val recents = shelves.indices.filter { shelves[it].title.isAppleBrowseRecent() }
    val editorial = shelves.indices.filter { index ->
        index !in recents && shelves[index].title.isAppleBrowseEditorial()
    }.ifEmpty {
        shelves.indices.firstOrNull { index ->
            index !in recents && shelves[index].items.any { it.browseId != null && !it.isAppleSong() }
        }?.let(::listOf).orEmpty()
    }
    recents.forEach { index ->
        item(key = "home:$index") {
            AppleBrowseRecentShelf(shelves[index], onItemClick, onItemMenu)
        }
    }
    editorial.forEach { index ->
        item(key = "home:$index") {
            AppleBrowseHeroShelf(shelves[index], onItemClick, onItemMenu)
        }
    }
    // Apple's home leads with the charts and only then shows what you have been
    // playing; the feed the backend hands over arrives in the opposite order, so
    // the two are pinned to the front here rather than left in arrival order.
    // Apple's own playlist rows follow them, and everything else keeps the order
    // it came in.
    val rest = shelves.indices.filter { it !in recents && it !in editorial && !shelves[it].title.isAppleBrowseDropped() }
        .sortedBy { index -> appleBrowseShelfRank(shelves[index], index) }
    rest.forEach { index ->
        item(key = "home:$index") {
            // Every row on Apple's home is a sideways run of covers, playlists and albums included:
            // a playlist is a tile you open, and the list is what you find inside it.
            DesktopAppleShelf(shelves[index], onItemClick, onItemMenu)
        }
    }
}

private fun LazyListScope.appleBrowseNewShelves(
    source: List<HomeShelf>,
    onItemClick: (ShelfItem, String?) -> Unit,
    onItemMenu: (ShelfItem) -> Unit,
) {
    val shelves = source.filterNot { it.title.isAppleBrowseRecent() }
        .sortedBy { if (it.title.isAppleBrowseEditorial()) 0 else 1 }
    shelves.firstOrNull()?.let { first ->
        item(key = "new:hero") { AppleBrowseHeroShelf(first, onItemClick, onItemMenu) }
    }
    val tracksIndex = shelves.indexOfFirst { shelf -> shelf.items.any { !it.videoId.isNullOrBlank() || it.isAppleSong() } }
    if (tracksIndex >= 0) {
        val shelf = shelves[tracksIndex]
        item(key = "new:tracks") {
            DesktopAppleShelf(
                shelf.copy(items = shelf.items.filter { !it.videoId.isNullOrBlank() || it.isAppleSong() }),
                onItemClick,
                onItemMenu,
            )
        }
    }
    shelves.forEachIndexed { index, shelf ->
        if (index == 0) return@forEachIndexed
        val remaining = if (index == tracksIndex) {
            shelf.copy(items = shelf.items.filter { it.videoId.isNullOrBlank() && !it.isAppleSong() })
        } else {
            shelf
        }
        if (remaining.items.isNotEmpty()) {
            item(key = "new:$index") { DesktopAppleShelf(remaining, onItemClick, onItemMenu) }
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
internal fun DesktopAppleRadioPage(
    shelves: UiState<List<HomeShelf>>,
    contentPadding: PaddingValues,
    onItemClick: (ShelfItem, String?) -> Unit,
    onItemMenu: (ShelfItem) -> Unit,
    onRetry: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(DesktopPageGround()),
        contentPadding = contentPadding,
    ) {
        item { AppleBrowsePageTitle("Radio") }
        item {
            Text(
                "Free live stations, streamed in-app on your system default audio output.",
                color = DesktopAppleSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = DesktopPageGutter, vertical = 16.dp),
            )
        }
        when (shelves) {
            UiState.Loading -> item { AppleBrowseShelfSkeleton() }
            is UiState.Error -> item { AppleBrowseError(shelves.message, onRetry) }
            is UiState.Success -> {
                items(shelves.data) { shelf -> DesktopAppleShelf(shelf, onItemClick, onItemMenu) }
                if (shelves.data.isEmpty()) item { AppleBrowseEmpty("No radio content to show yet.") }
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

@Composable
private fun AppleBrowseHeroShelf(
    shelf: HomeShelf,
    onItemClick: (ShelfItem, String?) -> Unit,
    onItemMenu: (ShelfItem) -> Unit,
) {
    Column(Modifier.padding(top = 22.dp, bottom = 6.dp)) {
        // The shelf is reached through its heading, like every other one: the
        // corner button on the tile is gone and the chevron that opens the whole
        // page is the only thing beside the title. A tile still answers a right
        // click with its menu.
        val more = shelf.moreBrowseId
        DesktopAppleHeading(
            shelf.title,
            Modifier.padding(horizontal = DesktopPageGutter, vertical = 12.dp)
                .then(if (more != null) Modifier.clickable { onItemClick(ShelfItem(shelf.title, "", null, null, more), shelf.title) } else Modifier),
            chevron = more != null,
        )
        DesktopScrollableRow(spacing = 20.dp) {
            items(shelf.items) { item ->
                val interaction = remember { MutableInteractionSource() }
                val hovered by interaction.collectIsHoveredAsState()
                val menu = { onItemMenu(item) }
                Column(
                    Modifier.width(457.dp).hoverable(interaction)
                        .longPressMenuClickable(onClick = { onItemClick(item, shelf.title) }, onLongClick = menu)
                        .contextClick(menu),
                ) {
                    Text(
                        shelf.subtitle.ifBlank { shelf.title }.uppercase(Locale.ROOT),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = DesktopAppleSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        item.title,
                        fontSize = 22.sp,
                        lineHeight = 27.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = DesktopAppleText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Text(
                        item.subtitle,
                        fontSize = 13.sp,
                        color = DesktopAppleSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 3.dp, bottom = 12.dp),
                    )
                    Box(Modifier.width(457.dp).height(261.dp).clip(RoundedCornerShape(8.dp))) {
                        DesktopArtwork(item.thumbnailUrl, Modifier.fillMaxSize(), px = 800)
                        if (hovered) {
                            Box(Modifier.matchParentSize().background(Color.White.copy(alpha = 0.05f)))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppleBrowseRecentShelf(
    shelf: HomeShelf,
    onItemClick: (ShelfItem, String?) -> Unit,
    onItemMenu: (ShelfItem) -> Unit,
) {
    Column(Modifier.padding(top = 20.dp, bottom = 6.dp)) {
        DesktopAppleHeading(shelf.title, Modifier.padding(horizontal = DesktopPageGutter, vertical = 12.dp))
        DesktopScrollableRow(spacing = 20.dp) {
            items(shelf.items) { item ->
                Column(
                    Modifier.width(172.dp).clip(RoundedCornerShape(8.dp)).desktopHoverFill()
                        .longPressMenuClickable(
                            onClick = { onItemClick(item, shelf.title) },
                            onLongClick = { onItemMenu(item) },
                        ).contextClick { onItemMenu(item) },
                ) {
                    DesktopArtwork(item.thumbnailUrl, Modifier.size(172.dp).clip(RoundedCornerShape(8.dp)), px = 400)
                    AppleBrowseCaptions(item.title, item.subtitle)
                }
            }
        }
    }
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
                    AppleTrackTableRow(track, title, onItemClick, onItemMenu)
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
 * The web list answers the pointer in three places at once: the row lights, a play triangle comes
 * up over the cover, and the length in the last column is replaced by a ⋯ that opens the same menu
 * a right-click does. All three are driven by the one hover value so they arrive together.
 */
@Composable
private fun AppleTrackTableRow(
    track: ShelfItem,
    shelfTitle: String,
    onItemClick: (ShelfItem, String?) -> Unit,
    onItemMenu: (ShelfItem) -> Unit,
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
    Column(Modifier.padding(horizontal = DesktopPageGutter)) {
        // The hairline above a row, which goes when the pointer is on it, so a hovered row reads as
        // one block rather than two halves.
        Box(
            Modifier.fillMaxWidth().height(0.5.dp).background(Color.White.copy(alpha = 0.12f * (1f - reveal))),
        )
        Row(
            Modifier.fillMaxWidth().height(TRACK_ROW_HEIGHT).hoverable(interaction)
                .background(Color.White.copy(alpha = 0.08f * reveal), RoundedCornerShape(12.dp))
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
            Text(
                track.subtitle,
                color = DesktopAppleSecondary,
                fontSize = 12.sp,
                lineHeight = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(25.25f).padding(end = 10.dp),
            )
            Text(
                detail?.album.orEmpty(),
                color = DesktopAppleSecondary,
                fontSize = 12.sp,
                lineHeight = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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
                    Modifier.width(172.dp).clip(RoundedCornerShape(8.dp)).desktopHoverFill()
                        .clickable { onStartStation(song) },
                ) {
                    DesktopArtwork(song.thumbnailUrl, Modifier.size(172.dp).clip(RoundedCornerShape(8.dp)), px = 400)
                    AppleBrowseCaptions("${song.artist} Station", song.title)
                }
            }
        }
    }
}

@Composable
private fun AppleBrowseWideShelf(shelf: HomeShelf, onItemClick: (ShelfItem, String?) -> Unit) {
    Column(Modifier.padding(top = 25.dp, bottom = 5.dp)) {
        DesktopAppleHeading(shelf.title, Modifier.padding(horizontal = DesktopPageGutter, vertical = 12.dp))
        DesktopScrollableRow(spacing = 20.dp) {
            items(shelf.items) { item ->
                Column(Modifier.width(220.dp).desktopHoverFill().clickable { onItemClick(item, shelf.title) }) {
                    DesktopArtwork(
                        item.thumbnailUrl,
                        Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)),
                        px = 800,
                    )
                    AppleBrowseCaptions(item.title, item.subtitle)
                }
            }
        }
    }
}

@Composable
private fun AppleBrowseCaptions(title: String, subtitle: String) {
    Text(title, color = DesktopAppleText, fontSize = 13.sp, fontWeight = FontWeight.Normal,
        maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 7.dp))
    if (subtitle.isNotBlank()) {
        Text(subtitle, color = DesktopAppleSecondary, fontSize = 12.sp, fontWeight = FontWeight.Normal,
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
                    ).desktopHoverFill(corner = 10.dp),
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
            Text("Retry", color = DesktopAppleText, fontSize = 13.sp)
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

/** The shelves that lead the home page, in the order they lead it. */
private val AppleBrowsePinnedShelves = listOf("Trending", "Listen again")

/**
 * Where a shelf belongs in home's running order: the pinned rows first, then
 * Apple's playlist rows, then the rest as the feed delivered them.
 */
private fun appleBrowseShelfRank(shelf: HomeShelf, index: Int): Int {
    val pinned = AppleBrowsePinnedShelves.indexOfFirst { it.equals(shelf.title.trim(), ignoreCase = true) }
    return when {
        pinned >= 0 -> pinned
        shelf.items.any(ShelfItem::isApplePlaylistTile) -> AppleBrowsePinnedShelves.size
        else -> AppleBrowsePinnedShelves.size + 1 + index
    }
}

/**
 * Feed rows Apple's own home has no equivalent of, so the page drops them rather
 * than carrying the backend's taxonomy onto a screen that is meant to read as
 * Apple Music.
 */
private fun String.isAppleBrowseDropped(): Boolean {
    val value = trim().lowercase(Locale.ROOT)
    return value.contains("fresh finds") || value.contains("old favourite") || value.contains("old favorite")
}

private fun String.isAppleBrowseEditorial(): Boolean {
    val value = trim().lowercase(Locale.ROOT)
    return value in setOf("albums", "singles", "albums & singles", "new albums & singles", "new releases") ||
        value.contains("featured") || value.contains("editorial")
}

private fun HomeShelf.isAppleBrowseWide(): Boolean =
    title.contains("interview", ignoreCase = true) || title.contains("video", ignoreCase = true)

private fun appleBrowseCategoryColor(title: String): Color = when ((title.hashCode() and Int.MAX_VALUE) % 6) {
    0 -> Color(0xFF96576D)
    1 -> Color(0xFF476D93)
    2 -> Color(0xFF7D619E)
    3 -> Color(0xFF887044)
    4 -> Color(0xFF4F7C75)
    else -> Color(0xFF9C584E)
}
