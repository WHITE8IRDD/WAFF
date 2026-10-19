package com.waff.app

import android.app.DownloadManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.waff.app.core.DirectDownloadRepository
import com.waff.app.core.EnqueueResult
import com.waff.app.core.Platform
import com.waff.app.core.PlatformLink
import com.waff.app.core.UrlRouter
import com.waff.app.core.TransferSnapshot
import com.waff.app.platform.newpipe.ExtractedMediaInfo
import com.waff.app.platform.newpipe.NewPipeMediaExtractor
import com.waff.app.platform.newpipe.YtDlpMediaExtractor
import com.waff.app.platform.newpipe.MediaFormatPolicy
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var incomingLink by mutableStateOf("")
    private var incomingIntentVersion by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        incomingLink = sharedText(intent)
        setContent { WaffApp(incomingLink, incomingIntentVersion) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingLink = sharedText(intent)
        incomingIntentVersion++
    }

    private fun sharedText(source: Intent?): String = when (source?.action) {
        Intent.ACTION_SEND -> source.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty()
        Intent.ACTION_VIEW -> source.dataString.orEmpty()
        else -> ""
    }
}

internal val Ink = androidx.compose.ui.graphics.Color(0xFF000000)
internal val Paper = androidx.compose.ui.graphics.Color(0xFFFFFFFF)
internal val Muted = androidx.compose.ui.graphics.Color(0xFF9E9E9E)
internal val Line = androidx.compose.ui.graphics.Color(0xFF2A2A2A)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WaffApp(sharedText: String, intentVersion: Int) {
    var selected by remember { mutableStateOf("Home") }
    var link by remember { mutableStateOf(sharedText) }
    var feedback by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(intentVersion) {
        if (sharedText.isNotBlank()) { link = sharedText; selected = "Home"; feedback = null }
    }
    val context = LocalContext.current
    val repository = remember { DirectDownloadRepository(context) }
    MaterialTheme(colorScheme = darkColorScheme(
        primary = Paper, onPrimary = Ink, background = Ink, onBackground = Paper,
        surface = androidx.compose.ui.graphics.Color(0xFF0A0A0A), onSurface = Paper,
        surfaceVariant = androidx.compose.ui.graphics.Color(0xFF111111), onSurfaceVariant = Muted,
        outline = Line, error = Paper, onError = Ink
    )) {
        Scaffold(
            containerColor = Ink,
            topBar = { TopAppBar(title = { Text("WAFF", fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp) }, actions = {
                IconButton(onClick = { selected = "Settings" }) { Icon(Icons.Outlined.Settings, contentDescription = "Settings") }
            }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Ink, titleContentColor = Paper, actionIconContentColor = Paper)) },
            bottomBar = {
                NavigationBar(containerColor = Ink, contentColor = Paper) {
                    listOf("Home" to Icons.Outlined.Home, "Browser" to Icons.Outlined.Language,
                        "Queue" to Icons.Outlined.Download, "History" to Icons.Outlined.History).forEach { (label, icon) ->
                        NavigationBarItem(selected == label, onClick = { selected = label; feedback = null },
                            icon = { Icon(icon, contentDescription = label) }, label = { Text(label) },
                            colors = NavigationBarItemDefaults.colors(selectedIconColor = Ink, selectedTextColor = Paper,
                                indicatorColor = Paper, unselectedIconColor = Muted, unselectedTextColor = Muted))
                    }
                }
            }
        ) { padding ->
            when (selected) {
                "Home" -> HomeScreen(link, { link = it }, feedback, { feedback = it }, repository,
                    { selected = "Queue" }, Modifier.padding(padding))
                "Browser" -> BrowserScreen(repository, { selected = "Queue" }, Modifier.padding(padding))
                "Queue" -> TransferList(repository, historyOnly = false, Modifier.padding(padding))
                "History" -> TransferList(repository, historyOnly = true, Modifier.padding(padding))
                "Settings" -> SettingsScreen(Modifier.padding(padding))
                else -> SimpleScreen("Browser", "An embedded browser is not available yet. Share a direct media-file link to WAFF to download it.", Modifier.padding(padding))
            }
        }
    }
}

