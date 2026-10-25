package com.music.bitchord.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.music.bitchord.data.model.Song
import com.music.bitchord.ui.icons.BitChordIcons

/**
 * The signal chain behind what is playing, stage by stage: the stream that arrived, the decoder,
 * whether anything is being resampled, the processing on top, and the device it lands on.
 *
 * The player's quality badge opens this panel, and what it asks first is where the music should
 * come out, so the output devices lead — see [DesktopAudioDevices]. Above them sits what the song
 * is rather than how it arrived — see [AboutTrackStage].
 *
 * Android opens the same readout from the quality badge — see its `AudioPipelineDialog`.
 */
@Composable
internal fun DesktopAudioPipelineDialog(
    song: Song?,
    format: DesktopStreamFormat?,
    sourceName: String?,
    pipeline: DesktopAudioPipeline,
    onDismiss: () -> Unit,
) {
    DesktopDialogPanel(onDismiss = onDismiss, maxWidth = 460) {
        Column(
            // Bounded and scrollable: this is the same chain that already ran past most windows'
            // height, and the track, its devices and its source sit above it.
            Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .verticalScroll(rememberScrollState())
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                DesktopStrings["d_track_information", "Track information"],
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            if (song != null) TrackHeader(song)
            if (song != null) AboutTrackStage(song)
            OutputChooser()
            // The engine's own copy, so Source and Decoder cannot disagree while an upgrade lands.
            val source = pipeline.sourceFormat ?: format
            PipelineStage(DesktopStrings["pipeline_source", "Source"]) {
                PipelineRow(DesktopStrings["pipeline_format", "Format"], source?.codecLabel)
                PipelineRow(DesktopStrings["pipeline_bitrate", "Bitrate"], source?.kbps?.let { "$it kbps" })
                PipelineRow(
                    DesktopStrings["pipeline_sample_rate", "Sample rate"],
                    source?.sampleRateHz?.let { "${it / 1000.0} kHz" },
                )
                PipelineRow(DesktopStrings["pipeline_bit_depth", "Bit depth"], source?.bitDepth?.let { "$it-bit" })
                PipelineRow(DesktopStrings["pipeline_channels", "Channels"], channelLabel(source?.channels))
                PipelineRow(DesktopStrings["source", "Source"], sourceName)
            }
            PipelineStage(DesktopStrings["pipeline_decoder", "Decoder"]) {
                PipelineRow(DesktopStrings["pipeline_decoder_name", "Decoder"], pipeline.decoderLabel)
                PipelineRow(
                    DesktopStrings["pipeline_io_rate", "Decoded rate"],
                    pipeline.decodedSampleRateHz?.let { "${it / 1000.0} kHz" },
                )
                PipelineRow(DesktopStrings["pipeline_channels", "Channels"], channelLabel(pipeline.decodedChannels))
            }
            PipelineStage(DesktopStrings["pipeline_resampler", "Resampler"]) {
                PipelineRow(
                    DesktopStrings["pipeline_resampler", "Resampler"],
                    if (pipeline.resampling) {
                        "${pipeline.decodedSampleRateHz?.div(1000.0)} kHz → ${pipeline.outputSampleRateHz?.div(1000.0)} kHz"
                    } else {
                        DesktopStrings["d_bit_perfect", "Bit-perfect · no conversion"]
                    },
                )
            }
            PipelineStage(DesktopStrings["pipeline_dsp", "DSP"]) {
                PipelineRow(
                    DesktopStrings["equalizer", "Equalizer"],
                    if (pipeline.equalizerEnabled) DesktopStrings["on", "On"] else DesktopStrings["off", "Off"],
                )
                PipelineRow(
                    DesktopStrings["skip_silence", "Skip silence"],
                    if (pipeline.skipSilence) DesktopStrings["on", "On"] else DesktopStrings["off", "Off"],
                )
            }
            PipelineStage(DesktopStrings["pipeline_output_device", "Output"]) {
                PipelineRow(DesktopStrings["pipeline_output_api", "Output API"], "javax.sound")
                PipelineRow(DesktopStrings["pipeline_device_name", "Device"], pipeline.deviceName)
                PipelineRow(
                    DesktopStrings["pipeline_pcm_format", "PCM format"],
                    pipeline.outputBytesPerSample?.let {
                        if (pipeline.outputIsFloat == true) "32-bit float" else "${it * 8}-bit"
                    },
                )
                PipelineRow(
                    DesktopStrings["pipeline_sample_rate", "Sample rate"],
                    pipeline.outputSampleRateHz?.let { "${it / 1000.0} kHz" },
                )
                PipelineRow(DesktopStrings["pipeline_channels", "Channels"], channelLabel(pipeline.outputChannels))
                PipelineRow(
                    DesktopStrings["pipeline_buffers", "Buffer"],
                    pipeline.bufferBytes.takeIf { it > 0 }?.let { "${it / 1024} KB" },
                )
            }
        }
    }
}

