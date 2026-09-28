package com.mhma.smartlearn.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

data class SectionWithItems(
    @Embedded val section: CourseSectionEntity,
    @Relation(parentColumn = "id", entityColumn = "sectionId")
    val items: List<ResourceEntity>,
)

/** Per-course file counts for the Courses tab. */
data class CourseFileCount(
    val courseId: Long,
    val total: Int,
    val downloaded: Int,
)
