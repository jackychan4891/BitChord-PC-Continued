package com.music.bitchord.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.music.bitchord.data.model.BrowseItem
import com.music.bitchord.data.model.BrowseType
import com.music.bitchord.data.model.SearchHistoryEntity
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.model.Song
import kotlinx.coroutines.delay

/**
 * Quick search: Ctrl+K anywhere, type, and the hits come to the pointer rather than the page.
 *
 * It is a palette, not the Search page shrunk: the field has the keyboard, the arrows move down
 * the hits, Enter opens the selected one, and the last row is always a way through to the full
 * search for what was typed. With nothing typed it offers the searches already made, as the
 * Search page does, so it is useful before a key is pressed.
 */
@Composable
internal fun DesktopQuickSearchOverlay(
    onDismiss: () -> Unit,
    onPlaySong: (Song) -> Unit,
    onOpenBrowse: (BrowseItem) -> Unit,
    onOpenRecent: (SearchHistoryEntity) -> Unit,
    onSearchAll: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(0) }
    val recent by DesktopSearchHistory.recent.collectAsState()
    val fieldFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val scrimInteraction = remember { MutableInteractionSource() }
    val cardInteraction = remember { MutableInteractionSource() }

    LaunchedEffect(Unit) { fieldFocus.requestFocus() }

    LaunchedEffect(query) {
        selected = 0
        val term = query.trim()
        if (term.isEmpty()) {
            results = emptyList()
            loading = false
            return@LaunchedEffect
        }
        loading = true
        // Short enough not to feel laggy, long enough that a word typed at speed is one request.
        delay(TYPEAHEAD_DEBOUNCE_MS)
        DesktopSearchClient.searchTypeahead(term)
            .onSuccess { results = it.take(TYPEAHEAD_LIMIT) }
            .onFailure { results = emptyList() }
        loading = false
    }

    val rows: List<QuickSearchRow> = if (query.isBlank()) {
        recent.take(RECENT_LIMIT).map(QuickSearchRow::Recent)
    } else {
        results
            .map { result ->
                when (result) {
                    is SearchResult.TopTrack -> QuickSearchRow.Track(result.song)
                    is SearchResult.Track -> QuickSearchRow.Track(result.song)
                    is SearchResult.Browse -> QuickSearchRow.Browse(result.item)
                }
            }
            .distinctBy(QuickSearchRow::key) + QuickSearchRow.SearchAll(query.trim())
    }
    val active = selected.coerceIn(0, rows.lastIndex.coerceAtLeast(0))

    // More hits than the card has room for, so the arrows have to bring the next one into view.
    LaunchedEffect(active, rows.size) {
        if (rows.isNotEmpty()) listState.animateScrollToItem(active)
    }

    fun choose(row: QuickSearchRow) {
        when (row) {
            is QuickSearchRow.Track -> onPlaySong(row.song)
            is QuickSearchRow.Browse -> onOpenBrowse(row.item)
            is QuickSearchRow.Recent -> onOpenRecent(row.entity)
            is QuickSearchRow.SearchAll -> onSearchAll(row.term)
        }
        onDismiss()
    }

    fun submit() {
        rows.getOrNull(active)?.let(::choose)
    }

    Box(
        Modifier
            .fillMaxSize()
            .desktopFrostedScrim()
            .clickable(interactionSource = scrimInteraction, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            Modifier
                .padding(top = 92.dp, start = 24.dp, end = 24.dp)
                .widthIn(max = 620.dp)
                .fillMaxWidth()
                .desktopGlassCard(RoundedCornerShape(18.dp))
                .clickable(interactionSource = cardInteraction, indication = null, onClick = {})
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionDown -> {
                            if (rows.isNotEmpty()) selected = (active + 1).coerceAtMost(rows.lastIndex)
                            rows.isNotEmpty()
                        }
                        Key.DirectionUp -> {
                            if (rows.isNotEmpty()) selected = (active - 1).coerceAtLeast(0)
                            rows.isNotEmpty()
                        }
                        Key.Enter, Key.NumPadEnter -> {
                            submit()
                            true
                        }
                        else -> false
                    }
                },
        ) {
            Column(Modifier.padding(10.dp)) {
                DesktopSearchField(
                    query = query,
                    onQueryChange = { query = it },
                    onSearch = ::submit,
                    focusRequester = fieldFocus,
                    placeholder = DesktopStrings["quick_search_placeholder", "Search songs, artists, albums"],
                )

                if (query.isBlank()) {
                    if (recent.isEmpty()) {
                        Text(
                            DesktopStrings[
                                "quick_search_hint",
                                "Type to search across your library and the catalogue.",
                            ],
                            style = MaterialTheme.typography.bodySmall,
                            color = DesktopSecondary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 14.dp),
                        )
                    } else {
                        SectionCaption(DesktopStrings["recent_searches", "Recent searches"])
                    }
                }

                if (query.isNotBlank() && loading && results.isEmpty()) {
                    Text(
                        DesktopStrings["quick_search_searching", "Searching…"],
                        style = MaterialTheme.typography.bodySmall,
                        color = DesktopSecondary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
                    )
                }

                if (query.isNotBlank() && !loading && results.isEmpty()) {
                    Text(
                        DesktopStrings["quick_search_none", "No matches — press Enter to search the catalogue."],
                        style = MaterialTheme.typography.bodySmall,
                        color = DesktopSecondary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                    )
                }

                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp), state = listState) {
                    itemsIndexed(rows, key = { _, row -> row.key }) { index, row ->
                        QuickSearchRowView(row, index == active, onClick = { choose(row) })
                    }
                }

                Row(
                    Modifier.fillMaxWidth().padding(start = 10.dp, end = 8.dp, top = 8.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        DesktopStrings["quick_search_keys", "↑↓ move · Enter open · Esc close"],
                        style = MaterialTheme.typography.labelSmall,
                        color = DesktopSecondary,
                    )
                    Spacer(Modifier.weight(1f))
                    KeyCap(if (DesktopPlatform.isMac) "⌘K" else "Ctrl K")
                }
            }
        }
    }
}

