package com.mhma.smartlearn.util

import java.util.Locale

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.ENGLISH, "%.0f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.ENGLISH, if (mb < 10) "%.1f MB" else "%.0f MB", mb)
    return String.format(Locale.ENGLISH, "%.2f GB", mb / 1024.0)
}
