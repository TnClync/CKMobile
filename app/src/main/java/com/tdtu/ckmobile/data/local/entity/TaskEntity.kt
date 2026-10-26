package com.tdtu.ckmobile.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
enum class SyncStatus {
    PENDING,   // Chưa đồng bộ (#FFF3E0)
    SYNCING,   // Đang đồng bộ (#E3F2FD)
    SYNCED,    // Đã đồng bộ (#E8F5E9)
    FAILED     // Đồng bộ thất bại (#FFEBEE)
}

@Serializable
@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey
    val id: String,

    val title: String,
    val description: String = "",
    val deadline: Long? = null,

    @ColumnInfo(name = "is_completed")
    val isCompleted: Boolean = false,

    @ColumnInfo(name = "is_deleted")
    val isDeleted: Boolean = false,

    @ColumnInfo(name = "sync_status")
    val syncStatus: SyncStatus = SyncStatus.PENDING,

    @ColumnInfo(name = "base_version")
    val baseVersion: Int = 0,

    @ColumnInfo(name = "local_revision")
    val localRevision: Int = 1,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)

