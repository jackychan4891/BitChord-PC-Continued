package com.music.bitchord.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.music.bitchord.data.model.Song

/**
 * The hairline around a thumbnail.
 *
 * Always light-on-dark: this target paints its own dark scheme whatever Windows is set to, so the
 * system-theme check that used to pick a black ring drew a solid border over the ambient glow and
 * made every square look die-cut rather than transparent.
 */
fun Modifier.thumbnailBorder(shape: Shape): Modifier = this.border(
    width = 1.dp,
    color = Color.White.copy(alpha = 0.15f),
    shape = shape,
)

/**
 * The square a thumbnail sits on while it loads.
 *
 * Translucent rather than the theme's opaque `surfaceVariant`: an opaque dark fill behind a
 * picture that does not exactly fill its box reads as a black border once the ambient artwork
 * glow is on, which is what the browse and search rows used to show.
 */
fun Modifier.thumbnailPlaceholder(shape: Shape): Modifier =
    this.background(Color.White.copy(alpha = 0.06f), shape)

/** A song title with the catalogue-standard outlined E for explicit audio. */
@Composable
fun ExplicitSongTitle(
    song: Song,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (song.isExplicit == true) {
            Text(
                text = "E",
                style = MaterialTheme.typography.labelSmall,
                color = color,
                modifier = Modifier
                    .border(1.dp, color.copy(alpha = 0.72f), RoundedCornerShape(2.dp))
                    .padding(horizontal = 3.dp),
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = song.title,
            style = style,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}
