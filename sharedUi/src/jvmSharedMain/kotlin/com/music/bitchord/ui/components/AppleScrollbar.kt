package com.music.bitchord.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The pane's own overlay scrollbar, macOS's way of showing one.
 *
 * Drawn over the right edge of [content] rather than beside it, so a list never shifts when the
 * bar arrives, and thin enough to read as part of the window's chrome. The window's version fades
 * in as the pointer comes near and out again when it leaves; the phone's draws nothing, because a
 * phone has no pointer to come near and its rows already show where they are in the list.
 *
 * The pane must hand over its scroll state — the same [LazyListState] it is built with — since
 * that is the only place the bar can learn how much of the content is on screen and how far it has
 * gone.
 */
@Composable
expect fun AppleScrollbarBox(
    state: LazyListState,
    modifier: Modifier,
    content: @Composable () -> Unit,
)

/** [AppleScrollbarBox] for a grid, whose bar tracks rows rather than items. */
@Composable
expect fun AppleScrollbarBox(
    state: LazyGridState,
    modifier: Modifier,
    content: @Composable () -> Unit,
)

/**
 * The bar on its own, for a list that already sits in a `Box`: called as that Box's last child it
 * lies over the list without disturbing what is already laid out under it.
 *
 * Use this rather than [AppleScrollbarBox] where wrapping the list would mean re-indenting a long
 * block — a pane that draws a drag shadow or a fade over its rows, say. The caller places the bar
 * with its own [modifier], since only the surrounding `Box` knows where its edge is.
 */
@Composable
expect fun AppleScrollbar(
    state: LazyListState,
    modifier: Modifier,
)
