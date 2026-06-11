package com.bytedance.ads_bytedance.search.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bytedance.ads_bytedance.behavior.model.BehaviorType
import com.bytedance.ads_bytedance.behavior.model.UserBehavior
import com.bytedance.ads_bytedance.behavior.tracker.BehaviorCollector
import com.bytedance.ads_bytedance.data.model.LoadState
import com.bytedance.ads_bytedance.data.repository.AdRepository
import com.bytedance.ads_bytedance.search.data.SearchHistoryManager
import com.bytedance.ads_bytedance.search.model.SearchEvent
import com.bytedance.ads_bytedance.search.model.SearchUiState
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 常规搜索 ViewModel
 *
 * ## 核心职责
 * 1. 加载热门搜索关键词（init）
 * 2. 加载搜索历史（init）
 * 3. 输入联想建议（300ms 防抖）
 * 4. 关键词搜索（通过 AdRepository → AdDataSource → 透明 Mock/Remote）
 * 5. 分页加载更多结果
 * 6. 管理搜索历史（记录/清空）
 *
 * ## 返回逻辑
 * - hasSearched == true 时按返回 → 清除结果回到初始态
 * - hasSearched == false 时按返回 → UI 层 popBackStack 回主页
 *
 * ## 与 ChatViewModel 的区别
 * - 不依赖 ChatBotService，纯数据层搜索
 * - 无对话 session 管理
 * - UI 形态不同（搜索框在顶部 + 热搜/历史/联想/结果列表 vs 聊天气泡）
 *
 * ## 行为采集 (Day 10)
 * 用户提交搜索时通过 [BehaviorCollector] 记录 SEARCH 行为，
 * 用于用户画像计算与个性化推荐。
 *
 * ## 数据流
 * ```
 * User Input → debounce(300ms) → repository.getSearchSuggestions()
 * Submit → repository.searchAds(page=1) + behaviorCollector.collect(SEARCH)
 * LoadMore → repository.loadMoreSearchResults()
 * ```
 */
