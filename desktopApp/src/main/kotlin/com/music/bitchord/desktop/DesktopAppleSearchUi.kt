package com.music.bitchord.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.data.model.BrowseType
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.ui.icons.BitChordIcons
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The field's own width, measured off the web page rather than guessed. */
private val SEARCH_FIELD_WIDTH = 347.dp
private val SEARCH_FIELD_HEIGHT = 38.dp

/** How far the field hangs below the top of the page, and where its dropdown starts. */
private val SEARCH_FIELD_TOP = 12.dp
private val SEARCH_DROPDOWN_TOP = 53.dp

/**
 * How much of the page the bar owns. The dropdown's dismiss layer starts below it, so clicking the
 * field again still lands on the field rather than on the thing it is being typed into.
 */
internal val DesktopAppleSearchBarHeight = 62.dp

/**
 * The words typed into the search field, kept as words.
 *
 * [DesktopSearchHistory] keeps the *thing* a search landed on, with its artwork, which is what the
 * results page wants. The dropdown above the field wants the query back instead, so the terms are
 * stored beside it rather than folded into it.
 */
internal object DesktopSearchTermHistory {

    private val persistence = DesktopPersistence()

    private val _recent = MutableStateFlow(load())

    /** Most recent first, newest at the head. */
    val recent: StateFlow<List<String>> = _recent

    fun record(term: String) {
        val clean = term.trim()
        if (clean.isEmpty()) return
        save((listOf(clean) + _recent.value.filterNot { it.equals(clean, ignoreCase = true) }).take(MAX))
    }

    fun remove(term: String) = save(_recent.value.filterNot { it == term })

    fun clear() = save(emptyList())

    private fun save(value: List<String>) {
        _recent.value = value
        persistence.saveString(KEY, value.joinToString("\n"))
    }

    private fun load(): List<String> =
        persistence.string(KEY, "").lineSequence().map(String::trim).filter(String::isNotEmpty).take(MAX).toList()

    private const val KEY = "search_terms"
    private const val MAX = 12
}

/**
 * The bar at the head of the search page: one field, centred in the page rather than pinned to
 * either margin, so it sits in the middle of the content column the way the web page's does.
 *
 * The catalogue it reads is always Apple Music; there is no second scope to choose between.
 */
@Composable
internal fun DesktopAppleSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    onFocusedChange: (Boolean) -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    Box(modifier.fillMaxWidth().height(DesktopAppleSearchBarHeight)) {
        Box(Modifier.align(Alignment.TopCenter).padding(top = SEARCH_FIELD_TOP).widthIn(max = SEARCH_FIELD_WIDTH).fillMaxWidth()) {
            DesktopSearchPill(
                query = query,
                onQueryChange = onQueryChange,
                onSubmit = onSubmit,
                focusRequester = focusRequester,
                focused = focused,
                onFocusChanged = {
                    focused = it
                    onFocusedChange(it)
                },
            )
        }
    }
}

