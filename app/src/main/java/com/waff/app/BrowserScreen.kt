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
    var candidate by remember { mutableStateOf<String?>(null) }
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
    DisposableEffect(webView) {
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
                    webView?.loadUrl(normalizeAddress(address))
                }),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Paper, unfocusedBorderColor = Line, cursorColor = Paper, focusedTextColor = Paper, unfocusedTextColor = Paper)
            )
            IconButton(onClick = {
                focus.clearFocus()
                val url = normalizeAddress(address)
                address = url
                webView?.loadUrl(url)
            }, enabled = address.isNotBlank()) { Icon(Icons.Outlined.ArrowForward, contentDescription = "Go to address") }
        }

        AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            factory = { viewContext ->
                WebView(viewContext).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    settings.safeBrowsingEnabled = true
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            return request.url.scheme !in setOf("https", "http")
                        }

                        override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                            address = url
                            candidate = null
                            message = null
                        }

                        override fun onPageFinished(view: WebView, url: String) {
                            canGoBack = view.canGoBack()
                        }
                    }
                    setDownloadListener { url, _, _, mimeType, _ ->
                        if (DirectMediaRules.isMediaMime(mimeType)) {
                            candidate = url
                            message = "A direct ${DirectMediaRules.normalizedMime(mimeType)} file is available."
                        } else {
                            candidate = null
                            message = "This download is not a supported image, audio, or video file."
                        }
                    }
                    webView = this
                }
            },
            update = { view -> if (webView !== view) webView = view }
        )

        candidate?.let { url ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(message ?: "Media link detected", style = MaterialTheme.typography.bodySmall, color = Muted)
                    Text(url, style = MaterialTheme.typography.labelSmall, color = Muted, maxLines = 1)
                }
                Button(onClick = {
                    busy = true
                    scope.launch {
                        when (val result = repository.enqueue(url)) {
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
    if (trimmed.startsWith("https://") || trimmed.startsWith("http://")) return trimmed
    return "https://$trimmed"
}
