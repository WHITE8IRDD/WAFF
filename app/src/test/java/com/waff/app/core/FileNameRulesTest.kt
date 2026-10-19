package com.waff.app.core

import org.junit.Assert.assertEquals
import org.junit.Test

class FileNameRulesTest {
    @Test fun sanitizesPathCharactersAndUsesMimeExtensionInsteadOfUrlExtension() {
        assertEquals("clip_name.mp4", FileNameRules.withMimeExtension("../clip name.jpg", "mp4"))
    }

    @Test fun suppliesSafeFallbackAndLimitsExtension() {
        assertEquals("media", FileNameRules.withMimeExtension("../../", null))
        assertEquals("portrait.png", FileNameRules.withMimeExtension("portrait", ".png"))
    }

    @Test fun duplicateNamesReceiveStableSuffixes() {
        val names = setOf("video.mp4", "video (2).mp4")
        assertEquals("video (3).mp4", FileNameRules.unique("video.mp4", names))
    }
}
