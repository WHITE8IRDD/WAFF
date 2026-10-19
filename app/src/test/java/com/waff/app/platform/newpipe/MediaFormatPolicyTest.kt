package com.waff.app.platform.newpipe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaFormatPolicyTest {
    @Test fun defaultPrefersLargestMuxedStreamOverSilentHigherResolution() {
        val formats = listOf(
            format("small", ExtractedFormatKind.VIDEO_WITH_AUDIO, 640, 360),
            format("silent-4k", ExtractedFormatKind.VIDEO_ONLY, 3840, 2160),
            format("large-muxed", ExtractedFormatKind.VIDEO_WITH_AUDIO, 1920, 1080)
        )
        assertEquals("large-muxed", MediaFormatPolicy.defaultFormat(info(formats))?.id)
    }

    @Test fun doesNotSilentlyDefaultToVideoOnlyOrAudioOnly() {
        val videoOnly = format("video-only", ExtractedFormatKind.VIDEO_ONLY, 1920, 1080)
        val audio = format("audio", ExtractedFormatKind.AUDIO, null, null)
        assertNull(MediaFormatPolicy.defaultFormat(info(listOf(videoOnly, audio))))
    }

    @Test fun returnsNullWhenExtractorReturnsNoFormats() {
        assertNull(MediaFormatPolicy.defaultFormat(info(emptyList())))
    }

    private fun info(formats: List<ExtractedFormat>) = ExtractedMediaInfo(
        canonicalUrl = "https://www.youtube.com/watch?v=example",
        title = "Example", durationSeconds = 0, thumbnailUrl = null,
        formats = formats, subtitles = emptyList()
    )

    private fun format(id: String, kind: ExtractedFormatKind, width: Int?, height: Int?) = ExtractedFormat(
        id = id, kind = kind, label = id, url = "https://example.test/$id",
        mimeType = if (kind == ExtractedFormatKind.AUDIO) "audio/mp4" else "video/mp4",
        extension = "mp4", width = width, height = height
    )
}
