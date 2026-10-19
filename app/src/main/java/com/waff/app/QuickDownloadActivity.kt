package com.waff.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.waff.app.core.Platform
import com.waff.app.core.PlatformLink
import com.waff.app.core.UrlRouter
import com.waff.app.core.SharedLinkParser
import com.waff.app.platform.newpipe.QuickYouTubeDownloadWorker

/** Quick route: YouTube is queued in the background; other links open the interactive picker. */
class QuickDownloadActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sharedText = when (intent?.action) {
            Intent.ACTION_SEND -> intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.let(SharedLinkParser::extract).orEmpty()
            Intent.ACTION_VIEW -> intent.dataString.orEmpty()
            else -> ""
        }.trim()
        val parsed = UrlRouter.parse(sharedText) as? PlatformLink.Supported
        if (parsed?.platform == Platform.INSTAGRAM) {
            startActivity(Intent(this, InstagramQuickActivity::class.java).putExtra(InstagramQuickActivity.EXTRA_URL, parsed.canonicalUrl))
            finish()
            return
        }
        if (parsed?.platform == Platform.YOUTUBE) {
            // Expedited so the share starts extraction promptly even after returning to YouTube;
            // falls back to a normal request when the expedited quota is exhausted.
            val request = OneTimeWorkRequestBuilder<QuickYouTubeDownloadWorker>()
                .setInputData(workDataOf(QuickYouTubeDownloadWorker.KEY_URL to parsed.canonicalUrl))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                "waff-quick-youtube-${parsed.canonicalUrl.hashCode()}", ExistingWorkPolicy.KEEP, request
            )
            finish()
            return
        }
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
