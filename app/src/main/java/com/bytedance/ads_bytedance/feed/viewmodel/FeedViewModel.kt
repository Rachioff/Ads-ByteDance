package com.bytedance.ads_bytedance.feed.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bytedance.ads_bytedance.ai.api.AiContentGenerator
import com.bytedance.ads_bytedance.ai.model.AiGeneratedContent
import com.bytedance.ads_bytedance.behavior.model.BehaviorType
import com.bytedance.ads_bytedance.behavior.model.UserBehavior
import com.bytedance.ads_bytedance.behavior.recommend.RecommendRanker
import com.bytedance.ads_bytedance.behavior.tracker.BehaviorCollector
import com.bytedance.ads_bytedance.data.model.AdItem
import com.bytedance.ads_bytedance.data.model.Channel
import com.bytedance.ads_bytedance.data.model.LoadState
import com.bytedance.ads_bytedance.data.repository.AdRepository
import com.bytedance.ads_bytedance.feed.model.FeedEvent
import com.bytedance.ads_bytedance.feed.model.FeedUiState
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 信息流 ViewModel
 *
 * 每个频道持有独立实例（通过 Koin parameter 注入不同 Channel）。
 *
 * 互动状态（点赞/收藏）使用 [mutableStateMapOf] 独立追踪——
 * 当卡片 Composable 在 LazyColumn content lambda 中读取 map[adId] 时，
 * Compose snapshot 系统为每个 item 创建独立的依赖图。
 * 写入 map[adId] = newValue 只触发这一个 item 的重组。
 *
 * AI 内容（摘要/标签）同样使用 [mutableStateMapOf] 追踪——
 * 加载广告后异步生成 AI 内容，完成后写入 map 触发卡片局部重组。
 *
 * @param repository 广告数据仓库（Koin 注入）
 * @param channel 所属频道（Koin parameter 注入）
 * @param aiContentGenerator AI 内容生成器（Koin 注入）
 * @param behaviorCollector 用户行为采集器（Koin 注入，Day 9）
 * @param recommendRanker 个性化推荐排序器（Koin 注入，Day 10 — 精选频道按画像匹配度排序）
 */
