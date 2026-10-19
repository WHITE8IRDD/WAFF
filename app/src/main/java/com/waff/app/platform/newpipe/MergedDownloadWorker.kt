package com.waff.app.platform.newpipe

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.waff.app.core.DownloadDatabase
import com.waff.app.core.ManagedTransferRecord
import android.app.DownloadManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Persistent foreground yt-dlp job for selecting separate video/audio formats and muxing them locally. */
internal class MergedDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val dao = DownloadDatabase.get(context).managedTransferDao()
    private val transferId = inputData.getLong(KEY_TRANSFER_ID, 0L)

    override suspend fun doWork(): Result {
        val record = dao.getById(transferId) ?: return Result.failure()
        return try {
            setForeground(createForegroundInfo(record.filename))
            YoutubeDL.init(applicationContext)
            FFmpeg.getInstance().init(applicationContext)
            val parent = File(record.localPath).parentFile ?: return fail(record)
            if (!parent.exists() && !parent.mkdirs()) return fail(record)
            val outputTemplate = File(parent, record.filename.substringBeforeLast('.') + ".%(ext)s").absolutePath
            val request = YoutubeDLRequest(record.sourceUrl).apply {
                addOption("--no-playlist")
                addOption("--no-overwrites")
                addOption("-f", record.formatSelection)
                addOption("--merge-output-format", "mkv")
                addOption("-o", outputTemplate)
            }
            dao.updateProgress(transferId, DownloadManager.STATUS_RUNNING, record.bytesDownloaded, record.totalBytes, 0)
            val response = executeCancellable(request, "waff-$transferId", record)
            if (response.exitCode != 0) return fail(record)
            val output = File(parent, record.filename)
            if (!output.isFile || output.length() <= 0L) return fail(record)
            dao.updateProgress(transferId, DownloadManager.STATUS_SUCCESSFUL, output.length(), output.length(), 0)
            Result.success()
        } catch (_: CancellationException) {
            val paused = isStopped
            if (!paused) cleanupPartial(record)
            dao.updateProgress(transferId,
                if (paused) DownloadManager.STATUS_PAUSED else DownloadManager.STATUS_FAILED,
                record.bytesDownloaded, record.totalBytes, if (paused) 0 else DownloadManager.ERROR_UNKNOWN)
            Result.failure()
        } catch (_: Exception) {
            cleanupPartial(record)
            dao.updateProgress(transferId, DownloadManager.STATUS_FAILED, record.bytesDownloaded, record.totalBytes, DownloadManager.ERROR_UNKNOWN)
            Result.failure()
        }
    }

    private suspend fun executeCancellable(
        request: YoutubeDLRequest,
        processId: String,
        record: ManagedTransferRecord
    ) = suspendCancellableCoroutine<com.yausername.youtubedl_android.YoutubeDLResponse> { continuation ->
        val executor = Executors.newSingleThreadExecutor()
        val task = executor.submit {
            try {
                val response = YoutubeDL.getInstance().execute(request, processId) { progress, _, _ ->
                    val percent = progress.toInt().coerceIn(0, 99)
                    kotlinx.coroutines.runBlocking {
                        dao.updateProgress(record.id, DownloadManager.STATUS_RUNNING, record.bytesDownloaded, record.totalBytes, 0)
                        setProgress(androidx.work.workDataOf("progress" to percent))
                    }
                }
                continuation.resume(response)
            } catch (error: Throwable) {
                continuation.resumeWithException(error)
            } finally {
                executor.shutdown()
            }
        }
        continuation.invokeOnCancellation {
            runCatching { YoutubeDL.getInstance().destroyProcessById(processId) }
            task.cancel(true)
            executor.shutdownNow()
        }
    }

    private suspend fun fail(record: ManagedTransferRecord): Result {
        cleanupPartial(record)
        dao.updateProgress(record.id, DownloadManager.STATUS_FAILED, record.bytesDownloaded, record.totalBytes, DownloadManager.ERROR_UNKNOWN)
        return Result.failure()
    }

    private fun cleanupPartial(record: ManagedTransferRecord) {
        val parent = File(record.localPath).parentFile ?: return
        val stem = File(record.localPath).nameWithoutExtension
        parent.listFiles()?.filter { it.name == record.filename || it.name.startsWith("$stem.") }?.forEach { it.delete() }
    }

    private fun createForegroundInfo(title: String): ForegroundInfo {
        val channelId = "waff-merged-downloads"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(NotificationChannel(channelId, "Merged downloads", NotificationManager.IMPORTANCE_LOW))
        }
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Merging media")
            .setContentText(title)
            .setOngoing(true)
            .setProgress(100, 0, true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(transferId.toInt(), notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else ForegroundInfo(transferId.toInt(), notification)
    }

    companion object {
        const val KEY_TRANSFER_ID = "transfer_id"
    }
}
