package com.mhma.smartlearn.ui.courses

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mhma.smartlearn.ui.components.CourseAvatar
import com.mhma.smartlearn.ui.components.EmptyState
import com.mhma.smartlearn.ui.components.NoticeCard
import com.mhma.smartlearn.ui.components.SyncHint
import com.mhma.smartlearn.util.TimeText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoursesScreen(
    onOpenCourse: (Long) -> Unit,
    viewModel: CoursesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Courses") },
                actions = {
                    IconButton(onClick = viewModel::refresh, enabled = !state.sync.isRunning) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Refresh")
                    }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.sync.isRunning,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
            ) {
                if (state.sync.isRunning) {
                    item(key = "sync") { SyncHint(state.sync.step) }
                }
                state.sync.lastError?.let { message ->
                    item(key = "notice") {
                        NoticeCard(text = message, icon = Icons.Outlined.CloudOff, onDismiss = viewModel::dismissMessage)
                    }
                }
                if (state.loaded && state.rows.isEmpty()) {
                    item(key = "empty") {
                        val refreshButton: (@Composable () -> Unit)? = if (state.sync.isRunning) {
                            null
                        } else {
                            { Button(onClick = viewModel::refresh) { Text("Refresh now") } }
                        }
                        EmptyState(
                            icon = Icons.AutoMirrored.Outlined.MenuBook,
                            title = "No courses yet",
                            message = if (state.sync.isRunning) {
                                "Give it a moment, we're fetching them from Smart Learning."
                            } else {
                                "Pull down to fetch your courses from Smart Learning."
                            },
                            action = refreshButton,
                        )
                    }
                }
                items(state.rows, key = { it.course.id }) { row ->
                    CourseCard(row = row, onClick = { onOpenCourse(row.course.id) })
                }
            }
        }
    }
}

@Composable
private fun CourseCard(
    row: CourseRow,
    onClick: () -> Unit,
) {
    val course = row.course
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CourseAvatar(courseId = course.id, code = course.courseCode, title = course.title)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = course.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = buildString {
                        if (course.courseCode.isNotBlank()) append(course.courseCode).append(" · ")
                        when (row.fileCount) {
                            0 -> append("No files yet")
                            1 -> append("1 file")
                            else -> append("${row.fileCount} files")
                        }
                        if (row.downloadedCount > 0) append(", ${row.downloadedCount} saved")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = TimeText.updatedAgo(course.lastSynced),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
