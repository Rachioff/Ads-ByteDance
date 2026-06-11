package com.bytedance.ads_bytedance.feed.ui

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import com.bytedance.ads_bytedance.analytics.tracker.ExposureTracker
import com.bytedance.ads_bytedance.data.model.AdItem
import com.bytedance.ads_bytedance.data.model.Channel
import com.bytedance.ads_bytedance.data.model.LoadState
import com.bytedance.ads_bytedance.feed.model.FeedEvent
import com.bytedance.ads_bytedance.feed.viewmodel.FeedOneTimeEvent
import com.bytedance.ads_bytedance.feed.ui.card.LargeImageCard
import com.bytedance.ads_bytedance.feed.ui.card.SmallImageCard
import com.bytedance.ads_bytedance.feed.ui.card.VideoCard
import com.bytedance.ads_bytedance.feed.ui.component.EmptyState
import com.bytedance.ads_bytedance.feed.ui.component.EndFooter
import com.bytedance.ads_bytedance.feed.ui.component.ErrorState
import com.bytedance.ads_bytedance.feed.ui.component.FilterBar
import com.bytedance.ads_bytedance.feed.ui.component.LoadingShimmer
import com.bytedance.ads_bytedance.feed.viewmodel.FeedViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * 信息流主 Composable
 *
 * 功能：
 * - LazyColumn 渲染多类型广告卡片（大图/小图/视频）
 * - PullToRefreshBox 下拉刷新
 * - 上拉加载更多（滚动到末尾自动触发）
 * - 标签过滤状态栏
 * - 空态 / 加载态 / 错误态分发
 * - 点击当前频道 Tab → 回到顶部（通过 [scrollToTopTrigger] 触发）
 * - 快速滑动检测（通过 [isScrollInProgress] 暴露，用于视频加载优化）
 *
 * @param channel 当前频道，通过 Koin parameter 注入给 FeedViewModel
 * @param scrollToTopTrigger 父组件递增此值来触发列表滚动到顶部
 * @param onScrollStateChanged 滚动状态变化回调（true = 正在滚动/Fling）
 * @param isScrollInProgress 当前频道是否正在快速滑动（由 HomeScreen 传入）
 * @param onNavigateToDetail 导航到详情页回调（adId → Unit），由 HomeScreen → MainActivity NavHost 提供
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    channel: Channel = Channel.FEATURED,
    scrollToTopTrigger: Int = 0,
    onScrollStateChanged: ((Boolean) -> Unit)? = null,
    isScrollInProgress: Boolean = false,
    onNavigateToDetail: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val viewModel: FeedViewModel = koinViewModel(
        key = channel.name,
        parameters = { parametersOf(channel) }
    )
    val uiState = viewModel.uiState
    val listState = rememberLazyListState()
    val context = LocalContext.current

    // ── 一次性事件处理 ──
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is FeedOneTimeEvent.ShowToast -> {
                    // Toast 由系统层处理，此处预留 Snackbar 集成点
                }
                is FeedOneTimeEvent.NavigateToDetail -> {
                    onNavigateToDetail(event.adId)
                }
                is FeedOneTimeEvent.ShowShareSheet -> {
                    // 从当前列表中查找广告
                    val ad = uiState.ads.find { it.id == event.adId }
                    if (ad != null) {
                        val shareText = buildString {
                            appendLine(ad.title)
                            if (ad.description.isNotBlank()) {
                                appendLine(ad.description)
                            }
                            append("— 来自 ${ad.advertiserName}")
                        }
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareText)
                            putExtra(Intent.EXTRA_SUBJECT, "分享广告")
                        }
                        context.startActivity(Intent.createChooser(intent, "分享到"))
                    }
                }
            }
        }
    }

    // ── Tab 回到顶部 ──
    LaunchedEffect(scrollToTopTrigger) {
        if (scrollToTopTrigger > 0 && listState.layoutInfo.totalItemsCount > 0) {
            listState.animateScrollToItem(0)
        }
    }

    // ── 快速滑动检测 ──
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { isScrolling ->
                onScrollStateChanged?.invoke(isScrolling)
            }
    }

    // ── 上拉加载检测 ──
    val shouldLoadMore by remember {
        derivedStateOf {
            val totalItems = listState.layoutInfo.totalItemsCount
            if (totalItems == 0) return@derivedStateOf false

            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()
                ?: return@derivedStateOf false

            lastVisibleItem.index >= totalItems - 3
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && uiState.loadState == LoadState.IDLE) {
            viewModel.onEvent(FeedEvent.LoadMore)
        }
    }

    // ── 曝光追踪 (Day 9) ──
    val exposureTracker = remember { ExposureTracker() }
    exposureTracker.Track(
        lazyListState = listState,
        ads = uiState.ads,
        onExposed = { adId ->
            viewModel.onEvent(FeedEvent.Expose(adId))
        }
    )

    // ── 主内容 ──
    Box(modifier = modifier.fillMaxSize()) {
        when {
            uiState.loadState == LoadState.LOADING && uiState.ads.isEmpty() -> {
                LoadingShimmer()
            }

            uiState.loadState == LoadState.ERROR && uiState.ads.isEmpty() -> {
                ErrorState(onRetry = { viewModel.onEvent(FeedEvent.Retry) })
            }

            uiState.loadState == LoadState.IDLE && uiState.ads.isEmpty() -> {
                EmptyState()
            }

            else -> {
                PullToRefreshBox(
                    isRefreshing = uiState.loadState == LoadState.REFRESHING,
                    onRefresh = { viewModel.onEvent(FeedEvent.Refresh) }
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // ── 过滤标签栏 ──
                        val filterTag = uiState.activeFilterTag
                        if (filterTag != null) {
                            item(key = "filter_bar") {
                                AnimatedVisibility(
                                    visible = true,
                                    enter = fadeIn(),
                                    exit = fadeOut()
                                ) {
                                    FilterBar(
                                        activeTag = filterTag,
                                        onClear = { viewModel.onEvent(FeedEvent.ClearFilter) }
                                    )
                                }
                            }
                        }

                        // ── 广告卡片 ──
                        items(
                            items = uiState.ads,
                            key = { it.id },
                            contentType = { it::class }  // 按 AdItem 子类分组，Compose 可为同类型复用布局节点
                        ) { ad ->
                            // mutableStateMapOf 读取建立 snapshot 依赖 → 精确重组该 item
                            val liked = viewModel.likedAdIds[ad.id] ?: ad.isLiked
                            val collected = viewModel.collectedAdIds[ad.id] ?: ad.isCollected
                            val aiContent = viewModel.aiContentMap[ad.id]

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                when (ad) {
                                    is AdItem.LargeImageAd -> LargeImageCard(
                                        ad = ad.copy(isLiked = liked, isCollected = collected),
                                        aiContent = aiContent,
                                        onLikeClick = { viewModel.onEvent(FeedEvent.ToggleLike(ad.id)) },
                                        onCollectClick = { viewModel.onEvent(FeedEvent.ToggleCollect(ad.id)) },
                                        onShareClick = { viewModel.onEvent(FeedEvent.Share(ad.id)) },
                                        onTagClick = { tag -> viewModel.onEvent(FeedEvent.FilterByTag(tag)) },
                                        onCardClick = { viewModel.onEvent(FeedEvent.CardClick(ad)) }
                                    )

                                    is AdItem.SmallImageAd -> SmallImageCard(
                                        ad = ad.copy(isLiked = liked, isCollected = collected),
                                        aiContent = aiContent,
                                        onLikeClick = { viewModel.onEvent(FeedEvent.ToggleLike(ad.id)) },
                                        onCollectClick = { viewModel.onEvent(FeedEvent.ToggleCollect(ad.id)) },
                                        onShareClick = { viewModel.onEvent(FeedEvent.Share(ad.id)) },
                                        onTagClick = { tag -> viewModel.onEvent(FeedEvent.FilterByTag(tag)) },
                                        onCardClick = { viewModel.onEvent(FeedEvent.CardClick(ad)) }
                                    )

                                    is AdItem.VideoAd -> {
                                        // 查找列表中下一个视频的封面 URL（用于预加载）
                                        val currentIndex = uiState.ads.indexOf(ad)
                                        val nextVideoCover = uiState.ads
                                            .drop(currentIndex + 1)
                                            .firstOrNull { it is AdItem.VideoAd }
                                            ?.let { (it as AdItem.VideoAd).coverImageUrl }

                                        VideoCard(
                                            ad = ad.copy(isLiked = liked, isCollected = collected),
                                            aiContent = aiContent,
                                            onLikeClick = { viewModel.onEvent(FeedEvent.ToggleLike(ad.id)) },
                                            onCollectClick = { viewModel.onEvent(FeedEvent.ToggleCollect(ad.id)) },
                                            onShareClick = { viewModel.onEvent(FeedEvent.Share(ad.id)) },
                                            onTagClick = { tag -> viewModel.onEvent(FeedEvent.FilterByTag(tag)) },
                                            onCardClick = { viewModel.onEvent(FeedEvent.CardClick(ad)) },
                                            onPlayClick = { /* PlayerPool 播放由 VideoCard 内部管理 */ },
                                            isScrollInProgress = isScrollInProgress,
                                            nextVideoCoverUrl = nextVideoCover
                                        )
                                    }
                                }
                            }
                        }

                        // ── 底部加载指示器 ──
                        if (uiState.loadState == LoadState.LOADING) {
                            item(key = "load_more_indicator") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.padding(8.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        // ── "没有更多了" ──
                        if (uiState.loadState == LoadState.END) {
                            item(key = "end_footer") {
                                EndFooter()
                            }
                        }
                    }
                }
            }
        }
    }
}
