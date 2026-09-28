package com.mhma.smartlearn.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.ui.graphics.vector.ImageVector
import com.mhma.smartlearn.data.network.scraper.ItemKind

/** Icon + short label for a course item, based on what kind of thing it is. */
object ItemVisuals {

    fun icon(kind: ItemKind, fileType: String): ImageVector = when (kind) {
        ItemKind.FILE -> fileIcon(fileType)
        ItemKind.FOLDER -> Icons.Outlined.Folder
        ItemKind.ASSIGNMENT -> Icons.AutoMirrored.Outlined.Assignment
        ItemKind.QUIZ -> Icons.Outlined.Quiz
        ItemKind.LINK -> Icons.Outlined.Link
        ItemKind.PAGE -> Icons.AutoMirrored.Outlined.Article
        ItemKind.FORUM -> Icons.Outlined.Forum
        ItemKind.OTHER -> Icons.Outlined.Extension
    }

    fun label(kind: ItemKind, fileType: String): String = when (kind) {
        ItemKind.FILE -> fileLabel(fileType)
        ItemKind.FOLDER -> "Folder"
        ItemKind.ASSIGNMENT -> "Assignment"
        ItemKind.QUIZ -> "Quiz"
        ItemKind.LINK -> "Link"
        ItemKind.PAGE -> "Page"
        ItemKind.FORUM -> "Forum"
        ItemKind.OTHER -> "Activity"
    }

    fun fileIcon(fileType: String): ImageVector = when (fileType.lowercase()) {
        "pdf" -> Icons.Outlined.PictureAsPdf
        "ppt", "pptx", "odp", "key" -> Icons.Outlined.Slideshow
        "doc", "docx", "odt", "rtf", "txt", "md" -> Icons.Outlined.Description
        "xls", "xlsx", "ods", "csv" -> Icons.Outlined.TableChart
        "zip", "rar", "7z", "tar", "gz" -> Icons.Outlined.FolderZip
        "mp4", "mkv", "mov", "avi", "webm" -> Icons.Outlined.Movie
        "mp3", "m4a", "wav", "ogg" -> Icons.Outlined.MusicNote
        "png", "jpg", "jpeg", "gif", "webp", "bmp" -> Icons.Outlined.Image
        else -> Icons.AutoMirrored.Outlined.InsertDriveFile
    }

    fun fileLabel(fileType: String): String = when (fileType.lowercase()) {
        "pdf" -> "PDF"
        "ppt", "pptx", "odp", "key" -> "Slides"
        "doc", "docx", "odt", "rtf" -> "Document"
        "txt", "md" -> "Text"
        "xls", "xlsx", "ods", "csv" -> "Spreadsheet"
        "zip", "rar", "7z", "tar", "gz" -> "Archive"
        "mp4", "mkv", "mov", "avi", "webm" -> "Video"
        "mp3", "m4a", "wav", "ogg" -> "Audio"
        "png", "jpg", "jpeg", "gif", "webp", "bmp" -> "Image"
        "" -> "File"
        else -> fileType.uppercase()
    }
}
