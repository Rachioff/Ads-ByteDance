package com.bytedance.ads_bytedance.detail.viewmodel

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
import com.bytedance.ads_bytedance.behavior.tracker.BehaviorCollector
import com.bytedance.ads_bytedance.data.model.AdItem
import com.bytedance.ads_bytedance.data.model.LoadState
import com.bytedance.ads_bytedance.data.repository.AdRepository
import com.bytedance.ads_bytedance.detail.model.DetailEvent
import com.bytedance.ads_bytedance.detail.model.DetailOneTimeEvent
import com.bytedance.ads_bytedance.detail.model.DetailUiState
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 详情页 ViewModel
 *
 * ## 状态管理
 * - [uiState] 持有广告数据与加载状态
 * - [likedAdIds] / [collectedAdIds] 使用 `mutableStateMapOf` 按 adId 独立追踪互动状态
 *   （与 FeedViewModel 模式一致，确保 Compose 精确重组）
 * - [aiContent] 持有 AI 生成内容（摘要 + 标签），加载广告后异步生成
 *
 * ## 跨页面状态同步
 * 调用 [AdRepository.updateInteraction] 更新内存缓存中的 `isLiked`/`isCollected` 值，
 * 当用户返回信息流时，FeedScreen 通过 `likedAdIds[ad.id] ?: ad.isLiked` fallback
 * 自动获取到最新的互动状态。
 *
 * @param repository 广告数据仓库（Koin 注入）
 * @param adId 要显示的广告 ID（Koin parameter 注入）
 * @param aiContentGenerator AI 内容生成器（Koin 注入）
 * @param behaviorCollector 用户行为采集器（Koin 注入，Day 9）
 */
class DetailViewModel(
    private val repository: AdRepository,
    private val adId: String,
    private val aiContentGenerator: AiContentGenerator,
    private val behaviorCollector: BehaviorCollector
) : ViewModel() {

    /** 详情页 UI 状态 */
    var uiState by mutableStateOf(DetailUiState())
        private set

    /** 点赞状态 — Compose snapshot Map，精确重组 */
    val likedAdIds = mutableStateMapOf<String, Boolean>()

    /** 收藏状态 — 同上 */
    val collectedAdIds = mutableStateMapOf<String, Boolean>()

    /**
     * AI 生成内容 —— 按 adId 索引的 Compose 快照 Map。
     *
     * DetailScreen 读取 map[adId] 获取 AI 摘要和标签。
     * 若为 null 表示尚未生成或生成失败，退回到 `ad.aiSummary` 静态内容。
     */
    val aiContentMap = mutableStateMapOf<String, AiGeneratedContent>()

    /** 一次性事件（Toast / ShareSheet / Back） */
    private val _events = MutableSharedFlow<DetailOneTimeEvent>()
    val events = _events.asSharedFlow()

    init {
        loadAd()
    }

    // ═══════════════════════════════════════════════════════
    // 事件分发
    // ═══════════════════════════════════════════════════════

    fun onEvent(event: DetailEvent) {
        when (event) {
            is DetailEvent.ToggleLike    -> toggleLike(event.adId)
            is DetailEvent.ToggleCollect -> toggleCollect(event.adId)
            is DetailEvent.Share         -> share(event.ad)
            is DetailEvent.Back          -> navigateBack()
            is DetailEvent.Retry         -> loadAd()
        }
    }

    // ═══════════════════════════════════════════════════════
    // 数据加载
    // ═══════════════════════════════════════════════════════

    private fun loadAd() {
        viewModelScope.launch {
            uiState = uiState.copy(loadState = LoadState.LOADING, errorMessage = null)
            val result = withContext(Dispatchers.IO) {
                repository.getAdById(adId)
            }
            result.fold(
                onSuccess = { ad ->
                    // 初始化互动状态（从数据源获取的当前值）
                    likedAdIds[adId] = ad.isLiked
                    collectedAdIds[adId] = ad.isCollected
                    uiState = uiState.copy(
                        ad = ad,
                        loadState = LoadState.IDLE
                    )
                    // 异步生成 AI 内容（不阻塞 UI）
                    generateAiContent(ad)
                },
                onFailure = { error ->
                    uiState = uiState.copy(
                        loadState = LoadState.ERROR,
                        errorMessage = error.message ?: "加载失败"
                    )
                    _events.emit(DetailOneTimeEvent.ShowToast("加载广告失败: ${error.message}"))
                }
            )
        }
    }

    /**
     * 异步生成 AI 内容（摘要 + 智能标签）
     *
     * 在后台执行，完成后写入 [aiContentMap] 触发 Compose 重组。
     * 失败时保留 null，DetailScreen 退回到 `ad.aiSummary` 静态降级内容。
     */
    private fun generateAiContent(ad: AdItem) {
        viewModelScope.launch {
            try {
                val content = aiContentGenerator.generate(ad)
                aiContentMap[adId] = content
            } catch (_: Exception) {
                // 静默失败，UI 使用 ad.aiSummary 静态降级内容
            }
        }
    }

    // ═══════════════════════════════════════════════════════
    // 互动操作
    // ═══════════════════════════════════════════════════════

    private fun toggleLike(adId: String) {
        val current = likedAdIds[adId] ?: false
        val newLiked = !current

        // UI snapshot 立即更新（乐观更新）
        likedAdIds[adId] = newLiked

        // 采集行为：点赞
        collectBehavior(adId, BehaviorType.LIKE)

        // 异步委托给 Repository → DataSource → 跨页面缓存同步
        viewModelScope.launch {
            val result = repository.updateInteraction(adId, isLiked = newLiked)
            result.onSuccess { updatedAd ->
                uiState = uiState.copy(ad = updatedAd, interactionVersion = uiState.interactionVersion + 1)
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

        // 异步委托给 Repository → DataSource → 跨页面缓存同步
        viewModelScope.launch {
            val result = repository.updateInteraction(adId, isCollected = newCollected)
            result.onSuccess { updatedAd ->
                uiState = uiState.copy(ad = updatedAd, interactionVersion = uiState.interactionVersion + 1)
            }
        }
    }

    private fun share(ad: AdItem) {
        // 采集行为：分享
        collectBehavior(ad.id, BehaviorType.SHARE)

        viewModelScope.launch {
            // 乐观更新：分享计数 +1（分享无取消操作，点击即计数）
            val result = repository.updateInteraction(ad.id, incrementShare = true)
            result.onSuccess { updatedAd ->
                uiState = uiState.copy(ad = updatedAd, interactionVersion = uiState.interactionVersion + 1)
            }
            val shareText = buildString {
                appendLine(ad.title)
                if (ad.description.isNotBlank()) {
                    appendLine(ad.description)
                }
                append("— 来自 ${ad.advertiserName}")
            }
            _events.emit(DetailOneTimeEvent.ShowShareSheet(shareText))
        }
    }

    private fun navigateBack() {
        viewModelScope.launch {
            _events.emit(DetailOneTimeEvent.NavigateBack)
        }
    }

    /**
     * 采集一条用户行为（关联到详情页当前广告）
     */
    private fun collectBehavior(adId: String, type: BehaviorType) {
        val ad = uiState.ad ?: return
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
}
