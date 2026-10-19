package com.waff.app.core

import android.app.DownloadManager
import android.content.Context
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
    val status: Int,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val reason: Int
) {
    val progressPercent: Int? get() = if (totalBytes > 0) ((bytesDownloaded * 100) / totalBytes).toInt().coerceIn(0, 100) else null
}

/** Handles direct, publicly reachable media files. It does not extract media from social posts or protected streams. */
class DirectDownloadRepository(context: Context) {
    private val app = context.applicationContext
    private val manager = app.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val preferences = app.getSharedPreferences("direct_downloads", Context.MODE_PRIVATE)

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
                if (code !in 200..299) return@withContext EnqueueResult.Rejected("The server did not provide a downloadable file (HTTP $code). Try a direct media-file URL.")
                val type = DirectMediaRules.normalizedMime(connection.contentType)
                if (!DirectMediaRules.isMediaMime(type)) {
                    return@withContext EnqueueResult.Rejected("This link does not identify a direct image, audio, or video file.")
                }
                type
            } finally { connection.disconnect() }
        } catch (_: Exception) {
            return@withContext EnqueueResult.Rejected("Could not inspect that link. Check your connection and try again.")
        }
        val mime = response
        val filename = uniqueFilename(filename(uri.lastPathSegment.orEmpty(), mime))
        try {
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle(filename)
                .setDescription("WAFF · direct media")
                .setMimeType(mime)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "WAFF/$filename")
            val id = manager.enqueue(request)
            preferences.edit()
                .putString(id.toString(), filename)
                .putString("$id.mime", mime)
                .apply()
            EnqueueResult.Added(id, filename, mime)
        } catch (_: Exception) {
            EnqueueResult.Rejected("Android could not start this download. Check that Downloads is available and try again.")
        }
    }

    fun snapshots(): List<TransferSnapshot> {
        val ids = preferences.all.keys.mapNotNull { it.toLongOrNull() }
        if (ids.isEmpty()) return emptyList()
        val query = DownloadManager.Query().setFilterById(*ids.toLongArray())
        val cursor = runCatching { manager.query(query) }.getOrNull() ?: return emptyList()
        return cursor.use { c -> buildList { while (c.moveToNext()) add(c.snapshot()) } }.sortedByDescending { it.id }
    }

    fun cancel(id: Long) {
        manager.remove(id)
        preferences.edit().remove(id.toString()).remove("$id.mime").apply()
    }

    suspend fun retry(id: Long): EnqueueResult {
        val source = originalUrl(id)
            ?: return EnqueueResult.Rejected("The original link is no longer available in Android Downloads.")
        return when (val result = enqueue(source)) {
            is EnqueueResult.Added -> { cancel(id); result }
            is EnqueueResult.Rejected -> result
        }
    }

    fun open(id: Long, context: Context): Boolean {
        val uri = manager.getUriForDownloadedFile(id) ?: return false
        val mime = preferences.getString("$id.mime", "application/octet-stream") ?: "application/octet-stream"
        return runCatching {
            context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        }.isSuccess
    }

    fun share(id: Long, context: Context): Boolean {
        val uri = manager.getUriForDownloadedFile(id) ?: return false
        val mime = preferences.getString("$id.mime", "application/octet-stream") ?: "application/octet-stream"
        return runCatching {
            val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = mime
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(android.content.Intent.createChooser(send, "Share media"))
        }.isSuccess
    }

    private fun originalUrl(id: Long): String? {
        val cursor = runCatching { manager.query(DownloadManager.Query().setFilterById(id)) }.getOrNull() ?: return null
        return cursor.use { c -> if (c.moveToFirst()) c.getColumnIndex(DownloadManager.COLUMN_URI).takeIf { it >= 0 }?.let(c::getString) else null }
    }

    private fun uniqueFilename(candidate: String): String {
        val existing = preferences.all.keys.filter { it.toLongOrNull() != null }
            .mapNotNull { preferences.getString(it, null) }.toSet()
        if (candidate !in existing) return candidate
        val stem = candidate.substringBeforeLast('.', candidate)
        val suffix = candidate.substringAfterLast('.', "").takeIf { it.isNotBlank() }?.let { ".$it" }.orEmpty()
        var index = 2
        while ("$stem ($index)$suffix" in existing) index++
        return "$stem ($index)$suffix"
    }

    private fun Cursor.snapshot(): TransferSnapshot {
        fun int(column: String) = getColumnIndex(column).takeIf { it >= 0 }?.let(::getInt) ?: 0
        fun long(column: String) = getColumnIndex(column).takeIf { it >= 0 }?.let(::getLong) ?: 0L
        val id = long(DownloadManager.COLUMN_ID)
        val name = preferences.getString(id.toString(), "Media download") ?: "Media download"
        return TransferSnapshot(id, name, int(DownloadManager.COLUMN_STATUS), long(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR), long(DownloadManager.COLUMN_TOTAL_SIZE_BYTES), int(DownloadManager.COLUMN_REASON))
    }

    private fun filename(candidate: String, mime: String): String {
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)?.let { ".$it" } ?: ""
        val stem = candidate.substringBeforeLast('.', candidate).replace(Regex("[^A-Za-z0-9._-]"), "_").trim('.', '_').take(80)
            .ifBlank { "media" }
        // The response MIME type is the authority. Never preserve a misleading URL suffix (for example, .jpg on a video).
        val suffix = ext.removePrefix(".")
        return if (suffix.isBlank()) stem.take(100) else "$stem.$suffix".take(100)
    }
}
