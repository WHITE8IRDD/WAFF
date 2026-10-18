package com.waff.app.core

import java.net.URI
import java.util.Locale

enum class Platform(val label: String) { YOUTUBE("YouTube"), INSTAGRAM("Instagram"), TIKTOK("TikTok"), FACEBOOK("Facebook"), GENERIC("Web") }

sealed interface PlatformLink {
    data class Supported(val canonicalUrl: String, val platform: Platform) : PlatformLink
    data object Invalid : PlatformLink
    data object Unsupported : PlatformLink
}

/** Strictly parses web URLs; deliberately does not imply that parsing grants media access. */
object UrlRouter {
    fun parse(raw: String): PlatformLink {
        val value = raw.trim().let { if (it.startsWith("www.", true)) "https://$it" else it }
        val uri = runCatching { URI(value) }.getOrNull() ?: return PlatformLink.Invalid
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        val host = uri.host?.lowercase(Locale.ROOT)?.removePrefix("www.")
        if (scheme !in setOf("http", "https") || host.isNullOrBlank() || host.contains(' ')) return PlatformLink.Invalid
        val platform = when {
            host == "youtube.com" || host == "m.youtube.com" || host == "youtu.be" || host == "youtube-nocookie.com" -> Platform.YOUTUBE
            host == "instagram.com" -> Platform.INSTAGRAM
            host == "tiktok.com" || host.endsWith(".tiktok.com") -> Platform.TIKTOK
            host == "facebook.com" || host.endsWith(".facebook.com") || host == "fb.watch" -> Platform.FACEBOOK
            else -> Platform.GENERIC
        }
        if (platform == Platform.GENERIC) return PlatformLink.Unsupported
        val canonical = URI("https", null, host, uri.port.takeUnless { it == 443 || it == 80 }, uri.path.ifBlank { "/" }, uri.query, null).toASCIIString()
        return PlatformLink.Supported(canonical, platform)
    }
}
