package com.waff.app.core

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

sealed interface EnqueueResult {
    data class Added(val id: Long, val filename: String, val mimeType: String) : EnqueueResult
    data class Rejected(val reason: String) : EnqueueResult
}

data class TransferSnapshot(
    val id: Long,
    val filename: String,
    val mimeType: String,
    val status: Int,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val reason: Int
) {
    val progressPercent: Int? get() = if (totalBytes > 0) ((bytesDownloaded * 100) / totalBytes).toInt().coerceIn(0, 100) else null
}

/** Handles direct, publicly reachable media files. Social-post extraction and protected streams are not supported. */
class DirectDownloadRepository(context: Context) {
    private val app = context.applicationContext
    private val manager = app.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val dao = DownloadDatabase.get(app).downloadRecordDao()

    suspend fun enqueue(rawUrl: String): EnqueueResult = withContext(Dispatchers.IO) {
        val parsed = UrlRouter.parse(rawUrl)
        val url = when (parsed) {
            is PlatformLink.Supported -> return@withContext EnqueueResult.Rejected("This is a platform page, not a direct media file. Platform extraction is not available yet.")
            PlatformLink.Unsupported -> rawUrl.trim().let { if (it.startsWith("www.", true)) "https://$it" else it }
            PlatformLink.Invalid -> return@withContext EnqueueResult.Rejected("Enter a valid HTTP or HTTPS media link.")
        }
        val uri = runCatching { Uri.parse(url) }.getOrNull()
            ?: return@withContext EnqueueResult.Rejected("The link is not valid.")
        if (uri.scheme != "https" || uri.host.isNullOrBlank()) {
            return@withContext EnqueueResult.Rejected("For a secure transfer, WAFF currently accepts HTTPS links only.")
        }
        val mime = try {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "HEAD"
                connectTimeout = 10_000
                readTimeout = 10_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "WAFF Android")
            }
            try {
                val code = connection.responseCode
                if (code !in 200..299) return@withContext EnqueueResult.Rejected("The server did not provide a downloadable file (HTTP $code). Try a direct media-file URL.")
                DirectMediaRules.normalizedMime(connection.contentType).also {
                    if (!DirectMediaRules.isMediaMime(it)) return@withContext EnqueueResult.Rejected("This link does not identify a direct image, audio, or video file.")
                }
            } finally { connection.disconnect() }
        } catch (_: Exception) {
            return@withContext EnqueueResult.Rejected("Could not inspect that link. Check your connection and try again.")
        }
        enqueueVerified(url, uri.lastPathSegment.orEmpty(), mime)
    }

    suspend fun enqueueResolvedMedia(url: String, title: String, expectedMime: String): EnqueueResult = withContext(Dispatchers.IO) {
        val uri = runCatching { Uri.parse(url) }.getOrNull()
            ?: return@withContext EnqueueResult.Rejected("The extractor returned an invalid media URL.")
        if (uri.scheme != "https" || uri.host.isNullOrBlank() || uri.userInfo != null) {
            return@withContext EnqueueResult.Rejected("The extracted stream URL is not a safe HTTPS address.")
        }
        val response = try {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "HEAD"
                connectTimeout = 10_000
                readTimeout = 10_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "WAFF Android")
            }
            try {
                val code = connection.responseCode
                if (code !in 200..299) return@withContext EnqueueResult.Rejected("The selected stream is unavailable (HTTP $code). Reopen the media details to refresh its link.")
                DirectMediaRules.normalizedMime(connection.contentType).also { actual ->
                    if (!DirectMediaRules.isMediaMime(actual) || actual.substringBefore('/') != expectedMime.substringBefore('/')) {
                        return@withContext EnqueueResult.Rejected("The source response does not match the selected media type; nothing was queued.")
                    }
                }
            } finally { connection.disconnect() }
        } catch (_: Exception) {
            return@withContext EnqueueResult.Rejected("Could not validate the selected media stream. Refresh the details and try again.")
        }
        enqueueVerified(url, title, response)
    }

    private suspend fun enqueueVerified(url: String, suggestedName: String, mime: String): EnqueueResult {
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
        val candidate = FileNameRules.withMimeExtension(suggestedName, extension)
        val filename = uniqueFilename(candidate)
        var id: Long? = null
        try {
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle(filename)
                .setDescription("WAFF · direct media")
                .setMimeType(mime)
                .addRequestHeader("User-Agent", "WAFF Android")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "WAFF/$filename")
            val downloadId = manager.enqueue(request)
            id = downloadId
            dao.insert(DownloadRecord(downloadId, filename, mime, System.currentTimeMillis()))
            EnqueueResult.Added(downloadId, filename, mime)
        } catch (_: Exception) {
            id?.let { manager.remove(it) }
            EnqueueResult.Rejected("Android could not start this download. Check that Downloads is available and try again.")
        }
    }

    suspend fun snapshots(): List<TransferSnapshot> = withContext(Dispatchers.IO) {
        val records = dao.getAll()
        if (records.isEmpty()) return@withContext emptyList()
        val byId = records.associateBy(DownloadRecord::id)
        val found = records.chunked(500).flatMap { batch ->
            val cursor = runCatching { manager.query(DownloadManager.Query().setFilterById(*batch.map(DownloadRecord::id).toLongArray())) }.getOrNull()
                ?: return@flatMap emptyList()
            cursor.use { c -> buildList { while (c.moveToNext()) {
                val id = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_ID))
                byId[id]?.let { add(c.snapshot(it)) }
            } } }
        }
        val activeIds = found.mapTo(HashSet(), TransferSnapshot::id)
        records.filterNot { it.id in activeIds }.forEach { dao.delete(it.id) }
        found.sortedByDescending { byId[it.id]?.createdAtMillis ?: 0L }
    }

    suspend fun cancel(id: Long) = withContext(Dispatchers.IO) {
        manager.remove(id)
        dao.delete(id)
    }

    suspend fun retry(id: Long): EnqueueResult {
        val source = originalUrl(id)
            ?: return EnqueueResult.Rejected("The original link is no longer available in Android Downloads.")
        return when (val result = enqueue(source)) {
            is EnqueueResult.Added -> { cancel(id); result }
            is EnqueueResult.Rejected -> result
        }
    }

    fun open(transfer: TransferSnapshot, context: Context): Boolean {
        val uri = manager.getUriForDownloadedFile(transfer.id) ?: return false
        return runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, transfer.mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        }.isSuccess
    }

    fun share(transfer: TransferSnapshot, context: Context): Boolean {
        val uri = manager.getUriForDownloadedFile(transfer.id) ?: return false
        return runCatching {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = transfer.mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, "Share media"))
        }.isSuccess
    }

    private suspend fun originalUrl(id: Long): String? = withContext(Dispatchers.IO) {
        val cursor = runCatching { manager.query(DownloadManager.Query().setFilterById(id)) }.getOrNull() ?: return@withContext null
        cursor.use { c -> if (c.moveToFirst()) c.getColumnIndex(DownloadManager.COLUMN_URI).takeIf { it >= 0 }?.let(c::getString) else null }
    }

    private suspend fun uniqueFilename(candidate: String): String {
        val existing = dao.getAll().mapTo(HashSet(), DownloadRecord::filename)
        return FileNameRules.unique(candidate, existing)
    }

    private fun Cursor.snapshot(record: DownloadRecord): TransferSnapshot {
        fun int(column: String) = getColumnIndex(column).takeIf { it >= 0 }?.let(::getInt) ?: 0
        fun long(column: String) = getColumnIndex(column).takeIf { it >= 0 }?.let(::getLong) ?: 0L
        return TransferSnapshot(record.id, record.filename, record.mimeType, int(DownloadManager.COLUMN_STATUS),
            long(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR), long(DownloadManager.COLUMN_TOTAL_SIZE_BYTES), int(DownloadManager.COLUMN_REASON))
    }

}
