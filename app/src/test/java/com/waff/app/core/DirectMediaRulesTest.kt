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

    @Test fun normalizesMimeAndUsesReliableExtensions() {
        assertEquals("image/jpeg", DirectMediaRules.normalizedMime(" Image/JPEG; q=1 "))
        assertEquals("mp4", DirectMediaRules.extensionForMime("video/mp4"))
        assertEquals("jpg", DirectMediaRules.extensionForMime("image/jpeg"))
        assertEquals("mov", DirectMediaRules.extensionForMime("video/quicktime"))
    }
}
