package com.bytedance.ads_bytedance.data.repository

import com.bytedance.ads_bytedance.data.local.AdDataSource
import com.bytedance.ads_bytedance.data.model.AdItem
import com.bytedance.ads_bytedance.data.model.AdPageResponse
import com.bytedance.ads_bytedance.data.model.Channel
import com.bytedance.ads_bytedance.data.model.LoadState
import com.bytedance.ads_bytedance.data.model.PaginationState

/**
 * 广告数据仓库
 *
 * 统一数据访问入口，职责：
 * 1. 通过 AdDataSource 获取数据（Mock/Remote 对上层透明）
 * 2. 维护内存缓存（分频道存储已加载广告列表）
 * 3. 管理分页状态（当前页、是否有更多、加载状态）
 * 4. 支持按标签本地过滤
 * 5. 互动状态跨页面同步（点赞/收藏）
 */
class AdRepository(
    private val dataSource: AdDataSource
) {
    /** 每页默认大小 */
    private val defaultPageSize = 10

    // ── 内存缓存 ──
    /** 分频道广告列表缓存 */
    private val channelItems = mutableMapOf<Channel, MutableList<AdItem>>()

    /** 分频道分页状态 */
    private val channelPagination = mutableMapOf<Channel, PaginationState>()

    /** 当前标签过滤状态（null = 不过滤） */
    private val channelFilterTag = mutableMapOf<Channel, String?>()

    // ═══════════════════════════════════════════════════════
    // 信息流数据加载
    // ═══════════════════════════════════════════════════════

    /**
     * 加载指定频道的首页数据
     *
     * @param channel 目标频道
     * @param pageSize 每页条数
     * @return 首页 AdPageResponse；失败时 Result.failure
     */
    suspend fun loadFirstPage(
        channel: Channel,
        pageSize: Int = defaultPageSize
    ): Result<AdPageResponse> {
        // 重置分页状态
        val state = PaginationState(currentPage = 1, pageSize = pageSize, loadState = LoadState.LOADING)
        channelPagination[channel] = state

        val result = dataSource.getAds(channel, page = 1, pageSize = pageSize)

        return result.fold(
            onSuccess = { response ->
                channelItems[channel] = response.items.toMutableList()
                channelPagination[channel] = state.copy(
                    currentPage = response.page,
                    hasMore = response.page < response.totalPages,
                    loadState = LoadState.IDLE
                )
                Result.success(response)
            },
            onFailure = { error ->
                channelPagination[channel] = state.copy(loadState = LoadState.ERROR)
                Result.failure(error)
            }
        )
    }

    /**
     * 下拉刷新
     */
    suspend fun refresh(
        channel: Channel,
        pageSize: Int = defaultPageSize
    ): Result<AdPageResponse> {
        channelItems.remove(channel)
        channelFilterTag.remove(channel)
        return loadFirstPage(channel, pageSize)
    }

    /**
     * 上拉加载更多
     *
     * @return 下一页数据；如果没有更多则返回空列表
     */
    suspend fun loadMore(
        channel: Channel,
        pageSize: Int = defaultPageSize
    ): Result<AdPageResponse> {
        val state = channelPagination[channel] ?: PaginationState()
        if (!state.hasMore) {
            return Result.success(
                AdPageResponse(page = state.currentPage, totalPages = state.currentPage, pageSize = pageSize, items = emptyList())
            )
        }

        val nextPage = state.currentPage + 1
        channelPagination[channel] = state.copy(loadState = LoadState.LOADING)

        val filterTag = channelFilterTag[channel]
        val result = if (filterTag != null) {
            dataSource.getAdsByTag(channel, filterTag, nextPage, pageSize)
        } else {
            dataSource.getAds(channel, nextPage, pageSize)
        }

        return result.fold(
            onSuccess = { response ->
                val current = channelItems[channel] ?: mutableListOf()
                current.addAll(response.items)
                channelItems[channel] = current
                channelPagination[channel] = state.copy(
                    currentPage = nextPage,
                    hasMore = response.page < response.totalPages,
                    loadState = if (response.page >= response.totalPages) LoadState.END else LoadState.IDLE
                )
                Result.success(response)
            },
            onFailure = { error ->
                channelPagination[channel] = state.copy(loadState = LoadState.ERROR)
                Result.failure(error)
            }
        )
    }

    // ═══════════════════════════════════════════════════════
    // 标签过滤
    // ═══════════════════════════════════════════════════════

    /**
     * 按标签过滤当前频道缓存数据
     *
     * @return 过滤后的首页数据
     */
    suspend fun filterByTag(
        channel: Channel,
        tag: String,
        pageSize: Int = defaultPageSize
    ): Result<AdPageResponse> {
        channelFilterTag[channel] = tag

        val result = dataSource.getAdsByTag(channel, tag, page = 1, pageSize = pageSize)

        return result.fold(
            onSuccess = { response ->
                channelItems[channel] = response.items.toMutableList()
                channelPagination[channel] = PaginationState(
                    currentPage = 1,
                    pageSize = pageSize,
                    hasMore = response.page < response.totalPages,
                    loadState = LoadState.IDLE
                )
                Result.success(response)
            },
            onFailure = { error ->
                channelPagination[channel] = PaginationState(loadState = LoadState.ERROR)
                Result.failure(error)
            }
        )
    }

    /** 清除当前标签过滤 */
    suspend fun clearFilter(channel: Channel, pageSize: Int = defaultPageSize): Result<AdPageResponse> {
        channelFilterTag.remove(channel)
        return refresh(channel, pageSize)
    }

    /** 当前是否有激活的标签过滤 */
    fun getActiveFilterTag(channel: Channel): String? = channelFilterTag[channel]

    // ═══════════════════════════════════════════════════════
    // 查询
    // ═══════════════════════════════════════════════════════

    /** 获取内存缓存中的所有已加载广告 */
    fun getCachedItems(channel: Channel): List<AdItem> =
        channelItems[channel] ?: emptyList()

    /** 获取分页状态 */
    fun getPaginationState(channel: Channel): PaginationState =
        channelPagination[channel] ?: PaginationState()

    /** 根据 ID 获取广告（先查缓存，未命中则查数据源） */
    suspend fun getAdById(adId: String): Result<AdItem> {
        // 先查内存缓存
        for ((_, items) in channelItems) {
            val found = items.find { it.id == adId }
            if (found != null) return Result.success(found)
        }
        // 未命中则查数据源
        return dataSource.getAdById(adId)
    }

    // ═══════════════════════════════════════════════════════
    // 互动状态同步
    // ═══════════════════════════════════════════════════════

    /**
     * 更新广告的点赞/收藏状态（跨页面同步 + 计数联动）
     *
     * **架构说明**：
     * ```
     * ViewModel → AdRepository.updateInteraction()     ← 业务层入口（不变）
     *              ├─ dataSource.updateInteraction()    ← Mock: 内存更新 | Remote: API 调用
     *              └─ 同步本地 channelItems 缓存        ← 确保 FeedScreen 读取到最新值
     * ```
     * 接入真实服务端时，只需在 [RemoteDataSource] 中实现 API 调用，
     * Repository 和 ViewModel 层无需任何改动。
     *
     * 更新流程：
     * 1. 委托给 DataSource（Mock 直接修改缓存，Remote 调用 API）
     * 2. DataSource 成功返回后，同步 Repository 本地缓存
     * 3. Compose snapshot 系统检测到 AdItem 字段变化 → 自动重组 UI
     */
    suspend fun updateInteraction(
        adId: String,
        isLiked: Boolean? = null,
        isCollected: Boolean? = null,
        incrementShare: Boolean = false
    ): Result<AdItem> {
        val result = dataSource.updateInteraction(adId, isLiked, isCollected, incrementShare)
        result.onSuccess { updatedAd ->
            // 同步 Repository 本地缓存（确保 FeedScreen 和 DetailScreen 读取到最新值）
            for ((_, items) in channelItems) {
                val index = items.indexOfFirst { it.id == adId }
                if (index >= 0) {
                    items[index] = updatedAd
                }
            }
        }
        return result
    }

    /** 清空所有缓存 */
    fun clearAll() {
        channelItems.clear()
        channelPagination.clear()
        channelFilterTag.clear()
        searchCache.clear()
        searchPagination.clear()
    }

    // ═══════════════════════════════════════════════════════
    // 搜索
    // ═══════════════════════════════════════════════════════

    /** 搜索缓存（key = query） */
    private val searchCache = mutableMapOf<String, MutableList<AdItem>>()

    /** 搜索分页状态（key = query） */
    private val searchPagination = mutableMapOf<String, PaginationState>()

    /**
     * 跨频道关键词搜索广告
     *
     * 通过 DataSource 查询，对 Mock/Remote 透明。
     *
     * @param query 搜索关键词
     * @param page 页码
     * @param pageSize 每页条数
     */
    suspend fun searchAds(
        query: String,
        page: Int = 1,
        pageSize: Int = 20
    ): Result<AdPageResponse> {
        val state = PaginationState(currentPage = page, pageSize = pageSize, loadState = LoadState.LOADING)
        searchPagination[query] = state

        val result = dataSource.searchAds(query, page, pageSize)

        return result.fold(
            onSuccess = { response ->
                if (page == 1) {
                    searchCache[query] = response.items.toMutableList()
                } else {
                    searchCache.getOrPut(query) { mutableListOf() }
                        .addAll(response.items)
                }
                searchPagination[query] = state.copy(
                    currentPage = response.page,
                    hasMore = response.page < response.totalPages,
                    loadState = if (response.page >= response.totalPages) LoadState.END else LoadState.IDLE
                )
                Result.success(response)
            },
            onFailure = { error ->
                searchPagination[query] = state.copy(loadState = LoadState.ERROR)
                Result.failure(error)
            }
        )
    }

    /**
     * 搜索加载更多
     */
    suspend fun loadMoreSearchResults(
        query: String,
        pageSize: Int = 20
    ): Result<AdPageResponse> {
        val state = searchPagination[query] ?: PaginationState()
        if (!state.hasMore) {
            return Result.success(
                AdPageResponse(page = state.currentPage, totalPages = state.currentPage, pageSize = pageSize, items = emptyList())
            )
        }

        val nextPage = state.currentPage + 1
        return searchAds(query, nextPage, pageSize)
    }

    /** 获取搜索缓存 */
    fun getCachedSearchResults(query: String): List<AdItem> =
        searchCache[query] ?: emptyList()

    /** 获取搜索分页状态 */
    fun getSearchPaginationState(query: String): PaginationState =
        searchPagination[query] ?: PaginationState()

    /** 获取全量广告（不做关键词过滤） */
    suspend fun getAllAds(): Result<AdPageResponse> =
        dataSource.getAllAds()

    /** 获取热门搜索关键词 */
    suspend fun getTrendingKeywords(): Result<List<String>> =
        dataSource.getTrendingKeywords()

    /** 输入联想建议 */
    suspend fun getSearchSuggestions(
        query: String,
        limit: Int = 10
    ): Result<List<String>> =
        dataSource.getSearchSuggestions(query, limit)

    // ═══════════════════════════════════════════════════════
    // 曝光/点击统计
    // ═══════════════════════════════════════════════════════

    /**
     * 递增广告曝光计数（去重由调用方保证——ExposureTracker 已去重）
     *
     * 更新 DataSource 后同步 Repository 本地缓存，确保 StatsScreen
     * 读取到的 exposureCount 是最新值。
     */
    suspend fun incrementExposure(adId: String): Result<AdItem> {
        val result = dataSource.incrementExposure(adId)
        result.onSuccess { updatedAd ->
            syncLocalCache(adId, updatedAd)
        }
        return result
    }

    /**
     * 递增广告点击计数
     */
    suspend fun incrementClick(adId: String): Result<AdItem> {
        val result = dataSource.incrementClick(adId)
        result.onSuccess { updatedAd ->
            syncLocalCache(adId, updatedAd)
        }
        return result
    }

    /**
     * 同步 Repository 本地 channelItems 缓存
     *
     * 当 DataSource 更新广告后，同步到所有频道的缓存中，
     * 确保 FeedScreen 和其他消费者读取到最新值。
     */
    private fun syncLocalCache(adId: String, updatedAd: AdItem) {
        for ((_, items) in channelItems) {
            val index = items.indexOfFirst { it.id == adId }
            if (index >= 0) {
                items[index] = updatedAd
            }
        }
    }
}
