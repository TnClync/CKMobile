package com.tdtu.ckmobile.data.remote.model

import com.tdtu.ckmobile.data.local.entity.SyncStatus
import com.tdtu.ckmobile.data.local.entity.TaskEntity
import kotlinx.serialization.Serializable

@Serializable
data class TaskDto(
    val id: String,
    val title: String,
    val description: String? = null,
    val isCompleted: Boolean = false,
    val deadline: Long? = null,
    val version: Int = 1,
    val updatedAt: Long = 0,
    val isDeleted: Boolean = false,
    val changeSeq: Long = 0
) {
    fun sameContentAs(local: TaskEntity): Boolean {
        return title == local.title &&
                (description ?: "") == (local.description ?: "") &&
                isCompleted == local.isCompleted &&
                deadline == local.deadline &&
                isDeleted == local.isDeleted
    }

    fun toEntity(syncStatus: SyncStatus = SyncStatus.SYNCED): TaskEntity {
        return TaskEntity(
            id = id,
            title = title,
            description = description ?: "",
            deadline = deadline,
            isCompleted = isCompleted,
            isDeleted = isDeleted,
            syncStatus = syncStatus,
            baseVersion = version,
            localRevision = 1,
            updatedAt = if (updatedAt > 0) updatedAt else System.currentTimeMillis(),
            createdAt = if (updatedAt > 0) updatedAt else System.currentTimeMillis()
        )
    }
}

@Serializable
data class CreateTaskRequest(
    val id: String,
    val title: String,
    val description: String? = null,
    val isCompleted: Boolean = false,
    val deadline: Long? = null,
    val baseVersion: Int = 0,
    val updatedAt: Long? = null
)

@Serializable
data class UpdateTaskRequest(
    val baseVersion: Int,
    val title: String,
    val description: String? = null,
    val isCompleted: Boolean = false,
    val deadline: Long? = null,
    val isDeleted: Boolean = false
)

@Serializable
data class ChangesResponse(
    val tasks: List<TaskDto>,
    val nextCursor: Long,
    val hasMore: Boolean
)

@Serializable
data class ConflictDetail(
    val error: String? = null,
    val baseVersion: Int? = null,
    val serverVersion: Int? = null,
    val current: TaskDto? = null
)

@Serializable
data class ConflictErrorResponse(
    val error: String? = null,
    val message: String? = null,
    val serverVersion: Int? = null,
    val current: TaskDto? = null,
    val detail: ConflictDetail? = null
) {
    fun resolveCurrentTask(): TaskDto? = current ?: detail?.current
}
