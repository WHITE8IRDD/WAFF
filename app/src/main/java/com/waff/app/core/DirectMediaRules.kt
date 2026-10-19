package com.waff.app.core

import java.util.Locale

object DirectMediaRules {
    fun normalizedMime(value: String?): String = value?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT).orEmpty()

    fun isMediaMime(value: String?): Boolean {
        val mime = normalizedMime(value)
        return mime.startsWith("video/") || mime.startsWith("audio/") || mime.startsWith("image/")
    }

    fun isSubtitleMime(value: String?): Boolean = normalizedMime(value) in setOf(
        "text/vtt", "text/plain", "application/ttml+xml", "application/x-subrip", "text/xml", "application/xml"
    )

    /** Use known response MIME information rather than a possibly misleading URL suffix. */
    fun extensionForMime(value: String?): String? = when (normalizedMime(value)) {
        "image/jpeg", "image/jpg" -> "jpg"
        "image/png" -> "png"
        "image/gif" -> "gif"
        "image/webp" -> "webp"
        "image/avif" -> "avif"
        "video/mp4" -> "mp4"
        "video/webm" -> "webm"
        "video/quicktime" -> "mov"
        "audio/mpeg" -> "mp3"
        "audio/mp4" -> "m4a"
        "audio/aac" -> "aac"
        "audio/ogg", "application/ogg" -> "ogg"
        "audio/wav", "audio/x-wav" -> "wav"
        "audio/flac" -> "flac"
        "text/vtt" -> "vtt"
        "application/ttml+xml" -> "ttml"
        "application/x-subrip" -> "srt"
        else -> null
    }
}
