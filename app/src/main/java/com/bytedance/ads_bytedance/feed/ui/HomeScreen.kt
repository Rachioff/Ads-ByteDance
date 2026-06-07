package com.bytedance.ads_bytedance.feed.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bytedance.ads_bytedance.data.model.Channel
import kotlinx.coroutines.launch

/**
 * 首页 — TopAppBar + PrimaryTabRow + HorizontalPager 多频道信息流
 *
 * 顶部固定标题栏 + 标签栏，横向滑动切换频道。
 * 每个频道持有独立的 FeedViewModel 实例。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    val channels = Channel.entries
    val tabTitles = listOf("精选", "电商", "本地")

    val pagerState = rememberPagerState(pageCount = { channels.size })
    val coroutineScope = rememberCoroutineScope()

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
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )

                // ── 频道 Tab 栏（PrimaryTabRow — Material3 推荐 API）──
                PrimaryTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    channels.forEachIndexed { index, _ ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            },
                            text = {
                                Text(
                                    text = tabTitles[index],
                                    fontWeight = if (pagerState.currentPage == index)
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
            FeedScreen(
                channel = channels[pageIndex],
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
