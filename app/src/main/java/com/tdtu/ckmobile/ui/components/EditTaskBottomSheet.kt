package com.tdtu.ckmobile.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tdtu.ckmobile.data.local.entity.SyncStatus
import com.tdtu.ckmobile.data.local.entity.TaskEntity
import com.tdtu.ckmobile.ui.theme.BorderLight
import com.tdtu.ckmobile.ui.theme.CardBackground
import com.tdtu.ckmobile.ui.theme.DeleteButtonBg
import com.tdtu.ckmobile.ui.theme.DeleteButtonBorder
import com.tdtu.ckmobile.ui.theme.DeleteButtonText
import com.tdtu.ckmobile.ui.theme.DividerColor
import com.tdtu.ckmobile.ui.theme.FailedText
import com.tdtu.ckmobile.ui.theme.PrimaryBlue
import com.tdtu.ckmobile.ui.theme.TextMuted
import com.tdtu.ckmobile.ui.theme.TextPrimary
import com.tdtu.ckmobile.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Edit Task bottom sheet matching mockup docs/mockups/04-form-edit.png
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTaskBottomSheet(
    task: TaskEntity,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onUpdate: (id: String, title: String, description: String, deadline: Long?, isCompleted: Boolean) -> Unit,
    onDelete: (id: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(task.title) }
    var description by remember { mutableStateOf(task.description) }
    var deadline by remember { mutableStateOf(task.deadline) }
    var titleError by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = CardBackground,
        dragHandle = {
            androidx.compose.material3.BottomSheetDefaults.DragHandle(
                color = Color(0xFFCBD5E1),
                width = 36.dp,
                height = 4.dp
            )
        },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header Title
            Text(
                text = "Sửa công việc",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            // Red warning when task sync failed (matching mockup 04-form-edit.png)
            if (task.syncStatus == SyncStatus.FAILED) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Đồng bộ thất bại — bấm Lưu để thử lại",
                    color = FailedText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = DividerColor, thickness = 1.dp)
            Spacer(modifier = Modifier.height(16.dp))

            // Field 1: Title *
            val titleLabel = buildAnnotatedString {
                append("Tiêu đề ")
                withStyle(SpanStyle(color = FailedText, fontWeight = FontWeight.Bold)) {
                    append("*")
                }
            }
            Text(
                text = titleLabel,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = title,
                onValueChange = {
                    title = it
                    if (titleError && it.isNotBlank()) titleError = false
                },
                placeholder = { Text("VD: Mua sữa", color = TextMuted, fontSize = 14.sp) },
                singleLine = true,
                isError = titleError,
                supportingText = if (titleError) {
                    { Text("Vui lòng nhập tiêu đề công việc", color = FailedText, fontSize = 12.sp) }
                } else null,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryBlue,
                    unfocusedBorderColor = BorderLight,
                    errorBorderColor = FailedText
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Field 2: Description
            Text(
                text = "Mô tả",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                placeholder = { Text("Chi tiết (tùy chọn)", color = TextMuted, fontSize = 14.sp) },
                minLines = 3,
                maxLines = 5,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryBlue,
                    unfocusedBorderColor = BorderLight
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Field 3: Deadline
            Text(
                text = "Thời hạn",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            val deadlineText = deadline?.let {
                SimpleDateFormat("dd/MM/yyyy · HH:mm", Locale.getDefault()).format(Date(it))
            } ?: ""

            OutlinedTextField(
                value = deadlineText,
                onValueChange = {},
                readOnly = true,
                placeholder = { Text("Chọn ngày...", color = TextMuted, fontSize = 14.sp) },
                trailingIcon = {
                    if (deadline != null) {
                        IconButton(onClick = { deadline = null }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear deadline",
                                tint = TextSecondary
                            )
                        }
                    } else {
                        IconButton(onClick = {
                            showDateTimePicker(context, deadline) { selected ->
                                deadline = selected
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = "Pick deadline",
                                tint = TextSecondary
                            )
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryBlue,
                    unfocusedBorderColor = BorderLight
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        showDateTimePicker(context, deadline) { selected ->
                            deadline = selected
                        }
                    }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Delete Task Button (matching mockup 04-form-edit.png)
            OutlinedButton(
                onClick = { showDeleteConfirmDialog = true },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = DeleteButtonBg,
                    contentColor = DeleteButtonText
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, DeleteButtonBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = DeleteButtonText,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Xóa công việc",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = DeleteButtonText
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons: Hủy & Lưu công việc
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text(text = "Hủy", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }

                Button(
                    onClick = {
                        if (title.isBlank()) {
                            titleError = true
                        } else {
                            onUpdate(task.id, title, description, deadline, task.isCompleted)
                            onDismiss()
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryBlue,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text(text = "Lưu công việc", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    // Confirmation dialog before deleting
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text(text = "Xác nhận xóa", fontWeight = FontWeight.Bold) },
            text = { Text(text = "Bạn có chắc chắn muốn xóa công việc \"${task.title}\"?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDelete(task.id)
                        onDismiss()
                    }
                ) {
                    Text(text = "Xóa", color = DeleteButtonText, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text(text = "Hủy", color = TextSecondary)
                }
            }
        )
    }
}
