package com.music.bitchord.desktop

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.MoodGenre
import com.music.bitchord.data.model.MoodGenreSection
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.UiState
import com.music.bitchord.ui.components.ShimmerBox
import com.music.bitchord.ui.components.contextClick
import com.music.bitchord.ui.components.longPressMenuClickable
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
        modifier = Modifier.fillMaxSize().background(DesktopBackground),
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
    shelves.indices.filter { it !in recents && it !in editorial }.forEach { index ->
        item(key = "home:$index") {
            val shelf = shelves[index]
            if (shelf.items.all { it.isAppleSong() }) AppleBrowseTrackGrid(shelf, onItemClick, onItemMenu)
            else DesktopAppleShelf(shelf, onItemClick, onItemMenu)
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
            AppleBrowseTrackGrid(
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
            item(key = "new:$index") {
                if (remaining.items.all { it.isAppleSong() }) AppleBrowseTrackGrid(remaining, onItemClick, onItemMenu)
                else DesktopAppleShelf(remaining, onItemClick, onItemMenu)
            }
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
    BoxWithConstraints(Modifier.fillMaxSize().background(DesktopBackground)) {
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
    categories: UiState<List<MoodGenreSection>>,
    contentPadding: PaddingValues,
    onStartStation: (Song) -> Unit,
    onItemClick: (ShelfItem, String?) -> Unit,
    onCategoryClick: (MoodGenre) -> Unit,
    onRetry: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize().background(DesktopBackground)) {
        val columns = if (maxWidth < 650.dp) 2 else 3
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = contentPadding) {
            item { AppleBrowsePageTitle("Radio") }
            when (shelves) {
                UiState.Loading -> {
                    item { AppleBrowseShelfSkeleton("Stations for You") }
                    item { AppleBrowseShelfSkeleton() }
                }
                is UiState.Error -> item { AppleBrowseError(shelves.message, onRetry) }
                is UiState.Success -> {
                    val seeds = shelves.data.filterNot { it.isAppleBrowseWide() }.flatMap { it.items }
                        .filter { !it.videoId.isNullOrBlank() }
                        .distinctBy { it.videoId }
                        .map { it.appleBrowseSong() }
                    if (seeds.isNotEmpty()) item { AppleBrowseStations(seeds, onStartStation) }
                    val collections = shelves.data.map { shelf ->
                        if (shelf.isAppleBrowseWide()) shelf else {
                            shelf.copy(items = shelf.items.filter { it.videoId.isNullOrBlank() })
                        }
                    }.filter { it.items.isNotEmpty() }
                    collections.forEachIndexed { index, shelf ->
                        item(key = "radio:collection:$index") {
                            when {
                                shelf.isAppleBrowseWide() -> AppleBrowseWideShelf(shelf, onItemClick)
                                index == 0 -> DesktopAppleShelf(shelf, onItemClick)
                                else -> AppleBrowseCollectionRows(shelf, onItemClick)
                            }
                        }
                    }
                    if (seeds.isEmpty() && collections.isEmpty()) {
                        item { AppleBrowseEmpty("No stations to show yet.") }
                    }
                }
            }
            item {
                DesktopAppleHeading(
                    "Genre Stations",
                    Modifier.padding(horizontal = DesktopPageGutter).padding(top = 32.dp, bottom = 20.dp),
                )
            }
            when (categories) {
                UiState.Loading -> items(2) { AppleBrowseCategorySkeleton(columns) }
                is UiState.Error -> item { AppleBrowseError(categories.message, onRetry) }
                is UiState.Success -> {
                    val groups = categories.data.filter { it.items.isNotEmpty() }
                    groups.forEachIndexed { index, section ->
                        if (section.title.isNotBlank() && groups.size > 1) {
                            item(key = "radio:genre-heading:$index") {
                                Text(
                                    section.title,
                                    color = DesktopAppleSecondary,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(horizontal = DesktopPageGutter).padding(bottom = 12.dp),
                                )
                            }
                        }
                        items(section.items.chunked(columns)) { row ->
                            AppleBrowseCategoryRow(row, columns, onCategoryClick)
                        }
                    }
                    if (groups.isEmpty()) item { AppleBrowseEmpty("No categories to show yet.") }
                }
            }
        }
    }
}

@Composable
private fun AppleBrowsePageTitle(title: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = DesktopPageGutter)) {
        Text(
            title,
            color = DesktopAppleText,
            fontSize = 34.sp,
            lineHeight = 41.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 8.dp, top = 24.dp, bottom = 18.dp),
        )
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
        DesktopScrollableRow(spacing = 20.dp) {
            items(shelf.items) { item ->
                val interaction = remember { MutableInteractionSource() }
                val hovered by interaction.collectIsHoveredAsState()
                val menu = { onItemMenu(item) }
                Column(
                    Modifier.width(395.dp).hoverable(interaction)
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
                    Box(Modifier.width(395.dp).height(226.dp).clip(RoundedCornerShape(8.dp))) {
                        DesktopArtwork(item.thumbnailUrl, Modifier.fillMaxSize(), px = 800)
                        if (hovered) {
                            Box(Modifier.matchParentSize().background(Color.White.copy(alpha = 0.05f)))
                            AppleBrowseMore(menu, Modifier.align(Alignment.BottomEnd).padding(10.dp))
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
                    Modifier.width(188.dp).clip(RoundedCornerShape(8.dp)).desktopHoverFill()
                        .longPressMenuClickable(
                            onClick = { onItemClick(item, shelf.title) },
                            onLongClick = { onItemMenu(item) },
                        ).contextClick { onItemMenu(item) },
                ) {
                    DesktopArtwork(item.thumbnailUrl, Modifier.size(188.dp).clip(RoundedCornerShape(8.dp)), px = 400)
                    AppleBrowseCaptions(item.title, item.subtitle)
                }
            }
        }
    }
}

