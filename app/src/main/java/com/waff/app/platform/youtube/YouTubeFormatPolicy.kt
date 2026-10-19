package com.waff.app.platform.youtube

/** Selects the largest listed muxed stream first, avoiding a silent video-only default where possible. */
internal object YouTubeFormatPolicy {
    fun defaultFormat(info: YouTubeMediaInfo): YouTubeFormat? = info.formats
        .filter { it.kind == YouTubeFormatKind.VIDEO_WITH_AUDIO }
        .maxByOrNull { (it.width ?: 0).toLong() * (it.height ?: 0).toLong() }
        ?: info.formats.firstOrNull()
}
