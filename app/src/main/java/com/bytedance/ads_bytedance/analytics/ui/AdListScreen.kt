package com.bytedance.ads_bytedance.analytics.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.bytedance.ads_bytedance.analytics.viewmodel.AdListType
import com.bytedance.ads_bytedance.data.model.AdItem

/**
 * 广告列表页面（浏览记录 / 已点赞 / 已收藏）
 *
 * 展示完整广告卡片列表，点击可跳转到详情页。
 *
 * @param type 列表类型（决定标题和空态文案）
 * @param ads 广告列表
 * @param isLoading 是否加载中
 * @param onBack 返回回调
 * @param onNavigateToDetail 跳转详情页回调（传 adId）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdListScreen(
    type: AdListType,
    ads: List<AdItem>,
    isLoading: Boolean,
    onBack: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "${type.title} (${ads.size})",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        when {
            isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Text("加载中...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            ads.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.History,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            when (type) {
                                AdListType.HISTORY -> "还没有浏览记录\n去信息流逛逛吧！"
                                AdListType.LIKED -> "还没有点赞的广告\n去给喜欢的广告点个赞吧！"
                                AdListType.COLLECTED -> "还没有收藏的广告\n去收藏感兴趣的广告吧！"
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 16.dp,
                        vertical = 8.dp
                    )
                ) {
                    items(ads, key = { it.id }) { ad ->
                        AdListCard(
                            ad = ad,
                            onClick = { onNavigateToDetail(ad.id) }
                        )
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 广告列表项卡片
 *
 * 样式与搜索历史条目 ([com.bytedance.ads_bytedance.search.ui.SearchHistoryItem]) 保持一致：
 * - 无 Card 包裹，轻量 Row 布局
 * - 36dp 圆角缩略图 + 标题 + 广告主
 * - 条目间通过 HorizontalDivider 分隔
 */
@Composable
private fun AdListCard(
    ad: AdItem,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 缩略图（与 SearchHistoryItem 一致）
        val imageUrl = when (ad) {
            is AdItem.LargeImageAd -> ad.coverImageUrl
            is AdItem.SmallImageAd -> ad.thumbnailUrl
            is AdItem.VideoAd -> ad.coverImageUrl
        }

        AsyncImage(
            model = imageUrl,
            contentDescription = ad.title,
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(6.dp)),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = ad.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = ad.advertiserName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}
