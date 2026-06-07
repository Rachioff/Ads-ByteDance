package com.bytedance.ads_bytedance.feed.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bytedance.ads_bytedance.data.model.AdItem
import com.bytedance.ads_bytedance.data.model.Channel
import com.bytedance.ads_bytedance.data.model.LoadState
import com.bytedance.ads_bytedance.data.repository.AdRepository
import com.bytedance.ads_bytedance.feed.model.FeedEvent
import com.bytedance.ads_bytedance.feed.model.FeedUiState
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
 * @param repository 广告数据仓库（Koin 注入）
 * @param channel 所属频道（Koin parameter 注入）
 */
class FeedViewModel(
    private val repository: AdRepository,
    private val channel: Channel
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
                    uiState = uiState.copy(
                        ads = response.items,
                        loadState = if (response.page >= response.totalPages) LoadState.END
                            else LoadState.IDLE
                    )
                },
                onFailure = { error ->
                    uiState = uiState.copy(loadState = LoadState.ERROR)
                    _events.emit(FeedOneTimeEvent.ShowToast("加载失败: ${error.message}"))
                }
            )
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            likedAdIds.clear()
            collectedAdIds.clear()
            uiState = uiState.copy(loadState = LoadState.REFRESHING)
            val result = withContext(Dispatchers.IO) {
                repository.refresh(channel)
            }
            result.fold(
                onSuccess = { response ->
                    uiState = uiState.copy(
                        ads = response.items,
                        loadState = if (response.page >= response.totalPages) LoadState.END
                            else LoadState.IDLE,
                        activeFilterTag = repository.getActiveFilterTag(channel)
                    )
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
                    uiState = uiState.copy(
                        ads = allAds,
                        loadState = if (pagination.loadState == LoadState.END) LoadState.END
                            else LoadState.IDLE
                    )
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

        likedAdIds[adId] = newLiked
        repository.updateInteraction(adId, isLiked = newLiked)
    }

    private fun toggleCollect(adId: String) {
        val current = collectedAdIds[adId] ?: false
        val newCollected = !current

        collectedAdIds[adId] = newCollected
        repository.updateInteraction(adId, isCollected = newCollected)
    }

    private fun share(adId: String) {
        viewModelScope.launch {
            _events.emit(FeedOneTimeEvent.ShowShareSheet(adId))
        }
    }

    // ═══════════════════════════════════════════════════════
    // 导航
    // ═══════════════════════════════════════════════════════

    private fun navigateToDetail(ad: AdItem) {
        viewModelScope.launch {
            _events.emit(FeedOneTimeEvent.NavigateToDetail(ad.id))
        }
    }

    // ═══════════════════════════════════════════════════════
    // 标签过滤
    // ═══════════════════════════════════════════════════════

    private fun filterByTag(tag: String) {
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
}

/**
 * 一次性 UI 事件（不持久化在 UI 状态中，避免重复消费）
 */
sealed class FeedOneTimeEvent {
    data class ShowToast(val message: String) : FeedOneTimeEvent()
    data class NavigateToDetail(val adId: String) : FeedOneTimeEvent()
    data class ShowShareSheet(val adId: String) : FeedOneTimeEvent()
}
