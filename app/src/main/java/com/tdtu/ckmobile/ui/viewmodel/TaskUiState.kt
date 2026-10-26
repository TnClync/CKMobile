package com.tdtu.ckmobile.ui.viewmodel

import com.tdtu.ckmobile.data.local.entity.TaskEntity

data class TaskUiState(
    val tasks: List<TaskEntity> = emptyList(),
    val totalCount: Int = 0,
    val unsyncedCount: Int = 0,
    val isOnline: Boolean = true,
    val isSyncing: Boolean = false,
    val conflictNotice: String? = null,
    val selectedTaskForEdit: TaskEntity? = null,
    val isAddSheetOpen: Boolean = false,
    val userMessage: String? = null
)
