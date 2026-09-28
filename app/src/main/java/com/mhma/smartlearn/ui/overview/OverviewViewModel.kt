package com.mhma.smartlearn.ui.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mhma.smartlearn.data.local.entity.CourseEntity
import com.mhma.smartlearn.data.local.entity.DeadlineEntity
import com.mhma.smartlearn.data.repository.CourseRepository
import com.mhma.smartlearn.data.repository.DeadlineRepository
import com.mhma.smartlearn.data.sync.SyncManager
import com.mhma.smartlearn.data.sync.SyncStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class OverviewUiState(
    val deadlines: List<DeadlineEntity> = emptyList(),
    val courses: List<CourseEntity> = emptyList(),
    val sync: SyncStatus = SyncStatus(),
    /** False until Room has answered once, so we don't flash an empty state. */
    val loaded: Boolean = false,
)

@HiltViewModel
class OverviewViewModel @Inject constructor(
    private val deadlineRepository: DeadlineRepository,
    courseRepository: CourseRepository,
    private val sync: SyncManager,
) : ViewModel() {

    // Show things that slipped by in the last few days too, so nothing gets quietly forgotten.
    private val since = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(3)

    val uiState: StateFlow<OverviewUiState> = combine(
        deadlineRepository.observeFrom(since),
        courseRepository.observeCourses(),
        sync.status,
    ) { deadlines, courses, status ->
        OverviewUiState(
            deadlines = deadlines.sortedWith(compareBy({ it.isCompleted }, { it.dueDateMillis })),
            courses = courses,
            sync = status,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OverviewUiState())

    fun refresh() = sync.refreshAll()

    fun toggleDone(deadline: DeadlineEntity) {
        viewModelScope.launch {
            deadlineRepository.setCompleted(deadline.id, !deadline.isCompleted)
        }
    }

    fun dismissMessage() = sync.clearError()
}