class FeedViewModel(
    private val repository: AdRepository,
    private val channel: Channel,
    private val aiContentGenerator: AiContentGenerator,
    private val behaviorCollector: BehaviorCollector,
    private val recommendRanker: RecommendRanker
) : ViewModel() {

    /** 信息流骨架状态（列表、加载状态、过滤标签） */
    var uiState by mutableStateOf(FeedUiState(currentChannel = channel))
        private set

    /**
     * 点赞状态 —— 按 adId 索引的 Compose 快照 Map。
     *
     * 在 LazyColumn content lambda 中读取 map[adId] 会建立 snapshot 依赖；
     * 写入后 Compose 精确重组该 item，不涉及列表 diff。
     */
    val likedAdIds = mutableStateMapOf<String, Boolean>()

    /** 收藏状态 —— 同上 */
    val collectedAdIds = mutableStateMapOf<String, Boolean>()

    /**
     * AI 生成内容 —— 按 adId 索引的 Compose 快照 Map。
     *
     * cards 在 content lambda 中读取 map[adId] 建立依赖；
     * AI 生成完成后写入，触发该卡片局部重组以显示摘要。
     *
     * 对不在 map 中的条目返回 `null`，卡片应退回到 `ad.aiSummary` 静态内容。
     */
    val aiContentMap = mutableStateMapOf<String, AiGeneratedContent>()

    /** 一次性事件（Toast / 导航 / 分享） */
    private val _events = MutableSharedFlow<FeedOneTimeEvent>()
    val events = _events.asSharedFlow()

    init {
        onEvent(FeedEvent.LoadFirstPage(channel))
    }

    // ═══════════════════════════════════════════════════════
    // 事件分发
    // ═══════════════════════════════════════════════════════

    fun onEvent(event: FeedEvent) {
        when (event) {
            is FeedEvent.LoadFirstPage -> loadFirstPage(event.channel)
            is FeedEvent.Refresh        -> refresh()
            is FeedEvent.LoadMore       -> loadMore()
            is FeedEvent.ToggleLike     -> toggleLike(event.adId)
            is FeedEvent.ToggleCollect  -> toggleCollect(event.adId)
            is FeedEvent.Share          -> share(event.adId)
            is FeedEvent.CardClick      -> navigateToDetail(event.ad)
            is FeedEvent.FilterByTag    -> filterByTag(event.tag)
            is FeedEvent.ClearFilter    -> clearFilter()
            is FeedEvent.Retry          -> retry()
            is FeedEvent.Expose         -> onAdExposed(event.adId)
        }
    }

    // ═══════════════════════════════════════════════════════
    // 数据加载
    // ═══════════════════════════════════════════════════════

    private fun loadFirstPage(ch: Channel) {
        viewModelScope.launch {
            uiState = uiState.copy(loadState = LoadState.LOADING)
            val result = withContext(Dispatchers.IO) {
                repository.loadFirstPage(ch)
            }
            result.fold(
                onSuccess = { response ->
                    // Day 10: 精选频道按用户画像匹配度个性化排序
                    val ranked = if (ch == Channel.FEATURED) {
                        recommendRanker.rank(response.items, ch)
                    } else {
                        response.items
                    }
                    uiState = uiState.copy(
                        ads = ranked,
                        loadState = if (response.page >= response.totalPages) LoadState.END
                            else LoadState.IDLE
                    )
                    // 异步生成 AI 摘要/标签（不阻塞 UI）
                    generateAiContent(ranked)
                },
                onFailure = { error ->
                    uiState = uiState.copy(loadState = LoadState.ERROR)
                    _events.emit(FeedOneTimeEvent.ShowToast("加载失败: ${error.message}"))
                }
            )
        }
    }

    /**
     * 异步生成 AI 内容（摘要 + 智能标签）
     *
     * 在后台线程运行，完成后通过 [aiContentMap] 通知 Compose 局部重组卡片。
     * 单条失败不影响其他广告——失败的广告卡片仍展示 `ad.aiSummary` 静态降级内容。
     */
    private fun generateAiContent(ads: List<AdItem>) {
        viewModelScope.launch {
            aiContentGenerator.generateBatch(ads) { adId, content ->
                // AI 生成完成后写入 Compose snapshot map，触发对应卡片重组
                aiContentMap[adId] = content
            }
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            likedAdIds.clear()
            collectedAdIds.clear()
            // 清空 AI 缓存快照（刷新意味着重新加载，让缓存管理器决定是否重新调用 API）
            aiContentMap.clear()
            uiState = uiState.copy(loadState = LoadState.REFRESHING)
            val result = withContext(Dispatchers.IO) {
                repository.refresh(channel)
            }
            result.fold(
                onSuccess = { response ->
                    // Day 10: 精选频道按用户画像匹配度个性化排序
                    val ranked = if (channel == Channel.FEATURED) {
                        recommendRanker.rank(response.items, channel)
                    } else {
                        response.items
                    }
                    uiState = uiState.copy(
                        ads = ranked,
                        loadState = if (response.page >= response.totalPages) LoadState.END
                            else LoadState.IDLE,
                        activeFilterTag = repository.getActiveFilterTag(channel)
                    )
                    generateAiContent(ranked)
                },
                onFailure = { error ->
                    uiState = uiState.copy(loadState = LoadState.ERROR)
                    _events.emit(FeedOneTimeEvent.ShowToast("刷新失败: ${error.message}"))
                }
            )
        }
    }

    private fun loadMore() {
        val current = uiState
        if (current.loadState == LoadState.LOADING ||
            current.loadState == LoadState.END ||
            current.loadState == LoadState.REFRESHING) return

        viewModelScope.launch {
            uiState = uiState.copy(loadState = LoadState.LOADING)
            val result = withContext(Dispatchers.IO) {
                repository.loadMore(channel)
            }
            result.fold(
                onSuccess = {
                    val allAds = repository.getCachedItems(channel)
                    val pagination = repository.getPaginationState(channel)
                    // Day 10: 精选频道合并列表后整体重排（确保最匹配的广告始终在前）
                    val ranked = if (channel == Channel.FEATURED) {
                        recommendRanker.rank(allAds, channel)
                    } else {
                        allAds
                    }
                    uiState = uiState.copy(
                        ads = ranked,
                        loadState = if (pagination.loadState == LoadState.END) LoadState.END
                            else LoadState.IDLE
                    )
                    // 仅对新加载的广告（无缓存的）生成 AI 内容
                    val newAds = ranked.filter { it.id !in aiContentMap }
                    if (newAds.isNotEmpty()) {
                        generateAiContent(newAds)
                    }
                },
                onFailure = { error ->
                    uiState = uiState.copy(loadState = LoadState.ERROR)
                    _events.emit(FeedOneTimeEvent.ShowToast("加载更多失败: ${error.message}"))
                }
            )
        }
    }

    // ═══════════════════════════════════════════════════════
    // 互动操作 — mutableStateMapOf 精确重组
    // ═══════════════════════════════════════════════════════

    private fun toggleLike(adId: String) {
        val current = likedAdIds[adId] ?: false
        val newLiked = !current

        // UI snapshot 立即更新（乐观更新，用户无需等待网络）
        likedAdIds[adId] = newLiked

        // 采集行为：点赞
        collectBehavior(adId, BehaviorType.LIKE)

        // 异步委托给 Repository → DataSource（Mock: 内存计数联动 / Remote: API 调用）
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                repository.updateInteraction(adId, isLiked = newLiked)
            }
            result.onSuccess { updatedAd ->
                uiState = uiState.copy(
                    ads = replaceAdInList(uiState.ads, updatedAd),
                    interactionVersion = uiState.interactionVersion + 1
                )
            }
        }
    }

    private fun toggleCollect(adId: String) {
        val current = collectedAdIds[adId] ?: false
        val newCollected = !current

        // UI snapshot 立即更新（乐观更新）
        collectedAdIds[adId] = newCollected

        // 采集行为：收藏
        collectBehavior(adId, BehaviorType.COLLECT)

        // 异步委托给 Repository → DataSource
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                repository.updateInteraction(adId, isCollected = newCollected)
            }
            result.onSuccess { updatedAd ->
                uiState = uiState.copy(
                    ads = replaceAdInList(uiState.ads, updatedAd),
                    interactionVersion = uiState.interactionVersion + 1
                )
            }
        }
    }

    private fun share(adId: String) {
        // 采集行为：分享
        collectBehavior(adId, BehaviorType.SHARE)

        viewModelScope.launch {
            // 乐观更新：分享计数 +1（分享无取消操作，点击即计数）
            val result = withContext(Dispatchers.IO) {
                repository.updateInteraction(adId, incrementShare = true)
            }
            result.onSuccess { updatedAd ->
                uiState = uiState.copy(
                    ads = replaceAdInList(uiState.ads, updatedAd),
                    interactionVersion = uiState.interactionVersion + 1
                )
            }
            _events.emit(FeedOneTimeEvent.ShowShareSheet(adId))
        }
    }

    /** 替换列表中指定 ID 的广告项，若未找到则返回原列表 */
    private fun replaceAdInList(ads: List<AdItem>, updatedAd: AdItem): List<AdItem> {
        val index = ads.indexOfFirst { it.id == updatedAd.id }
        if (index < 0) return ads
        return ads.toMutableList().also { it[index] = updatedAd }
    }

    // ═══════════════════════════════════════════════════════
    // 导航
    // ═══════════════════════════════════════════════════════

    private fun navigateToDetail(ad: AdItem) {
        // 采集行为：点击
        collectBehavior(ad.id, BehaviorType.CLICK)

        viewModelScope.launch {
            // 递增点击计数（IO 操作：更新 Room 缓存）
            withContext(Dispatchers.IO) {
                repository.incrementClick(ad.id)
            }
            _events.emit(FeedOneTimeEvent.NavigateToDetail(ad.id))
        }
    }

    // ═══════════════════════════════════════════════════════
    // 标签过滤
    // ═══════════════════════════════════════════════════════

    private fun filterByTag(tag: String) {
        // 采集行为：标签点击
        behaviorCollector.collect(
            UserBehavior(
                id = UUID.randomUUID().toString(),
                adId = null,  // 标签过滤不关联特定广告
                behaviorType = BehaviorType.TAG_CLICK,
                tags = listOf(tag),
                timestamp = System.currentTimeMillis()
            )
        )

        viewModelScope.launch {
            uiState = uiState.copy(loadState = LoadState.LOADING)
            val result = withContext(Dispatchers.IO) {
                repository.filterByTag(channel, tag)
            }
            result.fold(
                onSuccess = { response ->
                    uiState = uiState.copy(
                        ads = response.items,
                        activeFilterTag = tag,
                        loadState = if (response.page >= response.totalPages) LoadState.END
                            else LoadState.IDLE
                    )
                },
                onFailure = { error ->
                    uiState = uiState.copy(loadState = LoadState.ERROR)
                    _events.emit(FeedOneTimeEvent.ShowToast("过滤失败: ${error.message}"))
                }
            )
        }
    }

    private fun clearFilter() {
        viewModelScope.launch {
            uiState = uiState.copy(loadState = LoadState.REFRESHING)
            val result = withContext(Dispatchers.IO) {
                repository.clearFilter(channel)
            }
            result.fold(
                onSuccess = { response ->
                    uiState = uiState.copy(
                        ads = response.items,
                        activeFilterTag = null,
                        loadState = if (response.page >= response.totalPages) LoadState.END
                            else LoadState.IDLE
                    )
                },
                onFailure = { error ->
                    uiState = uiState.copy(loadState = LoadState.ERROR)
                    _events.emit(FeedOneTimeEvent.ShowToast("清除过滤失败: ${error.message}"))
                }
            )
        }
    }

    private fun retry() {
        loadFirstPage(channel)
    }

    // ═══════════════════════════════════════════════════════
    // 行为采集 (Day 9)
    // ═══════════════════════════════════════════════════════

    /**
     * 采集一条用户行为（关联到指定广告）
     *
     * 从当前列表中查找广告，提取其标签列表，构造 [UserBehavior] 后
     * 委托给 [BehaviorCollector] 异步写入 Room。
     *
     * @param adId 广告 ID
     * @param type 行为类型
     */
    private fun collectBehavior(adId: String, type: BehaviorType) {
        val ad = uiState.ads.find { it.id == adId } ?: return
        val tagNames = ad.tags.map { it.name }
        behaviorCollector.collect(
            UserBehavior(
                id = UUID.randomUUID().toString(),
                adId = adId,
                behaviorType = type,
                tags = tagNames,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    /** 广告曝光处理——递增曝光计数 */
    private fun onAdExposed(adId: String) {
        viewModelScope.launch {
            repository.incrementExposure(adId)
        }
    }
}

/**
 * 一次性 UI 事件（不持久化在 UI 状态中，避免重复消费）
 */
sealed class FeedOneTimeEvent {
    data class ShowToast(val message: String) : FeedOneTimeEvent()
    data class NavigateToDetail(val adId: String) : FeedOneTimeEvent()
    data class ShowShareSheet(val adId: String) : FeedOneTimeEvent()
}
