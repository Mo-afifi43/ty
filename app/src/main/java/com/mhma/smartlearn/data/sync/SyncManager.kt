package com.mhma.smartlearn.data.sync

import com.mhma.smartlearn.data.network.SessionExpiredException
import com.mhma.smartlearn.data.network.SessionManager
import com.mhma.smartlearn.data.repository.CourseRepository
import com.mhma.smartlearn.data.repository.DeadlineRepository
import com.mhma.smartlearn.util.friendlyMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class SyncStatus(
    val isRunning: Boolean = false,
    /** What we're doing right now, e.g. "Updating Data Structures". */
    val step: String? = null,
    val lastError: String? = null,
    val lastFinishedAt: Long = 0L,
)

/**
 * Runs the background refresh: course list, deadlines, then every course's content.
 * Lives for the whole process so a refresh survives screen changes.
 */
@Singleton
class SyncManager @Inject constructor(
    private val courses: CourseRepository,
    private val deadlines: DeadlineRepository,
    private val session: SessionManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()

    private val _status = MutableStateFlow(SyncStatus())
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    /** Full refresh in the background. Calls made while one is running are ignored. */
    fun refreshAll() {
        if (_status.value.isRunning || !session.isActive) return
        scope.launch { runFullSync() }
    }

    /** Full refresh, but only if the last one is older than [maxAge]. Used on app start. */
    fun refreshIfStale(maxAge: Long = TimeUnit.MINUTES.toMillis(15)) {
        val last = _status.value.lastFinishedAt
        if (System.currentTimeMillis() - last > maxAge) refreshAll()
    }

    fun refreshDeadlines() {
        if (!session.isActive) return
        scope.launch {
            try {
                deadlines.refresh()
            } catch (e: Exception) {
                report(e)
            }
        }
    }

    fun clearError() {
        _status.update { it.copy(lastError = null) }
    }

    private suspend fun runFullSync() {
        lock.withLock {
            if (!session.isActive) return
            // Two taps in quick succession shouldn't refresh twice.
            if (System.currentTimeMillis() - _status.value.lastFinishedAt < MIN_GAP_MILLIS) return
            runFullSyncLocked()
        }
    }

    private suspend fun runFullSyncLocked() {
        _status.update { it.copy(isRunning = true, step = "Checking your courses", lastError = null) }

        var failures = 0
        try {
            val list = courses.refreshCourses()

            _status.update { it.copy(step = "Looking for deadlines") }
            try {
                deadlines.refresh()
            } catch (e: SessionExpiredException) {
                throw e
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failures++
            }

            for (course in list) {
                _status.update { it.copy(step = "Updating ${course.title}") }
                try {
                    courses.refreshCourseContent(course.id)
                } catch (e: SessionExpiredException) {
                    throw e
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failures++
                }
            }

            _status.update {
                it.copy(
                    isRunning = false,
                    step = null,
                    lastFinishedAt = System.currentTimeMillis(),
                    lastError = if (failures > 0) "Some courses didn't update. Pull down to try again." else null,
                )
            }
        } catch (e: SessionExpiredException) {
            // The session banner takes over from here; no need for a second message.
            _status.update { it.copy(isRunning = false, step = null) }
        } catch (e: CancellationException) {
            _status.update { it.copy(isRunning = false, step = null) }
            throw e
        } catch (e: Exception) {
            report(e)
        }
    }

    private fun report(e: Exception) {
        _status.update { it.copy(isRunning = false, step = null, lastError = e.friendlyMessage()) }
    }

    private companion object {
        const val MIN_GAP_MILLIS = 5_000L
    }
}
