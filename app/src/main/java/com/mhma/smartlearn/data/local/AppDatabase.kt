package com.mhma.smartlearn.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.mhma.smartlearn.data.local.dao.CourseDao
import com.mhma.smartlearn.data.local.dao.DeadlineDao
import com.mhma.smartlearn.data.local.dao.ResourceDao
import com.mhma.smartlearn.data.local.dao.SectionDao
import com.mhma.smartlearn.data.local.entity.CourseEntity
import com.mhma.smartlearn.data.local.entity.CourseSectionEntity
import com.mhma.smartlearn.data.local.entity.DeadlineEntity
import com.mhma.smartlearn.data.local.entity.ResourceEntity

@Database(
    entities = [
        CourseEntity::class,
        CourseSectionEntity::class,
        ResourceEntity::class,
        DeadlineEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun courseDao(): CourseDao
    abstract fun sectionDao(): SectionDao
    abstract fun resourceDao(): ResourceDao
    abstract fun deadlineDao(): DeadlineDao

    companion object {
        const val NAME = "smartlearn.db"
    }
}
