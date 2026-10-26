package com.tdtu.ckmobile.ui.components

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tdtu.ckmobile.data.local.entity.SyncStatus
import com.tdtu.ckmobile.ui.theme.FailedBg
import com.tdtu.ckmobile.ui.theme.FailedText
import com.tdtu.ckmobile.ui.theme.PendingBg
import com.tdtu.ckmobile.ui.theme.PendingText
import com.tdtu.ckmobile.ui.theme.SyncedBg
import com.tdtu.ckmobile.ui.theme.SyncedText
import com.tdtu.ckmobile.ui.theme.SyncingBg
import com.tdtu.ckmobile.ui.theme.SyncingText

/**
 * Renders the 4 variants of sync status chips matching mockup:
 * docs/mockups/05-SyncStatus-4variants.png and docs/mockups/01-list-online.png
 */
@Composable
fun SyncStatusChip(
    status: SyncStatus,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null
) {
    val (bgColor, textColor, label, icon: ImageVector) = when (status) {
        SyncStatus.PENDING -> Quad(
            PendingBg,
            PendingText,
            "Chưa đồng bộ",
            Icons.Default.Schedule
        )
        SyncStatus.SYNCING -> Quad(
            SyncingBg,
            SyncingText,
            "Đang đồng bộ",
            Icons.Default.Sync
        )
        SyncStatus.SYNCED -> Quad(
            SyncedBg,
            SyncedText,
            "Đã đồng bộ",
            Icons.Default.Check
        )
        SyncStatus.FAILED -> Quad(
            FailedBg,
            FailedText,
            "Thất bại",
            Icons.Default.PriorityHigh
        )
    }

    // Optional subtle rotation animation for SYNCING icon
    val rotation by if (status == SyncStatus.SYNCING) {
        val infiniteTransition = rememberInfiniteTransition(label = "sync_rotate")
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "sync_rotate_anim"
        )
    } else {
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    }

    Column(
        horizontalAlignment = Alignment.End,
        modifier = modifier
    ) {
        // Status Badge Pill
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(bgColor)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = textColor,
                    modifier = Modifier
                        .size(13.dp)
                        .rotate(rotation)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = label,
                    color = textColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // "Thử lại" retry button under FAILED chip (matching mockup 01-list-online.png)
        if (status == SyncStatus.FAILED && onRetry != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .border(width = 1.dp, color = FailedText.copy(alpha = 0.5f), shape = RoundedCornerShape(8.dp))
                    .clickable { onRetry() }
                    .padding(horizontal = 10.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Thử lại",
                    color = FailedText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private data class Quad<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun SyncStatusChipAllVariantsPreview() {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SyncStatusChip(status = SyncStatus.PENDING)
        SyncStatusChip(status = SyncStatus.SYNCING)
        SyncStatusChip(status = SyncStatus.SYNCED)
        SyncStatusChip(status = SyncStatus.FAILED, onRetry = {})
    }
}

