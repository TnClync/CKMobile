package com.tdtu.ckmobile.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.tdtu.ckmobile.data.local.entity.SyncStatus
import com.tdtu.ckmobile.data.local.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE is_deleted = 0 ORDER BY is_completed ASC, deadline IS NULL, deadline ASC, updated_at DESC")
    fun observeTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: String): TaskEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(task: TaskEntity)

    @Upsert
    suspend fun upsert(task: TaskEntity)

    @Query("""
        UPDATE tasks SET title = :title, description = :description, deadline = :deadline,
            is_completed = :isCompleted, sync_status = 'PENDING',
            local_revision = local_revision + 1, updated_at = :now
        WHERE id = :id
    """)
    suspend fun updateContent(
        id: String,
        title: String,
        description: String,
        deadline: Long?,
        isCompleted: Boolean,
        now: Long
    )

    @Query("""
        UPDATE tasks SET is_completed = :isCompleted, sync_status = 'PENDING',
            local_revision = local_revision + 1, updated_at = :now
        WHERE id = :id
    """)
    suspend fun updateCompleted(id: String, isCompleted: Boolean, now: Long)

    @Query("""
        UPDATE tasks SET is_deleted = 1, sync_status = 'PENDING',
            local_revision = local_revision + 1, updated_at = :now
        WHERE id = :id
    """)
    suspend fun softDelete(id: String, now: Long)

    @Query("SELECT * FROM tasks WHERE sync_status != 'SYNCED'")
    suspend fun getUnsynced(): List<TaskEntity>

    @Query("SELECT COUNT(*) FROM tasks WHERE is_deleted = 0")
    fun observeTotalCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM tasks WHERE is_deleted = 0 AND sync_status != 'SYNCED'")
    fun observeUnsyncedCount(): Flow<Int>

    @Query("UPDATE tasks SET sync_status = :status WHERE id = :id")
    suspend fun setStatus(id: String, status: SyncStatus)

    @Query("""
        UPDATE tasks SET base_version = :serverVersion,
            sync_status = CASE WHEN local_revision = :sentRevision THEN 'SYNCED' ELSE 'PENDING' END
        WHERE id = :id
    """)
    suspend fun markPushed(id: String, serverVersion: Int, sentRevision: Int)

    @Query("UPDATE tasks SET base_version = :serverVersion, sync_status = 'PENDING' WHERE id = :id")
    suspend fun rebase(id: String, serverVersion: Int)

    @Query("DELETE FROM tasks WHERE id = :id AND local_revision = :sentRevision")
    suspend fun deleteIfUnchanged(id: String, sentRevision: Int)

    @Query("DELETE FROM tasks WHERE is_deleted = 1 AND sync_status = 'SYNCED'")
    suspend fun purgeSyncedTombstones()
}
