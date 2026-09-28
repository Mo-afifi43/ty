package com.mhma.smartlearn.ui.overview

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedCard
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mhma.smartlearn.data.local.entity.CourseEntity
import com.mhma.smartlearn.data.local.entity.DeadlineEntity
import com.mhma.smartlearn.ui.components.CourseAvatar
import com.mhma.smartlearn.ui.components.DueChip
import com.mhma.smartlearn.ui.components.EmptyState
import com.mhma.smartlearn.ui.components.NoticeCard
import com.mhma.smartlearn.ui.components.SectionTitle
import com.mhma.smartlearn.ui.components.SyncHint
import com.mhma.smartlearn.util.TimeText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(
    onOpenCourse: (Long) -> Unit,
    onOpenLink: (String) -> Unit,
    onSeeAllCourses: () -> Unit,
    viewModel: OverviewViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(TimeText.greeting(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            text = TimeText.todayHeading(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
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
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                if (state.sync.isRunning) {
                    item(key = "sync") { SyncHint(state.sync.step) }
                }
                state.sync.lastError?.let { message ->
                    item(key = "notice") {
                        NoticeCard(
                            text = message,
                            icon = Icons.Outlined.CloudOff,
                            onDismiss = viewModel::dismissMessage,
                        )
                    }
                }

                item(key = "deadlines-title") {
                    val open = state.deadlines.count { !it.isCompleted }
                    SectionTitle(
                        text = "Coming up",
                        caption = when (open) {
                            0 -> null
                            1 -> "1 to do"
                            else -> "$open to do"
                        },
                    )
                }
                if (state.loaded && state.deadlines.isEmpty()) {
                    item(key = "deadlines-empty") {
                        EmptyState(
                            icon = Icons.Outlined.Celebration,
                            title = "Nothing due soon",
                            message = if (state.courses.isEmpty()) {
                                "Deadlines show up here once your courses have been loaded."
                            } else {
                                "Enjoy the breather. Pull down any time to check again."
                            },
                        )
                    }
                } else {
                    items(state.deadlines, key = { it.id }) { deadline ->
                        DeadlineCard(
                            deadline = deadline,
                            onToggle = { viewModel.toggleDone(deadline) },
                            onOpen = { deadline.url?.let(onOpenLink) },
                        )
                    }
                }

                item(key = "courses-title") {
                    SectionTitle(
                        text = "Your courses",
                        actionText = if (state.courses.isNotEmpty()) "See all" else null,
                        onAction = onSeeAllCourses,
                    )
                }
                if (state.loaded && state.courses.isEmpty()) {
                    item(key = "courses-empty") {
                        Text(
                            text = if (state.sync.isRunning) {
                                "Fetching your courses…"
                            } else {
                                "Your courses will show up here after the first refresh."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                } else {
                    item(key = "courses-row") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            items(state.courses, key = { it.id }) { course ->
                                QuickCourseCard(
                                    course = course,
                                    onClick = { onOpenCourse(course.id) },
                                    modifier = Modifier.padding(end = 12.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeadlineCard(
    deadline: DeadlineEntity,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
) {
    Card(
        onClick = onOpen,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 6.dp, end = 16.dp, top = 10.dp, bottom = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            IconButton(onClick = onToggle) {
                Icon(
                    imageVector = if (deadline.isCompleted) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                    contentDescription = if (deadline.isCompleted) "Mark as not done" else "Mark as done",
                    tint = if (deadline.isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 8.dp),
            ) {
                Text(
                    text = deadline.title,
                    style = MaterialTheme.typography.titleMedium,
                    textDecoration = if (deadline.isCompleted) TextDecoration.LineThrough else null,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (deadline.courseName.isNotBlank()) {
                    Text(
                        text = deadline.courseName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DueChip(dueMillis = deadline.dueDateMillis, completed = deadline.isCompleted)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = TimeText.dateTime(deadline.dueDateMillis),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickCourseCard(
    course: CourseEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        onClick = onClick,
        modifier = modifier.width(176.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            CourseAvatar(courseId = course.id, code = course.courseCode, title = course.title, size = 36.dp)
            Spacer(Modifier.height(10.dp))
            if (course.courseCode.isNotBlank()) {
                Text(
                    text = course.courseCode,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = course.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.size(width = 148.dp, height = 44.dp),
            )
        }
    }
}
