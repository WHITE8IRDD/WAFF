package com.waff.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.waff.app.platform.youtube.YouTubeFormat
import com.waff.app.platform.youtube.YouTubeFormatKind
import com.waff.app.platform.youtube.YouTubeMediaInfo

@Composable
internal fun YouTubeFormatsPanel(info: YouTubeMediaInfo, selectedId: String?, onSelect: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
        Text(info.title, style = MaterialTheme.typography.titleMedium, color = Paper, maxLines = 2)
        if (info.durationSeconds > 0) Text(formatDuration(info.durationSeconds), color = Muted, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(8.dp))
        if (info.formats.isEmpty()) {
            Text("The extractor returned no downloadable formats.", color = Muted, style = MaterialTheme.typography.bodySmall)
        }
        if (info.subtitles.isNotEmpty()) {
            Text("${info.subtitles.size} subtitle tracks found · subtitle export is not supported yet.", color = Muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
        }
        listOf(
            YouTubeFormatKind.VIDEO_WITH_AUDIO to "Video with audio",
            YouTubeFormatKind.VIDEO_ONLY to "Video only · no audio",
            YouTubeFormatKind.AUDIO to "Audio only"
        ).forEach { (kind, title) ->
            val formats = info.formats.filter { it.kind == kind }
            if (formats.isNotEmpty()) {
                Text(title, style = MaterialTheme.typography.labelLarge, color = Paper, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
                formats.forEach { format -> FormatChoice(format, selectedId == format.id, onSelect) }
            }
        }
        if (info.formats.any { it.kind == YouTubeFormatKind.VIDEO_ONLY }) {
            Text("Video-only streams do not contain audio. WAFF does not merge streams in this preview.", color = Muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun FormatChoice(format: YouTubeFormat, selected: Boolean, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = { onSelect(format.id) }, modifier = Modifier.semantics { contentDescription = format.label })
        Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
            Text(format.label, color = Paper, style = MaterialTheme.typography.bodySmall, maxLines = 2)
            val details = listOfNotNull(format.mimeType, format.bitrate?.takeIf { it > 0 }?.let { "${it / 1000} kb/s" }).joinToString(" · ")
            if (details.isNotBlank()) Text(details, color = Muted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun formatDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val remainingSeconds = seconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, remainingSeconds)
    else "%d:%02d".format(minutes, remainingSeconds)
}

