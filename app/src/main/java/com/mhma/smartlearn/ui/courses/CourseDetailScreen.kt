package com.mhma.smartlearn.ui.courses

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mhma.smartlearn.data.download.DownloadProgress
import com.mhma.smartlearn.data.local.entity.ResourceEntity
import com.mhma.smartlearn.data.local.entity.isFile
import com.mhma.smartlearn.data.local.entity.itemKind
import com.mhma.smartlearn.data.network.scraper.ItemKind
import com.mhma.smartlearn.ui.components.DueChip
import com.mhma.smartlearn.ui.components.EmptyState
import com.mhma.smartlearn.ui.components.ItemVisuals
import com.mhma.smartlearn.util.FileOpener
import com.mhma.smartlearn.util.formatBytes
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseDetailScreen(
    onBack: () -> Unit,
    onOpenLink: (String) -> Unit,
    viewModel: CourseDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        val text = state.message ?: return@LaunchedEffect
        snackbar.showSnackbar(text)
        viewModel.dismissMessage()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = state.course?.title ?: "Course",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val code = state.course?.courseCode.orEmpty()
                        if (code.isNotBlank()) {
                            Text(
                                text = code,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh, enabled = !state.isRefreshing) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Refresh this course")
                    }
                    state.course?.url?.let { url ->
                        IconButton(onClick = { onOpenLink(url) }) {
                            Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = "Open in Smart Learning")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            when {
                !state.loaded -> Box(Modifier.fillMaxSize())
                state.sections.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        EmptyState(
                            icon = Icons.Outlined.Inbox,
                            title = if (state.isRefreshing) "Fetching course content…" else "Nothing here yet",
                            message = if (state.isRefreshing) {
                                "This only takes a few seconds."
                            } else {
                                "The lecturer hasn't posted anything, or the page couldn't be read. Pull down to try again."
                            },
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 32.dp),
                    ) {
                        state.sections.forEach { section ->
                            val expanded = state.isExpanded(section.section.id)
                            item(key = "section-${section.section.id}") {
                                SectionHeader(
                                    name = section.section.sectionName,
                                    count = section.items.size,
                                    expanded = expanded,
                                    onToggle = { viewModel.toggleSection(section.section.id) },
                                )
                            }
                            if (expanded) {
                                if (section.items.isEmpty()) {
                                    item(key = "empty-${section.section.id}") {
                                        Text(
                                            text = "Nothing in this section yet.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                                        )
                                    }
                                }
                                items(section.items, key = { "item-${it.id}" }) { item ->
                                    ResourceRow(
                                        item = item,
                                        progress = state.downloads[item.id],
                                        onOpen = {
                                            openItem(
                                                item = item,
                                                onOpenLink = onOpenLink,
                                                onDownload = { viewModel.download(item) },
                                                openFile = { file ->
                                                    if (!FileOpener.open(context, file)) {
                                                        viewModel.showMessage("No app on this phone can open that file.")
                                                    }
                                                },
                                            )
                                        },
                                        onDownload = { viewModel.download(item) },
                                        onCancel = { viewModel.cancelDownload(item.id) },
                                        onDismissError = { viewModel.dismissDownloadError(item.id) },
                                        onOpenInPortal = { onOpenLink(item.downloadUrl) },
                                        onRemoveDownload = { viewModel.removeDownload(item) },
                                        onShare = {
                                            item.localFilePath?.let { path ->
                                                if (!FileOpener.share(context, File(path))) {
                                                    viewModel.showMessage("Couldn't share that file.")
                                                }
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun openItem(
    item: ResourceEntity,
    onOpenLink: (String) -> Unit,
    onDownload: () -> Unit,
    openFile: (File) -> Unit,
) {
    when {
        item.isFile && item.isDownloaded && item.localFilePath != null -> openFile(File(item.localFilePath))
        item.isFile -> onDownload()
        else -> onOpenLink(item.downloadUrl)
    }
}

@Composable
private fun SectionHeader(
    name: String,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val rotation by animateFloatAsState(targetValue = if (expanded) 180f else 0f, label = "chevron")
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = when (count) {
                    0 -> "empty"
                    1 -> "1 item"
                    else -> "$count items"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Icon(
                imageVector = Icons.Outlined.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                modifier = Modifier.rotate(rotation),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ResourceRow(
    item: ResourceEntity,
    progress: DownloadProgress?,
    onOpen: () -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDismissError: () -> Unit,
    onOpenInPortal: () -> Unit,
    onRemoveDownload: () -> Unit,
    onShare: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val kind = item.itemKind
    val failed = progress?.state == DownloadProgress.State.FAILED

    Box {
        ListItem(
            modifier = Modifier
                .combinedClickable(onClick = onOpen, onLongClick = { menuOpen = true })
                .animateContentSize(),
            headlineContent = {
                Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
            },
            supportingContent = {
                when {
                    failed -> Column {
                        Text(
                            text = progress?.message ?: "Download failed.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Row {
                            TextButton(onClick = onDownload) { Text("Try again") }
                            TextButton(onClick = onDismissError) { Text("Dismiss") }
                        }
                    }
                    kind == ItemKind.ASSIGNMENT || kind == ItemKind.QUIZ -> {
                        val due = item.dueDateMillis
                        if (due != null) {
                            DueChip(dueMillis = due, modifier = Modifier.padding(top = 4.dp))
                        } else {
                            Text(ItemVisuals.label(kind, item.fileType))
                        }
                    }
                    else -> Text(
                        text = buildString {
                            append(ItemVisuals.label(kind, item.fileType))
                            if (item.isDownloaded && item.fileSizeBytes > 0) append(" · ").append(formatBytes(item.fileSizeBytes))
                            if (item.details.isNotBlank()) append(" · ").append(item.details)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
            leadingContent = {
                Icon(
                    imageVector = ItemVisuals.icon(kind, item.fileType),
                    contentDescription = null,
                    tint = if (item.isDownloaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            trailingContent = {
                when {
                    progress != null && progress.isActive -> {
                        IconButton(onClick = onCancel) {
                            val fraction = progress.fraction
                            if (fraction == null) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                            } else {
                                CircularProgressIndicator(
                                    progress = { fraction },
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.5.dp,
                                )
                            }
                        }
                    }
                    item.isFile && item.isDownloaded -> {
                        IconButton(onClick = onOpen) {
                            Icon(
                                imageVector = Icons.Outlined.DownloadDone,
                                contentDescription = "Saved. Tap to open",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    item.isFile -> {
                        IconButton(onClick = onDownload) {
                            Icon(Icons.Outlined.Download, contentDescription = "Download")
                        }
                    }
                    else -> {
                        Icon(
                            imageVector = Icons.Outlined.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
        )

        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text("Open in Smart Learning") },
                onClick = {
                    menuOpen = false
                    onOpenInPortal()
                },
            )
            if (item.isFile && item.isDownloaded) {
                DropdownMenuItem(
                    text = { Text("Share file") },
                    onClick = {
                        menuOpen = false
                        onShare()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Remove download") },
                    onClick = {
                        menuOpen = false
                        onRemoveDownload()
                    },
                )
            } else if (item.isFile) {
                DropdownMenuItem(
                    text = { Text("Download") },
                    onClick = {
                        menuOpen = false
                        onDownload()
                    },
                )
            }
        }
    }
}
