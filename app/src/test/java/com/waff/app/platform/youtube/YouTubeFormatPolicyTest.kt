package com.waff.app.platform.youtube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YouTubeFormatPolicyTest {
    @Test fun defaultPrefersLargestMuxedStreamOverSilentHigherResolution() {
        val formats = listOf(
            format("small", YouTubeFormatKind.VIDEO_WITH_AUDIO, 640, 360),
            format("silent-4k", YouTubeFormatKind.VIDEO_ONLY, 3840, 2160),
            format("large-muxed", YouTubeFormatKind.VIDEO_WITH_AUDIO, 1920, 1080)
        )
        assertEquals("large-muxed", YouTubeFormatPolicy.defaultFormat(info(formats))?.id)
    }

    @Test fun fallsBackToFirstAvailableWhenNoMuxedStreamExists() {
        val first = format("video-only", YouTubeFormatKind.VIDEO_ONLY, 1920, 1080)
        val audio = format("audio", YouTubeFormatKind.AUDIO, null, null)
        assertEquals(first, YouTubeFormatPolicy.defaultFormat(info(listOf(first, audio))))
    }

    @Test fun returnsNullWhenExtractorReturnsNoFormats() {
        assertNull(YouTubeFormatPolicy.defaultFormat(info(emptyList())))
    }

    private fun info(formats: List<YouTubeFormat>) = YouTubeMediaInfo(
        canonicalUrl = "https://www.youtube.com/watch?v=example",
        title = "Example", durationSeconds = 0, thumbnailUrl = null,
        formats = formats, subtitles = emptyList()
    )

    private fun format(id: String, kind: YouTubeFormatKind, width: Int?, height: Int?) = YouTubeFormat(
        id = id, kind = kind, label = id, url = "https://example.test/$id",
        mimeType = if (kind == YouTubeFormatKind.AUDIO) "audio/mp4" else "video/mp4",
        extension = "mp4", width = width, height = height
    )
}