@Composable
private fun AppleBrowseTrackGrid(
    shelf: HomeShelf,
    onItemClick: (ShelfItem, String?) -> Unit,
    onItemMenu: (ShelfItem) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 25.dp, bottom = 5.dp)) {
        val available = (maxWidth - DesktopPageGutter * 2).coerceAtLeast(0.dp)
        val columnCount = if (maxWidth < 650.dp) 2 else 3
        val columnWidth = ((available - 20.dp * (columnCount - 1)) / columnCount).coerceAtLeast(220.dp)
        Column {
            val more = shelf.moreBrowseId
            DesktopAppleHeading(
                shelf.title,
                Modifier.padding(horizontal = DesktopPageGutter, vertical = 12.dp)
                    .then(if (more != null) Modifier.clickable { onItemClick(ShelfItem(shelf.title, "", null, null, more), shelf.title) } else Modifier),
                chevron = more != null,
            )
            DesktopScrollableRow(spacing = 20.dp) {
                items(shelf.items.chunked(4)) { tracks ->
                    Column(Modifier.width(columnWidth)) {
                        tracks.forEach { item ->
                            DesktopAppleSongRow(
                                song = item.appleBrowseSong(),
                                subtitle = item.subtitle,
                                onClick = { onItemClick(item, shelf.title) },
                                onOpenMenu = { onItemMenu(item) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppleBrowseStations(seeds: List<Song>, onStartStation: (Song) -> Unit) {
    Column(Modifier.padding(top = 20.dp, bottom = 6.dp)) {
        DesktopAppleHeading("Stations for You", Modifier.padding(horizontal = DesktopPageGutter, vertical = 12.dp))
        DesktopScrollableRow(spacing = 20.dp) {
            items(seeds, key = { it.videoId }) { song ->
                Column(
                    Modifier.width(188.dp).clip(RoundedCornerShape(8.dp)).desktopHoverFill()
                        .clickable { onStartStation(song) },
                ) {
                    DesktopArtwork(song.thumbnailUrl, Modifier.size(188.dp).clip(RoundedCornerShape(8.dp)), px = 400)
                    AppleBrowseCaptions("${song.artist} Station", song.title)
                }
            }
        }
    }
}

@Composable
private fun AppleBrowseCollectionRows(shelf: HomeShelf, onItemClick: (ShelfItem, String?) -> Unit) {
    Column(Modifier.padding(horizontal = DesktopPageGutter).padding(top = 25.dp, bottom = 5.dp)) {
        DesktopAppleHeading(shelf.title, Modifier.padding(vertical = 12.dp))
        shelf.items.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                row.forEach { item ->
                    Column(Modifier.weight(1f)) {
                        Row(
                            Modifier.fillMaxWidth().height(88.dp).clip(RoundedCornerShape(8.dp))
                                .desktopHoverFill().clickable { onItemClick(item, shelf.title) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            DesktopArtwork(item.thumbnailUrl, Modifier.size(72.dp).clip(RoundedCornerShape(6.dp)), px = 160)
                            Column(Modifier.weight(1f).padding(start = 12.dp, end = 8.dp)) {
                                Text(item.title, color = DesktopAppleText, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(item.subtitle, color = DesktopAppleSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        HorizontalDivider(thickness = 0.5.dp, color = Color.White.copy(alpha = 0.1f))
                    }
                }
                if (row.size < 2) Spacer(Modifier.weight(1f))
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
                Column(Modifier.width(395.dp).desktopHoverFill().clickable { onItemClick(item, shelf.title) }) {
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
    Text(title, color = DesktopAppleText, fontSize = 12.sp, fontWeight = FontWeight.Normal,
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
            Box(
                Modifier.weight(1f).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp))
                    .background(tone).clickable { onCategoryClick(category) },
            ) {
                if (!category.thumbnailUrl.isNullOrBlank()) {
                    DesktopArtwork(category.thumbnailUrl, Modifier.fillMaxSize(), px = 600)
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
private fun AppleBrowseMore(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.size(28.dp).clip(RoundedCornerShape(14.dp))
            .background(Color.Black.copy(alpha = 0.55f)).desktopHoverFill().clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.MoreHoriz, "More", tint = Color.White, modifier = Modifier.size(19.dp))
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
                Column(Modifier.width(if (landscape) 395.dp else 188.dp)) {
                    ShimmerBox(
                        Modifier.fillMaxWidth().height(if (landscape) 226.dp else 188.dp),
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

private fun ShelfItem.appleBrowseSong(): Song {
    val parts = subtitle.split(" • ", " · ", " | ").map(String::trim).filter(String::isNotBlank)
    val duration = Regex("""\d{1,2}:\d{2}(?::\d{2})?""")
    val tally = Regex("""[0-9,.]+\s*(?:views?|likes?|songs?)""", RegexOption.IGNORE_CASE)
    val types = setOf("song", "video", "album", "ep", "single", "playlist", "mix")
    val artist = parts.firstOrNull {
        !it.matches(duration) && !it.matches(tally) && it.lowercase(Locale.ROOT) !in types
    } ?: "Unknown Artist"
    return Song(
        videoId = videoId.orEmpty(),
        title = title,
        artist = artist,
        thumbnailUrl = thumbnailUrl,
        durationText = parts.firstOrNull { it.matches(duration) },
    )
}
