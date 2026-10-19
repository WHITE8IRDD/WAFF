package com.waff.app.platform.newpipe

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.waff.app.R
import com.waff.app.core.DirectDownloadRepository
import com.waff.app.core.EnqueueResult

/** Extracts and queues YouTube media without launching WAFF's UI. */
class QuickYouTubeDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val url = inputData.getString(KEY_URL)?.takeIf { it.startsWith("https://") } ?: return Result.failure()
        setForeground(foregroundInfo())
        return try {
            val info = YtDlpMediaExtractor(applicationContext).extract(url)
            val format = MediaFormatPolicy.defaultFormat(info) ?: return Result.failure()
            when (DirectDownloadRepository(applicationContext).enqueueResolvedMedia(
                format.url, info.title, format.mimeType, format.headers,
                sourcePageUrl = info.canonicalUrl, formatLabel = format.label, hashtags = info.hashtags
            )) {
                is EnqueueResult.Added -> Result.success()
                is EnqueueResult.Rejected -> Result.failure()
            }
        } catch (_: Exception) {
            Result.failure()
        }
    }

    private fun foregroundInfo(): ForegroundInfo {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "Quick downloads", NotificationManager.IMPORTANCE_LOW))
        }
        val notification: Notification = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("WAFF · Quick Download")
            .setContentText("Preparing your YouTube download")
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()
        return ForegroundInfo(NOTIFICATION_ID, notification)
    }

    companion object {
        const val KEY_URL = "youtube_url"
        const val CHANNEL = "waff_quick_download"
        const val NOTIFICATION_ID = 8102
    }
}
