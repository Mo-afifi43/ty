package com.mhma.smartlearn.util

import java.net.URLDecoder

/** Small helpers for turning URLs and headers into safe file names. Pure Kotlin on purpose. */
object FileNames {

    private val extensionInUrl = Regex(
        "\\.(pdf|pptx?|docx?|xlsx?|zip|rar|7z|mp4|mkv|mov|mp3|m4a|wav|png|jpe?g|gif|webp|txt|csv|md|ipynb|py|java|c|cpp|h|js|html?)(?=[?#]|$)",
        RegexOption.IGNORE_CASE,
    )

    private val filenameStar = Regex("filename\\*\\s*=\\s*(?:UTF-8|utf-8)?''?([^;]+)")
    private val filenamePlain = Regex("filename\\s*=\\s*\"?([^\";]+)\"?")

    /** Last path segment of a URL, URL-decoded, without the query string. */
    fun fromUrl(url: String): String {
        val path = url.substringBefore('?').substringBefore('#')
        val segment = path.substringAfterLast('/')
        return decode(segment)
    }

    /** Extension from a URL like `.../Lecture%201.pdf?forcedownload=1`, lower case, or "". */
    fun extensionFromUrl(url: String): String =
        extensionInUrl.find(url)?.groupValues?.get(1)?.lowercase().orEmpty()

    /** Reads the file name out of a `Content-Disposition` header. */
    fun fromContentDisposition(header: String?): String? {
        if (header.isNullOrBlank()) return null
        filenameStar.find(header)?.groupValues?.get(1)?.let { return decode(it.trim()) }
        filenamePlain.find(header)?.groupValues?.get(1)?.let { return it.trim() }
        return null
    }

    fun extensionOf(name: String): String {
        val dot = name.lastIndexOf('.')
        if (dot <= 0 || dot == name.length - 1) return ""
        val ext = name.substring(dot + 1).lowercase()
        return if (ext.length in 1..6 && ext.all { it.isLetterOrDigit() }) ext else ""
    }

    /** Strips characters that upset file systems and keeps the name a sensible length. */
    fun sanitize(name: String, fallback: String = "file"): String {
        val cleaned = name
            .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trim('.')
        if (cleaned.isEmpty()) return fallback
        return if (cleaned.length > 120) cleaned.take(120) else cleaned
    }

    private fun decode(value: String): String = try {
        URLDecoder.decode(value.replace("+", "%2B"), "UTF-8")
    } catch (e: Exception) {
        value
    }
}
