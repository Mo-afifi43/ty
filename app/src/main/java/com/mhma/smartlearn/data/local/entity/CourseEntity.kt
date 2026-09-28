package com.mhma.smartlearn.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "courses")
data class CourseEntity(
    /** Moodle's course id. */
    @PrimaryKey val id: Long,
    val title: String,
    /** Moodle's "short name", e.g. CS301. Empty if the portal didn't give us one. */
    val courseCode: String,
    val url: String,
    /** When the course content was last fetched. 0 means never. */
    val lastSynced: Long = 0L,
)
