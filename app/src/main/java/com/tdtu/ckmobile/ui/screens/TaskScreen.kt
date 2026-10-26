package com.tdtu.ckmobile.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tdtu.ckmobile.data.local.entity.SyncStatus
import com.tdtu.ckmobile.data.local.entity.TaskEntity
import com.tdtu.ckmobile.ui.components.AddTaskBottomSheet
import com.tdtu.ckmobile.ui.components.ConflictBanner
import com.tdtu.ckmobile.ui.components.EditTaskBottomSheet
import com.tdtu.ckmobile.ui.components.OfflineBanner
import com.tdtu.ckmobile.ui.components.TaskCard
import com.tdtu.ckmobile.ui.theme.AppBackground
import com.tdtu.ckmobile.ui.theme.BorderLight
import com.tdtu.ckmobile.ui.theme.CKMobileTheme
import com.tdtu.ckmobile.ui.theme.OfflineDot
import com.tdtu.ckmobile.ui.theme.OnlineDot
import com.tdtu.ckmobile.ui.theme.PrimaryBlue
import com.tdtu.ckmobile.ui.theme.TextMuted
import com.tdtu.ckmobile.ui.theme.TextSecondary
import com.tdtu.ckmobile.ui.viewmodel.TaskUiState
import com.tdtu.ckmobile.ui.viewmodel.TaskViewModel