/**
 * Where the music comes out. Enumerated when the panel opens rather than held, as in
 * [DesktopAudioOutputDialog]: devices come and go with what is plugged in, and choosing one here
 * takes effect immediately, like the settings page's own list.
 */
@Composable
private fun OutputChooser() {
    val selected by DesktopAudioDevices.selected.collectAsState()
    val changes by DesktopAudioDevices.changes.collectAsState()
    val devices = remember(changes) { DesktopAudioDevices.available() }
    Column {
        Text(
            DesktopStrings["audio_output", "Audio output"].uppercase(),
            color = DesktopSecondary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        Column(Modifier.fillMaxWidth().desktopCardInset(RoundedCornerShape(10.dp)).padding(vertical = 4.dp)) {
            ChoiceRow(
                DesktopStrings["d_system_default", "System default"],
                detail = null,
                selected = selected == DesktopAudioDevices.SYSTEM_DEFAULT,
                onClick = { DesktopAudioDevices.select(DesktopAudioDevices.SYSTEM_DEFAULT) },
            )
            devices.forEach { device ->
                ChoiceRow(
                    device.name,
                    detail = device.description,
                    selected = selected == device.id,
                    onClick = { DesktopAudioDevices.select(device.id) },
                )
            }
        }
    }
}

@Composable
private fun TrackHeader(song: Song) {
    Column(
        Modifier.fillMaxWidth().desktopCardInset(RoundedCornerShape(10.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            song.title,
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        // The byline says what the row elsewhere says, and here it is the caption of a measurement.
        Text(
            listOfNotNull(
                song.artist.takeIf(String::isNotBlank),
                song.albumName?.takeIf(String::isNotBlank),
            ).joinToString(" · "),
            color = DesktopSecondary,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * What the song is, in the words Genius prints under it. Quiet when Genius is quiet: the stage
 * arrives only with a paragraph in hand, as the rest of this panel lists nothing it cannot answer.
 */
@Composable
private fun AboutTrackStage(song: Song) {
    var bio by remember(song.videoId) { mutableStateOf<String?>(null) }
    LaunchedEffect(song.title, song.artist) { bio = DesktopGenius.about(song.title, song.artist) }
    val text = bio
    if (text.isNullOrBlank()) return
    PipelineStage(DesktopStrings["d_about_track", "About this song"]) {
        Text(
            text,
            color = Color.White,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun ChoiceRow(name: String, detail: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().desktopRowClickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                name,
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!detail.isNullOrBlank()) {
                Text(
                    detail,
                    color = DesktopSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        if (selected) {
            Icon(
                BitChordIcons.Check,
                DesktopStrings["selected", "Selected"],
                tint = DesktopAccent,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun PipelineStage(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            title.uppercase(),
            color = DesktopSecondary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        Column(Modifier.desktopCardInset(RoundedCornerShape(10.dp)).padding(vertical = 4.dp)) { content() }
    }
}

/** One measurement. Nothing the engine cannot answer is listed at all, rather than shown as "—". */
@Composable
private fun PipelineRow(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // The label keeps its room: a long value used to squeeze it to one character per line.
        Text(
            label,
            color = DesktopSecondary,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(0.9f),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            value,
            color = Color.White,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1.1f),
        )
    }
}

private fun channelLabel(channels: Int?): String? = when (channels) {
    null -> null
    1 -> DesktopStrings["mono", "Mono"]
    2 -> DesktopStrings["stereo", "Stereo"]
    else -> "$channels ch"
}
