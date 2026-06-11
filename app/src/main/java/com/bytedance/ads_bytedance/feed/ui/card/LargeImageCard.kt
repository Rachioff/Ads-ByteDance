package com.bytedance.ads_bytedance.feed.ui.card

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
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
import com.bytedance.ads_bytedance.ai.model.AiGeneratedContent
import com.bytedance.ads_bytedance.data.model.AdItem
import com.bytedance.ads_bytedance.ui.theme.Blue100
import com.bytedance.ads_bytedance.ui.theme.Blue600
import com.bytedance.ads_bytedance.ui.theme.GradientOverlayEnd
import com.bytedance.ads_bytedance.ui.theme.GradientOverlayStart

/**
 * 大图广告卡片
 *
 * 布局：大图封面（16dp 圆角）+ 底部渐变遮罩文字叠加
 *       + 标签行 + 互动按钮栏
 *
 * 适用于品牌广告、高质量视觉内容。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LargeImageCard(
    ad: AdItem.LargeImageAd,
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
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onCardClick)
    ) {
        // ── 图片区（带渐变遮罩 + 叠加文字）──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.5f) // 3:2 比例
        ) {
            // 封面图片
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
                    .height(160.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(GradientOverlayStart, GradientOverlayEnd)
                        )
                    )
            )

            // 叠加文字：广告主头像 + 名称 + 标题
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                // 广告主信息行
                RowWithAvatar(
                    avatarUrl = ad.advertiserAvatar,
                    name = ad.advertiserName,
                    textColor = Color.White.copy(alpha = 0.9f)
                )

                Spacer(modifier = Modifier.height(6.dp))

                // 标题
                Text(
                    text = ad.title,
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = MaterialTheme.typography.headlineLarge.lineHeight
                )
            }
        }

        // ── 图片下方内容区 ──
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            // 描述
            if (ad.description.isNotBlank()) {
                Text(
                    text = ad.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            // AI 摘要（优先用 AI 生成内容，否则回退到静态 aiSummary）
            val summaryText = aiContent?.summary ?: ad.aiSummary
            if (!summaryText.isNullOrBlank()) {
                AiSummaryLabel(summary = summaryText)
                Spacer(modifier = Modifier.height(8.dp))
            }

            // 标签行
            if (ad.tags.isNotEmpty()) {
                TagChips(tags = ad.tags, onTagClick = onTagClick)
                Spacer(modifier = Modifier.height(10.dp))
            }

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

/**
 * 广告主头像 + 名称行
 *
 * 用于卡片头部或底部，圆角头像 + 名称垂直居中。
 */
@Composable
fun RowWithAvatar(
    avatarUrl: String,
    name: String,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.layout.Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = avatarUrl,
            contentDescription = "$name 头像",
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.labelMedium,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * AI 摘要标签
 *
 * 在卡片内容区展示 AI 生成的摘要文字。
 * 蓝色渐变背景 + AutoAwesome 图标，与普通描述区分。
 *
 * @param summary AI 生成的摘要文本
 */
@Composable
fun AiSummaryLabel(
    summary: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Blue100)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = Icons.Outlined.AutoAwesome,
            contentDescription = "AI 摘要",
            modifier = Modifier.size(14.dp),
            tint = Blue600
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = summary,
            style = MaterialTheme.typography.bodySmall,
            color = Blue600,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
