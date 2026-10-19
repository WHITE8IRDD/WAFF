package com.waff.app.platform.newpipe

import android.content.Context
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
            supportsYtDlpMerge = true
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
            headers = httpHeaders.orEmpty()
        )
    }

    companion object {
        private val initialized = AtomicBoolean(false)
        private val initMutex = Mutex()
    }
}
