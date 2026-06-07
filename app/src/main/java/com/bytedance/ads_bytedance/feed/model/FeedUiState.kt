package com.bytedance.ads_bytedance.feed.model

import com.bytedance.ads_bytedance.data.model.AdItem
import com.bytedance.ads_bytedance.data.model.Channel
import com.bytedance.ads_bytedance.data.model.LoadState

/**
 * 信息流 UI 状态
 *
 * FeedViewModel 通过 StateFlow<FeedUiState> 单向推送到 FeedScreen，
 * Compose 通过 collectAsStateWithLifecycle() 观察并自动重组。
 *
 * @property ads 当前展示的广告列表
 * @property loadState 数据加载状态（IDLE / LOADING / REFRESHING / END / ERROR）
 * @property activeFilterTag 当前过滤标签（非 null 时顶部显示过滤栏）
 * @property currentChannel 当前频道
 */
data class FeedUiState(
    val ads: List<AdItem> = emptyList(),
    val loadState: LoadState = LoadState.IDLE,
    val activeFilterTag: String? = null,
    val currentChannel: Channel = Channel.FEATURED
)

/**
 * 信息流用户事件
 *
 * FeedScreen 将用户操作封装为 FeedEvent，
 * 通过 FeedViewModel.onEvent() 统一处理。
 */
sealed class FeedEvent {
    /** 首次加载频道数据 */
    data class LoadFirstPage(val channel: Channel) : FeedEvent()

    /** 下拉刷新 */
    data object Refresh : FeedEvent()

    /** 上拉加载更多 */
    data object LoadMore : FeedEvent()

    /** 切换点赞状态 */
    data class ToggleLike(val adId: String) : FeedEvent()

    /** 切换收藏状态 */
    data class ToggleCollect(val adId: String) : FeedEvent()

    /** 分享广告 */
    data class Share(val adId: String) : FeedEvent()

    /** 点击广告卡片 → 进入详情页 */
    data class CardClick(val ad: AdItem) : FeedEvent()

    /** 点击标签 Chip → 按标签过滤 */
    data class FilterByTag(val tag: String) : FeedEvent()

    /** 清除标签过滤 */
    data object ClearFilter : FeedEvent()

    /** 重试加载 */
    data object Retry : FeedEvent()
}
