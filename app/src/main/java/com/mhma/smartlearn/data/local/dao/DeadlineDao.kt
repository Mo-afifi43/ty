package com.mhma.smartlearn.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.mhma.smartlearn.data.local.entity.DeadlineEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DeadlineDao {

    @Query("SELECT * FROM deadlines WHERE dueDateMillis >= :since ORDER BY dueDateMillis")
    fun observeFrom(since: Long): Flow<List<DeadlineEntity>>

    @Query("SELECT * FROM deadlines")
    suspend fun getAll(): List<DeadlineEntity>

    @Upsert
    suspend fun upsertAll(deadlines: List<DeadlineEntity>)

    /** Anything still in the future that Moodle no longer lists was moved or removed. */
    @Query("DELETE FROM deadlines WHERE dueDateMillis >= :now AND id NOT IN (:keep)")
    suspend fun deleteStaleUpcoming(now: Long, keep: List<Long>)

    @Query("DELETE FROM deadlines WHERE dueDateMillis < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)

    @Query("UPDATE deadlines SET isCompleted = :done WHERE id = :id")
    suspend fun setCompleted(id: Long, done: Boolean)

    @Query("DELETE FROM deadlines")
    suspend fun deleteAll()
}