/** What the palette renders under the scrim, and what Enter does to it. */
private sealed interface QuickSearchRow {
    val key: String
    val title: String
    val subtitle: String?
    val artworkUrl: String?
    val kind: String?

    data class Track(val song: Song) : QuickSearchRow {
        override val key = "track:${song.videoId}"
        override val title = song.title
        override val subtitle = song.artist
        override val artworkUrl = song.thumbnailUrl
        override val kind = DesktopStrings["quick_search_song", "Song"]
    }

    data class Browse(val item: BrowseItem) : QuickSearchRow {
        override val key = "browse:${item.browseId}"
        override val title = item.title
        override val subtitle = item.subtitle
        override val artworkUrl = item.thumbnailUrl
        override val kind = when (item.type) {
            BrowseType.ALBUM -> DesktopStrings["quick_search_album", "Album"]
            BrowseType.ARTIST -> DesktopStrings["quick_search_artist", "Artist"]
            BrowseType.PLAYLIST -> DesktopStrings["quick_search_playlist", "Playlist"]
            BrowseType.OTHER -> null
        }
    }

    data class Recent(val entity: SearchHistoryEntity) : QuickSearchRow {
        override val key = "recent:${entity.id}"
        override val title = entity.title
        override val subtitle = entity.subtitle
        override val artworkUrl = entity.artworkUrl
        override val kind = null
    }

    data class SearchAll(val term: String) : QuickSearchRow {
        override val key = "search-all"
        override val title = term
        override val subtitle = null
        override val artworkUrl = null
        override val kind = null
    }
}

