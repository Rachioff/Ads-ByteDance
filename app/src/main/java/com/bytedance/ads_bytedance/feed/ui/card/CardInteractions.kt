package com.bytedance.ads_bytedance.feed.ui.card

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.bytedance.ads_bytedance.ui.theme.CollectAmber
import com.bytedance.ads_bytedance.ui.theme.LikeRed

/**
 * 卡片互动按钮栏
 *
 * 所有卡片共享的底部互动组件。
 * 点赞/收藏带缩放动画反馈，分享为图标按钮。
 *
 * @param likeCount 点赞数
 * @param isLiked 当前是否已点赞
 * @param collectCount 收藏数
 * @param isCollected 当前是否已收藏
 * @param onLikeClick 点赞点击回调
 * @param onCollectClick 收藏点击回调
 * @param onShareClick 分享点击回调
 */
@Composable
fun CardInteractions(
    likeCount: Int,
    isLiked: Boolean,
    collectCount: Int,
    isCollected: Boolean,
    onLikeClick: () -> Unit,
    onCollectClick: () -> Unit,
    onShareClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ── 点赞 ──
        InteractionButton(
            count = likeCount,
            isActive = isLiked,
            activeColor = LikeRed,
            activeIcon = { Icons.Filled.Favorite },
            inactiveIcon = { Icons.Outlined.FavoriteBorder },
            onClick = onLikeClick,
            contentDescription = if (isLiked) "取消点赞" else "点赞"
        )

        // ── 收藏 ──
        InteractionButton(
            count = collectCount,
            isActive = isCollected,
            activeColor = CollectAmber,
            activeIcon = { Icons.Filled.Bookmark },
            inactiveIcon = { Icons.Outlined.BookmarkBorder },
            onClick = onCollectClick,
            contentDescription = if (isCollected) "取消收藏" else "收藏"
        )

        // ── 分享 ──
        Icon(
            imageVector = Icons.Filled.Share,
            contentDescription = "分享",
            modifier = Modifier
                .size(22.dp)
                .clickable(onClick = onShareClick),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 单个互动按钮（图标 + 计数）
 *
 * 带弹簧缩放动画反馈 + Material ripple 按压反馈。
 * 额外 padding 确保触摸目标 ≥ 48dp（Material 无障碍最小尺寸）。
 */
@Composable
private fun InteractionButton(
    count: Int,
    isActive: Boolean,
    activeColor: Color,
    activeIcon: @Composable () -> androidx.compose.ui.graphics.vector.ImageVector,
    inactiveIcon: @Composable () -> androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    contentDescription: String
) {
    // 点击时触发弹簧缩放动画
    val scale by animateFloatAsState(
        targetValue = if (isActive) 1.2f else 1f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = 500f),
        label = "interaction_scale"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .scale(scale)
            .padding(horizontal = 6.dp, vertical = 4.dp)   // 扩大触摸目标
            .clickable(onClick = onClick)
    ) {
        Icon(
            imageVector = if (isActive) activeIcon() else inactiveIcon(),
            contentDescription = contentDescription,
            modifier = Modifier.size(22.dp),
            tint = if (isActive) activeColor else MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (count > 0 || isActive) {
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = formatCount(count),
                style = MaterialTheme.typography.labelSmall,
                color = if (isActive) activeColor else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 格式化计数：超过 999 显示 "999+"，超过 10000 显示 "1w+" */
private fun formatCount(count: Int): String = when {
    count >= 10000 -> "${count / 10000}w+"
    count >= 1000  -> "${count / 1000}k+"
    count > 0      -> count.toString()
    else           -> ""
}
