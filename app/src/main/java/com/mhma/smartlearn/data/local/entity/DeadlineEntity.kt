package com.mhma.smartlearn.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "deadlines",
    indices = [Index("courseId"), Index("dueDateMillis")],
)
data class DeadlineEntity(
    /** Moodle's calendar event id. */
    @PrimaryKey val id: Long,
    /** 0 for site-wide events. */
    val courseId: Long,
    val courseName: String,
    val title: String,
    val dueDateMillis: Long,
    val url: String? = null,
    /** Moodle event type: due, open, close, gradingdue... */
    val kind: String = "due",
    /** Ticked off by the student. Local only, Moodle never sees it. */
    val isCompleted: Boolean = false,
)
