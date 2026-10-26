package com.tdtu.ckmobile.data.repository

import android.content.Context
import com.tdtu.ckmobile.data.local.TaskDatabase
import com.tdtu.ckmobile.data.local.entity.SyncStatus
import com.tdtu.ckmobile.data.local.entity.TaskEntity
import com.tdtu.ckmobile.data.remote.RetrofitClient
import com.tdtu.ckmobile.data.sync.SyncManager
import com.tdtu.ckmobile.data.sync.SyncResult
import com.tdtu.ckmobile.data.sync.SyncScheduler
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class TaskRepository(
    private val context: Context,
    private val database: TaskDatabase = TaskDatabase.getInstance(context),
    val syncManager: SyncManager = SyncManager(
        taskDao = database.taskDao(),
        conflictLogDao = database.conflictLogDao(),
        taskApi = RetrofitClient.taskApi,
        context = context
    )
) {
    private val taskDao = database.taskDao()

    val tasks: Flow<List<TaskEntity>> = taskDao.observeTasks()
    val totalCount: Flow<Int> = taskDao.observeTotalCount()
    val unsyncedCount: Flow<Int> = taskDao.observeUnsyncedCount()
    val conflictEvents: Flow<String> = syncManager.conflictEvents

    suspend fun addTask(
        title: String,
        description: String,
        deadline: Long?
    ): String {
        val now = System.currentTimeMillis()
        val taskId = UUID.randomUUID().toString()
        val newTask = TaskEntity(
            id = taskId,
            title = title.trim(),
            description = description.trim(),
            deadline = deadline,
            isCompleted = false,
            isDeleted = false,
            syncStatus = SyncStatus.PENDING,
            baseVersion = 0,
            localRevision = 1,
            updatedAt = now,
            createdAt = now
        )

        taskDao.insert(newTask)
        SyncScheduler.requestSync(context)
        return taskId
    }

    suspend fun updateTask(
        id: String,
        title: String,
        description: String,
        deadline: Long?,
        isCompleted: Boolean
    ) {
        val now = System.currentTimeMillis()
        taskDao.updateContent(
            id = id,
            title = title.trim(),
            description = description.trim(),
            deadline = deadline,
            isCompleted = isCompleted,
            now = now
        )
        SyncScheduler.requestSync(context)
    }

    suspend fun toggleCompleted(id: String, isCompleted: Boolean) {
        val now = System.currentTimeMillis()
        taskDao.updateCompleted(id, isCompleted, now)
        SyncScheduler.requestSync(context)
    }

    suspend fun deleteTask(id: String) {
        val now = System.currentTimeMillis()
        taskDao.softDelete(id, now)
        SyncScheduler.requestSync(context)
    }

    suspend fun retryTask(id: String) {
        taskDao.setStatus(id, SyncStatus.PENDING)
        SyncScheduler.requestSync(context)
    }

    suspend fun syncNow(): SyncResult {
        return syncManager.sync()
    }
}