/** The pill itself: a 38dp capsule of `#1F1F1F`, the glyph inside its left padding, the text after it. */
@Composable
private fun DesktopSearchPill(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    focusRequester: FocusRequester,
    focused: Boolean,
    onFocusChanged: (Boolean) -> Unit,
) {
    // The glyph, the words and the clear button are one row rather than three layers in a box: a
    // field that starts at the pill's own inset runs under the glyph, so the text would be read
    // through the magnifier instead of beside it.
    Row(
        Modifier.fillMaxWidth().height(SEARCH_FIELD_HEIGHT).clip(CircleShape)
            .background(Color(0xFF1F1F1F))
            .border(
                width = if (focused) 2.dp else 0.5.dp,
                // The web page rings the field in its accent while it is being typed into. Ours is
                // the pink, not Apple's blue.
                color = if (focused) DesktopAccent else Color.White.copy(alpha = 0.08f),
                shape = CircleShape,
            )
            .clickable { focusRequester.requestFocus() }
            .padding(start = 11.dp, end = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            BitChordIcons.Search,
            contentDescription = null,
            tint = if (focused) DesktopAccent else DesktopAppleSecondary,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(8.dp))
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .onFocusChanged { onFocusChanged(it.isFocused) }
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                        onSubmit()
                        true
                    } else {
                        false
                    }
                },
            singleLine = true,
            // The chrome's own face, like every other line of text on the page: the platform's
            // default would set the query in a different typeface from the results under it.
            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, color = DesktopAppleText),
            cursorBrush = SolidColor(DesktopAccent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isBlank()) {
                        Text(
                            DesktopStrings["search", "Search"],
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFF5F5F7),
                            maxLines = 1,
                        )
                    }
                    innerTextField()
                }
            },
        )
        if (query.isNotBlank()) {
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(16.dp).clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.18f))
                    .clickable { onQueryChange("") },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    BitChordIcons.Close,
                    contentDescription = DesktopStrings["clear", "Clear"],
                    tint = DesktopAppleSecondary,
                    modifier = Modifier.size(8.dp),
                )
            }
        }
    }
}

/**
 * The dropdown that hangs off the field, drawn over the page rather than in a window of its own so
 * that it frosts what it covers the way the web page's does.
 *
 * With nothing typed it is the recently-searched list; with a query it is the completions first,
 * then the media they name. Those two groups are cut differently on purpose — a completion is a
 * line of text with a magnifier, a result is a lockup with its sleeve — and the difference is what
 * makes the list readable at a glance.
 */
@Composable
internal fun DesktopAppleSearchTypeahead(
    query: String,
    terms: List<String>,
    recentTerms: List<String>,
    results: List<SearchResult>,
    onTerm: (String) -> Unit,
    onClearTerms: () -> Unit,
    onPickEntity: (SearchResult) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(9.dp)
    val typing = query.isNotBlank()
    val shownTerms = if (typing) terms else recentTerms
    Box(
        Modifier
            .fillMaxSize()
            // Everything below the bar closes the dropdown; the bar itself keeps its own clicks, so
            // pointing back at the field places the caret rather than dismissing what it asked for.
            .padding(top = DesktopAppleSearchBarHeight)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
    )
    Column(
        modifier
            .widthIn(max = SEARCH_FIELD_WIDTH)
            .fillMaxWidth()
            .padding(top = SEARCH_DROPDOWN_TOP)
            .heightIn(max = 576.dp)
            .clip(shape)
            // The page under this list is a grid of covers, and a pane that only tints leaves their
            // colours showing between the rows of words — so the glass is laid over a fill rather
            // than over the page.
            .background(DesktopCardFill)
            .desktopMenuGlass(shape)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 6.dp),
    ) {
        if (typing) {
            shownTerms.forEach { term ->
                SearchHintTextRow(term = term, query = query, glyph = BitChordIcons.Search, onClick = { onTerm(term) })
            }
            if (shownTerms.isNotEmpty() && results.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth().height(0.5.dp).background(Color.White.copy(alpha = 0.10f)))
                Spacer(Modifier.height(6.dp))
            }
            results.forEach { result ->
                SearchHintLockupRow(result = result, query = query, onClick = { onPickEntity(result) })
            }
        } else {
            if (shownTerms.isEmpty()) {
                Text(
                    DesktopStrings["d_no_recent_searches", "No recent searches"],
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    fontSize = 13.sp,
                    color = DesktopAppleSecondary,
                )
            } else {
                Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        DesktopStrings["d_recently_searched", "Recently searched"],
                        Modifier.weight(1f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = DesktopAppleSecondary,
                    )
                    Text(
                        DesktopStrings["clear", "Clear"],
                        Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onClearTerms).padding(horizontal = 6.dp, vertical = 2.dp),
                        fontSize = 11.sp,
                        color = DesktopAccent,
                    )
                }
                shownTerms.forEach { term ->
                    SearchHintTextRow(term = term, query = "", glyph = BitChordIcons.Clock, onClick = { onTerm(term) })
                }
            }
        }
    }
}

