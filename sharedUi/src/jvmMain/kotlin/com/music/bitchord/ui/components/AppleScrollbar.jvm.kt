package com.music.bitchord.ui.components

import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.v2.ScrollbarAdapter
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * macOS's overlay bar, in macOS's colours: a light grey hairline that only really arrives when the
 * pointer comes for it. [ScrollbarStyle] has no "hidden" state to ask for, so the resting colour is
 * the faintest grey that still reads against both the app's dark chrome and a white page.
 */
private val AppleScrollbarStyle = ScrollbarStyle(
    minimalHeight = 26.dp,
    thickness = 7.dp,
    shape = CircleShape,
    hoverDurationMillis = 450,
    unhoverColor = Color(0x2EABABB2),
    hoverColor = Color(0x8CABABB2),
)

@Composable
actual fun AppleScrollbarBox(
    state: LazyListState,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier) {
        content()
        AppleEdge(state)
    }
}

@Composable
actual fun AppleScrollbarBox(
    state: LazyGridState,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    val adapter: ScrollbarAdapter = rememberScrollbarAdapter(state)
    Box(modifier) {
        content()
        // No bar where the pane has nowhere left to go: an overlay bar is a thing to reach with,
        // and a list that already shows all of itself offers nothing to reach.
        if (state.canScrollBackward || state.canScrollForward) AppleBar(adapter)
    }
}

@Composable
actual fun AppleScrollbar(state: LazyListState, modifier: Modifier) {
    val adapter: ScrollbarAdapter = rememberScrollbarAdapter(state)
    if (state.canScrollBackward || state.canScrollForward) AppleBar(adapter, modifier)
}

/** The bar at the trailing edge of the box it is called in, where that box has an edge to keep. */
@Composable
private fun BoxScope.AppleEdge(state: LazyListState) {
    AppleScrollbar(state, Modifier.align(Alignment.CenterEnd).fillMaxHeight())
}

/** The bar itself, laid over the right edge of its box rather than beside the list inside it. */
@Composable
private fun AppleBar(adapter: ScrollbarAdapter, modifier: Modifier = Modifier) {
    VerticalScrollbar(
        adapter = adapter,
        modifier = modifier.padding(end = 3.dp, top = 6.dp, bottom = 6.dp),
        style = AppleScrollbarStyle,
    )
}
