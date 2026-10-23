package com.music.bitchord.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.ui.components.contextClick
import com.music.bitchord.ui.icons.BitChordIcons
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Apple's radio page keeps its squares 20 apart across, and a little more from one row to the next. */
private val RadioTileGap = 20.dp
private val RadioRowGap = 24.dp

/** A tile is never asked to be narrower than this, nor allowed to grow past that. */
private val MinRadioTile = 140.dp
private val MaxRadioTile = 216.dp

/** One station on the radio page, with whatever is known about what it is carrying. */
internal data class DesktopRadioTile(
    val key: String,
    val title: String,
    /** The record on air, or the programme instead when the station has no record to name. */
    val subtitle: String?,
    val artworkUrl: String?,
    /** The two marks a station with no artwork of its own is drawn with. */
    val monogram: String?,
    val playing: Boolean = false,
    /** Whether pressing this tile's clock opens a day's schedule. */
    val hasSchedule: Boolean = false,
)

/**
 * The radio page's stations: squares in a grid that wraps.
 *
 * These used to run two-deep in a horizontal row that let them touch. Apple's own radio page spaces
 * them — as many to a row as the window takes at [MinRadioTile] each, capped at six so a wide window
 * gets bigger tiles rather than unreadable ones — and the gaps are drawn, not assumed.
 */
