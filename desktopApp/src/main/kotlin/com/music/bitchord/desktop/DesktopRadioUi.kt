package com.music.bitchord.desktop

import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.ui.components.contextClick

/** Apple's radio page keeps its squares 20 apart across, and a little more from one row to the next. */
private val RadioTileGap = 20.dp
private val RadioRowGap = 24.dp

/**
 * A station square is never smaller than this, nor asked to grow past that.
 *
 * The upper bound is what the page looks like at a wide window: three squares of this carry the
 * row across the whole content width, and past it the row would stop filling rather than become a
 * wall of artwork. Two rows of these, plus the names under them, are about a screen tall.
 */
private val MinRadioTile = 140.dp
private val MaxRadioTile = 420.dp

/** One station on the radio page, with whatever is known about what it is carrying. */
internal data class DesktopRadioTile(
    val key: String,
    val title: String,
    /** The record on air, read off the station's own stream. */
    val subtitle: String?,
    val artworkUrl: String?,
    val playing: Boolean = false,
)

/**
 * The radio page's stations: squares in a grid that wraps.
 *
 * Three to a row, so the six free stations make two even rows and the page is filled. Six across —
 * which is what fits the width — left the whole lower half of it empty, with squares too small to
 * read the name under. A tile takes whatever the width offers up to [MaxRadioTile], and a narrow
 * window drops to two columns.
 */
@Composable
internal fun DesktopRadioTileGrid(
    /** Null on a page that already names itself, which the radio page does. */
    heading: String?,
    tiles: List<DesktopRadioTile>,
    onOpen: (DesktopRadioTile) -> Unit,
    onHide: (DesktopRadioTile) -> Unit,
) {
    Column(Modifier.padding(top = if (heading != null) 25.dp else 22.dp, bottom = 5.dp)) {
        heading?.let { DesktopAppleHeading(it, Modifier.padding(horizontal = DesktopPageGutter, vertical = 12.dp)) }
        BoxWithConstraints(Modifier.padding(horizontal = DesktopPageGutter)) {
            val columns = if (maxWidth < 620.dp) 2 else 3
            val tile = ((maxWidth - RadioTileGap * (columns - 1)) / columns).coerceIn(MinRadioTile, MaxRadioTile)
            Column(verticalArrangement = Arrangement.spacedBy(RadioRowGap)) {
                tiles.chunked(columns).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(RadioTileGap)) {
                        row.forEach { item ->
                            DesktopRadioTileView(item, tile, { onOpen(item) }, { onHide(item) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DesktopRadioTileView(
    tile: DesktopRadioTile,
    size: Dp,
    onOpen: () -> Unit,
    onHide: () -> Unit,
) {
    val pressed = remember { MutableInteractionSource() }
    val hovered by pressed.collectIsHoveredAsState()
    // The station's own menu, opened where its square was right-clicked. A tile is not a row, so it
    // carries no ⋯ of its own; holding the pointer on it is what asks, and the answer is a single
    // thing: this station need not be on the page.
    var menu by remember(tile.key) { mutableStateOf(false) }
    Box {
        Column(Modifier.width(size).hoverable(pressed)) {
            // Only the square takes the wash — the words belong to the tile and start the station like
            // the square does, but they are not the thing being pointed at.
            Box(
                Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp))
                    .desktopHoverFill(corner = 8.dp).desktopQuietClick(onClick = onOpen)
                    .contextClick { menu = true },
            ) {
                DesktopArtwork(tile.artworkUrl, Modifier.fillMaxSize(), px = 600)
            }
            Column(Modifier.desktopQuietClick(onClick = onOpen)) {
                Text(
                    tile.title,
                    fontSize = 15.sp,
                    fontWeight = if (tile.playing) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (tile.playing) DesktopAccent else DesktopAppleText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 9.dp),
                )
                tile.subtitle?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        fontSize = 12.sp,
                        color = DesktopAppleSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
        DesktopDropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(DesktopStrings["d_radio_hide", "Hide this station"]) },
                onClick = {
                    menu = false
                    onHide()
                },
            )
        }
    }
}
