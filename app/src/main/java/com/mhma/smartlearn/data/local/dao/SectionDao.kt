package com.mhma.smartlearn.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.mhma.smartlearn.data.local.entity.CourseSectionEntity
import com.mhma.smartlearn.data.local.entity.SectionWithItems
import kotlinx.coroutines.flow.Flow

@Dao
interface SectionDao {

    @Transaction
    @Query("SELECT * FROM sections WHERE courseId = :courseId ORDER BY orderIndex")
    fun observeWithItems(courseId: Long): Flow<List<SectionWithItems>>

    @Query("SELECT * FROM sections WHERE courseId = :courseId ORDER BY orderIndex")
    suspend fun getForCourse(courseId: Long): List<CourseSectionEntity>

    @Upsert
    suspend fun upsertAll(sections: List<CourseSectionEntity>)

    @Query("DELETE FROM sections WHERE courseId = :courseId AND id NOT IN (:keep)")
    suspend fun deleteStale(courseId: Long, keep: List<Long>)
}
