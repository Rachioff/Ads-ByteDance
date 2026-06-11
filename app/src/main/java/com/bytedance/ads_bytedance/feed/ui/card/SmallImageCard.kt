package com.bytedance.ads_bytedance.feed.ui.card

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.bytedance.ads_bytedance.ai.model.AiGeneratedContent
import com.bytedance.ads_bytedance.data.model.AdItem

/**
 * 小图广告卡片
 *
 * 水平布局：左侧文字区（标题 + 描述 + 广告主 + 标签）+ 右侧缩略图
 *
 * 适用于电商商品、应用推荐等紧凑内容。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SmallImageCard(
    ad: AdItem.SmallImageAd,
    aiContent: AiGeneratedContent? = null,
    onLikeClick: () -> Unit,
    onCollectClick: () -> Unit,
    onShareClick: () -> Unit,
    onTagClick: (String) -> Unit,
    onCardClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onCardClick)
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            // ── 左侧：文字内容区 ──
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 14.dp)
            ) {
                // 标题
                Text(
                    text = ad.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(6.dp))

                // 描述
                if (ad.description.isNotBlank()) {
                    Text(
                        text = ad.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // AI 摘要（优先用 AI 生成内容，否则回退到静态 aiSummary）
                val summaryText = aiContent?.summary ?: ad.aiSummary
                if (!summaryText.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    AiSummaryLabel(summary = summaryText)
                }

                // 广告主信息
                RowWithAvatar(
                    avatarUrl = ad.advertiserAvatar,
                    name = ad.advertiserName
                )

                // 标签行
                if (ad.tags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TagChips(tags = ad.tags.take(3), onTagClick = onTagClick)
                }
            }

            // ── 右侧：缩略图 ──
            AsyncImage(
                model = ad.thumbnailUrl,
                contentDescription = ad.title,
                modifier = Modifier
                    .size(width = 110.dp, height = 110.dp)
                    .clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop
            )
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
