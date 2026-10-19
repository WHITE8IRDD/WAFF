package com.waff.app.platform.newpipe

import android.content.Context
import org.json.JSONObject
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.mapper.VideoFormat
import com.yausername.youtubedl_android.mapper.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/** On-device yt-dlp fallback for public pages not recognized by NewPipe. */
internal class YtDlpMediaExtractor(context: Context) {
    private val appContext = context.applicationContext

    suspend fun extract(url: String): ExtractedMediaInfo = withContext(Dispatchers.IO) {
        require(url.length <= 4096 && url.startsWith("https://", ignoreCase = true)) { "Only valid HTTPS pages can be extracted." }
        initMutex.withLock {
            if (!initialized.get()) {
                YoutubeDL.init(appContext)
                initialized.set(true)
            }
        }
        val request = YoutubeDLRequest(url).apply { addOption("--no-playlist") }
        val info = YoutubeDL.getInfo(request)
        ExtractedMediaInfo(
            canonicalUrl = info.webpageUrl?.takeIf { it.isNotBlank() } ?: url,
            title = info.title?.takeIf { it.isNotBlank() } ?: info.fulltitle?.takeIf { it.isNotBlank() } ?: "Media",
            durationSeconds = info.duration.toLong().coerceAtLeast(0),
            thumbnailUrl = info.thumbnail,
            formats = info.formats.orEmpty().mapNotNull { it.toExtractedFormat() }
                .distinctBy { it.id },
            subtitles = emptyList(),
            hashtags = info.tags.orEmpty().map { it.trim().removePrefix("#") }
                .filter { it.matches(Regex("[\\p{L}\\p{N}_]{1,80}")) }.distinct(),
            supportsYtDlpMerge = true
        )
    }

