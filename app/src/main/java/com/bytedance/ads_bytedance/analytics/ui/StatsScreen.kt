package com.bytedance.ads_bytedance.analytics.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bytedance.ads_bytedance.analytics.model.AdStatItem
import com.bytedance.ads_bytedance.analytics.model.StatsEvent
import com.bytedance.ads_bytedance.analytics.model.StatsSortBy
import com.bytedance.ads_bytedance.analytics.model.StatsTab
import com.bytedance.ads_bytedance.analytics.model.StatsUiState
import com.bytedance.ads_bytedance.ui.theme.CollectAmber
import com.bytedance.ads_bytedance.ui.theme.LikeRed

/**
 * 统计页面
 *
 * 两个 Tab 页：广告统计 + 我的偏好
 *
 * ## 广告统计
 * - 排序切换：按曝光 / 按点击 / 按 CTR
 * - 广告列表：标题 + 曝光数 + 点击数 + CTR 进度条
 *
 * ## 我的偏好
 * - 行为总览卡片：浏览记录 / 总点赞 / 总收藏 / 总分享（点赞/收藏点击跳转对应列表页）
 * - Top 偏好标签 + 权重柱状图
 * - 标签云（FlowRow 可视化）
 *
 * @param uiState 当前 UI 状态
 * @param onEvent 用户事件回调
 * @param onBack 返回回调
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StatsScreen(
    uiState: StatsUiState,
    onEvent: (StatsEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("数据统计", fontWeight = FontWeight.SemiBold) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // ── Tab 栏 ──
            val tabs = StatsTab.entries
            PrimaryTabRow(
                selectedTabIndex = uiState.activeTab.ordinal,
                containerColor = MaterialTheme.colorScheme.background
            ) {
                tabs.forEach { tab ->
                    Tab(
                        selected = uiState.activeTab == tab,
                        onClick = { onEvent(StatsEvent.ChangeTab(tab)) },
                        text = {
                            Text(
                                text = tab.label,
                                fontWeight = if (uiState.activeTab == tab)
                                    FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            // ── 内容区 ──
            when (uiState.activeTab) {
                StatsTab.AD_STATS -> AdStatsContent(uiState, onEvent)
                StatsTab.MY_PREFERENCES -> MyPreferencesContent(uiState, onEvent)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════
// 广告统计 Tab
// ═══════════════════════════════════════════════════════

@Composable
private fun AdStatsContent(
    uiState: StatsUiState,
    onEvent: (StatsEvent) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // ── 排序选项 ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatsSortBy.entries.forEach { sortBy ->
                val isSelected = uiState.sortBy == sortBy
                FilterChip(
                    selected = isSelected,
                    onClick = { onEvent(StatsEvent.ChangeSort(sortBy)) },
                    label = { Text(sortBy.label, fontSize = 13.sp) }
                )
            }
        }

        // ── 广告统计列表 ──
        if (uiState.adStats.isEmpty() && !uiState.isLoading) {
            // 空态
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.BarChart,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "暂无统计数据",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 15.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 16.dp,
                    vertical = 8.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(uiState.adStats, key = { it.ad.id }) { statItem ->
                    AdStatRow(statItem)
                }
            }
        }
    }
}

/**
 * 单条广告统计行
 *
 * 显示：广告标题 + 子标题（曝光/点击）+ CTR 进度条
 */
