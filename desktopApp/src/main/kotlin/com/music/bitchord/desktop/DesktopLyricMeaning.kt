package com.music.bitchord.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** A line the reader right-clicked, and the song it belongs to. */
internal data class DesktopLyricMeaningRequest(val song: com.music.bitchord.data.model.Song, val line: String)

/**
 * What Genius wrote over the lyric line, asked for by right-clicking it.
 *
 * The lookup is the annotation, not the song's blurb — a reader who points at one line wants that
 * line explained. Genius annotates selectively, so a line nobody has written about says so rather
 * than answering with something adjacent, and the one action offered is the page itself.
 */
@Composable
internal fun DesktopLyricMeaningDialog(request: DesktopLyricMeaningRequest, onDismiss: () -> Unit) {
    var meaning by remember(request) { mutableStateOf<DesktopGenius.LineMeaning?>(null) }
    var answered by remember(request) { mutableStateOf(false) }
    LaunchedEffect(request) {
        answered = false
        meaning = DesktopGenius.lineMeaning(request.song.title, request.song.artist, request.line)
        answered = true
    }
    val result = meaning

    DesktopDialogPanel(onDismiss = onDismiss, maxWidth = 460, popup = true, frosted = true) {
        Column(
            Modifier.padding(
                start = panelInset(22.dp),
                end = panelInset(22.dp),
                top = 20.dp,
                bottom = 4.dp,
            ),
        ) {
            Text(
                DesktopStrings["d_line_meaning", "Line meaning"],
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
            )
            Text(
                DesktopStrings["d_line_meaning_source", "Annotated by Genius"],
                style = MaterialTheme.typography.bodySmall,
                color = DesktopSecondary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Column(
            Modifier
                .then(desktopPanelBody(cardMax = 400.dp))
                .verticalScroll(rememberScrollState())
                .padding(horizontal = panelInset(22.dp), vertical = 12.dp),
        ) {
            Text(
                "\u201C${request.line}\u201D",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = Color.White,
            )
            Spacer(Modifier.height(12.dp))
            when {
                !answered -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        color = DesktopAccent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        DesktopStrings["d_line_meaning_looking", "Looking this line up…"],
                        style = MaterialTheme.typography.bodyMedium,
                        color = DesktopSecondary,
                    )
                }
                result == null -> Text(
                    DesktopStrings[
                        "d_line_meaning_missing",
                        "Genius has no annotation for this line.",
                    ],
                    style = MaterialTheme.typography.bodyMedium,
                    color = DesktopSecondary,
                )
                else -> Text(
                    result.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = panelInset(20.dp), vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (result != null) {
                TextButton(onClick = { DesktopExternalLinks.open(result.url) }) {
                    Text(
                        DesktopStrings["d_open_on_genius", "Open on Genius"],
                        color = DesktopAccent,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onDismiss) {
                Text(DesktopStrings["done", "Done"], color = DesktopAccent)
            }
        }
    }
}