    /** Instagram may emit one JSON object per carousel slide; retain each child's identity and media kind. */
    suspend fun extractInstagram(url: String): ExtractedMediaInfo = withContext(Dispatchers.IO) {
        require(url.length <= 4096 && url.startsWith("https://", ignoreCase = true)) { "Only valid HTTPS pages can be extracted." }
        initMutex.withLock {
            if (!initialized.get()) {
                YoutubeDL.init(appContext)
                initialized.set(true)
            }
        }
        val request = YoutubeDLRequest(url).apply {
            addOption("--dump-json")
            addOption("--no-warnings")
        }
        val response = YoutubeDL.execute(request, null, null)
        val records = response.out.lineSequence().mapNotNull { line ->
            runCatching { JSONObject(line) }.getOrNull()
        }.flatMap { root ->
            val entries = root.optJSONArray("entries")
            if (entries == null) sequenceOf(root) else (0 until entries.length()).asSequence()
                .mapNotNull { entries.optJSONObject(it) }
        }.toList()
        require(records.isNotEmpty()) { "Instagram returned no media items." }
        val formats = records.flatMapIndexed { itemIndex, item ->
            val listed = item.optJSONArray("formats")
            val candidates = if (listed == null || listed.length() == 0) listOf(item)
                else (0 until listed.length()).mapNotNull { listed.optJSONObject(it) }
            candidates.mapNotNull { candidate -> candidate.toInstagramFormat(itemIndex, item) }
        }.distinctBy { it.id }
        val first = records.first()
        val title = first.optString("title").takeIf { it.isNotBlank() }
            ?: first.optString("description").lineSequence().firstOrNull()?.takeIf { it.isNotBlank() }
            ?: "Instagram media"
        val tags = first.optJSONArray("tags")?.let { array ->
            (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
                .map { it.removePrefix("#") }.distinct()
        }.orEmpty()
        ExtractedMediaInfo(
            canonicalUrl = first.optString("webpage_url").takeIf { it.startsWith("https://") } ?: url,
            title = title,
            durationSeconds = first.optDouble("duration", 0.0).toLong().coerceAtLeast(0),
            thumbnailUrl = first.optString("thumbnail").takeIf { it.startsWith("https://") },
            formats = formats,
            subtitles = emptyList(),
            hashtags = tags,
            supportsYtDlpMerge = true
        )
    }

    private fun JSONObject.toInstagramFormat(itemIndex: Int, item: JSONObject): ExtractedFormat? {
        val streamUrl = optString("url").takeIf { it.startsWith("https://", ignoreCase = true) }
            ?: item.optString("url").takeIf { it.startsWith("https://", ignoreCase = true) }
            ?: return null
        val ext = optString("ext", item.optString("ext")).lowercase()
        val vcodec = optString("vcodec", item.optString("vcodec"))
        val acodec = optString("acodec", item.optString("acodec"))
        val image = ext in setOf("jpg", "jpeg", "png", "webp", "avif") && (vcodec.isBlank() || vcodec == "none")
        val hasVideo = vcodec.isNotBlank() && vcodec != "none"
        val hasAudio = acodec.isNotBlank() && acodec != "none"
        val kind = when {
            image -> ExtractedFormatKind.IMAGE
            hasVideo && hasAudio -> ExtractedFormatKind.VIDEO_WITH_AUDIO
            hasVideo -> ExtractedFormatKind.VIDEO_ONLY
            hasAudio -> ExtractedFormatKind.AUDIO
            else -> return null
        }
        val mime = when {
            image && ext in setOf("jpg", "jpeg") -> "image/jpeg"
            image && ext == "png" -> "image/png"
            image && ext == "webp" -> "image/webp"
            image -> "image/avif"
            ext in setOf("mp4", "m4v", "mov") -> "video/mp4"
            ext in setOf("webm", "mkv") -> "video/webm"
            ext == "m4a" -> "audio/mp4"
            ext == "mp3" -> "audio/mpeg"
            ext in setOf("opus", "ogg") -> "audio/ogg"
            ext == "aac" -> "audio/aac"
            else -> return null
        }
        val width = optInt("width", item.optInt("width", 0)).takeIf { it > 0 }
        val height = optInt("height", item.optInt("height", 0)).takeIf { it > 0 }
        val quality = optString("format_note").takeIf { it.isNotBlank() }
            ?: if (width != null && height != null) "${height}p" else "Item ${itemIndex + 1}"
        val rawHeaders = optJSONObject("http_headers") ?: item.optJSONObject("http_headers")
        val headers = rawHeaders?.let { obj -> obj.keys().asSequence().associateWith { obj.optString(it) } }.orEmpty()
        val itemLabel = if (kind == ExtractedFormatKind.IMAGE) "Image" else "Video"
        return ExtractedFormat(
            id = "instagram-item-${itemIndex + 1}-${optString("format_id", ext).ifBlank { ext }}",
            kind = kind,
            label = "Item ${itemIndex + 1} · $itemLabel · $quality",
            url = streamUrl,
            mimeType = mime,
            extension = ext,
            width = width,
            height = height,
            frameRate = optInt("fps", 0).takeIf { it > 0 },
            codec = listOf(vcodec.takeUnless { it.isBlank() || it == "none" }, acodec.takeUnless { it.isBlank() || it == "none" }).joinToString(" + ").takeIf { it.isNotBlank() },
            bitrate = (optDouble("tbr", 0.0) * 1000).toInt().takeIf { it > 0 },
            fileSizeBytes = optLong("filesize", 0L).takeIf { it > 0 } ?: optLong("filesize_approx", 0L).takeIf { it > 0 },
            headers = headers
        )
    }

    private fun VideoFormat.toExtractedFormat(): ExtractedFormat? {
        val streamUrl = url?.takeIf { it.startsWith("https://", ignoreCase = true) } ?: return null
        val video = !vcodec.isNullOrBlank() && vcodec != "none"
        val audio = !acodec.isNullOrBlank() && acodec != "none"
        val mime = ext?.lowercase()?.let { extension ->
            when {
                video && extension in setOf("mp4", "m4v", "mov") -> "video/mp4"
                video && extension in setOf("webm", "mkv") -> "video/webm"
                audio && extension in setOf("m4a", "mp4") -> "audio/mp4"
                audio && extension == "mp3" -> "audio/mpeg"
                audio && extension in setOf("opus", "ogg") -> "audio/ogg"
                audio && extension == "aac" -> "audio/aac"
                audio && extension == "wav" -> "audio/wav"
                else -> null
            }
        } ?: return null
        val kind = when {
            video && audio -> ExtractedFormatKind.VIDEO_WITH_AUDIO
            video -> ExtractedFormatKind.VIDEO_ONLY
            audio -> ExtractedFormatKind.AUDIO
            else -> return null
        }
        val quality = formatNote?.takeIf { it.isNotBlank() }
            ?: height.takeIf { it > 0 }?.let { "${it}p" }
            ?: format?.takeIf { it.isNotBlank() }
            ?: formatId?.let { "Format $it" }
            ?: return null
        return ExtractedFormat(
            id = formatId?.takeIf { it.isNotBlank() } ?: streamUrl,
            kind = kind,
            label = quality,
            url = streamUrl,
            mimeType = mime,
            extension = ext.orEmpty(),
            width = width.takeIf { it > 0 },
            height = height.takeIf { it > 0 },
            frameRate = fps.takeIf { it > 0 },
            codec = listOfNotNull(vcodec, acodec).joinToString(" + ").takeIf { it.isNotBlank() },
            bitrate = (tbr * 1000).takeIf { it > 0 },
            fileSizeBytes = (fileSize.takeIf { it > 0 } ?: fileSizeApproximate.takeIf { it > 0 }),
            headers = httpHeaders.orEmpty()
        )
    }

    companion object {
        private val initialized = AtomicBoolean(false)
        private val initMutex = Mutex()
    }
}
