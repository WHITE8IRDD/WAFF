package com.waff.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectMediaRulesTest {
    @Test fun acceptsOnlyImageAudioOrVideoMimeTypes() {
        listOf("video/mp4", "audio/mpeg", "image/jpeg", "image/webp; charset=binary").forEach {
            assertTrue("Expected media MIME: $it", DirectMediaRules.isMediaMime(it))
        }
        listOf(null, "text/html", "application/json", "application/octet-stream").forEach {
            assertFalse("Unexpected media MIME: $it", DirectMediaRules.isMediaMime(it))
        }
    }

    @Test fun subtitleMimeTypesAreExplicitlyAllowlisted() {
        listOf("text/vtt", "application/ttml+xml", "text/plain; charset=utf-8").forEach {
            assertTrue("Expected subtitle MIME: $it", DirectMediaRules.isSubtitleMime(it))
        }
        listOf(null, "text/html", "application/json", "video/mp4").forEach {
            assertFalse("Unexpected subtitle MIME: $it", DirectMediaRules.isSubtitleMime(it))
        }
        assertEquals("vtt", DirectMediaRules.extensionForMime("text/vtt"))
    }

    @Test fun normalizesMimeAndUsesReliableExtensions() {
        assertEquals("image/jpeg", DirectMediaRules.normalizedMime(" Image/JPEG; q=1 "))
        assertEquals("mp4", DirectMediaRules.extensionForMime("video/mp4"))
        assertEquals("jpg", DirectMediaRules.extensionForMime("image/jpeg"))
        assertEquals("mov", DirectMediaRules.extensionForMime("video/quicktime"))
    }
}