@Composable
private fun HomeScreen(
    link: String,
    onLinkChange: (String) -> Unit,
    feedback: String?,
    setFeedback: (String?) -> Unit,
    repository: DirectDownloadRepository,
    onQueued: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboardManager.current
    val focus = LocalFocusManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mediaExtractor = remember { NewPipeMediaExtractor() }
    val ytDlpExtractor = remember(context) { YtDlpMediaExtractor(context) }
    var mediaInfo by remember { mutableStateOf<ExtractedMediaInfo?>(null) }
    var selectedFormatId by remember { mutableStateOf<String?>(null) }
    var selectedVideoFormatId by remember { mutableStateOf<String?>(null) }
    var selectedAudioFormatId by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val parsedLink = UrlRouter.parse(link)
    val extractorUrl = when (parsedLink) {
        is PlatformLink.Supported -> parsedLink.canonicalUrl
        PlatformLink.Unsupported -> link.trim().let { raw ->
            when {
                raw.startsWith("www.", ignoreCase = true) -> "https://$raw"
                raw.startsWith("http://", ignoreCase = true) -> "https://${raw.substringAfter("://")}"
                else -> raw
            }
        }
        PlatformLink.Invalid -> null
    }
    val newPipeCanExtract = extractorUrl?.let(mediaExtractor::supportsUrl) == true
    val socialNeedsFallback = (parsedLink as? PlatformLink.Supported)?.platform?.let { it in setOf(Platform.INSTAGRAM, Platform.TIKTOK, Platform.FACEBOOK) } == true
    val canExtract = newPipeCanExtract || socialNeedsFallback
    Column(modifier.fillMaxSize().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Spacer(Modifier.height(24.dp))
        Text("Save media.\nKeep it yours.", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Medium, lineHeight = 38.sp)
        Text("Paste a supported media page or a direct file URL.", style = MaterialTheme.typography.bodyMedium, color = Muted)
        OutlinedTextField(value = link, onValueChange = { onLinkChange(it); setFeedback(null); mediaInfo = null; selectedFormatId = null; selectedVideoFormatId = null; selectedAudioFormatId = null }, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Paste a direct media link", color = Muted) }, leadingIcon = { Icon(Icons.Outlined.Link, null) },
            singleLine = true, shape = RoundedCornerShape(12.dp), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { focus.moveFocus(FocusDirection.Down) }),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Paper, unfocusedBorderColor = Line, cursorColor = Paper, focusedTextColor = Paper, unfocusedTextColor = Paper))
        if (parsedLink is PlatformLink.Supported && !canExtract) {
            Text("${parsedLink.platform.label} page extraction is unavailable in this build. Paste a direct media URL instead.", color = Muted, style = MaterialTheme.typography.bodySmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = {
                val clip = clipboard.getText()?.text
                if (!clip.isNullOrBlank()) { onLinkChange(clip.trim()); mediaInfo = null; selectedFormatId = null; setFeedback("Link pasted") } else setFeedback("Clipboard is empty")
            }, shape = RoundedCornerShape(10.dp)) {
                Icon(Icons.Outlined.ContentPaste, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Paste")
            }
            Button(onClick = {
                focus.clearFocus()
                busy = true
                scope.launch {
                    try {
                        val info = mediaInfo
                        if (canExtract && info == null) {
                            val extracted = if (socialNeedsFallback) ytDlpExtractor.extract(extractorUrl.orEmpty())
                                else mediaExtractor.extract(extractorUrl.orEmpty())
                            mediaInfo = extracted
                            selectedFormatId = MediaFormatPolicy.defaultFormat(extracted)?.id
                            selectedVideoFormatId = extracted.formats.firstOrNull { it.kind == com.waff.app.platform.newpipe.ExtractedFormatKind.VIDEO_ONLY }?.id
                            selectedAudioFormatId = extracted.formats.firstOrNull { it.kind == com.waff.app.platform.newpipe.ExtractedFormatKind.AUDIO }?.id
                            setFeedback(if (extracted.formats.isEmpty()) "No downloadable formats were returned for this media." else null)
                        } else if (info != null) {
                            val format = info.formats.firstOrNull { it.id == selectedFormatId }
                            if (format == null) setFeedback("Select one of the formats returned by the extractor.")
                            else when (val result = repository.enqueueResolvedMedia(format.url, info.title, format.mimeType, format.headers)) {
                                is EnqueueResult.Added -> { setFeedback("Download started · ${result.filename}"); onQueued() }
                                is EnqueueResult.Rejected -> setFeedback(result.reason)
                            }
                        } else {
                            when (val result = repository.enqueue(link)) {
                                is EnqueueResult.Added -> { setFeedback("Download started · ${result.filename}"); onQueued() }
                                is EnqueueResult.Rejected -> setFeedback(result.reason)
                            }
                        }
                    } catch (_: java.io.IOException) {
                        setFeedback("Network request failed. Check your connection and try again.")
                    } catch (_: Exception) {
                        setFeedback("The extractor could not read this media page. It may be unavailable, restricted, rate-limited, or unsupported.")
                    } finally { busy = false }
                }
            }, enabled = link.isNotBlank() && !busy && (mediaInfo == null || selectedFormatId != null), shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Paper, contentColor = Ink)) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Ink)
                else Text(if (canExtract && mediaInfo == null) "Get formats" else if (mediaInfo != null) "Download selected" else "Download", fontWeight = FontWeight.SemiBold)
                if (!busy) { Spacer(Modifier.width(8.dp)); Icon(Icons.Outlined.ArrowDownward, null, Modifier.size(18.dp)) }
            }
        }
        mediaInfo?.let { info ->
            ExtractedFormatsPanel(info, selectedFormatId, { selectedFormatId = it },
                selectedVideoFormatId, { selectedVideoFormatId = it },
                selectedAudioFormatId, { selectedAudioFormatId = it },
                onMerge = {
                    val videoId = selectedVideoFormatId
                    val audioId = selectedAudioFormatId
                    if (videoId != null && audioId != null) scope.launch {
                        if (busy) return@launch
                        busy = true
                        try {
                            when (val result = repository.enqueueMerged(extractorUrl.orEmpty(), info.title, videoId, audioId)) {
                                is EnqueueResult.Added -> { setFeedback("Merge queued · ${result.filename}"); onQueued() }
                                is EnqueueResult.Rejected -> setFeedback(result.reason)
                            }
                        } finally { busy = false }
                    }
                },
                onSaveSubtitle = { subtitle ->
                    scope.launch {
                        if (busy) return@launch
                        busy = true
                        try {
                            when (val result = repository.enqueueResolvedSubtitle(subtitle.url, info.title, subtitle.languageTag, subtitle.extension)) {
                                is EnqueueResult.Added -> { setFeedback("Subtitle queued · ${result.filename}"); onQueued() }
                                is EnqueueResult.Rejected -> setFeedback(result.reason)
                            }
                        } finally { busy = false }
                    }
                },
                onSaveThumbnail = {
                    scope.launch {
                        if (busy) return@launch
                        busy = true
                        try {
                            val imageUrl = info.thumbnailUrl.orEmpty()
                            when (val result = repository.enqueueResolvedMedia(imageUrl, "${info.title} thumbnail", "image/*")) {
                                is EnqueueResult.Added -> { setFeedback("Thumbnail queued · ${result.filename}"); onQueued() }
                                is EnqueueResult.Rejected -> setFeedback(result.reason)
                            }
                        } finally { busy = false }
                    }
                })
        }
        if (feedback != null) Text(feedback, color = Muted, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Outlined.VerifiedUser, null, tint = Muted, modifier = Modifier.size(18.dp))
            Text("No WAFF account required. Links go directly to their source.", color = Muted, style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun TransferList(repository: DirectDownloadRepository, historyOnly: Boolean, modifier: Modifier = Modifier) {
    var transfers by remember { mutableStateOf(emptyList<TransferSnapshot>()) }
    var query by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<TransferSnapshot?>(null) }
    var feedback by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, repository) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) scope.launch { transfers = repository.snapshots() }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(repository) {
        while (true) { transfers = repository.snapshots(); delay(1000) }
    }
    val visible = transfers.filter { if (historyOnly) it.status == DownloadManager.STATUS_SUCCESSFUL else it.status != DownloadManager.STATUS_SUCCESSFUL }
        .filter { !historyOnly || it.filename.contains(query.trim(), ignoreCase = true) }
    Column(modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Text(if (historyOnly) "History" else "Queue", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 20.dp, bottom = 16.dp))
        if (historyOnly) OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            singleLine = true, placeholder = { Text("Search downloaded files", color = Muted) }, leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Paper, unfocusedBorderColor = Line, cursorColor = Paper, focusedTextColor = Paper, unfocusedTextColor = Paper))
        if (feedback != null) Text(feedback!!, color = Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp))
        if (visible.isEmpty()) {
            val message = when {
                historyOnly && query.isNotBlank() -> "No downloads match “${query.trim()}”."
                historyOnly -> "No completed direct downloads yet."
                else -> "No active downloads. Add a direct media-file link to get started."
            }
            Text(message, style = MaterialTheme.typography.bodyMedium, color = Muted, modifier = Modifier.padding(top = 8.dp))
        } else LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(visible, key = { it.id }) { transfer ->
                TransferRow(transfer,
                    onCancel = { scope.launch { repository.cancel(transfer.id); transfers = repository.snapshots() } },
                    onRetry = { scope.launch {
                        when (val result = repository.retry(transfer.id)) {
                            is EnqueueResult.Added -> feedback = "Retry started · ${result.filename}"
                            is EnqueueResult.Rejected -> feedback = result.reason
                        }
                        transfers = repository.snapshots()
                    } },
                    onOpen = { feedback = if (repository.open(transfer, context)) null else "No app could open this file, or it is no longer available." },
                    onShare = { feedback = if (repository.share(transfer, context)) null else "This file is no longer available to share." },
                    onDelete = { pendingDelete = transfer })
            }
        }
    }
    pendingDelete?.let { transfer ->
        AlertDialog(onDismissRequest = { pendingDelete = null },
            title = { Text("Delete downloaded file?") },
            text = { Text("${transfer.filename} will be removed from device storage and WAFF history.") },
            confirmButton = { TextButton(onClick = { scope.launch { repository.cancel(transfer.id); pendingDelete = null; transfers = repository.snapshots() } }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Keep file") } },
            containerColor = androidx.compose.ui.graphics.Color(0xFF111111), titleContentColor = Paper, textContentColor = Muted)
    }
}

@Composable
private fun TransferRow(transfer: TransferSnapshot, onCancel: () -> Unit, onRetry: () -> Unit, onOpen: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit) {
    val status = when (transfer.status) {
        DownloadManager.STATUS_PENDING -> "Waiting"
        DownloadManager.STATUS_RUNNING -> "Downloading"
        DownloadManager.STATUS_PAUSED -> "Paused by Android"
        DownloadManager.STATUS_SUCCESSFUL -> "Complete"
        DownloadManager.STATUS_FAILED -> "Failed · ${failureText(transfer.reason)}"
        else -> "Checking status"
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(transfer.filename, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                Text(status, style = MaterialTheme.typography.bodySmall, color = Muted)
            }
            if (transfer.status == DownloadManager.STATUS_SUCCESSFUL) {
                IconButton(onClick = onOpen) { Icon(Icons.Outlined.OpenInNew, contentDescription = "Open downloaded file") }
                IconButton(onClick = onShare) { Icon(Icons.Outlined.Share, contentDescription = "Share downloaded file") }
                IconButton(onClick = onDelete) { Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete downloaded file") }
            } else {
                if (transfer.status == DownloadManager.STATUS_FAILED) IconButton(onClick = onRetry) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Retry failed download")
                }
                IconButton(onClick = onCancel) { Icon(Icons.Outlined.Close, contentDescription = "Cancel and remove download") }
            }
        }
        if (transfer.status == DownloadManager.STATUS_RUNNING || transfer.status == DownloadManager.STATUS_PENDING) {
            if (transfer.totalBytes > 0) {
                LinearProgressIndicator(progress = { transfer.bytesDownloaded.toFloat() / transfer.totalBytes }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp), color = Paper, trackColor = Line)
                Text("${formatBytes(transfer.bytesDownloaded)} of ${formatBytes(transfer.totalBytes)}", style = MaterialTheme.typography.labelSmall, color = Muted, modifier = Modifier.padding(top = 4.dp))
            } else LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 10.dp), color = Paper, trackColor = Line)
        }
        HorizontalDivider(color = Line, modifier = Modifier.padding(top = 10.dp))
    }
}

