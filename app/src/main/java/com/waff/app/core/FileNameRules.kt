package com.waff.app.core

object FileNameRules {
    fun withMimeExtension(sourceName: String, mimeExtension: String?): String {
        val stem = sourceName.substringBeforeLast('.', sourceName)
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .trim('.', '_')
            .take(80)
            .ifBlank { "media" }
        val extension = mimeExtension.orEmpty().removePrefix(".")
            .filter(Char::isLetterOrDigit).take(10)
        return if (extension.isBlank()) stem else "$stem.$extension".take(100)
    }

    fun unique(candidate: String, existingNames: Set<String>): String {
        if (candidate !in existingNames) return candidate
        val stem = candidate.substringBeforeLast('.', candidate)
        val extension = candidate.substringAfterLast('.', "").takeIf { it.isNotBlank() }?.let { ".$it" }.orEmpty()
        var index = 2
        while ("$stem ($index)$extension" in existingNames) index++
        return "$stem ($index)$extension"
    }
}
