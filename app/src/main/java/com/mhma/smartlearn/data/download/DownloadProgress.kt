package com.mhma.smartlearn.data.download

data class DownloadProgress(
    val resourceId: Long,
    val state: State,
    val bytesRead: Long = 0L,
    /** -1 when the server didn't say. */
    val totalBytes: Long = -1L,
    val message: String? = null,
) {
    enum class State { QUEUED, RUNNING, FAILED }

    /** 0..1 when the size is known, otherwise null (show an indeterminate spinner). */
    val fraction: Float?
        get() = if (totalBytes > 0) (bytesRead.toFloat() / totalBytes).coerceIn(0f, 1f) else null

    val isActive: Boolean
        get() = state == State.QUEUED || state == State.RUNNING
}
