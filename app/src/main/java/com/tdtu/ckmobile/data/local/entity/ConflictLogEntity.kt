package com.tdtu.ckmobile.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conflict_log")
data class ConflictLogEntity(
    @PrimaryKey(autoGenerate = true)
    val logId: Long = 0,
    val taskId: String,
    val type: String,
    val localJson: String,
    val serverJson: String,
    val resolution: String,
    val detectedAt: Long = System.currentTimeMillis()
)
