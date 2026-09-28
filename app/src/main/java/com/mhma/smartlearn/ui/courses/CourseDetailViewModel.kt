package com.mhma.smartlearn.ui.courses

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mhma.smartlearn.data.download.DownloadManager
import com.mhma.smartlearn.data.download.DownloadProgress
import com.mhma.smartlearn.data.local.entity.CourseEntity
import com.mhma.smartlearn.data.local.entity.ResourceEntity
import com.mhma.smartlearn.data.local.entity.SectionWithItems
import com.mhma.smartlearn.data.network.SessionExpiredException
import com.mhma.smartlearn.data.repository.CourseRepository
import com.mhma.smartlearn.data.sync.SyncManager
import com.mhma.smartlearn.util.friendlyMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class CourseDetailUiState(
    val course: CourseEntity? = null,
    val sections: List<SectionWithItems> = emptyList(),
    val downloads: Map<Long, DownloadProgress> = emptyMap(),
    val isRefreshing: Boolean = false,
    val message: String? = null,
    /** Null means "use the defaults" until the student toggles something. */
    val expanded: Set<Long>? = null,
    val loaded: Boolean = false,
) {
    fun isExpanded(sectionId: Long): Boolean =
        expanded?.contains(sectionId) ?: defaultExpanded(sections).contains(sectionId)
}

/** Short courses open everything; long ones open the first section and the last couple with content. */
internal fun defaultExpanded(sections: List<SectionWithItems>): Set<Long> {
    if (sections.size <= 6) return sections.map { it.section.id }.toSet()
    val withContent = sections.filter { it.items.isNotEmpty() }
    val open = LinkedHashSet<Long>()
    sections.firstOrNull()?.let { open += it.section.id }
    withContent.takeLast(2).forEach { open += it.section.id }
    return open
}

@HiltViewModel
class CourseDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val courses: CourseRepository,
    private val downloads: DownloadManager,
    private val sync: SyncManager,
) : ViewModel() {

    val courseId: Long = checkNotNull(savedStateHandle["courseId"]) { "courseId is required" }

    private val refreshing = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)
    private val expanded = MutableStateFlow<Set<Long>?>(null)

    private val data = combine(
        courses.observeCourse(courseId),
        courses.observeSections(courseId),
        downloads.progress,
    ) { course, sections, progress -> Triple(course, sections, progress) }

    private val local = combine(refreshing, message, expanded) { r, m, e -> Triple(r, m, e) }

    val uiState: StateFlow<CourseDetailUiState> = combine(data, local) { (course, sections, progress), (r, m, e) ->
        CourseDetailUiState(
            course = course,
            sections = sections,
            downloads = progress,
            isRefreshing = r,
            message = m,
            expanded = e,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CourseDetailUiState())

    init {
        // Opening a course you haven't looked at in a while should quietly bring it up to date.
        viewModelScope.launch {
            val course = courses.observeCourse(courseId).first()
            val stale = course == null || System.currentTimeMillis() - course.lastSynced > STALE_AFTER
            if (stale && !sync.status.value.isRunning) refresh()
        }
    }

    fun refresh() {
        if (refreshing.value) return
        viewModelScope.launch {
            refreshing.value = true
            try {
                courses.refreshCourseContent(courseId)
            } catch (e: SessionExpiredException) {
                // The banner at the bottom of the screen explains what happened.
            } catch (e: Exception) {
                message.value = e.friendlyMessage()
            } finally {
                refreshing.value = false
            }
        }
    }

    fun toggleSection(sectionId: Long) {
        expanded.update { current ->
            val base = current ?: defaultExpanded(uiState.value.sections)
            if (sectionId in base) base - sectionId else base + sectionId
        }
    }

    fun download(item: ResourceEntity) = downloads.download(item)

    fun cancelDownload(itemId: Long) = downloads.cancel(itemId)

    fun dismissDownloadError(itemId: Long) = downloads.dismiss(itemId)

    fun removeDownload(item: ResourceEntity) {
        viewModelScope.launch { downloads.remove(item) }
    }

    fun dismissMessage() {
        message.value = null
    }

    fun showMessage(text: String) {
        message.value = text
    }

    private companion object {
        val STALE_AFTER = TimeUnit.MINUTES.toMillis(10)
    }
}
