package com.waff.app.core

/** Extracts the actual shared URL from text that may also contain a caption or surrounding punctuation. */
object SharedLinkParser {
    private val urlPattern = Regex("(?:https?://|www\\.)[^\\s<>\\\"']+", RegexOption.IGNORE_CASE)
    private val trailingPunctuation = charArrayOf('.', ',', ';', ':', '!', '?', ')', ']', '}')

    fun extract(text: String): String? {
        val candidates = urlPattern.findAll(text).map { it.value.trimEnd(*trailingPunctuation) }.filter(String::isNotBlank).toList()
        return candidates.firstOrNull { UrlRouter.parse(it) is PlatformLink.Supported }
            ?: candidates.firstOrNull()
    }
}
