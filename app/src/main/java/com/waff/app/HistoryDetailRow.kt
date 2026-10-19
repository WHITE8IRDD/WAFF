package com.waff.app

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp

@Composable
internal fun HistoryDetailRow(label: String, value: String?, clipboard: ClipboardManager) {
    if (value.isNullOrBlank()) return
    Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Muted)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(value, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Paper)
            IconButton(onClick = { clipboard.setText(AnnotatedString(value)) }) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy $label", tint = Muted)
            }
        }
        HorizontalDivider(color = Line)
    }
}
