package com.mhma.smartlearn.ui.courses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mhma.smartlearn.data.local.entity.CourseEntity
import com.mhma.smartlearn.data.repository.CourseRepository
import com.mhma.smartlearn.data.sync.SyncManager
import com.mhma.smartlearn.data.sync.SyncStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class CourseRow(
    val course: CourseEntity,
    val fileCount: Int,
    val downloadedCount: Int,
)

data class CoursesUiState(
    val rows: List<CourseRow> = emptyList(),
    val sync: SyncStatus = SyncStatus(),
    val loaded: Boolean = false,
)

@HiltViewModel
class CoursesViewModel @Inject constructor(
    courseRepository: CourseRepository,
    private val sync: SyncManager,
) : ViewModel() {

    val uiState: StateFlow<CoursesUiState> = combine(
        courseRepository.observeCourses(),
        courseRepository.observeFileCounts(),
        sync.status,
    ) { courses, counts, status ->
        val byCourse = counts.associateBy { it.courseId }
        CoursesUiState(
            rows = courses.map { course ->
                val count = byCourse[course.id]
                CourseRow(course, count?.total ?: 0, count?.downloaded ?: 0)
            },
            sync = status,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CoursesUiState())

    fun refresh() = sync.refreshAll()

    fun dismissMessage() = sync.clearError()
}
