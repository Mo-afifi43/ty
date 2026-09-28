package com.mhma.smartlearn.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.mhma.smartlearn.data.local.entity.CourseFileCount
import com.mhma.smartlearn.data.local.entity.ResourceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ResourceDao {

    @Query("SELECT * FROM resources WHERE courseId = :courseId")
    suspend fun getForCourse(courseId: Long): List<ResourceEntity>

    @Query("SELECT * FROM resources WHERE id = :id")
    suspend fun get(id: Long): ResourceEntity?

    @Query("SELECT * FROM resources WHERE isDownloaded = 1 ORDER BY title COLLATE NOCASE")
    fun observeDownloaded(): Flow<List<ResourceEntity>>

    @Query("SELECT * FROM resources WHERE isDownloaded = 1")
    suspend fun getDownloaded(): List<ResourceEntity>

    @Query("SELECT * FROM resources WHERE isDownloaded = 1 AND courseId IN (:courseIds)")
    suspend fun getDownloadedForCourses(courseIds: List<Long>): List<ResourceEntity>

    @Query(
        "SELECT courseId, COUNT(*) AS total, SUM(isDownloaded) AS downloaded " +
            "FROM resources WHERE kind = 'FILE' GROUP BY courseId"
    )
    fun observeFileCounts(): Flow<List<CourseFileCount>>

    @Upsert
    suspend fun upsertAll(resources: List<ResourceEntity>)

    /** Removes items that disappeared from the course, unless the student has them downloaded. */
    @Query("DELETE FROM resources WHERE courseId = :courseId AND isDownloaded = 0 AND id NOT IN (:keep)")
    suspend fun deleteStale(courseId: Long, keep: List<Long>)

    @Query("DELETE FROM resources WHERE courseId NOT IN (:keepCourseIds)")
    suspend fun deleteForOtherCourses(keepCourseIds: List<Long>)

    @Query(
        "UPDATE resources SET isDownloaded = 1, localFilePath = :path, fileType = :fileType, " +
            "fileSizeBytes = :size WHERE id = :id"
    )
    suspend fun markDownloaded(id: Long, path: String, fileType: String, size: Long)

    @Query("UPDATE resources SET isDownloaded = 0, localFilePath = NULL, fileSizeBytes = 0 WHERE id = :id")
    suspend fun markRemoved(id: Long)
}
