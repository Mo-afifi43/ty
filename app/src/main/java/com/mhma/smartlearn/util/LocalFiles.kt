package com.mhma.smartlearn.util

import java.io.File

object LocalFiles {

    /** Deletes a downloaded file if it exists. Never throws. */
    fun delete(path: String?) {
        if (path.isNullOrBlank()) return
        try {
            val file = File(path)
            if (file.exists()) file.delete()
            // Drop the course folder too once it's empty, so Files apps stay tidy.
            file.parentFile?.let { dir -> if (dir.isDirectory && dir.list().isNullOrEmpty()) dir.delete() }
        } catch (_: Exception) {
        }
    }

    fun exists(path: String?): Boolean = !path.isNullOrBlank() && File(path).exists()

    /** Stable negative id for things Moodle doesn't number itself (files inside folders). */
    fun stableId(key: String): Long {
        // FNV-1a, 64 bit. Plenty for a few thousand files.
        var hash = -0x340d631b7bdddcdbL
        for (ch in key) {
            hash = hash xor ch.code.toLong()
            hash *= 0x100000001b3L
        }
        return -(hash and Long.MAX_VALUE) - 1
    }
}