@Composable
private fun AdStatRow(statItem: AdStatItem) {
    val ad = statItem.ad

    // CTR 进度条动画
    val animatedCtr by animateFloatAsState(
        targetValue = (statItem.ctr / 100.0).toFloat().coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 600),
        label = "ctr_bar"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // 标题
            Text(
                text = ad.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 曝光 + 点击 数据行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 曝光
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.RemoveRedEye,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "曝光 ${statItem.exposureCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 点击
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.TouchApp,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "点击 ${statItem.clickCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // CTR 数值
                Text(
                    text = "CTR ${"%.1f".format(statItem.ctr)}%",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // CTR 进度条
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedCtr)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.tertiary)
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════
// 我的偏好 Tab
// ═══════════════════════════════════════════════════════

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MyPreferencesContent(
    uiState: StatsUiState,
    onEvent: (StatsEvent) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── 行为总览卡片 ──
        item(key = "behavior_overview") {
            Text(
                text = "行为总览",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            BehaviorOverviewRow(uiState, onEvent)
        }

        // ── Top 偏好标签 ──
        if (uiState.topTags.isNotEmpty()) {
            item(key = "top_tags") {
                Text(
                    text = "Top 偏好标签",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                TopTagsBarChart(uiState.topTags)
            }
        }

        // ── 标签云 ──
        if (uiState.topTags.isNotEmpty()) {
            item(key = "tag_cloud") {
                Text(
                    text = "兴趣标签云",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                TagCloud(uiState.topTags)
            }
        }

        // ── 空态 ──
        if (uiState.topTags.isEmpty() && !uiState.isLoading) {
            item(key = "empty_preferences") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.BarChart,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "还没有偏好数据\n去浏览和互动一些广告吧！",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

/**
 * 行为总览卡片行
 *
 * 4 个统计卡片：浏览记录 / 总点赞 / 总收藏 / 总分享
 * - 浏览记录：点击 → ShowHistory 事件 → 导航到浏览记录页
 * - 总点赞：点击 → ShowLikedAds 事件 → 导航到已点赞列表页
 * - 总收藏：点击 → ShowCollectedAds 事件 → 导航到已收藏列表页
 */
@Composable
private fun BehaviorOverviewRow(
    uiState: StatsUiState,
    onEvent: (StatsEvent) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        BehaviorStatCard(
            label = "浏览记录",
            value = uiState.totalClicks,
            icon = { Icons.Filled.History },
            color = MaterialTheme.colorScheme.primary,
            onClick = { onEvent(StatsEvent.ShowHistory) },
            modifier = Modifier.weight(1f)
        )
        BehaviorStatCard(
            label = "总点赞",
            value = uiState.totalLikes,
            icon = { Icons.Filled.Favorite },
            color = LikeRed,
            onClick = { onEvent(StatsEvent.ShowLikedAds) },
            modifier = Modifier.weight(1f)
        )
        BehaviorStatCard(
            label = "总收藏",
            value = uiState.totalCollects,
            icon = { Icons.Filled.Bookmark },
            color = CollectAmber,
            onClick = { onEvent(StatsEvent.ShowCollectedAds) },
            modifier = Modifier.weight(1f)
        )
        BehaviorStatCard(
            label = "总分享",
            value = uiState.totalShares,
            icon = { Icons.Filled.Share },
            color = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * 单个行为统计小卡片
 *
 * @param onClick 可选点击回调（浏览记录/总点赞/总收藏 有，总分享 无）
 */
@Composable
private fun BehaviorStatCard(
    label: String,
    value: Int,
    icon: @Composable () -> androidx.compose.ui.graphics.vector.ImageVector,
    color: androidx.compose.ui.graphics.Color,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.then(
            if (onClick != null) Modifier.clickable { onClick() } else Modifier
        ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.08f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon(),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = color
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
        }
    }
}

/**
 * Top 标签权重柱状图
 *
 * 每个标签一行：标签名 + 水平柱状条 + 权重得分
 */
@Composable
private fun TopTagsBarChart(topTags: List<Pair<String, Double>>) {
    // 最大权重用于归一化柱状条宽度
    val maxWeight = topTags.maxOfOrNull { it.second } ?: 1.0

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            topTags.forEach { (tagName, weight) ->
                TagWeightBar(
                    tagName = tagName,
                    weight = weight,
                    maxWeight = maxWeight
                )
            }
        }
    }
}

/**
 * 单个标签权重条
 *
 * 标签名 + 动画柱状条 + 数值
 */
@Composable
private fun TagWeightBar(
    tagName: String,
    weight: Double,
    maxWeight: Double
) {
    val fraction = if (maxWeight > 0) (weight / maxWeight).toFloat() else 0f

    // 柱状条宽度动画
    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(durationMillis = 500),
        label = "tag_bar_$tagName"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 标签名
        Text(
            text = tagName,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(64.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.width(8.dp))

        // 柱状条
        Box(
            modifier = Modifier
                .weight(1f)
                .height(16.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedFraction)
                    .height(16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        when {
                            weight >= maxWeight * 0.7 -> MaterialTheme.colorScheme.primary
                            weight >= maxWeight * 0.4 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                            else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                        }
                    )
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // 权重数值
        Text(
            text = "%.1f".format(weight),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * 标签云（FlowRow 自动换行）
 *
 * 标签大小随权重变化：权重越高，字号越大。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagCloud(topTags: List<Pair<String, Double>>) {
    val maxWeight = topTags.maxOfOrNull { it.second } ?: 1.0

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        FlowRow(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            topTags.forEach { (tagName, weight) ->
                // 字号基于权重相对大小：11sp ~ 16sp
                val ratio = (weight / maxWeight).toFloat().coerceIn(0.3f, 1.0f)
                val fontSize = (11 + 5 * ratio).sp

                SuggestionChip(
                    onClick = { /* 标签云仅展示，不交互 */ },
                    label = {
                        Text(
                            text = tagName,
                            fontSize = fontSize,
                            fontWeight = if (ratio > 0.6) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(
                            alpha = (0.1f + 0.3f * ratio).coerceIn(0.1f, 0.4f)
                        )
                    ),
                    border = null
                )
            }
        }
    }
}
