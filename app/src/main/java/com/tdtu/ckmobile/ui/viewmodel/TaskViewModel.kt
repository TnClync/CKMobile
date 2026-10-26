package com.tdtu.ckmobile.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tdtu.ckmobile.data.local.entity.TaskEntity
import com.tdtu.ckmobile.data.repository.TaskRepository
import com.tdtu.ckmobile.util.NetworkMonitor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TaskViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: TaskRepository = TaskRepository(application.applicationContext),
    private val networkMonitor: NetworkMonitor = NetworkMonitor(application.applicationContext)
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(TaskUiState())
    val uiState: StateFlow<TaskUiState> = _uiState.asStateFlow()

    init {
        // Observe local database tasks (Single Source of Truth)
        viewModelScope.launch {
            repository.tasks.collect { taskList ->
                _uiState.update { it.copy(tasks = taskList) }
            }
        }

        // Observe task counts
        viewModelScope.launch {
            repository.totalCount.collect { total ->
                _uiState.update { it.copy(totalCount = total) }
            }
        }

        viewModelScope.launch {
            repository.unsyncedCount.collect { unsynced ->
                _uiState.update { it.copy(unsyncedCount = unsynced) }
            }
        }

        // Observe network connectivity
        var previousOnline: Boolean? = null
        viewModelScope.launch {
            networkMonitor.isOnline.collect { isOnline ->
                _uiState.update { it.copy(isOnline = isOnline) }
                // Trigger auto-sync when network reconnects (TC03)
                if (previousOnline == false && isOnline) {
                    syncNow()
                }
                previousOnline = isOnline
            }
        }

        // Observe conflict resolution events
        viewModelScope.launch {
            repository.conflictEvents.collect { message ->
                _uiState.update { it.copy(conflictNotice = message) }
                // Auto dismiss after 6 seconds
                delay(6000)
                _uiState.update { current ->
                    if (current.conflictNotice == message) current.copy(conflictNotice = null) else current
                }
            }
        }

        // Initial sync on startup
        syncNow()
    }

    fun addTask(title: String, description: String, deadline: Long?) {
        viewModelScope.launch {
            repository.addTask(title, description, deadline)
        }
    }

    fun updateTask(
        id: String,
        title: String,
        description: String,
        deadline: Long?,
        isCompleted: Boolean
    ) {
        viewModelScope.launch {
            repository.updateTask(id, title, description, deadline, isCompleted)
        }
    }

    fun toggleCompleted(task: TaskEntity) {
        viewModelScope.launch {
            repository.toggleCompleted(task.id, !task.isCompleted)
        }
    }

    fun deleteTask(id: String) {
        viewModelScope.launch {
            repository.deleteTask(id)
        }
    }

    fun retryTask(task: TaskEntity) {
        viewModelScope.launch {
            repository.retryTask(task.id)
            syncNow()
        }
    }

    fun syncNow() {
        if (_uiState.value.isSyncing) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true) }
            try {
                repository.syncNow()
            } catch (e: Exception) {
                // Log and update state
            } finally {
                _uiState.update { it.copy(isSyncing = false) }
            }
        }
    }

    fun openAddTaskSheet() {
        _uiState.update { it.copy(isAddSheetOpen = true) }
    }

    fun closeAddTaskSheet() {
        _uiState.update { it.copy(isAddSheetOpen = false) }
    }

    fun openEditTaskSheet(task: TaskEntity) {
        _uiState.update { it.copy(selectedTaskForEdit = task) }
    }

    fun closeEditTaskSheet() {
        _uiState.update { it.copy(selectedTaskForEdit = null) }
    }

    fun dismissConflictNotice() {
        _uiState.update { it.copy(conflictNotice = null) }
    }
}
