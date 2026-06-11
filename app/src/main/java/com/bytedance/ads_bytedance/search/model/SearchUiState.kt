package com.bytedance.ads_bytedance.search.model

import com.bytedance.ads_bytedance.data.model.AdItem
import com.bytedance.ads_bytedance.data.model.LoadState

/**
 * 常规搜索页面 UI 状态
 *
 * 三阶段模式 + 搜索历史：
 * - INITIAL: 用户刚进入，展示热门搜索关键词 + 搜索历史
 * - SUGGESTING: 用户正在输入，展示联想建议
 * - RESULTS: 已提交搜索，展示结果列表
 *
 * ## 返回逻辑
 * - 在 RESULTS 阶段按返回 → 清除结果回到 INITIAL
 * - 在 INITIAL/SUGGESTING 阶段按返回 → pop 导航栈回到主页
 *
 * @property query 当前搜索框文本
 * @property hasSearched 是否已执行过搜索（用于区分 RESULTS vs 初始空态）
 * @property trendingKeywords 热门搜索关键词列表
 * @property suggestions 输入联想建议列表
 * @property results 搜索匹配结果
 * @property searchHistory 搜索历史（用户从结果中点进详情的广告列表）
 * @property isHistoryLoaded 搜索历史是否已从文件加载
 * @property searchLoadState 搜索加载状态
 * @property isLoadingTrending 是否正在加载热搜关键词
 * @property isLoadingSuggestions 是否正在加载联想建议
 * @property hasMoreResults 是否有更多搜索结果
 * @property errorMessage 错误信息
 */
data class SearchUiState(
    val query: String = "",
    val hasSearched: Boolean = false,
    val trendingKeywords: List<String> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val results: List<AdItem> = emptyList(),
    val searchHistory: List<AdItem> = emptyList(),
    val isHistoryLoaded: Boolean = false,
    val searchLoadState: LoadState = LoadState.IDLE,
    val isLoadingTrending: Boolean = false,
    val isLoadingSuggestions: Boolean = false,
    val hasMoreResults: Boolean = false,
    val errorMessage: String? = null
)

/**
 * 搜索页用户事件（UDF 模式）
 */
sealed class SearchEvent {
    /** 更新搜索框文本 → 触发防抖联想建议 */
    data class UpdateQuery(val text: String) : SearchEvent()

    /** 提交搜索 */
    data object SubmitSearch : SearchEvent()

    /** 加载更多搜索结果 */
    data object LoadMore : SearchEvent()

    /** 下拉刷新搜索结果 */
    data object Refresh : SearchEvent()

    /** 选中联想建议项 */
    data class SelectSuggestion(val suggestion: String) : SearchEvent()

    /** 选中热门搜索关键词 */
    data class SelectTrending(val keyword: String) : SearchEvent()

    /** 清空搜索结果（回到初始态） */
    data object ClearResults : SearchEvent()

    /** 从搜索结果中点进详情 → 记录到搜索历史 */
    data class AddToHistory(val adItem: AdItem) : SearchEvent()

    /** 清空搜索历史 */
    data object ClearHistory : SearchEvent()

    /** 加载搜索历史（ViewModel init 时触发） */
    data object LoadHistory : SearchEvent()

    /** 点击返回按钮 */
    data object GoBack : SearchEvent()
}
