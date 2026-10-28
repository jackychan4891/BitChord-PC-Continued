package com.music.bitchord.desktop

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import dev.chrisbanes.haze.hazeSource

/**
 * The web overlay's own entrance: the full player fades in over 300ms while it
 * settles from 102% to full size, and fades straight back out in 200. No drag,
 * no handle — the close glyph and Escape (which the window routes through
 * [com.music.bitchord.ui.player.PlayerBack] first, so the lyrics or the queue
 * close before the player does) are the two ways out.
 *
 * Drawn inside the window rather than as Material's modal sheet: that one is a
 * focusable popup on the desktop, and would take the media keys and the
 * window's shortcuts away for as long as the player was up.
 */
@Composable
internal fun DesktopPlayerSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    content: @Composable BoxScope.(windowWidth: Dp, windowHeight: Dp) -> Unit,
) {
    // Mounted while it animates out, so the fade down is seen.
    var mounted by remember { mutableStateOf(visible) }
    if (visible) mounted = true
    if (!mounted) return

    val reveal = remember { Animatable(if (visible) 0f else 1f) }
    LaunchedEffect(visible) {
        if (visible) {
            reveal.animateTo(1f, tween(SHEET_ENTER_MS, easing = SHEET_ENTER_EASING))
        } else {
            reveal.animateTo(0f, tween(SHEET_EXIT_MS))
            mounted = false
        }
    }

    // A source of the window's glass as well as a sheet: the player covers the page, so a menu
    // opened over it has to blur the player, not the page it has hidden. The sheet carries no
    // effect of its own, so it can be sampled without feeding back into itself.
    val haze = LocalDesktopHaze.current
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            // The player covers the window, so nothing under it may hear the pointer:
            // without this node the sheet's empty space is see-through for clicks and
            // the page beneath navigates from under the player. Children take their
            // own events first — this only consumes what is left over.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Main).changes.forEach { it.consume() }
                    }
                }
            }
            .then(if (haze != null) Modifier.hazeSource(haze) else Modifier),
    ) {
        val windowWidth = maxWidth
        val windowHeight = maxHeight
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = reveal.value
                    val scale = 1f + SHEET_ENTER_SCALE * (1f - reveal.value)
                    scaleX = scale
                    scaleY = scale
                },
        ) {
            content(windowWidth, windowHeight)
        }
    }
}

/** The site's `animate-enter`: 300ms with the ease the bundle's keyframes carry. */
private const val SHEET_ENTER_MS = 300
private val SHEET_ENTER_EASING = CubicBezierEasing(0.215f, 0.61f, 0.355f, 1f)
private const val SHEET_EXIT_MS = 200
private const val SHEET_ENTER_SCALE = 0.02f
