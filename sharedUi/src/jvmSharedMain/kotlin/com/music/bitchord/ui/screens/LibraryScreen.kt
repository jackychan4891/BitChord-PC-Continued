package com.music.bitchord.ui.screens

import com.music.bitchord.sharedui.resources.*
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.LibraryPage
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.UiState
import com.music.bitchord.data.settings.LibrarySort
import com.music.bitchord.ui.AppUi
import com.music.bitchord.ui.icons.BitChordIcons
import com.music.bitchord.ui.components.LIBRARY_GRID_SPACING
import com.music.bitchord.ui.components.LibraryGridSpec
import com.music.bitchord.ui.components.MessageState
import com.music.bitchord.ui.components.PAGE_GUTTER
import com.music.bitchord.ui.components.PullToRefresh
import com.music.bitchord.ui.components.libraryGrid
import com.music.bitchord.ui.components.librarySkeleton

/**
 * The signed-in library, drawn the way macOS Music draws it: a "Library"
 * heading and the playlists beneath — the ones the user made first, then the
 * account's auto playlists at the foot of the page, above the folders.
 *
 * [links] and [deviceItems] stay below the grid: the folders and on-device
 * releases the rest of the app reads, which have no Apple counterpart to fold
 * into.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    signedIn: Boolean,
    state: UiState<LibraryPage>,
    listState: LazyListState,
    onShelfItemClick: (ShelfItem) -> Unit,
    onShelfItemLongPress: (ShelfItem) -> Unit,
    onNewPlaylist: () -> Unit,
    onImportSpotifyPlaylist: (() -> Unit)? = null,
    onSignIn: () -> Unit,
    onRetry: () -> Unit,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    pullState: PullToRefreshState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues,
    /**
     * The device's folders — downloads, local files, the remote libraries this
     * build supports — drawn as a list under the grid. Built by the app, since
     * which of those exist is the platform's business.
     */
    links: List<LibraryLink>,
    /**
     * The "On device" shelf under that list: the releases kept on this device
     * whole. Left off the page entirely while there are none.
     */
    deviceItems: List<ShelfItem>,
    /** The big "Library" heading; the desktop's pages carry the smaller one. */
    showTitle: Boolean = true,
    /** Drawn in front of that heading — the desktop puts its mark there. */
    titleLeading: (@Composable () -> Unit)? = null,
) {
    val pinnedPlaylists by AppUi.host.pinnedPlaylists.collectAsStateWithLifecycle()
    val librarySort by AppUi.host.librarySort.collectAsStateWithLifecycle()
    val onDevice = stringResource(Res.string.on_device)

    PullToRefresh(
        refreshing = refreshing,
        onRefresh = onRefresh,
        state = pullState,
        modifier = modifier,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // The page's own width, less the gutters each grid row carries — the
            // same arithmetic the "Show all" page runs, so a card lands the same
            // size wherever it is drawn.
            val grid = libraryGrid(maxWidth - PAGE_GUTTER * 2)
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = contentPadding,
            ) {
            item(key = "header") {
                Row(
                    modifier = Modifier.padding(start = PAGE_GUTTER, end = PAGE_GUTTER, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    titleLeading?.let { leading ->
                        Box(Modifier.padding(end = 12.dp)) { leading() }
                    }
                    Text(
                        text = stringResource(Res.string.library),
                        style = if (showTitle) {
                            MaterialTheme.typography.displayLarge
                        } else {
                            MaterialTheme.typography.headlineSmall
                        },
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
            if (!signedIn) {
                item {
                    MessageState(
                        message = stringResource(Res.string.library_sign_in_description),
                        actionLabel = stringResource(Res.string.sign_in),
                        onAction = onSignIn,
                    )
                }
                return@LazyColumn
            }
            when (state) {
                is UiState.Loading -> librarySkeleton()
                is UiState.Error -> item {
                    MessageState(state.message, actionLabel = stringResource(Res.string.retry), onAction = onRetry)
                }
                is UiState.Success -> {
                    LibraryPlaylistsContent(
                        page = state.data,
                        grid = grid,
                        pinnedPlaylists = pinnedPlaylists,
                        librarySort = librarySort,
                        onItemClick = onShelfItemClick,
                        onItemLongPress = onShelfItemLongPress,
                        onNewPlaylist = onNewPlaylist,
                        onImportSpotifyPlaylist = onImportSpotifyPlaylist,
                    )
                }
            }
            if (links.isNotEmpty()) {
                item(key = "links") { LibraryLinkList(links = links, onClick = onShelfItemClick) }
            }
            if (deviceItems.isNotEmpty()) {
                item(key = "shelf:$onDevice") {
                    val onDeviceShelf = HomeShelf(title = onDevice, items = deviceItems)
                    Column(Modifier.padding(bottom = 26.dp)) {
                        SectionHeader(onDeviceShelf.title, onDeviceShelf.subtitle)
                        BoxWithConstraints(Modifier.padding(horizontal = PAGE_GUTTER)) {
                            val spec = libraryGrid(maxWidth)
                            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                                onDeviceShelf.items.chunked(spec.columns).forEach { rowItems ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(LIBRARY_GRID_SPACING)) {
                                        rowItems.forEach { item ->
                                            ShelfCard(
                                                item = item,
                                                onClick = { onShelfItemClick(item) },
                                                onLongPress = { onShelfItemLongPress(item) },
                                                modifier = Modifier.width(spec.cardWidth),
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
    }
}

/** One of the Library's folder rows: the page [item] opens, behind [icon]. */
/** [logo], when set, is drawn in place of [icon] — a service's own mark. */
data class LibraryLink(val item: ShelfItem, val icon: ImageVector, val logo: DrawableResource? = null)

/**
 * The folders as a plain list — icon, name, chevron, hairlines between — the
 * way a music app's library has always opened, rather than as cards that all
 * look alike because none of them has artwork.
 */
@Composable
private fun LibraryLinkList(links: List<LibraryLink>, onClick: (ShelfItem) -> Unit) {
    Column(Modifier.padding(bottom = 22.dp)) {
        links.forEachIndexed { index, link ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 54.dp)
                    .clickable { onClick(link.item) }
                    .padding(horizontal = PAGE_GUTTER),
            ) {
                if (link.logo != null) {
                    // A mark, not a glyph: white on dark, black on light, with
                    // its cut-outs left clear.
                    Icon(
                        painter = painterResource(link.logo),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(LINK_ICON_SIZE),
                    )
                } else {
                    Icon(
                        imageVector = link.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(LINK_ICON_SIZE),
                    )
                }
                Spacer(Modifier.width(LINK_ICON_GAP))
                Text(
                    text = link.item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = BitChordIcons.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .6f),
                    modifier = Modifier.size(20.dp),
                )
            }
            if (index < links.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = PAGE_GUTTER + LINK_ICON_SIZE + LINK_ICON_GAP),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

private val LINK_ICON_SIZE = 24.dp
private val LINK_ICON_GAP = 16.dp

/**
 * The account's auto playlists are recognised by their browse ids: `VLLM` is
 * Liked Music, `VLSE` Episodes for Later. Everything else on the Playlists
 * shelf is the user's own.
 */
private fun ShelfItem.isAutoPlaylist(): Boolean =
    browseId?.startsWith("VLLM") == true || browseId?.startsWith("VLSE") == true

/** Each auto playlist's row glyph, told apart by the same browse-id prefixes. */
private fun ShelfItem.autoPlaylistIcon(): ImageVector = when {
    browseId?.startsWith("VLSE") == true -> Icons.Rounded.QueueMusic
    else -> Icons.Rounded.PlaylistPlay
}

/**
 * The auto playlists as folder rows, exactly as Downloads and Local Music are
 * drawn — an icon, the name, a chevron — rather than as cover cards: they have
 * no covers worth showing, and the page already reads a folder row as "a page
 * of this app's" which is what these are.
 */
@Composable
private fun AutoPlaylistLinkList(items: List<ShelfItem>, onClick: (ShelfItem) -> Unit) {
    Column(Modifier.padding(top = 4.dp)) {
        items.forEach { item ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 54.dp)
                    .clickable { onClick(item) }
                    .padding(horizontal = PAGE_GUTTER),
            ) {
                Icon(
                    imageVector = item.autoPlaylistIcon(),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(LINK_ICON_SIZE),
                )
                Spacer(Modifier.width(LINK_ICON_GAP))
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = BitChordIcons.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .6f),
                    modifier = Modifier.size(20.dp),
                )
            }
            // A hairline after every row, the last one included: the folders
            // below carry this list straight on.
            HorizontalDivider(
                modifier = Modifier.padding(start = PAGE_GUTTER + LINK_ICON_SIZE + LINK_ICON_GAP),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

/**
 * The Playlists heading with its add and import actions sitting right after
 * the name, the way Apple Music keeps a section's controls beside its title.
 */
@Composable
private fun PlaylistsHeader(
    onNewPlaylist: () -> Unit,
    onImportSpotifyPlaylist: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PAGE_GUTTER, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(Res.string.playlists),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(6.dp))
        PlaylistHeaderActions(
            onNewPlaylist = onNewPlaylist,
            onImportSpotifyPlaylist = onImportSpotifyPlaylist,
        )
        Spacer(Modifier.weight(1f))
    }
}

/**
 * The small actions beside the Playlists heading: a plus for a new playlist,
 * and the Spotify mark for an import.
 */
@Composable
private fun PlaylistHeaderActions(
    onNewPlaylist: () -> Unit,
    onImportSpotifyPlaylist: (() -> Unit)?,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = BitChordIcons.Plus,
            contentDescription = stringResource(Res.string.new_playlist),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onNewPlaylist)
                .padding(3.dp)
                .size(22.dp),
        )
        if (onImportSpotifyPlaylist != null) {
            Icon(
                painter = painterResource(Res.drawable.spotify_logo),
                contentDescription = stringResource(Res.string.import_spotify),
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onImportSpotifyPlaylist)
                    .padding(3.dp)
                    .size(22.dp),
            )
        }
    }
}

private const val PLAYLISTS = YtMusicRepository.PLAYLISTS_SHELF

/**
 * The library's content: the user's own playlists as a grid under a heading
 * that carries the add and import actions, then the account's auto playlists
 * as folder rows — drawn last, directly above the Downloads and Local Music
 * rows, with no heading of their own.
 */
private fun LazyListScope.LibraryPlaylistsContent(
    page: LibraryPage,
    grid: LibraryGridSpec,
    pinnedPlaylists: List<String>,
    librarySort: LibrarySort,
    onItemClick: (ShelfItem) -> Unit,
    onItemLongPress: (ShelfItem) -> Unit,
    onNewPlaylist: () -> Unit,
    onImportSpotifyPlaylist: (() -> Unit)?,
) {
    val shelf = page.shelves.firstOrNull { it.title.equals(PLAYLISTS, ignoreCase = true) }
    if (shelf != null && shelf.items.isNotEmpty()) {
        // Liked Music and Episodes for Later are the account's, not the
        // user's: the playlists the user made come first, and the account's
        // rows close the page out above the folders.
        val (auto, user) = shelf.items.partition { it.isAutoPlaylist() }
        item(key = "playlists:user-header") {
            PlaylistsHeader(
                onNewPlaylist = onNewPlaylist,
                onImportSpotifyPlaylist = onImportSpotifyPlaylist,
            )
        }
        if (user.isNotEmpty()) {
            libraryGridItems(
                keyPrefix = PLAYLISTS,
                shelf = shelf.copy(items = user),
                grid = grid,
                pinnedPlaylists = pinnedPlaylists,
                librarySort = librarySort,
                onItemClick = onItemClick,
                onItemLongPress = onItemLongPress,
            )
        }
        if (auto.isNotEmpty()) {
            item(key = "playlists:auto-rows") {
                AutoPlaylistLinkList(items = auto, onClick = onItemClick)
            }
        }
    }
}

/**
 * One category's covers as a grid, chunked into rows inside the page's
 * [LazyColumn] — covers with the title and artist beneath each, as Apple draws
 * its Albums view. [grid] is measured once by the screen, since a
 * [LazyListScope] body isn't a composable context and can't run
 * [BoxWithConstraints] itself.
 */
private fun LazyListScope.libraryGridItems(
    keyPrefix: String,
    shelf: HomeShelf,
    grid: LibraryGridSpec,
    pinnedPlaylists: List<String>,
    librarySort: LibrarySort,
    onItemClick: (ShelfItem) -> Unit,
    onItemLongPress: (ShelfItem) -> Unit,
) {
    // Pinning wins over the default order, but an explicit sort is a stronger,
    // more deliberate signal than a pin and is left to reorder the whole grid.
    val sorted = shelf.pinnedFirst(pinnedPlaylists).sortedForLibrary(librarySort)
    val rows = sorted.items.chunked(grid.columns)
    items(rows.size, key = { "$keyPrefix:row:$it" }) { rowIndex ->
        Row(
            Modifier.fillMaxWidth().padding(horizontal = PAGE_GUTTER).padding(bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(LIBRARY_GRID_SPACING),
        ) {
            rows[rowIndex].forEach { item ->
                ShelfCard(
                    item = item,
                    onClick = { onItemClick(item) },
                    onLongPress = { onItemLongPress(item) },
                    modifier = Modifier.width(grid.cardWidth),
                    isPinned = item.browseId != null && item.browseId in pinnedPlaylists,
                )
            }
        }
    }
}

/**
 * Everything a Library shelf's "Show all" opened onto — the same cards, at the
 * same [libraryGrid] width, run down the screen instead of stopping at one row.
 */
@Composable
fun LibraryGridPage(
    shelf: HomeShelf,
    gridState: LazyGridState,
    onItemClick: (ShelfItem) -> Unit,
    onItemLongPress: (ShelfItem) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    onNewPlaylist: (() -> Unit)? = null,
) {
    // Re-read live rather than trusting [shelf] to already be sorted: this page
    // is opened from a snapshot (see `libraryShowAll` in MainActivity), and a
    // pin toggled from this page's own long-press menu must move the card
    // immediately rather than waiting for the row underneath to be revisited.
    val pinnedPlaylists by AppUi.host.pinnedPlaylists.collectAsStateWithLifecycle()
    val librarySort by AppUi.host.librarySort.collectAsStateWithLifecycle()
    // Pinning wins over the default order, but an explicit sort is a stronger,
    // more deliberate signal than a pin and is left to reorder the whole grid,
    // pinned cards included.
    val sortedShelf = shelf.pinnedFirst(pinnedPlaylists).sortedForLibrary(librarySort)
    BoxWithConstraints(modifier.fillMaxSize()) {
        val grid = libraryGrid(maxWidth - PAGE_GUTTER * 2)
        LazyVerticalGrid(
            columns = GridCells.Fixed(grid.columns),
            state = gridState,
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(LIBRARY_GRID_SPACING),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.padding(horizontal = PAGE_GUTTER),
        ) {
            if (onNewPlaylist != null) {
                item(key = "leading") {
                    NewShelfCard(
                        icon = BitChordIcons.Plus,
                        label = stringResource(Res.string.new_playlist),
                        subtitle = stringResource(Res.string.saved_to_youtube_music),
                        onClick = onNewPlaylist,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            items(sortedShelf.items, key = { it.browseId ?: it.title }) { item ->
                ShelfCard(
                    item = item,
                    onClick = { onItemClick(item) },
                    onLongPress = { onItemLongPress(item) },
                    modifier = Modifier.fillMaxWidth(),
                    isPinned = item.browseId != null && item.browseId in pinnedPlaylists,
                )
            }
        }
    }
}

/**
 * Moves whichever of this shelf's cards are in [pinned] to the front, in the
 * order they were pinned, leaving everything else in its existing order behind
 * them.
 *
 * A no-op on any shelf that isn't Playlists: [pinned] only ever holds playlist
 * browse ids, so an album or artist shelf never has a card that matches.
 */
private fun HomeShelf.pinnedFirst(pinned: List<String>): HomeShelf {
    if (pinned.isEmpty()) return this
    val byId = items.filter { it.browseId != null }.associateBy { it.browseId }
    val pinnedItems = pinned.mapNotNull { byId[it] }
    if (pinnedItems.isEmpty()) return this
    val pinnedSet = pinnedItems.toSet()
    return copy(items = pinnedItems + items.filter { it !in pinnedSet })
}

/**
 * A card's title is all a Library shelf carries, so [LibrarySort.DEFAULT] is
 * the only option that isn't alphabetical — everything else sorts on it.
 */
private fun HomeShelf.sortedForLibrary(sort: LibrarySort): HomeShelf = when (sort) {
    LibrarySort.DEFAULT -> this
    LibrarySort.TITLE_ASC -> copy(items = items.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }))
    LibrarySort.TITLE_DESC -> copy(
        items = items.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.title }),
    )
}
