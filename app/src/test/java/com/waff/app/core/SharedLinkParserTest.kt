package com.waff.app.core

import org.junit.Assert.assertEquals
import org.junit.Test

class SharedLinkParserTest {
    @Test fun extractsSupportedUrlFromCaptionText() {
        assertEquals("https://youtu.be/abc123", SharedLinkParser.extract("Watch this: https://youtu.be/abc123. 🔥"))
    }

    @Test fun prefersSupportedLinkAmongMultipleUrls() {
        assertEquals("https://www.instagram.com/p/ABC/", SharedLinkParser.extract("Link: https://example.com/info then https://www.instagram.com/p/ABC/"))
    }

    @Test fun addsNoInventedUrlWhenTextHasNoLink() {
        assertEquals(null, SharedLinkParser.extract("no link here"))
    }
}
