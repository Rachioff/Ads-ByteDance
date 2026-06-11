package com.bytedance.ads_bytedance.detail.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.bytedance.ads_bytedance.ui.theme.CollectAmber
import com.bytedance.ads_bytedance.ui.theme.LikeRed
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.cos
import kotlin.random.Random

/**
 * 详情页互动按钮栏（增强版）
 *
 * 比 CardInteractions 更大更突出的互动区域，用于详情页底部。
 * 包含增强动效：
 * - 点赞：爱心扩散粒子效果
 * - 收藏：弹簧缩放 + 颜色动画
 * - 分享：图标按钮 + 完整计数
 *
 * @param likeCount 点赞数
 * @param isLiked 当前是否已点赞
 * @param collectCount 收藏数
 * @param isCollected 当前是否已收藏
 * @param shareCount 分享数
 * @param onLikeClick 点赞点击回调
 * @param onCollectClick 收藏点击回调
 * @param onShareClick 分享点击回调
 */
@Composable
fun DetailInteractions(
    likeCount: Int,
    isLiked: Boolean,
    collectCount: Int,
    isCollected: Boolean,
    shareCount: Int,
    onLikeClick: () -> Unit,
    onCollectClick: () -> Unit,
    onShareClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ── 点赞（爱心扩散）──
        LikeButtonWithBurst(
            count = likeCount,
            isActive = isLiked,
            onClick = onLikeClick
        )

        // ── 收藏（弹簧缩放）──
        CollectButtonEnhanced(
            count = collectCount,
            isActive = isCollected,
            onClick = onCollectClick
        )

        // ── 分享 ──
        ShareButtonEnhanced(
            count = shareCount,
            onClick = onShareClick
        )
    }
}

// ═══════════════════════════════════════════════════════════
// 点赞按钮（爱心扩散动画）
// ═══════════════════════════════════════════════════════════

/**
 * 带爱心粒子扩散动效的点赞按钮
 *
 * 当 isActive 从 false → true 时触发：
 * 1. 主图标弹簧缩放（0.6→1.3→1.0）
 * 2. 6 个小爱心从中心向四周扩散 + 渐隐
 */
@Composable
private fun LikeButtonWithBurst(
    count: Int,
    isActive: Boolean,
    onClick: () -> Unit
) {
    // 跟踪上一次的 isActive 状态，用于触发 burst
    var prevActive by remember { mutableStateOf(false) }
    var triggerBurst by remember { mutableStateOf(false) }

    if (isActive && !prevActive) {
        triggerBurst = true
    } else if (!isActive) {
        triggerBurst = false
    }
    prevActive = isActive

    // 外层 Box 仅用于承载粒子动画叠加层，尺寸由内层 Column 决定
    Box(contentAlignment = Alignment.Center) {
        // ── 爱心扩散粒子 ──
        if (triggerBurst) {
            HeartBurstParticles()
        }

        // ── 图标 + 计数（Column 自然测量，无硬编码偏移）──
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.clickable(onClick = onClick)
        ) {
            Icon(
                imageVector = if (isActive) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = if (isActive) "取消点赞" else "点赞",
                modifier = Modifier
                    .scale(if (isActive) 1.2f else 1f)
                    .size(28.dp)
                    .padding(4.dp),
                tint = if (isActive) LikeRed else MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (count > 0 || isActive) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatCount(count),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    color = if (isActive) LikeRed else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 爱心扩散粒子动画
 *
 * 6 个小型爱心图标从按钮中心向四周扩散，
 * 使用 [Animatable] 驱动 scale 和 alpha 变化。
 */
@Composable
private fun HeartBurstParticles() {
    val particleCount = 6

    for (i in 0 until particleCount) {
        val angle = (2 * Math.PI * i / particleCount).toFloat()
        val distance = Random.nextFloat() * 40f + 20f // 20-60dp 扩散距离
        val targetX = (cos(angle) * distance).roundToInt()
        val targetY = (sin(angle) * distance).roundToInt()

        val offsetX = remember { Animatable(0f) }
        val offsetY = remember { Animatable(0f) }
        val alpha = remember { Animatable(1f) }
        val particleScale = remember { Animatable(0.6f) }

        LaunchedEffect(Unit) {
            // 并行执行：向外移动 + 缩放到 1.2x + 渐隐
            launch {
                offsetX.animateTo(
                    targetValue = targetX.toFloat(),
                    animationSpec = tween(
                        durationMillis = 500,
                        easing = FastOutSlowInEasing
                    )
                )
            }
            launch {
                offsetY.animateTo(
                    targetValue = targetY.toFloat(),
                    animationSpec = tween(
                        durationMillis = 500,
                        easing = FastOutSlowInEasing
                    )
                )
            }
            launch {
                particleScale.animateTo(
                    targetValue = 1.4f,
                    animationSpec = tween(500, easing = FastOutSlowInEasing)
                )
            }
            launch {
                alpha.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(400, easing = FastOutSlowInEasing)
                )
            }
        }

        Icon(
            imageVector = Icons.Filled.Favorite,
            contentDescription = null,
            modifier = Modifier
                .size(12.dp)
                .offset { IntOffset(offsetX.value.roundToInt(), offsetY.value.roundToInt()) }
                .scale(particleScale.value)
                .alpha(alpha.value),
            tint = LikeRed.copy(alpha = 0.7f)
        )
    }
}

// ═══════════════════════════════════════════════════════════
// 收藏按钮（弹簧缩放增强版）
// ═══════════════════════════════════════════════════════════

@Composable
private fun CollectButtonEnhanced(
    count: Int,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isActive) 1.2f else 1f,
        animationSpec = spring(
            dampingRatio = 0.3f,  // 更小的阻尼 → 更明显的弹性
            stiffness = 400f
        ),
        label = "collect_scale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Icon(
            imageVector = if (isActive) Icons.Filled.Bookmark
                else Icons.Outlined.BookmarkBorder,
            contentDescription = if (isActive) "取消收藏" else "收藏",
            modifier = Modifier
                .scale(scale)
                .size(28.dp)
                .padding(4.dp),
            tint = if (isActive) CollectAmber else MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (count > 0 || isActive) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formatCount(count),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                color = if (isActive) CollectAmber else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════
// 分享按钮
// ═══════════════════════════════════════════════════════════

@Composable
private fun ShareButtonEnhanced(
    count: Int,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Icon(
            imageVector = Icons.Filled.Share,
            contentDescription = "分享",
            modifier = Modifier
                .size(28.dp)
                .padding(4.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (count > 0) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formatCount(count),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════
// 工具函数
// ═══════════════════════════════════════════════════════════

/** 格式化计数：超过 999 显示 "999+"，超过 10000 显示 "1w+"，避免长数字溢出 */
private fun formatCount(count: Int): String = when {
    count >= 10000 -> "${count / 10000}w+"
    count >= 1000  -> "${count / 1000}k+"
    count > 0      -> count.toString()
    else           -> ""
}
