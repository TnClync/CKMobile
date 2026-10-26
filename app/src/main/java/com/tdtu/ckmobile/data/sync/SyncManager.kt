package com.tdtu.ckmobile.data.sync

import android.content.Context
import android.util.Log
import com.tdtu.ckmobile.data.local.dao.ConflictLogDao
import com.tdtu.ckmobile.data.local.dao.TaskDao
import com.tdtu.ckmobile.data.local.entity.ConflictLogEntity
import com.tdtu.ckmobile.data.local.entity.SyncStatus
import com.tdtu.ckmobile.data.local.entity.TaskEntity
import com.tdtu.ckmobile.data.remote.api.TaskApi
import com.tdtu.ckmobile.data.remote.model.ConflictErrorResponse
import com.tdtu.ckmobile.data.remote.model.CreateTaskRequest
import com.tdtu.ckmobile.data.remote.model.TaskDto
import com.tdtu.ckmobile.data.remote.model.UpdateTaskRequest
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.io.IOException

sealed class SyncResult {
    data object Success : SyncResult()
    data class TransientError(val message: String) : SyncResult()
    data class PermanentError(val message: String) : SyncResult()
}

class SyncManager(
    private val taskDao: TaskDao,
    private val conflictLogDao: ConflictLogDao,
    private val taskApi: TaskApi,
    private val context: Context
) {
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val prefs = context.getSharedPreferences("task_sync_prefs", Context.MODE_PRIVATE)

    private val _conflictEvents = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val conflictEvents = _conflictEvents.asSharedFlow()

    private var lastCursor: Long
        get() = prefs.getLong("last_cursor", 0L)
        set(value) = prefs.edit().putLong("last_cursor", value).apply()

    suspend fun sync(): SyncResult = mutex.withLock {
        Log.d("SyncManager", "Starting sync cycle...")
        // Phase 1: PUSH local changes to server (up to 3 rounds)
        val pushResult = executePushRounds(maxRounds = 3)
        if (pushResult is SyncResult.TransientError) {
            return@withLock pushResult
        }

        // Phase 2: PULL server changes
        val pullResult = executePull()
        return@withLock pullResult
    }

    private suspend fun executePushRounds(maxRounds: Int): SyncResult {
        var rounds = 0
        while (rounds < maxRounds) {
            rounds++
            val unsynced = taskDao.getUnsynced()
            if (unsynced.isEmpty()) break

            for (task in unsynced) {
                val outcome = pushOneTask(task)
                if (outcome is SyncResult.TransientError) {
                    return outcome
                }
            }
        }
        return SyncResult.Success
    }

    private suspend fun pushOneTask(task: TaskEntity): SyncResult {
        val sentRevision = task.localRevision

        // Task created and deleted offline without reaching server
        if (task.isDeleted && task.baseVersion == 0) {
            taskDao.deleteIfUnchanged(task.id, sentRevision)
            return SyncResult.Success
        }

        taskDao.setStatus(task.id, SyncStatus.SYNCING)

        try {
            val response = when {
                task.isDeleted -> taskApi.delete(task.id, task.baseVersion)
                task.baseVersion == 0 -> taskApi.create(
                    CreateTaskRequest(
                        id = task.id,
                        title = task.title,
                        description = task.description,
                        isCompleted = task.isCompleted,
                        deadline = task.deadline,
                        baseVersion = 0,
                        updatedAt = task.updatedAt
                    )
                )
                else -> taskApi.update(
                    task.id,
                    UpdateTaskRequest(
                        baseVersion = task.baseVersion,
                        title = task.title,
                        description = task.description,
                        isCompleted = task.isCompleted,
                        deadline = task.deadline,
                        isDeleted = task.isDeleted
                    )
                )
            }

            when (response.code()) {
                200, 201 -> {
                    val serverDto = response.body()
                    if (serverDto != null) {
                        if (serverDto.sameContentAs(task)) {
                            taskDao.markPushed(task.id, serverDto.version, sentRevision)
                        } else {
                            taskDao.rebase(task.id, serverDto.version)
                        }
                    }
                    return SyncResult.Success
                }

                409 -> {
                    // Conflict detected: Last-Write-Wins (LWW) by server arrival order
                    val rawBody = response.errorBody()?.string() ?: ""
                    var serverDto: TaskDto? = null
                    try {
                        val conflictResp = json.decodeFromString<ConflictErrorResponse>(rawBody)
                        serverDto = conflictResp.resolveCurrentTask()
                    } catch (e: Exception) {
                        Log.e("SyncManager", "Failed to parse conflict response", e)
                    }

                    if (serverDto != null) {
                        if (serverDto.sameContentAs(task)) {
                            taskDao.markPushed(task.id, serverDto.version, sentRevision)
                            return SyncResult.Success
                        }

                        // Real conflict: record in conflict log
                        conflictLogDao.insert(
                            ConflictLogEntity(
                                taskId = task.id,
                                type = if (serverDto.isDeleted) "DELETE_EDIT" else "EDIT_EDIT",
                                localJson = json.encodeToString(TaskEntity.serializer(), task),
                                serverJson = json.encodeToString(TaskDto.serializer(), serverDto),
                                resolution = "LOCAL_WINS",
                                detectedAt = System.currentTimeMillis()
                            )
                        )

                        // Notify UI about LWW resolution
                        _conflictEvents.tryEmit(
                            "Đã có thay đổi từ thiết bị khác. Đã giữ phiên bản của bạn (quy tắc cuối-gọi-thắng)."
                        )

                        // Rebase to server version; next push round will resend with new baseVersion
                        taskDao.rebase(task.id, serverDto.version)
                    } else {
                        taskDao.setStatus(task.id, SyncStatus.FAILED)
                    }
                    return SyncResult.Success
                }

                404 -> {
                    // Not found on PUT: convert to create
                    taskDao.rebase(task.id, 0)
                    return SyncResult.Success
                }

                400, 422 -> {
                    taskDao.setStatus(task.id, SyncStatus.FAILED)
                    return SyncResult.PermanentError("HTTP ${response.code()}: ${response.message()}")
                }

                else -> {
                    taskDao.setStatus(task.id, SyncStatus.FAILED)
                    return SyncResult.TransientError("HTTP ${response.code()}: ${response.message()}")
                }
            }
        } catch (e: IOException) {
            taskDao.setStatus(task.id, SyncStatus.FAILED)
            return SyncResult.TransientError("Network error: ${e.localizedMessage}")
        } catch (e: Exception) {
            taskDao.setStatus(task.id, SyncStatus.FAILED)
            return SyncResult.PermanentError("Error: ${e.localizedMessage}")
        }
    }

    private suspend fun executePull(): SyncResult {
        var hasMore = true
        var cursor = lastCursor

        while (hasMore) {
            try {
                val response = taskApi.getChanges(since = cursor, limit = 100)
                if (!response.isSuccessful || response.body() == null) {
                    return SyncResult.TransientError("Pull failed with status ${response.code()}")
                }

                val changes = response.body()!!
                for (serverTask in changes.tasks) {
                    val localTask = taskDao.getById(serverTask.id)
                    if (localTask == null) {
                        if (!serverTask.isDeleted) {
                            taskDao.upsert(serverTask.toEntity(SyncStatus.SYNCED))
                        }
                    } else {
                        if (localTask.syncStatus == SyncStatus.SYNCED) {
                            if (serverTask.version > localTask.baseVersion) {
                                taskDao.upsert(serverTask.toEntity(SyncStatus.SYNCED))
                            }
                        }
                        // If localTask has unsynced local changes (PENDING/SYNCING/FAILED),
                        // retain local changes so push phase handles conflict via 409 LWW.
                    }
                }

                cursor = changes.nextCursor
                lastCursor = cursor
                hasMore = changes.hasMore
            } catch (e: IOException) {
                return SyncResult.TransientError("Network error during pull: ${e.localizedMessage}")
            } catch (e: Exception) {
                return SyncResult.PermanentError("Error during pull: ${e.localizedMessage}")
            }
        }

        taskDao.purgeSyncedTombstones()
        return SyncResult.Success
    }
}
