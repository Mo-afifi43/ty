package com.mhma.smartlearn.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mhma.smartlearn.data.network.scraper.ItemKind

/**
 * Anything that shows up in a course section: files, folders, assignments, links...
 *
 * No foreign key on purpose. When a lecturer removes a file from the course we keep the
 * row if the student already downloaded it, so it stays visible in Downloads.
 */
@Entity(
    tableName = "resources",
    indices = [Index("sectionId"), Index("courseId"), Index("isDownloaded")],
)
data class ResourceEntity(
    /** Moodle's course-module id, or a negative hash for files found inside folders. */
    @PrimaryKey val id: Long,
    val sectionId: Long,
    val courseId: Long,
    val title: String,
    /** One of [ItemKind]'s names. Stored as text so the schema stays readable. */
    val kind: String,
    val downloadUrl: String,
    val localFilePath: String? = null,
    /** File extension without the dot ("pdf"), or empty when unknown. */
    val fileType: String = "",
    val isDownloaded: Boolean = false,
    val fileSizeBytes: Long = 0L,
    /** Assignment / quiz deadline shown on the course page, if any. */
    val dueDateMillis: Long? = null,
    /** Small print from Moodle, e.g. "Uploaded 3/9/25" or the folder a file came from. */
    val details: String = "",
    val orderIndex: Int = 0,
)

// Kept outside the class so Room never mistakes them for columns.
val ResourceEntity.itemKind: ItemKind
    get() = runCatching { ItemKind.valueOf(kind) }.getOrDefault(ItemKind.OTHER)

val ResourceEntity.isFile: Boolean
    get() = itemKind == ItemKind.FILE
