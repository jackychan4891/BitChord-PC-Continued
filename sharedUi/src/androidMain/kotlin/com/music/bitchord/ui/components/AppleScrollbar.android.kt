package com.music.bitchord.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// Nothing to hang a bar on: the phone is scrolled with a finger that is already inside the list,
// and Compose for Android has no scrollbar to draw. The pane is handed straight back.

@Composable
actual fun AppleScrollbarBox(
    state: LazyListState,
    modifier: Modifier,
    content: @Composable () -> Unit,
) = content()

@Composable
actual fun AppleScrollbarBox(
    state: LazyGridState,
    modifier: Modifier,
    content: @Composable () -> Unit,
) = content()

@Composable
actual fun AppleScrollbar(state: LazyListState, modifier: Modifier) {
    // Nothing drawn, so the modifier has nothing to size.
}
