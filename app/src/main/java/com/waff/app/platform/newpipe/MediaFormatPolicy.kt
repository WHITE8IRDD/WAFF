package com.waff.app.platform.newpipe

/** Selects the largest listed muxed stream first, avoiding a silent video-only default where possible. */
internal object MediaFormatPolicy {
    fun defaultFormat(info: ExtractedMediaInfo): ExtractedFormat? = info.formats
        .filter { it.kind == ExtractedFormatKind.VIDEO_WITH_AUDIO }
        .maxByOrNull { (it.width ?: 0).toLong() * (it.height ?: 0).toLong() }
}