@Composable
internal fun DesktopRadioTileGrid(
    heading: String,
    tiles: List<DesktopRadioTile>,
    onOpen: (DesktopRadioTile) -> Unit,
    onInfo: (DesktopRadioTile) -> Unit,
    onHide: (DesktopRadioTile) -> Unit,
) {
    Column(Modifier.padding(top = 25.dp, bottom = 5.dp)) {
        DesktopAppleHeading(heading, Modifier.padding(horizontal = DesktopPageGutter, vertical = 12.dp))
        BoxWithConstraints(Modifier.padding(horizontal = DesktopPageGutter)) {
            val columns = ((maxWidth + RadioTileGap) / (MinRadioTile + RadioTileGap)).toInt().coerceIn(2, 6)
            val tile = ((maxWidth - RadioTileGap * (columns - 1)) / columns).coerceAtMost(MaxRadioTile)
            Column(verticalArrangement = Arrangement.spacedBy(RadioRowGap)) {
                tiles.chunked(columns).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(RadioTileGap)) {
                        row.forEach { item ->
                            DesktopRadioTileView(item, tile, { onOpen(item) }, { onInfo(item) }, { onHide(item) })
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
    onInfo: () -> Unit,
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
                // The BBC provides a picture for what it is carrying; the monogram stands in until
                // one has arrived, or for a station that never sends one.
                if (tile.artworkUrl != null) DesktopArtwork(tile.artworkUrl, Modifier.fillMaxSize(), px = 400)
                else if (tile.monogram != null) RadioMonogram(tile.monogram, tile.playing, Modifier.fillMaxSize())
                else DesktopArtwork(null, Modifier.fillMaxSize(), px = 400)
                if (tile.hasSchedule) Icon(
                    BitChordIcons.Clock,
                    DesktopStrings["d_radio_schedule", "Schedule"],
                    tint = Color.White,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(24.dp)
                        .alpha(if (hovered) 1f else 0f)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        .clickable(onClick = onInfo)
                        .padding(5.dp),
                )
            }
            Column(Modifier.desktopQuietClick(onClick = onOpen)) {
                Text(
                    tile.title,
                    fontSize = 13.sp,
                    fontWeight = if (tile.playing) FontWeight.Bold else FontWeight.Normal,
                    color = if (tile.playing) DesktopAccent else DesktopAppleText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 7.dp),
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

/**
 * The grounds a drawn station square gets its letters on.
 *
 * A station only falls back to its letters when its feed has sent no picture yet: the BBC serves
 * the current record's sleeve, which is what its tiles show. These are picked by a hash of the
 * letters, so a station is the colour it was yesterday.
 */
private val RadioMonogramGrounds = listOf(
    listOf(Color(0xFF8E2C48), Color(0xFF4A1226)),
    listOf(Color(0xFF2C5C8E), Color(0xFF122E4A)),
    listOf(Color(0xFF3F7A4E), Color(0xFF1A3A24)),
    listOf(Color(0xFF7A5A2C), Color(0xFF3A2A12)),
    listOf(Color(0xFF55438E), Color(0xFF281E4A)),
    listOf(Color(0xFF8E5A2C), Color(0xFF4A2C12)),
)

@Composable
private fun RadioMonogram(monogram: String, playing: Boolean, modifier: Modifier = Modifier) {
    val ground = RadioMonogramGrounds[(monogram.hashCode() and 0x7fffffff) % RadioMonogramGrounds.size]
    Box(modifier.background(Brush.linearGradient(ground)), contentAlignment = Alignment.Center) {
        Text(
            monogram,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = if (playing) 1f else 0.88f),
        )
    }
}

/**
 * A station's day, in running order, opened from the clock on its tile.
 *
 * The BBC's schedule is reported in UTC and listened to in the room's own hours, so every time is
 * moved before it is drawn, and every comparison is made on the instant rather than the clock face.
 * What has already run stays on the list and fades: a listener who wants the programme that was on
 * an hour ago needs to see that it was, and when.
 */
@Composable
internal fun DesktopBbcScheduleDialog(station: DesktopBbcStation, onDismiss: () -> Unit) {
    val programmes by produceState<List<DesktopBbcProgramme>>(emptyList(), station) {
        value = DesktopBbcRadio.schedule(station)
    }
    val clock = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val zone = remember { ZoneId.systemDefault() }
    val now = remember { Instant.now() }
    DesktopDialogPanel(onDismiss = onDismiss, maxWidth = 460) {
        Column(Modifier.padding(horizontal = panelInset(22.dp), vertical = 22.dp)) {
            Text(station.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                DesktopStrings["d_radio_schedule_subtitle", "Today's programme schedule"],
                color = DesktopSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(14.dp))
            if (programmes.isEmpty()) {
                Text(
                    DesktopStrings["d_radio_no_schedule", "No schedule is available for this station."],
                    color = DesktopSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            } else {
                Column(Modifier.fillMaxWidth().height(420.dp).verticalScroll(rememberScrollState())) {
                    programmes.forEach { programme ->
                        val live = programme.isRunning(now)
                        Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.Top) {
                            Text(
                                programme.start?.let { clock.format(it.atZoneSameInstant(zone)) }
                                    ?: DesktopStrings["d_radio_live", "Live"],
                                fontSize = 12.sp,
                                color = if (live) DesktopAccent else DesktopAppleSecondary,
                                fontWeight = if (live) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.width(52.dp),
                            )
                            Column(Modifier.weight(1f).alpha(if (programme.isOver(now)) 0.45f else 1f)) {
                                Text(
                                    programme.title,
                                    fontSize = 13.sp,
                                    color = DesktopAppleText,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                programme.summary?.let {
                                    Text(
                                        it,
                                        fontSize = 12.sp,
                                        color = DesktopAppleSecondary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                        HorizontalDivider(thickness = 0.5.dp, color = Color.White.copy(alpha = 0.07f))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                DesktopStrings["d_ok", "OK"],
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = DesktopAccent,
                modifier = Modifier.align(Alignment.End).clip(RoundedCornerShape(8.dp))
                    .desktopRowClickable(onClick = onDismiss).padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

/** The hour it is booked for has begun and has not finished. */
private fun DesktopBbcProgramme.isRunning(now: Instant): Boolean {
    val from = start?.toInstant() ?: return false
    val to = end?.toInstant() ?: return false
    return !now.isBefore(from) && now.isBefore(to)
}

private fun DesktopBbcProgramme.isOver(now: Instant): Boolean = end?.toInstant()?.let { !now.isBefore(it) } == true
