package com.music.bitchord.ui.player

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * The turntable drawn over the landscape sleeve, so the cover reads as a record on a deck rather
 * than a square in a case.
 *
 * It paints only what does not turn — the grooves, the rim, the centre and the arm — and lies over
 * artwork the sleeve itself spins. Every measure is a fraction of the sleeve's own radius, so the
 * deck keeps its proportions at any window size and while the player docks into the bar.
 */
@Composable
internal fun TurntableDeck(isPlaying: Boolean, reduceAnimation: Boolean) {
    // The arm drops onto the record when it starts turning, and lifts clear again when it stops.
    val laidDown by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = if (reduceAnimation) snap() else tween(700, easing = FastOutSlowInEasing),
        label = "tonearm",
    )
    Canvas(Modifier.fillMaxSize()) {
        val radius = size.minDimension / 2f
        // A sleeve is square, so the corner it is drawn in sits on the diagonal out of the middle.
        val corner = Offset(0.7071f, 0.7071f)
        val pivot = Offset(size.width - radius * 0.14f, size.height - radius * 0.14f)
        // Where the stylus sits, as a share of the radius: out at the rim before a take is put on,
        // in over the grooves once it is running.
        val tipShare = radius * (0.93f + (0.62f - 0.93f) * laidDown)
        val tip = center + corner * tipShare

        // Grooves, closer together toward the rim, and dark enough to read on a bright cover.
        val groove = Color.Black.copy(alpha = 0.26f)
        val grooveWidth = radius * 0.008f
        var share = 0.52f
        while (share < 0.98f) {
            drawCircle(groove, radius = radius * share, center = center, style = Stroke(width = grooveWidth))
            share += if (share < 0.75f) 0.055f else 0.03f
        }
        drawCircle(
            Color(0xE60C0C0E),
            radius = radius * 0.975f,
            center = center,
            style = Stroke(width = radius * 0.05f),
        )
        // The ring the label ends at, and the spindle it turns round.
        drawCircle(
            Color.Black.copy(alpha = 0.45f),
            radius = radius * 0.46f,
            center = center,
            style = Stroke(width = radius * 0.014f),
        )
        drawCircle(Color(0xFF141416), radius = radius * 0.045f, center = center)
        drawCircle(
            Color.White.copy(alpha = 0.35f),
            radius = radius * 0.045f,
            center = center,
            style = Stroke(width = radius * 0.006f),
        )

        val steel = Color(0xFFC9CBD1)
        drawCircle(steel, radius = radius * 0.055f, center = pivot + (pivot - tip) * 0.22f)
        drawCircle(steel, radius = radius * 0.075f, center = pivot)
        drawCircle(
            Color.Black.copy(alpha = 0.5f),
            radius = radius * 0.075f,
            center = pivot,
            style = Stroke(width = radius * 0.012f),
        )
        // The arm's shadow first, then the metal it is laid on.
        drawLine(
            Color.Black.copy(alpha = 0.35f),
            pivot,
            tip,
            strokeWidth = radius * 0.034f,
            cap = StrokeCap.Round,
        )
        drawLine(steel, pivot, tip, strokeWidth = radius * 0.02f, cap = StrokeCap.Round)
        drawCircle(Color(0xFF2A2A2E), radius = radius * 0.05f, center = tip)
    }
}