@Composable
private fun QuickSearchRowView(row: QuickSearchRow, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Color.White.copy(alpha = 0.10f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (row is QuickSearchRow.SearchAll) {
            Text(
                "${DesktopStrings["quick_search_all", "Search for"]} “${row.term}”",
                style = MaterialTheme.typography.bodyMedium,
                color = DesktopAccent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        } else {
            DesktopArtwork(
                row.artworkUrl,
                Modifier.size(36.dp).clip(RoundedCornerShape(6.dp)),
                px = ROW_ART_PX,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    row.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                row.subtitle?.takeIf(String::isNotBlank)?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = DesktopSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            row.kind?.let {
                Spacer(Modifier.width(10.dp))
                Text(it, style = MaterialTheme.typography.labelSmall, color = DesktopSecondary)
            }
        }
    }
}

@Composable
private fun SectionCaption(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = DesktopSecondary,
        modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 14.dp, bottom = 4.dp),
    )
}

/** Every shortcut the window listens for, in the place someone would look them up. */
@Composable
internal fun DesktopShortcutsDialog(onDismiss: () -> Unit) {
    val mac = DesktopPlatform.isMac
    DesktopDialogPanel(onDismiss = onDismiss, maxWidth = 480, popup = true, frosted = true) {
        Column(Modifier.padding(start = panelInset(22.dp), end = panelInset(22.dp), top = 20.dp, bottom = 4.dp)) {
            Text(
                DesktopStrings["keyboard_shortcuts", "Keyboard shortcuts"],
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
            )
            Text(
                DesktopStrings["keyboard_shortcuts_footer", "They work anywhere in the window, including while typing."],
                style = MaterialTheme.typography.bodySmall,
                color = DesktopSecondary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Column(
            Modifier
                .then(desktopPanelBody(cardMax = 460.dp))
                .padding(horizontal = panelInset(22.dp))
                .verticalScroll(rememberScrollState()),
        ) {
            ShortcutGroup(
                DesktopStrings["shortcuts_playback", "Playback"],
                listOf(
                    "⏯" to DesktopStrings["shortcut_play_pause", "Play or pause"],
                    "⏭" to DesktopStrings["shortcut_next", "Next track"],
                    "⏮" to DesktopStrings["shortcut_previous", "Previous track"],
                ),
            )
            ShortcutGroup(
                DesktopStrings["shortcuts_navigation", "Navigation"],
                listOf(
                    (if (mac) "⌘K" else "Ctrl+K") to DesktopStrings["shortcut_quick_search", "Quick search"],
                    (if (mac) "⌘F" else "Ctrl+F") to DesktopStrings["shortcut_search_page", "Go to the search field"],
                    "Alt+←" to DesktopStrings["shortcut_back", "Back"],
                    (if (mac) "⌘," else "Ctrl+,") to DesktopStrings["shortcut_settings", "Settings"],
                ),
            )
            ShortcutGroup(
                DesktopStrings["shortcuts_window", "Window"],
                buildList {
                    add("F11" to DesktopStrings["shortcut_fullscreen", "Fullscreen"])
                    add(
                        (if (mac) "⌘W" else "Alt+F4") to
                            DesktopStrings["shortcut_close", "Close the window"],
                    )
                    if (mac) add("⌘M" to DesktopStrings["shortcut_minimise", "Minimise"])
                },
            )
            ShortcutGroup(
                DesktopStrings["shortcuts_layers", "Panels"],
                listOf(
                    "Esc" to DesktopStrings["shortcut_escape", "Close the panel that is open"],
                    (if (mac) "⌘/" else "Ctrl+/") to DesktopStrings["shortcut_help", "This list"],
                ),
            )
            Spacer(Modifier.height(14.dp))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = panelInset(20.dp), vertical = 14.dp)) {
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text(DesktopStrings["done", "Done"], color = DesktopAccent) }
        }
    }
}

@Composable
private fun ShortcutGroup(title: String, rows: List<Pair<String, String>>) {
    SectionCaption(title)
    rows.forEach { (keys, action) ->
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                action,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            KeyCap(keys)
        }
    }
}

@Composable
private fun KeyCap(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
        color = Color.White.copy(alpha = 0.85f),
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White.copy(alpha = 0.09f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

private const val TYPEAHEAD_DEBOUNCE_MS = 180L
private const val TYPEAHEAD_LIMIT = 8
private const val RECENT_LIMIT = 8
private const val ROW_ART_PX = 80
