package com.bytedance.ads_bytedance.feed.ui.card

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.bytedance.ads_bytedance.data.model.AdItem
import com.bytedance.ads_bytedance.ui.theme.Blue600
import com.bytedance.ads_bytedance.ui.theme.GradientOverlayEnd
import com.bytedance.ads_bytedance.ui.theme.GradientOverlayStart
import com.bytedance.ads_bytedance.ui.theme.White

/**
 * 视频广告卡片
 *
 * 布局：16:9 封面图 + 居中播放按钮 + 顶部"视频"角标
 *       + 底部渐变遮罩标题叠加 + 标签行 + 互动按钮栏
 *
 * Day 3 实现封面态，Day 4 接入 ExoPlayer 实现真实播放。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VideoCard(
    ad: AdItem.VideoAd,
    onLikeClick: () -> Unit,
    onCollectClick: () -> Unit,
    onShareClick: () -> Unit,
    onTagClick: (String) -> Unit,
    onCardClick: () -> Unit,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onCardClick)
    ) {
        // ── 视频封面区（16:9）──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
        ) {
            // 封面图
            AsyncImage(
                model = ad.coverImageUrl,
                contentDescription = ad.title,
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.Crop
            )

            // 底部渐变遮罩
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(GradientOverlayStart, GradientOverlayEnd)
                        )
                    )
            )

            // 居中播放按钮
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.85f))
                    .clickable(onClick = onPlayClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "播放视频",
                    modifier = Modifier.size(32.dp),
                    tint = Blue600
                )
            }

            // "视频" 角标
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "视频",
                    style = MaterialTheme.typography.labelSmall,
                    color = White,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // ── 封面下方内容 ──
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            // 广告主信息 + 标题
            RowWithAvatar(
                avatarUrl = ad.advertiserAvatar,
                name = ad.advertiserName
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = ad.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (ad.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = ad.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 标签行
            if (ad.tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                TagChips(tags = ad.tags, onTagClick = onTagClick)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 互动按钮栏
            CardInteractions(
                likeCount = ad.likeCount,
                isLiked = ad.isLiked,
                collectCount = ad.collectCount,
                isCollected = ad.isCollected,
                onLikeClick = onLikeClick,
                onCollectClick = onCollectClick,
                onShareClick = onShareClick
            )
        }
    }
}