/**
 * Main Task Screen matching mockups docs/mockups/01-list-online.png and 02-list-offline.png
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskScreen(
    viewModel: TaskViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    TaskScreenContent(
        state = state,
        onAddTaskClick = { viewModel.openAddTaskSheet() },
        onSyncClick = { viewModel.syncNow() },
        onDismissConflict = { viewModel.dismissConflictNotice() },
        onTaskClick = { viewModel.openEditTaskSheet(it) },
        onToggleCompleted = { viewModel.toggleCompleted(it) },
        onRetryTask = { viewModel.retryTask(it) },
        onCloseAddSheet = { viewModel.closeAddTaskSheet() },
        onSaveNewTask = { title, desc, deadline -> viewModel.addTask(title, desc, deadline) },
        onCloseEditSheet = { viewModel.closeEditTaskSheet() },
        onUpdateTask = { id, title, desc, deadline, isCompleted ->
            viewModel.updateTask(id, title, desc, deadline, isCompleted)
        },
        onDeleteTask = { viewModel.deleteTask(it) },
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskScreenContent(
    state: TaskUiState,
    onAddTaskClick: () -> Unit,
    onSyncClick: () -> Unit,
    onDismissConflict: () -> Unit,
    onTaskClick: (TaskEntity) -> Unit,
    onToggleCompleted: (TaskEntity) -> Unit,
    onRetryTask: (TaskEntity) -> Unit,
    onCloseAddSheet: () -> Unit,
    onSaveNewTask: (String, String, Long?) -> Unit,
    onCloseEditSheet: () -> Unit,
    onUpdateTask: (String, String, String, Long?, Boolean) -> Unit,
    onDeleteTask: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val addSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val editSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Smooth sync rotation animation when manual or background sync is running
    val syncRotation by if (state.isSyncing) {
        val transition = rememberInfiniteTransition(label = "sync_rotate_main")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "sync_rotate_anim"
        )
    } else {
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    }

    Scaffold(
        containerColor = AppBackground,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddTaskClick,
                shape = RoundedCornerShape(16.dp),
                containerColor = PrimaryBlue,
                contentColor = Color.White,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp),
                modifier = Modifier
                    .padding(end = 6.dp, bottom = 12.dp)
                    .size(56.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Thêm công việc",
                    modifier = Modifier.size(26.dp)
                )
            }
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
        ) {
            // Header Top Bar matching mockups 01-list-online and 02-list-offline
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Title and subtitle count
                Column {
                    Text(
                        text = "Công việc",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryBlue
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${state.totalCount} công việc · ${state.unsyncedCount} chưa đồng bộ",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Right: Network Status Badge + Sync Icon Button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Network Status Badge (Green dot: Online, Grey dot: Ngoại tuyến)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White)
                            .border(width = 1.dp, color = BorderLight, shape = RoundedCornerShape(20.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (state.isOnline) OnlineDot else OfflineDot)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (state.isOnline) "Online" else "Ngoại tuyến",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Manual Sync Trigger Button (circular border with sync icon)
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White)
                            .border(width = 1.dp, color = BorderLight, shape = RoundedCornerShape(12.dp))
                            .clickable(enabled = !state.isSyncing) { onSyncClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = "Đồng bộ thủ công",
                            tint = if (state.isSyncing) PrimaryBlue else TextSecondary,
                            modifier = Modifier
                                .size(20.dp)
                                .rotate(syncRotation)
                        )
                    }
                }
            }

            // Offline warning banner (mockup 02-list-offline.png)
            OfflineBanner(visible = !state.isOnline)

            // Conflict auto-resolution banner (mockup 04-form-edit.png)
            ConflictBanner(
                message = state.conflictNotice,
                onDismiss = onDismissConflict
            )

            // Task List
            if (state.tasks.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AssignmentTurnedIn,
                            contentDescription = "Trống",
                            tint = BorderLight,
                            modifier = Modifier.size(72.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Chưa có công việc nào",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Nhấn nút + bên dưới để tạo công việc mới",
                            fontSize = 13.sp,
                            color = TextMuted
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(
                        items = state.tasks,
                        key = { _, item -> item.id }
                    ) { index, task ->
                        TaskCard(
                            task = task,
                            taskIndex = index,
                            onTaskClick = { onTaskClick(it) },
                            onToggleCompleted = { onToggleCompleted(it) },
                            onRetry = { onRetryTask(it) }
                        )
                    }
                }
            }
        }
    }

    // Modal Bottom Sheet: Add Task (mockup 03-form-add.png)
    if (state.isAddSheetOpen) {
        AddTaskBottomSheet(
            sheetState = addSheetState,
            onDismiss = onCloseAddSheet,
            onSave = onSaveNewTask
        )
    }

    // Modal Bottom Sheet: Edit Task (mockup 04-form-edit.png)
    state.selectedTaskForEdit?.let { taskToEdit ->
        EditTaskBottomSheet(
            task = taskToEdit,
            sheetState = editSheetState,
            onDismiss = onCloseEditSheet,
            onUpdate = onUpdateTask,
            onDelete = onDeleteTask
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun TaskScreenOnlinePreview() {
    CKMobileTheme {
        TaskScreenContent(
            state = TaskUiState(
                totalCount = 5,
                unsyncedCount = 3,
                isOnline = true,
                tasks = listOf(
                    TaskEntity(
                        id = "1",
                        title = "Mua sữa chua",
                        description = "Gợi ý của Lan, hộp 2lít",
                        deadline = System.currentTimeMillis(),
                        isCompleted = true,
                        syncStatus = SyncStatus.SYNCED
                    ),
                    TaskEntity(
                        id = "2",
                        title = "Nộp báo cáo nhóm",
                        description = "Đưa file vào email trước hạn",
                        deadline = System.currentTimeMillis() + 86400000L,
                        isCompleted = false,
                        syncStatus = SyncStatus.PENDING
                    ),
                    TaskEntity(
                        id = "3",
                        title = "Gội đầu",
                        description = "",
                        isCompleted = false,
                        syncStatus = SyncStatus.SYNCING
                    ),
                    TaskEntity(
                        id = "4",
                        title = "Sửa CV",
                        description = "Thêm mục dự án Android",
                        deadline = System.currentTimeMillis() + 172800000L,
                        isCompleted = false,
                        syncStatus = SyncStatus.FAILED
                    ),
                    TaskEntity(
                        id = "5",
                        title = "Đặt vé máy bay",
                        description = "Cần so giá trước",
                        isCompleted = true,
                        syncStatus = SyncStatus.SYNCED
                    )
                )
            ),
            onAddTaskClick = {},
            onSyncClick = {},
            onDismissConflict = {},
            onTaskClick = {},
            onToggleCompleted = {},
            onRetryTask = {},
            onCloseAddSheet = {},
            onSaveNewTask = { _, _, _ -> },
            onCloseEditSheet = {},
            onUpdateTask = { _, _, _, _, _ -> },
            onDeleteTask = {}
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun TaskScreenOfflinePreview() {
    CKMobileTheme {
        TaskScreenContent(
            state = TaskUiState(
                totalCount = 5,
                unsyncedCount = 3,
                isOnline = false,
                tasks = emptyList()
            ),
            onAddTaskClick = {},
            onSyncClick = {},
            onDismissConflict = {},
            onTaskClick = {},
            onToggleCompleted = {},
            onRetryTask = {},
            onCloseAddSheet = {},
            onSaveNewTask = { _, _, _ -> },
            onCloseEditSheet = {},
            onUpdateTask = { _, _, _, _, _ -> },
            onDeleteTask = {}
        )
    }
}

