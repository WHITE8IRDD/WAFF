package com.waff.app.platform.newpipe

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.waff.app.core.DirectDownloadRepository
import com.waff.app.core.EnqueueResult
import kotlinx.coroutines.CancellationException

/**
 * Extracts and queues YouTube media without launching WAFF's UI.
 *
 * The work request is expedited so a share from YouTube starts promptly even while the app is in the
 * background. The progress notification is best-effort: Android 12+ can forbid starting a foreground
 * service from the background (WorkManager's setForeground throws in that case), and extraction then
 * continues silently — completion is still announced by DownloadCompleteReceiver. Failures always
 * post their own notification so a quick share never fails invisibly.
 */
class QuickYouTubeDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    /** Required for expedited work on Android 11 and lower, where WorkManager runs it as a foreground service. */
    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo()

    override suspend fun doWork(): Result {
        val url = inputData.getString(KEY_URL)?.takeIf { it.startsWith("https://") }
            ?: return failure("The shared link is not a valid YouTube address.")
        try {
            try {
                setForeground(foregroundInfo())
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                // Best-effort progress notification only; see the class doc.
            }
            val info = YtDlpMediaExtractor(applicationContext).extract(url)
            val format = MediaFormatPolicy.defaultFormat(info)
                ?: return failure("No combined stream was available for this video. Open WAFF to pick a quality.")
            return when (val result = DirectDownloadRepository(applicationContext).enqueueResolvedMedia(
                format.url, info.title, format.mimeType, format.headers,
                sourcePageUrl = info.canonicalUrl, formatLabel = format.label, hashtags = info.hashtags
            )) {
                is EnqueueResult.Added -> Result.success()
                is EnqueueResult.Rejected -> failure(result.reason)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            return failure("WAFF could not extract this YouTube link. Open WAFF to try another option.")
        }
    }

    private fun failure(message: String): Result {
        postOutcome("YouTube Quick Download failed", message)
        return Result.failure()
    }

    private fun postOutcome(title: String, text: String) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(STATUS_CHANNEL, "Quick download results", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val notification: Notification = NotificationCompat.Builder(applicationContext, STATUS_CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
        if (NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()) {
            runCatching { manager.notify(STATUS_NOTIFICATION_ID, notification) }
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
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val KEY_URL = "youtube_url"
        const val CHANNEL = "waff_quick_download"
        const val NOTIFICATION_ID = 8102
        const val STATUS_CHANNEL = "waff_quick_download_status"
        const val STATUS_NOTIFICATION_ID = 8103
    }
}
