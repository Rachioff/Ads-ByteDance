package com.bytedance.ads_bytedance.feed.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bytedance.ads_bytedance.ui.theme.Blue600
import com.bytedance.ads_bytedance.ui.theme.Gray200
import com.bytedance.ads_bytedance.ui.theme.Gray300
import com.bytedance.ads_bytedance.ui.theme.Gray400
import com.bytedance.ads_bytedance.ui.theme.Gray500

// ═══════════════════════════════════════════════════════
// 加载态：Shimmer 骨架屏
// ═══════════════════════════════════════════════════════

/**
 * 首次加载时的骨架屏占位
 *
 * Shimmer 效果模拟内容加载，3 个卡片骨架交替闪烁。
 */
@Composable
fun LoadingShimmer(modifier: Modifier = Modifier) {
    val shimmerColors = listOf(
        Gray200.copy(alpha = 0.6f),
        Gray300.copy(alpha = 0.3f),
        Gray200.copy(alpha = 0.6f)
    )

    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )

    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(translateAnim - 200f, 0f),
        end = Offset(translateAnim + 200f, 0f)
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        repeat(4) {
            ShimmerCardPlaceholder(brush)
        }
    }
}

@Composable
private fun ShimmerCardPlaceholder(brush: Brush) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(brush)
            .padding(14.dp)
    ) {
        // 图片占位
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(brush)
        )
        Spacer(modifier = Modifier.height(10.dp))
        // 标题占位
        Box(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(16.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(brush)
        )
        Spacer(modifier = Modifier.height(6.dp))
        // 描述占位
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(brush)
        )
        Spacer(modifier = Modifier.height(8.dp))
        // 互动按钮占位
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(brush)
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════
// 空态
// ═══════════════════════════════════════════════════════

/**
 * 数据为空时的空态视图
 */
@Composable
fun EmptyState(
    message: String = "暂无广告内容",
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Inbox,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = Gray300
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = Gray500,
            textAlign = TextAlign.Center
        )
    }
}

// ═══════════════════════════════════════════════════════
// 错误态
// ═══════════════════════════════════════════════════════

/**
 * 加载失败时的错误态视图
 */
@Composable
fun ErrorState(
    message: String = "加载失败，请检查网络",
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.ErrorOutline,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = Gray400
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = Gray500,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(
                containerColor = Blue600
            ),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text("重新加载", modifier = Modifier.padding(horizontal = 8.dp))
        }
    }
}

// ═══════════════════════════════════════════════════════
// 过滤栏
// ═══════════════════════════════════════════════════════

/**
 * 标签过滤激活时的顶部状态栏
 *
 * 显示当前过滤标签 + 清除按钮。
 */
@Composable
fun FilterBar(
    activeTag: String,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "筛选: ",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = activeTag,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        IconButton(
            onClick = onClear,
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "清除过滤",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

// ═══════════════════════════════════════════════════════
// "没有更多了" Footer
// ═══════════════════════════════════════════════════════

/**
 * 列表底部 "没有更多了" 提示
 */
@Composable
fun EndFooter(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "— 没有更多了 —",
            style = MaterialTheme.typography.bodySmall,
            color = Gray400
        )
    }
}
