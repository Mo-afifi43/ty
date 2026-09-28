package com.mhma.smartlearn.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.mhma.smartlearn.data.local.entity.CourseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CourseDao {

    @Query("SELECT * FROM courses ORDER BY title COLLATE NOCASE")
    fun observeAll(): Flow<List<CourseEntity>>

    @Query("SELECT * FROM courses WHERE id = :id")
    fun observe(id: Long): Flow<CourseEntity?>

    @Query("SELECT * FROM courses")
    suspend fun getAll(): List<CourseEntity>

    @Query("SELECT * FROM courses WHERE id = :id")
    suspend fun get(id: Long): CourseEntity?

    @Upsert
    suspend fun upsertAll(courses: List<CourseEntity>)

    @Query("DELETE FROM courses WHERE id NOT IN (:keep)")
    suspend fun deleteAllExcept(keep: List<Long>)

    @Query("UPDATE courses SET lastSynced = :time WHERE id = :id")
    suspend fun markSynced(id: Long, time: Long)

    @Query("DELETE FROM courses")
    suspend fun deleteAll()
}