private fun failureText(reason: Int): String = when (reason) {
    DownloadManager.ERROR_CANNOT_RESUME -> "cannot resume"
    DownloadManager.ERROR_DEVICE_NOT_FOUND -> "storage unavailable"
    DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "file already exists"
    DownloadManager.ERROR_FILE_ERROR -> "file error"
    DownloadManager.ERROR_HTTP_DATA_ERROR -> "network transfer error"
    DownloadManager.ERROR_INSUFFICIENT_SPACE -> "not enough storage"
    DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "too many redirects"
    DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "server rejected request"
    DownloadManager.ERROR_UNKNOWN -> "unknown error"
    else -> "download error"
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
    else -> "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
}

@Composable
private fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var confirmClear by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<String?>(null) }
    val appVersion = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "Unknown"
    }
    Column(modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Medium)
        HorizontalDivider(color = Line)
        Text("Privacy", style = MaterialTheme.typography.titleMedium)
        Text("WAFF Browser stores site data in Android WebView. Clear saved cookies, cached files, and form data from this device.",
            style = MaterialTheme.typography.bodySmall, color = Muted)
        OutlinedButton(onClick = { confirmClear = true }) {
            Icon(Icons.Outlined.DeleteOutline, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Clear browser data")
        }
        if (feedback != null) Text(feedback.orEmpty(), style = MaterialTheme.typography.bodySmall, color = Muted)
        Spacer(Modifier.height(8.dp))
        Text("About", style = MaterialTheme.typography.titleMedium)
        Text("WAFF · version $appVersion", style = MaterialTheme.typography.bodySmall, color = Muted)
        Text("Downloads are processed on this device. WAFF has no account, ad SDK, or analytics SDK.",
            style = MaterialTheme.typography.bodySmall, color = Muted)
    }
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("Clear browser data?") },
        text = { Text("This removes cached pages, cookies, and saved form data for WAFF Browser on this device.") },
        confirmButton = {
            TextButton(onClick = {
                confirmClear = false
                runCatching {
                    android.webkit.WebStorage.getInstance().deleteAllData()
                    android.webkit.CookieManager.getInstance().removeAllCookies { }
                    android.webkit.WebViewDatabase.getInstance(context).apply {
                        clearFormData()
                        clearHttpAuthUsernamePassword()
                        clearUsernamePassword()
                    }
                    android.webkit.WebView(context).apply {
                        clearCache(true)
                        clearHistory()
                        clearFormData()
                        destroy()
                    }
                    feedback = "Browser data cleared on this device."
                }.onFailure { feedback = "Could not clear all browser data. Try again." }
            }) { Text("Clear data") }
        },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        containerColor = androidx.compose.ui.graphics.Color(0xFF111111), titleContentColor = Paper, textContentColor = Muted
    )
}

@Composable
private fun SimpleScreen(title: String, message: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Medium)
        HorizontalDivider(color = Line)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = Muted)
    }
}
