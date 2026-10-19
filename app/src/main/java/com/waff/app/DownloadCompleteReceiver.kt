package com.waff.app

import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.waff.app.core.DownloadDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Posts a WAFF-specific success notification only after DownloadManager confirms completion. */
class DownloadCompleteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
        val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        if (id < 0) return
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val record = DownloadDatabase.get(app).downloadRecordDao().getById(id) ?: return@launch
                val manager = app.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                val cursor = manager.query(DownloadManager.Query().setFilterById(id)) ?: return@launch
                val success = cursor.use {
                    it.moveToFirst() && it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) == DownloadManager.STATUS_SUCCESSFUL
                }
                if (!success) return@launch
                val notifications = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (Build.VERSION.SDK_INT >= 26) {
                    notifications.createNotificationChannel(NotificationChannel(CHANNEL, "Download complete", NotificationManager.IMPORTANCE_DEFAULT))
                }
                val open = PendingIntent.getActivity(app, 0, Intent(app, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                val notification = NotificationCompat.Builder(app, CHANNEL)
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setContentTitle("Download successful")
                    .setContentText(record.filename)
                    .setStyle(NotificationCompat.BigTextStyle().bigText("${record.filename} was downloaded successfully."))
                    .setContentIntent(open)
                    .setAutoCancel(true)
                    .setCategory(NotificationCompat.CATEGORY_STATUS)
                    .build()
                if (NotificationManagerCompat.from(app).areNotificationsEnabled()) {
                    runCatching { notifications.notify(id.hashCode(), notification) }
                }
            } finally { pending.finish() }
        }
    }

    companion object { private const val CHANNEL = "waff_download_complete" }
}
