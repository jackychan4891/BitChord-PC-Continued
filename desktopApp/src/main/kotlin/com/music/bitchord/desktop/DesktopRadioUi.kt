package com.music.bitchord.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.ui.components.contextClick
import com.music.bitchord.ui.icons.BitChordIcons

/** One station on the radio row, with whatever is known about what it is carrying. */
internal data class DesktopRadioTile(
    val key: String,
    val title: String,
    /** The record on air, read off the station's own stream. */
    val subtitle: String?,
    val artworkUrl: String?,
    val playing: Boolean = false,
)

/**
 * The stations as the site draws them: a sideways row of squares, captioned underneath.
 *
 * Apple does not give radio a page of its own — the stations are one shelf in the New feed, the
 * same shape as every other row of covers, so this borrows the shelf's own measures rather than
 * inventing a grid. The square is where the station is started; the name and the programme under it
 * start it too, as they do on the site.
 */
@Composable
internal fun DesktopRadioShelf(
    title: String,
    tiles: List<DesktopRadioTile>,
    onOpen: (DesktopRadioTile) -> Unit,
    onHide: (DesktopRadioTile) -> Unit,
    showHeading: Boolean = true,
    topPadding: Dp = 36.dp,
    /** Where the row's own page is, when the source names one; null leaves the name a label. */
    onOpenHeading: (() -> Unit)? = null,
) {
    if (tiles.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(top = topPadding)) {
        if (showHeading) {
            AppleFeedShelfHeading(
                title,
                Modifier.padding(horizontal = DesktopPageGutter),
                onOpenHeading,
            )
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = appleFeedColumns(AppleFeedLayout.Square, maxWidth)
            val width = appleFeedTileWidth(AppleFeedLayout.Square, maxWidth, columns)
            DesktopScrollableRow(gutter = DesktopPageGutter, spacing = AppleShelfGap) {
                items(tiles, key = { it.key }) { tile ->
                    DesktopRadioTileView(tile, width, { onOpen(tile) }, { onHide(tile) })
                }
            }
        }
    }
}

@Composable
private fun DesktopRadioTileView(
    tile: DesktopRadioTile,
    width: Dp,
    onOpen: () -> Unit,
    onHide: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    // The station's own menu, opened where its square was right-clicked. A tile carries no ⋯ of its
    // own: holding the pointer on it is what asks, and the answer is a single thing — this station
    // need not be on the page.
    var menu by remember(tile.key) { mutableStateOf(false) }
    Box {
        Column(Modifier.width(width).hoverable(interaction)) {
            Box(
                Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(7.dp))
                    .clickable(interaction, indication = null, onClick = onOpen)
                    .contextClick { menu = true },
            ) {
                DesktopArtwork(tile.artworkUrl, Modifier.fillMaxSize(), px = 400)
                // The site's squares answer the pointer with a play button over the art; a station
                // is played the same way a song is.
                if (hovered) {
                    Box(
                        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            BitChordIcons.TransportPlay,
                            null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
            Column(Modifier.fillMaxWidth().clickable(interaction, indication = null, onClick = onOpen)) {
                Text(
                    tile.title,
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                    fontWeight = if (tile.playing) FontWeight.Bold else FontWeight.Normal,
                    color = if (tile.playing) DesktopAccent else DesktopAppleText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp),
                )
                tile.subtitle?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        fontSize = 12.sp,
                        lineHeight = 15.sp,
                        color = DesktopAppleSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
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
