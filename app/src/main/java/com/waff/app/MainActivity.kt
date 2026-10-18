package com.waff.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.waff.app.core.PlatformLink
import com.waff.app.core.UrlRouter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sharedText = intent?.getCharSequenceExtra(android.content.Intent.EXTRA_TEXT)?.toString().orEmpty()
        setContent { WaffApp(sharedText) }
    }
}

private val Ink = androidx.compose.ui.graphics.Color(0xFF000000)
private val Paper = androidx.compose.ui.graphics.Color(0xFFFFFFFF)
private val Surface = androidx.compose.ui.graphics.Color(0xFF0A0A0A)
private val Muted = androidx.compose.ui.graphics.Color(0xFF9E9E9E)
private val Line = androidx.compose.ui.graphics.Color(0xFF2A2A2A)

@Composable
private fun WaffApp(sharedText: String) {
    var selected by remember { mutableStateOf("Home") }
    var link by remember { mutableStateOf(sharedText) }
    var feedback by remember { mutableStateOf<String?>(null) }
    MaterialTheme(colorScheme = darkColorScheme(
        primary = Paper, onPrimary = Ink, background = Ink, onBackground = Paper,
        surface = Surface, onSurface = Paper, surfaceVariant = androidx.compose.ui.graphics.Color(0xFF111111),
        onSurfaceVariant = Muted, outline = Line, error = Paper, onError = Ink
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
                "Home" -> HomeScreen(link, { link = it }, feedback, { feedback = it }, Modifier.padding(padding))
                "Settings" -> SimpleScreen("Settings", "Preferences will appear here as they become functional.", Modifier.padding(padding))
                else -> SimpleScreen(selected, when (selected) {
                    "Browser" -> "Browse the web and send a page link to WAFF. Media detection is not available yet."
                    "Queue" -> "No downloads yet. Downloads will appear here when the transfer engine is available."
                    else -> "Completed files will appear here after a verified download."
                }, Modifier.padding(padding))
            }
        }
    }
}

@Composable
private fun HomeScreen(link: String, onLinkChange: (String) -> Unit, feedback: String?, setFeedback: (String?) -> Unit, modifier: Modifier = Modifier) {
    val clipboard = LocalClipboardManager.current
    val focus = LocalFocusManager.current
    Column(modifier.fillMaxSize().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Spacer(Modifier.height(24.dp))
        Text("Save media.\nKeep it yours.", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Medium, lineHeight = 38.sp)
        Text("Paste a link to check whether WAFF can work with it.", style = MaterialTheme.typography.bodyMedium, color = Muted)
        OutlinedTextField(value = link, onValueChange = { onLinkChange(it); setFeedback(null) }, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Paste a video or post link", color = Muted) }, leadingIcon = { Icon(Icons.Outlined.Link, null) },
            singleLine = true, shape = RoundedCornerShape(12.dp), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { focus.moveFocus(FocusDirection.Down) }),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Paper, unfocusedBorderColor = Line, cursorColor = Paper, focusedTextColor = Paper, unfocusedTextColor = Paper))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { val clip = clipboard.getText()?.text; if (!clip.isNullOrBlank()) { onLinkChange(clip); setFeedback("Link pasted") } else setFeedback("Clipboard is empty") }, shape = RoundedCornerShape(10.dp)) {
                Icon(Icons.Outlined.ContentPaste, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Paste")
            }
            Button(onClick = {
                val result = UrlRouter.parse(link)
                setFeedback(when (result) {
                    is PlatformLink.Supported -> "${result.platform.label} link recognized. Download extraction is not implemented yet."
                    PlatformLink.Invalid -> "Enter a valid web link to continue."
                    PlatformLink.Unsupported -> "This source is not supported yet."
                })
                focus.clearFocus()
            }, enabled = link.isNotBlank(), shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = Paper, contentColor = Ink)) {
                Text("Continue", fontWeight = FontWeight.SemiBold); Spacer(Modifier.width(8.dp)); Icon(Icons.Outlined.ArrowForward, null, Modifier.size(18.dp))
            }
        }
        if (feedback != null) Text(feedback, color = Muted, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Outlined.VerifiedUser, null, tint = Muted, modifier = Modifier.size(18.dp))
            Text("Private by design. Links stay on your device.", color = Muted, style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun SimpleScreen(title: String, message: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Medium)
        HorizontalDivider(color = Line)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = Muted)
    }
}
