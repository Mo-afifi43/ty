package com.mhma.smartlearn.ui.downloads

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mhma.smartlearn.data.download.DownloadProgress
import com.mhma.smartlearn.data.local.entity.ResourceEntity
import com.mhma.smartlearn.ui.components.EmptyState
import com.mhma.smartlearn.ui.components.ItemVisuals
import com.mhma.smartlearn.util.FileOpener
import com.mhma.smartlearn.util.formatBytes
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var pendingRemoval by remember { mutableStateOf<ResourceEntity?>(null) }
    var pendingGroupRemoval by remember { mutableStateOf<DownloadGroup?>(null) }

    fun openFile(item: ResourceEntity) {
        val path = item.localFilePath
        val ok = path != null && FileOpener.open(context, File(path))
        if (!ok) {
            scope.launch { snackbar.showSnackbar("Couldn't open that file. It may have been moved or deleted.") }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Downloads")
                        if (state.fileCount > 0) {
                            Text(
                                text = "${state.fileCount} ${if (state.fileCount == 1) "file" else "files"} · ${formatBytes(state.totalBytes)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (state.loaded && state.groups.isEmpty() && state.active.isEmpty()) {
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                EmptyState(
                    icon = Icons.Outlined.FileDownload,
                    title = "Nothing saved yet",
                    message = "Tap the download icon next to any lecture file and it'll show up here, ready to open without a connection.",
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                if (state.active.isNotEmpty()) {
                    item(key = "active-header") { GroupHeader(title = "In progress", subtitle = null, onRemoveAll = null) }
                    items(state.active, key = { "active-${it.item.id}" }) { row ->
                        ActiveRow(
                            row = row,
                            onCancel = { viewModel.cancel(row.item.id) },
                            onDismiss = { viewModel.dismissError(row.item.id) },
                        )
                    }
                }
                state.groups.forEach { group ->
                    item(key = "group-${group.courseId}") {
                        GroupHeader(
                            title = group.title,
                            subtitle = buildString {
                                if (group.code.isNotBlank()) append(group.code).append(" · ")
                                append("${group.files.size} ${if (group.files.size == 1) "file" else "files"}")
                                append(" · ").append(formatBytes(group.bytes))
                            },
                            onRemoveAll = { pendingGroupRemoval = group },
                        )
                    }
                    items(group.files, key = { "file-${it.id}" }) { item ->
                        DownloadedRow(
                            item = item,
                            onOpen = { openFile(item) },
                            onShare = {
                                item.localFilePath?.let { path ->
                                    if (!FileOpener.share(context, File(path))) {
                                        scope.launch { snackbar.showSnackbar("Couldn't share that file.") }
                                    }
                                }
                            },
                            onRemove = { pendingRemoval = item },
                        )
                    }
                }
            }
        }
    }

    pendingRemoval?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Text("Remove download?") },
            text = { Text("\"${item.title}\" will be deleted from this phone. You can download it again any time.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.remove(item)
                    pendingRemoval = null
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoval = null }) { Text("Keep") }
            },
        )
    }

    pendingGroupRemoval?.let { group ->
        AlertDialog(
            onDismissRequest = { pendingGroupRemoval = null },
            title = { Text("Remove all files for ${group.title}?") },
            text = { Text("${group.files.size} files (${formatBytes(group.bytes)}) will be deleted from this phone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeAll(group)
                    pendingGroupRemoval = null
                }) { Text("Remove all") }
            },
            dismissButton = {
                TextButton(onClick = { pendingGroupRemoval = null }) { Text("Keep") }
            },
        )
    }
}

@Composable
private fun GroupHeader(
    title: String,
    subtitle: String?,
    onRemoveAll: (() -> Unit)?,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 16.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (onRemoveAll != null) {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Remove all files") },
                            onClick = {
                                menuOpen = false
                                onRemoveAll()
                            },
                        )
                    }
                }
            }
        }
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
    }
}

@Composable
private fun DownloadedRow(
    item: ResourceEntity,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onRemove: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        ListItem(
            modifier = Modifier.clickable(onClick = onOpen),
            headlineContent = { Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            supportingContent = {
                Text(
                    text = buildString {
                        append(ItemVisuals.fileLabel(item.fileType))
                        if (item.fileSizeBytes > 0) append(" · ").append(formatBytes(item.fileSizeBytes))
                    },
                )
            },
            leadingContent = {
                Icon(
                    imageVector = ItemVisuals.fileIcon(item.fileType),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
            trailingContent = {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "More")
                }
            },
        )
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            DropdownMenuItem(
                text = { Text("Open") },
                onClick = {
                    menuOpen = false
                    onOpen()
                },
            )
            DropdownMenuItem(
                text = { Text("Share") },
                onClick = {
                    menuOpen = false
                    onShare()
                },
            )
            DropdownMenuItem(
                text = { Text("Remove from phone") },
                leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    onRemove()
                },
            )
        }
    }
}

@Composable
private fun ActiveRow(
    row: ActiveDownload,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    val progress = row.progress
    val failed = progress.state == DownloadProgress.State.FAILED
    ListItem(
        headlineContent = { Text(row.item.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                text = when {
                    failed -> progress.message ?: "Download failed."
                    progress.state == DownloadProgress.State.QUEUED -> "Starting…"
                    progress.totalBytes > 0 -> "${formatBytes(progress.bytesRead)} of ${formatBytes(progress.totalBytes)}"
                    else -> "${formatBytes(progress.bytesRead)} so far"
                },
                color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingContent = {
            if (failed) {
                Icon(
                    imageVector = ItemVisuals.fileIcon(row.item.fileType),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            } else {
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
        },
        trailingContent = {
            IconButton(onClick = if (failed) onDismiss else onCancel) {
                Icon(Icons.Outlined.Close, contentDescription = if (failed) "Dismiss" else "Cancel")
            }
        },
    )
}
