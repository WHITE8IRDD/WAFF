package com.waff.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlRouterTest {
    @Test fun recognizesShortsAndStandardYoutubeOnSamePlatform() {
        listOf("https://youtube.com/shorts/abc", "https://youtu.be/abc").forEach {
            assertEquals(Platform.YOUTUBE, (UrlRouter.parse(it) as PlatformLink.Supported).platform)
        }
    }

    @Test fun recognizesSupportedHostnamesWithoutMatchingLookalikes() {
        assertEquals(Platform.INSTAGRAM, (UrlRouter.parse("https://www.instagram.com/p/x/") as PlatformLink.Supported).platform)
        assertEquals(Platform.TIKTOK, (UrlRouter.parse("https://vm.tiktok.com/x") as PlatformLink.Supported).platform)
        assertEquals(Platform.FACEBOOK, (UrlRouter.parse("https://fb.watch/x") as PlatformLink.Supported).platform)
        assertEquals(PlatformLink.Unsupported, UrlRouter.parse("https://notinstagram.com/p/x"))
    }

    @Test fun rejectsMalformedAndNonWebLinks() {
        listOf("", "not a url", "file:///tmp/a", "https:///missing-host", "https://user:pass@example.com/video.mp4").forEach {
            assertTrue("Expected invalid for $it", UrlRouter.parse(it) is PlatformLink.Invalid)
        }
    }

    @Test fun normalizesWwwAndRemovesFragment() {
        val result = UrlRouter.parse("www.youtube.com/watch?v=abc#comments") as PlatformLink.Supported
        assertEquals("https://youtube.com/watch?v=abc", result.canonicalUrl)
    }
}
