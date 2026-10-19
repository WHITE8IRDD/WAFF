package com.waff.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/** Explicit share-sheet entry that forwards a public media link into WAFF's quick-save flow. */
class QuickDownloadActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sharedText = when (intent?.action) {
            Intent.ACTION_SEND -> intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty()
            Intent.ACTION_VIEW -> intent.dataString.orEmpty()
            else -> ""
        }.trim()
        val open = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, sharedText)
            putExtra(MainActivity.EXTRA_QUICK_DOWNLOAD, true)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(open)
        finish()
    }
}
