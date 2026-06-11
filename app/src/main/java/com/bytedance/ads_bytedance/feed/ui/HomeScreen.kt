package com.bytedance.ads_bytedance.feed.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bytedance.ads_bytedance.data.model.Channel
import kotlinx.coroutines.launch

/**
 * 首页 — TopAppBar + PrimaryTabRow + HorizontalPager 多频道信息流
 *
 * ## Tab 回到顶部机制
 * 当用户点击**已选中的 Tab** 时，递增该频道对应的 `scrollToTopTrigger`。
 * FeedScreen 通过 [LaunchedEffect] 监听该值变化，触发
 * `LazyListState.animateScrollToItem(0)`。
 *
 * **触发条件**：`pagerState.currentPage == index`（即用户点击的是当前页）
 * **不触发**：滑动切换 Tab（`HorizontalPager` 滑动不会触发 onClick）
 *
 * ## 快速滑动检测
 * HomeScreen 追踪每个频道的滚动状态，用于后续视频自动播放优化。
 * 当前仅记录状态，尚未对 VideoCard 行为产生影响（Day 4 为 click-to-play）。
 *
 * ## 导航
 * [onNavigateToDetail] 回调由外部 NavController 提供，点击卡片时触发导航到详情页。
 *
 * @param onNavigateToDetail 导航到详情页回调（adId → Unit），由 MainActivity NavHost 提供
 * @param onNavigateToSearch 导航到常规搜索页
 * @param onNavigateToChat 导航到 AI 对话搜索页
 * @param onNavigateToStats 导航到数据统计页（Day 9 新增）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToDetail: (String) -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    onNavigateToChat: () -> Unit = {},
    onNavigateToStats: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val channels = Channel.entries
    val tabTitles = listOf("精选", "电商", "本地")

    val pagerState = rememberPagerState(pageCount = { channels.size })
    val coroutineScope = rememberCoroutineScope()

    /**
     * 每个频道的"回到顶部"触发器。
     * Key = Channel, Value = 触发次数。
     * 每次点击已选中 Tab 时递增，FeedScreen 通过 LaunchedEffect 监听并滚动。
     */
    var scrollToTopTriggers by remember {
        mutableStateOf(mapOf<Channel, Int>())
    }

    /** 当前各频道是否正在滚动（用于视频加载优化） */
    var scrollStates by remember {
        mutableStateOf(mapOf<Channel, Boolean>())
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Column {
                // ── 顶部标题栏 ──
                TopAppBar(
                    title = {
                        Text(
                            text = "广告发现",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    actions = {
                        IconButton(onClick = onNavigateToSearch) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = "搜索广告",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        IconButton(onClick = onNavigateToChat) {
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = "AI 对话搜索",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        IconButton(onClick = onNavigateToStats) {
                            Icon(
                                imageVector = Icons.Filled.BarChart,
                                contentDescription = "数据统计",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )

                // ── 频道 Tab 栏 ──
                PrimaryTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    channels.forEachIndexed { index, channel ->
                        val isSelected = pagerState.currentPage == index
                        Tab(
                            selected = isSelected,
                            onClick = {
                                if (isSelected) {
                                    // ── 点击已选中 Tab → 回到顶部 ──
                                    val current = scrollToTopTriggers[channel] ?: 0
                                    scrollToTopTriggers = scrollToTopTriggers +
                                            (channel to (current + 1))
                                } else {
                                    // ── 切换到其他 Tab ──
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(index)
                                    }
                                }
                            },
                            text = {
                                Text(
                                    text = tabTitles[index],
                                    fontWeight = if (isSelected)
                                        FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        // ── 频道页面 HorizontalPager ──
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) { pageIndex ->
            val channel = channels[pageIndex]
            FeedScreen(
                channel = channel,
                scrollToTopTrigger = scrollToTopTriggers[channel] ?: 0,
                onScrollStateChanged = { isScrolling ->
                    scrollStates = scrollStates + (channel to isScrolling)
                },
                isScrollInProgress = scrollStates[channel] ?: false,
                onNavigateToDetail = onNavigateToDetail,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
