package com.tdtu.ckmobile.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tdtu.ckmobile.ui.theme.OfflineBannerBg
import com.tdtu.ckmobile.ui.theme.OfflineBannerBorder
import com.tdtu.ckmobile.ui.theme.OfflineBannerText

/**
 * Offline warning banner matching mockup docs/mockups/02-list-offline.png
 * "Bạn đang ở chế độ ngoại tuyến — thay đổi sẽ được đồng bộ khi có mạng"
 */
@Composable
fun OfflineBanner(
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(OfflineBannerBg)
                .border(width = 1.dp, color = OfflineBannerBorder, shape = RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.FlashOff,
                contentDescription = "Offline indicator",
                tint = OfflineBannerText,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Bạn đang ở chế độ ngoại tuyến — thay đổi sẽ được đồng bộ khi có mạng",
                color = OfflineBannerText,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
    }
}
