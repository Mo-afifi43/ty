package com.mhma.smartlearn.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sections",
    foreignKeys = [
        ForeignKey(
            entity = CourseEntity::class,
            parentColumns = ["id"],
            childColumns = ["courseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("courseId")],
)
data class CourseSectionEntity(
    /** Moodle's section id, or a negative number we made up when the page didn't expose one. */
    @PrimaryKey val id: Long,
    val courseId: Long,
    val sectionName: String,
    val orderIndex: Int,
)
