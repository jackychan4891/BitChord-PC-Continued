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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate

/**
 * The turntable drawn over the landscape sleeve, so the cover reads as a record on a deck rather
 * than a square in a case.
 *
 * It paints only what does not turn — the grooves, the rim, the centre and the arm — and lies over
 * artwork the sleeve itself spins. Every measure is a fraction of the sleeve's own radius, so the
 * deck keeps its proportions at any window size and while the player docks into the bar.
 */
@Composable
fun TurntableDeck(isPlaying: Boolean, reduceAnimation: Boolean) {
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

        // ---- The tonearm ----
        // Built from the bearing out: a counterweight behind the pivot to balance the headshell, a
        // tube that narrows as it goes, and a headshell set a few degrees off the tube so the
        // stylus meets the groove at an angle rather than point-first. Every part is cast with a
        // shadow offset to one side, which is what makes it read as metal lying over a record
        // instead of marks drawn on one.
        val steel = Color(0xFFC9CBD1)
        val midSteel = Color(0xFF8E9096)
        val darkSteel = Color(0xFF55575C)
        val shade = Color.Black.copy(alpha = 0.42f)
        val drop = Offset(radius * 0.014f, radius * 0.014f)
        val reach = tip - pivot
        val reachLength = reach.getDistance()
        val along = if (reachLength == 0f) Offset(1f, 0f) else reach / reachLength
        val across = Offset(-along.y, along.x)
        /** A point on the tube, `t` of the way to the stylus and `lift` across it. */
        fun onArm(t: Float, lift: Float = 0f) = pivot + reach * t + across * (radius * lift)
        val headAt = 0.84f

        // The counterweight drum, and the collar it is threaded onto.
        val weight = pivot - along * (radius * 0.13f)
        drawCircle(shade, radius * 0.072f, weight + drop)
        drawCircle(darkSteel, radius * 0.072f, weight)
        drawCircle(midSteel, radius * 0.046f, weight)
        drawCircle(
            Color.White.copy(alpha = 0.16f),
            radius = radius * 0.072f,
            center = weight,
            style = Stroke(width = radius * 0.006f),
        )

        // The bearing: plate, collar, the hole the arm turns on, and the light along its top edge.
        drawCircle(shade, radius * 0.088f, pivot + drop)
        drawCircle(Color(0xFF232326), radius * 0.084f, pivot)
        drawCircle(steel, radius * 0.06f, pivot)
        drawCircle(Color(0xFF0E0E10), radius * 0.024f, pivot)
        drawArc(
            Color.White.copy(alpha = 0.3f),
            startAngle = 195f,
            sweepAngle = 150f,
            useCenter = false,
            topLeft = Offset(pivot.x - radius * 0.06f, pivot.y - radius * 0.06f),
            size = Size(radius * 0.12f, radius * 0.12f),
            style = Stroke(width = radius * 0.008f),
        )

        // Cueing lever, thrown to the side of the pivot: down when the stylus is on the record.
        val lever = if (laidDown > 0.5f) 1f else 0f
        val leverRoot = pivot + across * (radius * 0.02f)
        val leverTip = pivot + across * (radius * (0.02f + 0.11f * lever)) + along * (radius * 0.03f)
        drawLine(
            shade,
            leverRoot + drop,
            leverTip + drop,
            strokeWidth = radius * 0.022f,
            cap = StrokeCap.Round,
        )
        drawLine(
            midSteel,
            leverRoot,
            leverTip,
            strokeWidth = radius * 0.012f,
            cap = StrokeCap.Round,
        )
        // The grip on its end: a moulded pad across the rod, which is the bit a hand catches.
        // Ended bare, the lever read as a wire stopping in mid-air.
        val leverRun = leverTip - leverRoot
        val leverAlong = if (leverRun.getDistance() == 0f) across else leverRun / leverRun.getDistance()
        val gripAcross = Offset(-leverAlong.y, leverAlong.x)
        val gripCenter = leverTip + leverAlong * (radius * 0.008f)
        val gripHalf = radius * 0.024f
        drawLine(
            shade,
            gripCenter - gripAcross * gripHalf + drop,
            gripCenter + gripAcross * gripHalf + drop,
            strokeWidth = radius * 0.026f,
            cap = StrokeCap.Round,
        )
        drawLine(
            Color(0xFF26262B),
            gripCenter - gripAcross * gripHalf,
            gripCenter + gripAcross * gripHalf,
            strokeWidth = radius * 0.024f,
            cap = StrokeCap.Round,
        )
        drawLine(
            Color.White.copy(alpha = 0.2f),
            gripCenter - gripAcross * (gripHalf * 0.55f),
            gripCenter + gripAcross * (gripHalf * 0.55f),
            strokeWidth = radius * 0.006f,
            cap = StrokeCap.Round,
        )

        // The tube: shadow, body, the highlight down its upper edge and the anti-skate wire under it.
        drawLine(
            shade,
            pivot + drop,
            onArm(headAt) + drop,
            strokeWidth = radius * 0.03f,
            cap = StrokeCap.Round,
        )
        drawLine(darkSteel, pivot, onArm(headAt), strokeWidth = radius * 0.026f, cap = StrokeCap.Round)
        drawLine(steel, pivot, onArm(headAt), strokeWidth = radius * 0.016f, cap = StrokeCap.Round)
        drawLine(
            Color.White.copy(alpha = 0.42f),
            onArm(0.08f, 0.005f),
            onArm(headAt, 0.004f),
            strokeWidth = radius * 0.004f,
            cap = StrokeCap.Round,
        )
        drawLine(
            midSteel,
            onArm(0.12f, -0.009f),
            onArm(headAt, -0.007f),
            strokeWidth = radius * 0.005f,
            cap = StrokeCap.Round,
        )

        // The headshell. A black one vanishes over a dark cover, and the arm read as though it
        // stopped in mid-air; the shell is turned a few degrees off the tube like a real one,
        // carries a light rim and a finger lift, and the cartridge and stylus finish the line.
        val heading = kotlin.math.atan2(along.y, along.x) * 57.29578f + 14f
        val shellLength = radius * 0.19f
        val shellWidth = radius * 0.084f
        translate(tip.x, tip.y) {
            // Turned about the stylus itself: rotate() otherwise pivots on the middle of the
            // canvas, which swung the whole headshell off the arm and out of sight.
            rotate(heading, pivot = Offset.Zero) {
                drawRoundRect(
                    shade,
                    topLeft = Offset(-shellLength + drop.x, -shellWidth / 2f + drop.y),
                    size = Size(shellLength, shellWidth),
                    cornerRadius = CornerRadius(radius * 0.022f),
                )
                drawRoundRect(
                    Color(0xFF191A1D),
                    topLeft = Offset(-shellLength, -shellWidth / 2f),
                    size = Size(shellLength, shellWidth),
                    cornerRadius = CornerRadius(radius * 0.022f),
                )
                drawRoundRect(
                    Color.White.copy(alpha = 0.28f),
                    topLeft = Offset(-shellLength, -shellWidth / 2f),
                    size = Size(shellLength, shellWidth),
                    cornerRadius = CornerRadius(radius * 0.022f),
                    style = Stroke(width = radius * 0.005f),
                )
                // The collar the tube enters, and the brushed plate on the shell's back.
                drawRoundRect(
                    midSteel,
                    topLeft = Offset(-shellLength, -shellWidth / 2f),
                    size = Size(radius * 0.026f, shellWidth),
                )
                drawRoundRect(
                    Color(0xFF4C4E54),
                    topLeft = Offset(-shellLength + radius * 0.035f, -shellWidth * 0.28f),
                    size = Size(shellLength * 0.55f, shellWidth * 0.56f),
                    cornerRadius = CornerRadius(radius * 0.01f),
                )
                drawLine(
                    Color.White.copy(alpha = 0.38f),
                    Offset(-shellLength + radius * 0.04f, -shellWidth * 0.22f),
                    Offset(-shellLength + radius * 0.135f, -shellWidth * 0.22f),
                    strokeWidth = radius * 0.004f,
                    cap = StrokeCap.Round,
                )
                // The finger lift, out over the grooves: the tab a hand catches.
                drawLine(
                    shade,
                    Offset(-radius * 0.055f, -shellWidth * 0.42f) + drop,
                    Offset(-radius * 0.012f, -radius * 0.105f) + drop,
                    strokeWidth = radius * 0.02f,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    steel,
                    Offset(-radius * 0.055f, -shellWidth * 0.42f),
                    Offset(-radius * 0.012f, -radius * 0.105f),
                    strokeWidth = radius * 0.014f,
                    cap = StrokeCap.Round,
                )
                drawCircle(Color(0xFFD8D9DD), radius * 0.017f, Offset(-radius * 0.012f, -radius * 0.105f))
                // The cartridge under the nose, and the stylus meeting the groove at the tip.
                drawRoundRect(
                    Color(0xFF33353A),
                    topLeft = Offset(-radius * 0.07f, -radius * 0.018f),
                    size = Size(radius * 0.055f, radius * 0.062f),
                    cornerRadius = CornerRadius(radius * 0.012f),
                )
                drawLine(
                    Color(0xFFC9CBD1),
                    Offset(-radius * 0.018f, radius * 0.02f),
                    Offset(radius * 0.002f, radius * 0.012f),
                    strokeWidth = radius * 0.007f,
                    cap = StrokeCap.Round,
                )
                drawCircle(Color(0xFFF2F2F4), radius * 0.012f, Offset(radius * 0.004f, radius * 0.012f))
            }
        }
    }
}
