package com.tdtu.ckmobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tdtu.ckmobile.data.local.entity.SyncStatus
import com.tdtu.ckmobile.data.local.entity.TaskEntity
import com.tdtu.ckmobile.ui.theme.BorderLight
import com.tdtu.ckmobile.ui.theme.CardBackground
import com.tdtu.ckmobile.ui.theme.PrimaryBlue
import com.tdtu.ckmobile.ui.theme.TextMuted
import com.tdtu.ckmobile.ui.theme.TextPrimary
import com.tdtu.ckmobile.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Task item card component matching mockups 01-list-online.png and 02-list-offline.png
 */
@Composable
fun TaskCard(
    task: TaskEntity,
    taskIndex: Int,
    onTaskClick: (TaskEntity) -> Unit,
    onToggleCompleted: (TaskEntity) -> Unit,
    onRetry: (TaskEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = BorderLight, shape = RoundedCornerShape(16.dp))
            .clickable { onTaskClick(task) }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Checkbox on the left
            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(22.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (task.isCompleted) PrimaryBlue else Color.Transparent)
                    .border(
                        width = 1.5.dp,
                        color = if (task.isCompleted) PrimaryBlue else Color(0xFFCBD5E1),
                        shape = RoundedCornerShape(6.dp)
                    )
                    .clickable { onToggleCompleted(task) },
                contentAlignment = Alignment.Center
            ) {
                if (task.isCompleted) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Completed",
                        tint = Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Main info (Title, description, deadline)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                // Title with index badge
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = task.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (task.isCompleted) TextSecondary else TextPrimary,
                        textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "T${taskIndex + 1}",
                        fontSize = 11.sp,
                        color = TextMuted,
                        fontWeight = FontWeight.Normal
                    )
                }

                Spacer(modifier = Modifier.height(3.dp))

                // Description
                val displayDesc = if (task.description.isNotBlank()) task.description else "—"
                Text(
                    text = displayDesc,
                    fontSize = 13.sp,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                // Deadline (if set)
                if (task.deadline != null && task.deadline > 0) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = "Deadline",
                            tint = TextSecondary,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = formatDeadline(task.deadline),
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }
            }

            // Sync Status Chip and Retry button on the right
            SyncStatusChip(
                status = task.syncStatus,
                onRetry = { onRetry(task) }
            )
        }
    }
}

private fun formatDeadline(timestamp: Long): String {
    val formatter = SimpleDateFormat("dd/MM · HH:mm", Locale.getDefault())
    return formatter.format(Date(timestamp))
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun TaskCardPreview() {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)
    ) {
        TaskCard(
            task = TaskEntity(
                id = "1",
                title = "Mua sữa chua",
                description = "Gợi ý của Lan, hộp 2lít",
                deadline = System.currentTimeMillis(),
                isCompleted = true,
                syncStatus = SyncStatus.SYNCED
            ),
            taskIndex = 0,
            onTaskClick = {},
            onToggleCompleted = {},
            onRetry = {}
        )
        TaskCard(
            task = TaskEntity(
                id = "2",
                title = "Nộp báo cáo nhóm",
                description = "Đưa file vào email trước hạn",
                deadline = System.currentTimeMillis() + 86400000L,
                isCompleted = false,
                syncStatus = SyncStatus.PENDING
            ),
            taskIndex = 1,
            onTaskClick = {},
            onToggleCompleted = {},
            onRetry = {}
        )
        TaskCard(
            task = TaskEntity(
                id = "3",
                title = "Sửa CV",
                description = "Thêm mục dự án Android",
                deadline = System.currentTimeMillis() + 172800000L,
                isCompleted = false,
                syncStatus = SyncStatus.FAILED
            ),
            taskIndex = 3,
            onTaskClick = {},
            onToggleCompleted = {},
            onRetry = {}
        )
    }
}