class SearchViewModel(
    private val repository: AdRepository,
    private val historyManager: SearchHistoryManager,
    private val behaviorCollector: BehaviorCollector
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    /** 当前搜索关键词（用于分页） */
    private var currentQuery: String = ""

    /** 联想建议防抖 Job */
    private var suggestionJob: Job? = null

    init {
        loadTrendingKeywords()
        loadHistory()
    }

    // ═══════════════════════════════════════════════════════
    // 事件分发
    // ═══════════════════════════════════════════════════════

    fun onEvent(event: SearchEvent) {
        when (event) {
            is SearchEvent.UpdateQuery -> onUpdateQuery(event.text)
            is SearchEvent.SubmitSearch -> onSubmitSearch()
            is SearchEvent.LoadMore -> onLoadMore()
            is SearchEvent.Refresh -> onRefresh()
            is SearchEvent.SelectSuggestion -> onSelectSuggestion(event.suggestion)
            is SearchEvent.SelectTrending -> onSelectTrending(event.keyword)
            is SearchEvent.ClearResults -> clearResults()
            is SearchEvent.AddToHistory -> addToHistory(event.adItem)
            is SearchEvent.ClearHistory -> clearHistory()
            is SearchEvent.LoadHistory -> loadHistory()
            is SearchEvent.GoBack -> onGoBack()
        }
    }

    // ═══════════════════════════════════════════════════════
    // 事件处理
    // ═══════════════════════════════════════════════════════

    private fun onUpdateQuery(text: String) {
        _uiState.update { it.copy(query = text) }

        // 取消上一次防抖
        suggestionJob?.cancel()

        if (text.isBlank()) {
            _uiState.update { it.copy(suggestions = emptyList(), isLoadingSuggestions = false) }
            return
        }

        // 300ms 防抖加载联想建议
        suggestionJob = viewModelScope.launch {
            delay(300)
            loadSuggestions(text)
        }
    }

    private fun onSubmitSearch() {
        val query = _uiState.value.query.trim()
        if (query.isBlank()) return

        currentQuery = query
        _uiState.update {
            it.copy(
                searchLoadState = LoadState.LOADING,
                errorMessage = null,
                suggestions = emptyList(),
                hasSearched = true
            )
        }

        // Day 10: 采集搜索行为
        collectSearchBehavior(query)

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                repository.searchAds(query, page = 1, pageSize = 20)
            }

            result.fold(
                onSuccess = { response ->
                    _uiState.update {
                        it.copy(
                            results = response.items,
                            searchLoadState = LoadState.IDLE,
                            hasMoreResults = response.page < response.totalPages
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            searchLoadState = LoadState.ERROR,
                            errorMessage = error.message ?: "搜索失败，请重试"
                        )
                    }
                }
            )
        }
    }

    private fun onLoadMore() {
        if (!_uiState.value.hasMoreResults ||
            _uiState.value.searchLoadState == LoadState.LOADING) return

        _uiState.update { it.copy(searchLoadState = LoadState.LOADING) }

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                repository.loadMoreSearchResults(currentQuery, pageSize = 20)
            }

            result.fold(
                onSuccess = { response ->
                    _uiState.update {
                        it.copy(
                            results = it.results + response.items,
                            searchLoadState = LoadState.IDLE,
                            hasMoreResults = response.page < response.totalPages
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            searchLoadState = LoadState.ERROR,
                            errorMessage = error.message ?: "加载更多失败"
                        )
                    }
                }
            )
        }
    }

    private fun onRefresh() {
        if (currentQuery.isBlank()) return
        _uiState.update { it.copy(searchLoadState = LoadState.LOADING, errorMessage = null) }

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                repository.searchAds(currentQuery, page = 1, pageSize = 20)
            }

            result.fold(
                onSuccess = { response ->
                    _uiState.update {
                        it.copy(
                            results = response.items,
                            searchLoadState = LoadState.IDLE,
                            hasMoreResults = response.page < response.totalPages
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            searchLoadState = LoadState.ERROR,
                            errorMessage = error.message ?: "刷新失败"
                        )
                    }
                }
            )
        }
    }

    private fun onSelectSuggestion(suggestion: String) {
        _uiState.update { it.copy(query = suggestion, suggestions = emptyList(), hasSearched = true) }
        currentQuery = suggestion
        executeSearch(suggestion)
    }

    private fun onSelectTrending(keyword: String) {
        _uiState.update { it.copy(query = keyword, hasSearched = true) }
        currentQuery = keyword
        executeSearch(keyword)
    }

    private fun clearResults() {
        currentQuery = ""
        _uiState.update {
            SearchUiState(
                query = "",
                hasSearched = false,
                trendingKeywords = it.trendingKeywords,
                searchHistory = it.searchHistory,
                isHistoryLoaded = it.isHistoryLoaded
            )
        }
    }

    /** 返回按钮逻辑：如果在结果页则清除结果，否则由 UI 层处理 pop */
    private fun onGoBack() {
        if (_uiState.value.hasSearched) {
            clearResults()
        }
    }

    /**
     * 点击返回时调用：
     * - 有搜索结果 → 清除结果（返回初始态）
     * - 无搜索结果 → 返回 true 交由 UI 层 pop 导航栈
     *
     * @return true 表示应由 UI 层执行 popBackStack
     */
    fun handleBackPress(): Boolean {
        return if (_uiState.value.hasSearched) {
            clearResults()
            false
        } else {
            true
        }
    }

    // ═══════════════════════════════════════════════════════
    // 搜索历史
    // ═══════════════════════════════════════════════════════

    /** 加载搜索历史（从文件异步读取） */
    private fun loadHistory() {
        viewModelScope.launch {
            historyManager.loadAsync()
            viewModelScope.launch {
                historyManager.history.collect { items ->
                    _uiState.update { it.copy(searchHistory = items, isHistoryLoaded = true) }
                }
            }
        }
    }

    /** 添加广告到搜索历史 */
    private fun addToHistory(adItem: com.bytedance.ads_bytedance.data.model.AdItem) {
        viewModelScope.launch {
            historyManager.addToHistory(adItem)
        }
    }

    /** 清空搜索历史 */
    private fun clearHistory() {
        viewModelScope.launch {
            historyManager.clearHistory()
        }
    }

    // ═══════════════════════════════════════════════════════
    // 内部方法
    // ═══════════════════════════════════════════════════════

    /** 加载热门搜索关键词 */
    private fun loadTrendingKeywords() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingTrending = true) }
            val result = withContext(Dispatchers.IO) {
                repository.getTrendingKeywords()
            }
            result.fold(
                onSuccess = { keywords ->
                    _uiState.update {
                        it.copy(trendingKeywords = keywords, isLoadingTrending = false)
                    }
                },
                onFailure = {
                    _uiState.update { it.copy(isLoadingTrending = false) }
                }
            )
        }
    }

    /** 加载输入联想建议 */
    private suspend fun loadSuggestions(text: String) {
        if (text.isBlank()) return

        _uiState.update { it.copy(isLoadingSuggestions = true) }
        val result = withContext(Dispatchers.IO) {
            repository.getSearchSuggestions(text, limit = 10)
        }
        result.fold(
            onSuccess = { suggestions ->
                _uiState.update {
                    it.copy(suggestions = suggestions, isLoadingSuggestions = false)
                }
            },
            onFailure = {
                _uiState.update { it.copy(isLoadingSuggestions = false) }
            }
        )
    }

    /** 执行搜索（内部方法，复用逻辑） */
    private fun executeSearch(query: String) {
        _uiState.update {
            it.copy(
                searchLoadState = LoadState.LOADING,
                errorMessage = null,
                hasSearched = true
            )
        }

        // Day 10: 采集搜索行为
        collectSearchBehavior(query)

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                repository.searchAds(query, page = 1, pageSize = 20)
            }

            result.fold(
                onSuccess = { response ->
                    _uiState.update {
                        it.copy(
                            results = response.items,
                            searchLoadState = LoadState.IDLE,
                            hasMoreResults = response.page < response.totalPages
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            searchLoadState = LoadState.ERROR,
                            errorMessage = error.message ?: "搜索失败，请重试"
                        )
                    }
                }
            )
        }
    }

    // ═══════════════════════════════════════════════════════
    // 行为采集 (Day 10)
    // ═══════════════════════════════════════════════════════

    /**
     * 采集搜索行为
     *
     * 将搜索查询关键词作为标签记录（不关联特定广告），
     * 委托给 [BehaviorCollector] 异步写入 Room。
     *
     * @param query 搜索查询文本
     */
    private fun collectSearchBehavior(query: String) {
        val keywords = query.trim().split(" ", "，", ",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
        behaviorCollector.collect(
            UserBehavior(
                id = UUID.randomUUID().toString(),
                adId = null,  // 搜索行为不关联特定广告
                behaviorType = BehaviorType.SEARCH,
                tags = keywords,  // 搜索关键词作为标签关联
                timestamp = System.currentTimeMillis()
            )
        )
    }
}
