package com.waff.app

import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.waff.app.core.DirectDownloadRepository
import com.waff.app.core.DirectMediaRules
import com.waff.app.core.EnqueueResult
import kotlinx.coroutines.launch

@Composable
internal fun BrowserScreen(
    repository: DirectDownloadRepository,
    onQueued: () -> Unit,
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    var address by remember { mutableStateOf("https://") }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var hasVisitedPage by remember { mutableStateOf(false) }
    var candidate by remember { mutableStateOf<String?>(null) }
    var candidateMime by remember { mutableStateOf("") }
    var candidateTitle by remember { mutableStateOf("") }
    var candidateHeaders by remember { mutableStateOf(emptyMap<String, String>()) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner, webView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> webView?.onResume()
                Lifecycle.Event.ON_PAUSE -> webView?.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(Unit) {
        onDispose { webView?.stopLoading(); webView?.destroy() }
    }

    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (canGoBack) webView?.goBack() }, enabled = canGoBack) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "Go back")
            }
            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("Enter a website address", color = Muted) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = {
                    focus.clearFocus()
                    hasVisitedPage = true
                    webView?.loadUrl(normalizeAddress(address))
                }),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Paper, unfocusedBorderColor = Line, cursorColor = Paper, focusedTextColor = Paper, unfocusedTextColor = Paper)
            )
            IconButton(onClick = {
                focus.clearFocus()
                val url = normalizeAddress(address)
                address = url
                hasVisitedPage = true
                webView?.loadUrl(url)
            }, enabled = address.isNotBlank()) { Icon(Icons.Outlined.ArrowForward, contentDescription = "Go to address") }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { viewContext ->
                WebView(viewContext).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    settings.safeBrowsingEnabled = true
                    android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val target = request.url
                            if (target.userInfo != null || target.host.isNullOrBlank()) return true
                            if (target.scheme.equals("http", ignoreCase = true)) {
                                view.loadUrl(target.buildUpon().scheme("https").build().toString())
                                return true
                            }
                            return !target.scheme.equals("https", ignoreCase = true)
                        }

                        override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                            address = url
                            candidate = null
                            candidateHeaders = emptyMap()
                            message = null
                        }

                        override fun onPageFinished(view: WebView, url: String) {
                            canGoBack = view.canGoBack()
                        }
                    }
                    setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
                        if (DirectMediaRules.isMediaMime(mimeType) && url.startsWith("https://", ignoreCase = true)) {
                            candidate = url
                            candidateMime = DirectMediaRules.normalizedMime(mimeType)
                            candidateTitle = android.webkit.URLUtil.guessFileName(url, contentDisposition, candidateMime)
                            val referer = this.url.orEmpty()
                            candidateHeaders = buildMap {
                                if (!userAgent.isNullOrBlank()) put("User-Agent", userAgent)
                                if (referer.startsWith("https://", ignoreCase = true)) put("Referer", referer)
                            }
                            message = "A direct ${candidateMime} file is available."
                        } else {
                            candidate = null
                            message = "Only direct HTTPS image, audio, or video downloads can be queued."
                        }
                    }
                    webView = this
                }
            },
            update = { view -> if (webView !== view) webView = view }
        )
        if (!hasVisitedPage) Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Browse the web", style = MaterialTheme.typography.titleMedium, color = Paper)
            Spacer(Modifier.height(8.dp))
            Text("Direct media download offers appear here when a page exposes them.", style = MaterialTheme.typography.bodySmall, color = Muted)
        }
        }

        candidate?.let { url ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(message ?: "Media link detected", style = MaterialTheme.typography.bodySmall, color = Muted)
                    Text(url, style = MaterialTheme.typography.labelSmall, color = Muted, maxLines = 1)
                }
                Button(onClick = {
                    busy = true
                    scope.launch {
                        when (val result = repository.enqueueResolvedMedia(url, candidateTitle, candidateMime, candidateHeaders)) {
                            is EnqueueResult.Added -> { candidate = null; message = "Download started · ${result.filename}"; onQueued() }
                            is EnqueueResult.Rejected -> message = result.reason
                        }
                        busy = false
                    }
                }, enabled = !busy, colors = ButtonDefaults.buttonColors(containerColor = Paper, contentColor = Ink)) {
                    if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Ink)
                    else Icon(Icons.Outlined.Download, contentDescription = "Download detected file")
                }
            }
            HorizontalDivider(color = Line)
        } ?: run {
            if (message != null) Text(message!!, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), color = Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun normalizeAddress(input: String): String {
    val trimmed = input.trim()
    if (trimmed.isBlank()) return "https://duckduckgo.com/"
    if (trimmed.contains(' ') || (!trimmed.contains('.') && !trimmed.startsWith("https://", true) && !trimmed.startsWith("http://", true))) {
        return "https://duckduckgo.com/?q=${android.net.Uri.encode(trimmed)}"
    }
    val secure = when {
        trimmed.startsWith("https://", ignoreCase = true) -> trimmed
        trimmed.startsWith("http://", ignoreCase = true) -> "https://${trimmed.substringAfter("://")}"
        else -> "https://$trimmed"
    }
    val uri = runCatching { android.net.Uri.parse(secure) }.getOrNull()
    return if (uri?.host.isNullOrBlank() || uri?.userInfo != null) "https://duckduckgo.com/?q=${android.net.Uri.encode(trimmed)}" else secure
}
