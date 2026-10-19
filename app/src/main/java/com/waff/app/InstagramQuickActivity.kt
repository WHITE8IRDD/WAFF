package com.waff.app

import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.waff.app.core.DirectDownloadRepository
import com.waff.app.core.EnqueueResult
import com.waff.app.platform.newpipe.ExtractedFormat
import com.waff.app.platform.newpipe.ExtractedFormatKind
import com.waff.app.platform.newpipe.ExtractedMediaInfo
import com.waff.app.platform.newpipe.YtDlpMediaExtractor
import kotlinx.coroutines.launch

class InstagramQuickActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.setDimAmount(.42f)
        window.setGravity(Gravity.BOTTOM)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Color.White, surface = Color(0xFF111111), onSurface = Color.White)) {
                InstagramQuickOverlay(intent.getStringExtra(EXTRA_URL).orEmpty(), onClose = { finish() })
            }
        }
    }

    companion object { const val EXTRA_URL = "com.waff.app.INSTAGRAM_URL" }
}

@Composable
private fun InstagramQuickOverlay(url: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var info by remember { mutableStateOf<ExtractedMediaInfo?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var downloading by remember { mutableStateOf(false) }
    var selectedFormats by remember(url) { mutableStateOf(emptyMap<Int, String>()) }
    var retryToken by remember { mutableIntStateOf(0) }
    val groups = remember(info?.formats) { info?.formats.orEmpty().filter { it.mediaIndex != null }.groupBy { it.mediaIndex!! }.toSortedMap() }

    LaunchedEffect(url, retryToken) {
        loading = true; error = null
        try { info = YtDlpMediaExtractor(context).extractInstagram(url) }
        catch (failure: Exception) {
            android.util.Log.e("WAFF", "Instagram quick extraction failed (${failure.javaClass.simpleName})")
            val diagnostic = failure.message.orEmpty().replace(Regex("https?://\\S+"), "[shared URL]").take(180)
            error = "Could not read this Instagram post (${failure.javaClass.simpleName})${diagnostic.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()}"
        } finally { loading = false }
    }

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .12f))) {
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(.98f).fillMaxHeight(.76f),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = Color(0xFF0A0A0A), tonalElevation = 12.dp
        ) {
            Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Instagram media", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(info?.title ?: "Preview and choose files", style = MaterialTheme.typography.bodySmall, color = Color.LightGray, maxLines = 1)
                    }
                    TextButton(onClick = onClose) { Text("Close") }
                }
                HorizontalDivider(color = Color.DarkGray, modifier = Modifier.padding(vertical = 8.dp))
                when {
                    loading -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator(color = Color.White)
                            Text("Finding post media…", color = Color.LightGray)
                        }
                    }
                    error != null -> Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                        Text(error.orEmpty(), color = Color.LightGray)
                        TextButton(onClick = { info = null; error = null; loading = true; retryToken++ }) { Text("Retry") }
                    }
                    groups.isEmpty() -> Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text("No downloadable images or videos were found.", color = Color.LightGray)
                    }
                    else -> {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("${groups.size} file(s)", modifier = Modifier.weight(1f), color = Color.LightGray)
                            TextButton(onClick = {
                                selectedFormats = if (selectedFormats.size == groups.size) emptyMap() else groups.mapNotNull { (index, formats) ->
                                    bestForItem(formats)?.let { index to it.id }
                                }.toMap()
                            }) { Text(if (selectedFormats.size == groups.size) "Deselect all" else "Select all") }
                        }
                        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(groups.entries.toList(), key = { it.key }) { (index, formats) ->
                                InstagramItemRow(index, formats, selectedFormats[index], onToggle = { checked ->
                                    selectedFormats = if (checked) bestForItem(formats)?.let { selectedFormats + (index to it.id) } ?: selectedFormats
                                        else selectedFormats - index
                                }, onFormatChange = { format -> selectedFormats = selectedFormats + (index to format.id) })
                            }
                        }
                        Button(onClick = {
                            val selected = selectedFormats.mapNotNull { (index, id) -> groups[index]?.firstOrNull { it.id == id } }
                            if (selected.isEmpty() || downloading) return@Button
                            downloading = true
                            scope.launch {
                                var count = 0
                                var failed: String? = null
                                val repository = DirectDownloadRepository(context)
                                selected.forEach { format ->
                                    if (failed == null) {
                                        val item = format.mediaIndex?.plus(1)
                                        when (val result = repository.enqueueResolvedMedia(
                                            format.url, "${info?.title ?: "Instagram"}${item?.let { " item $it" }.orEmpty()}",
                                            format.mimeType, format.headers,
                                            sourcePageUrl = info?.canonicalUrl ?: url,
                                            formatLabel = format.label, hashtags = info?.hashtags.orEmpty()
                                        )) {
                                            is EnqueueResult.Added -> count++
                                            is EnqueueResult.Rejected -> failed = result.reason
                                        }
                                    }
                                }
                                downloading = false
                                if (count > 0) { onClose() }
                                else error = failed ?: "No media was queued."
                            }
                        }, enabled = selectedFormats.isNotEmpty() && !downloading,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)) {
                            if (downloading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.Black)
                            else Text("Download selected (${selectedFormats.size})", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun InstagramItemRow(
    index: Int,
    formats: List<ExtractedFormat>,
    selectedId: String?,
    onToggle: (Boolean) -> Unit,
    onFormatChange: (ExtractedFormat) -> Unit
) {
    val chosen = formats.firstOrNull { it.id == selectedId } ?: bestForItem(formats)
    val isImage = formats.any { it.kind == ExtractedFormatKind.IMAGE }
    var menuOpen by remember(index) { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFF151515)) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(selectedId != null, onToggle)
            RemoteImagePreview(formats.firstNotNullOfOrNull { it.previewUrl })
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Item ${index + 1} · ${if (isImage) "Image" else "Video"}", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                if (!isImage && formats.size > 1) {
                    TextButton(onClick = { menuOpen = true }, enabled = selectedId != null, contentPadding = PaddingValues(0.dp)) {
                        Text(chosen?.let(::smallFormatLabel) ?: "Choose quality")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        formats.sortedWith(compareByDescending<ExtractedFormat> { it.height ?: 0 }.thenByDescending { it.bitrate ?: 0 }).forEach { format ->
                            DropdownMenuItem(text = { Text(smallFormatLabel(format)) }, onClick = { onFormatChange(format); menuOpen = false })
                        }
                    }
                } else chosen?.let { Text(it.mimeType, style = MaterialTheme.typography.labelSmall, color = Color.LightGray) }
            }
        }
    }
}

private fun bestForItem(formats: List<ExtractedFormat>): ExtractedFormat? {
    val comparator = compareBy<ExtractedFormat> { it.height ?: 0 }.thenBy { it.width ?: 0 }.thenBy { it.bitrate ?: 0 }
    return formats.filter { it.kind == ExtractedFormatKind.VIDEO_WITH_AUDIO }.maxWithOrNull(comparator)
        ?: formats.filter { it.kind == ExtractedFormatKind.VIDEO_ONLY }.maxWithOrNull(comparator)
        ?: formats.filter { it.kind == ExtractedFormatKind.IMAGE }.maxWithOrNull(comparator)
}

private fun smallFormatLabel(format: ExtractedFormat) = listOfNotNull(format.height?.let { "${it}p" }, format.codec, format.fileSizeBytes?.let { "${it / (1024 * 1024)} MB" }).joinToString(" · ").ifBlank { format.label }
