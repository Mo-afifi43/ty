package com.mhma.smartlearn.ui.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mhma.smartlearn.data.download.DownloadManager
import com.mhma.smartlearn.data.download.DownloadProgress
import com.mhma.smartlearn.data.local.dao.ResourceDao
import com.mhma.smartlearn.data.local.entity.ResourceEntity
import com.mhma.smartlearn.data.repository.CourseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DownloadGroup(
    val courseId: Long,
    val title: String,
    val code: String,
    val files: List<ResourceEntity>,
) {
    val bytes: Long get() = files.sumOf { it.fileSizeBytes }
}

data class ActiveDownload(
    val item: ResourceEntity,
    val progress: DownloadProgress,
)

data class DownloadsUiState(
    val groups: List<DownloadGroup> = emptyList(),
    val active: List<ActiveDownload> = emptyList(),
    val fileCount: Int = 0,
    val totalBytes: Long = 0L,
    val loaded: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val resourceDao: ResourceDao,
    courseRepository: CourseRepository,
    private val downloads: DownloadManager,
) : ViewModel() {

    // Progress only carries ids; look the rows up so the list can show titles.
    private val active = downloads.progress.flatMapLatest { map ->
        flow {
            val rows = map.values.mapNotNull { p -> resourceDao.get(p.resourceId)?.let { ActiveDownload(it, p) } }
            emit(rows.sortedBy { it.item.title })
        }
    }

    val uiState: StateFlow<DownloadsUiState> = combine(
        resourceDao.observeDownloaded(),
        courseRepository.observeCourses(),
        active,
    ) { files, courses, activeRows ->
        val byCourse = courses.associateBy { it.id }
        val groups = files
            .groupBy { it.courseId }
            .map { (courseId, items) ->
                val course = byCourse[courseId]
                DownloadGroup(
                    courseId = courseId,
                    title = course?.title ?: "Other files",
                    code = course?.courseCode.orEmpty(),
                    files = items.sortedBy { it.title.lowercase() },
                )
            }
            .sortedBy { it.title.lowercase() }
        DownloadsUiState(
            groups = groups,
            active = activeRows,
            fileCount = files.size,
            totalBytes = files.sumOf { it.fileSizeBytes },
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DownloadsUiState())

    fun remove(item: ResourceEntity) {
        viewModelScope.launch { downloads.remove(item) }
    }

    fun removeAll(group: DownloadGroup) {
        viewModelScope.launch { group.files.forEach { downloads.remove(it) } }
    }

    fun cancel(itemId: Long) = downloads.cancel(itemId)

    fun dismissError(itemId: Long) = downloads.dismiss(itemId)
}
