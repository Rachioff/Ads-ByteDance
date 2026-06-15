package com.bytedance.ads_bytedance.analytics.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bytedance.ads_bytedance.analytics.model.AdStatItem
import com.bytedance.ads_bytedance.analytics.model.StatsEvent
import com.bytedance.ads_bytedance.analytics.model.StatsSortBy
import com.bytedance.ads_bytedance.analytics.model.StatsTab
import com.bytedance.ads_bytedance.analytics.model.StatsUiState
import com.bytedance.ads_bytedance.behavior.profile.UserProfileEngine
import com.bytedance.ads_bytedance.data.repository.AdRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 统计页面 ViewModel
 *
 * ## 职责
 * 1. 加载全量广告数据，生成每广告的曝光/点击/CTR 统计
 * 2. 加载用户行为画像（标签权重 + 行为计数）
 * 3. 支持按不同维度排序
 * 4. 管理 Tab 切换（广告统计 / 我的偏好）
 *
 * ## 数据流
 * ```
 * AdRepository.getAllAds()      → 广告统计列表
 * UserProfileEngine.compute()   → 用户画像 + Top 标签
 * ```
 * 点击"浏览记录"/"总点赞"/"总收藏"卡片 → NavController 导航到对应 AdListScreen
 *
 * @param repository 广告数据仓库（Koin 注入）
 * @param profileEngine 用户画像引擎（Koin 注入）
 */
class StatsViewModel(
    private val repository: AdRepository,
    private val profileEngine: UserProfileEngine
) : ViewModel() {

    /** UI 状态 */
    var uiState by mutableStateOf(StatsUiState())
        private set

    init {
        loadStats()
    }

    /**
     * 刷新统计数据（Lifecycle ON_RESUME 时由外部调用）
     *
     * 当用户从详情页返回（可能修改了点赞/收藏状态）或从广告列表页返回时，
     * 需要重新加载数据以确保统计数字为最新。
     */
    fun refreshStats() {
        loadStats()
    }

    // ═══════════════════════════════════════════════════════
    // 事件分发
    // ═══════════════════════════════════════════════════════

    fun onEvent(event: StatsEvent) {
        when (event) {
            is StatsEvent.ChangeSort      -> changeSort(event.sortBy)
            is StatsEvent.ChangeTab       -> changeTab(event.tab)
            is StatsEvent.ShowHistory     -> { /* 由 NavController 处理 */ }
            is StatsEvent.ShowLikedAds    -> { /* 由 NavController 处理 */ }
            is StatsEvent.ShowCollectedAds -> { /* 由 NavController 处理 */ }
            is StatsEvent.Back            -> { /* 由 NavController 处理 */ }
        }
    }

    // ═══════════════════════════════════════════════════════
    // 数据加载
    // ═══════════════════════════════════════════════════════

    private fun loadStats() {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true)

            try {
                // 并行加载广告数据 + 用户画像
                val adResult = withContext(Dispatchers.IO) {
                    repository.getAllAds()
                }
                val profile = withContext(Dispatchers.IO) {
                    profileEngine.computeProfile()
                }

                adResult.fold(
                    onSuccess = { response ->
                        // 构建广告统计列表
                        val stats = response.items.map { ad ->
                            AdStatItem(
                                ad = ad,
                                exposureCount = ad.exposureCount,
                                clickCount = ad.clickCount
                            )
                        }
                        // 初始排序（按曝光降序）
                        val sorted = sortStats(stats, StatsSortBy.EXPOSURE)

                        // Top 标签（按权重降序，Top 10）
                        val topTags = profile.tagWeights.entries
                            .sortedByDescending { it.value }
                            .take(10)
                            .map { it.key to it.value }

                        uiState = uiState.copy(
                            adStats = sorted,
                            totalClicks = profile.totalClicks,
                            totalLikes = profile.totalLikes,
                            totalCollects = profile.totalCollects,
                            totalShares = profile.totalShares,
                            topTags = topTags,
                            isLoading = false
                        )
                    },
                    onFailure = { error ->
                        uiState = uiState.copy(
                            isLoading = false,
                            adStats = emptyList()
                        )
                    }
                )
            } catch (e: Exception) {
                uiState = uiState.copy(isLoading = false)
            }
        }
    }

    // ═══════════════════════════════════════════════════════
    // 排序
    // ═══════════════════════════════════════════════════════

    private fun changeSort(sortBy: StatsSortBy) {
        uiState = uiState.copy(
            sortBy = sortBy,
            adStats = sortStats(uiState.adStats, sortBy)
        )
    }

    private fun sortStats(stats: List<AdStatItem>, sortBy: StatsSortBy): List<AdStatItem> {
        return when (sortBy) {
            StatsSortBy.EXPOSURE -> stats.sortedByDescending { it.exposureCount }
            StatsSortBy.CLICK    -> stats.sortedByDescending { it.clickCount }
            StatsSortBy.CTR      -> stats.sortedByDescending { it.ctr }
        }
    }

    // ═══════════════════════════════════════════════════════
    // Tab 切换
    // ═══════════════════════════════════════════════════════

    private fun changeTab(tab: StatsTab) {
        uiState = uiState.copy(activeTab = tab)
    }
}