/** A completion: 24dp of text with the magnifier in front of it, as the web page draws them. */
@Composable
private fun SearchHintTextRow(
    term: String,
    query: String,
    glyph: ImageVector,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(24.dp).desktopHoverFill(corner = 4.dp, alpha = 0.10f)
            .clickable(onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(glyph, contentDescription = null, tint = DesktopAppleSecondary, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            desktopHighlighted(term, query),
            fontSize = 13.sp,
            color = DesktopAppleText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * A result in the dropdown: its sleeve, what it is called, and the kind of thing it is in front of
 * whoever made it. Sixty-one dp, because the sleeve is 46 and the row wants air around it.
 */
@Composable
private fun SearchHintLockupRow(
    result: SearchResult,
    query: String,
    onClick: () -> Unit,
) {
    val (title, credit, artwork, type) = desktopSearchHintFields(result)
    Row(
        Modifier.fillMaxWidth().height(61.dp).desktopHoverFill(corner = 5.dp, alpha = 0.10f)
            .clickable(onClick = onClick).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(46.dp).clip(if (type == BrowseType.ARTIST) CircleShape else RoundedCornerShape(2.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (artwork != null) DesktopArtwork(artwork, Modifier.fillMaxSize(), px = 92)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                desktopHighlighted(title, query),
                fontSize = 13.sp,
                color = DesktopAppleText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                desktopSearchHintCaption(credit, type),
                fontSize = 12.sp,
                color = DesktopAppleSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The four things a dropdown row shows, read off whichever kind of search row it was given.
 *
 * The kind stays untranslated here: whether a sleeve is round is a fact about the row, not a word
 * on it, and a translated label would answer differently in every locale.
 */
private fun desktopSearchHintFields(result: SearchResult): Quad = when (result) {
    is SearchResult.TopTrack -> Quad(result.song.title, result.song.artist, result.song.thumbnailUrl, BrowseType.OTHER)
    is SearchResult.Track -> Quad(result.song.title, result.song.artist, result.song.thumbnailUrl, BrowseType.OTHER)
    is SearchResult.Browse -> {
        val item = result.item
        Quad(
            item.title,
            // An artist row is only ever "Artist" — the web page has no credit to put after it.
            if (item.type == BrowseType.ARTIST) "" else item.subtitle,
            item.thumbnailUrl,
            item.type,
        )
    }
}

private fun desktopSearchHintCaption(credit: String, type: BrowseType): String {
    val kind = when (type) {
        BrowseType.ARTIST -> DesktopStrings["artist", "Artist"]
        BrowseType.ALBUM -> DesktopStrings["d_album", "Album"]
        BrowseType.PLAYLIST -> DesktopStrings["d_playlist", "Playlist"]
        else -> DesktopStrings["d_song", "Song"]
    }
    return if (credit.isBlank()) kind else "$kind · $credit"
}

private data class Quad(val title: String, val credit: String, val artwork: String?, val type: BrowseType)

/**
 * The query in bold inside [text], the way the web page sets the matched run of every suggestion.
 *
 * Only the first run is marked: a two-word query repeated through a title would otherwise put bold
 * every other word on the line.
 */
private fun desktopHighlighted(text: String, term: String): AnnotatedString {
    if (term.isBlank()) return AnnotatedString(text)
    val at = text.indexOf(term, ignoreCase = true)
    if (at < 0) return AnnotatedString(text)
    return buildAnnotatedString {
        withStyle(SpanStyle(fontWeight = FontWeight.Normal)) { append(text.substring(0, at)) }
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text.substring(at, at + term.length)) }
        withStyle(SpanStyle(fontWeight = FontWeight.Normal)) { append(text.substring(at + term.length)) }
    }
}

