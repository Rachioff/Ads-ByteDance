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
     * 更新广告的点赞/收藏状态（跨页面同步）
     *
     * 更新内存缓存中的所有引用，确保详情页和信息流卡片状态一致。
     */
    fun updateInteraction(adId: String, isLiked: Boolean? = null, isCollected: Boolean? = null) {
        for ((_, items) in channelItems) {
            items.find { it.id == adId }?.let { ad ->
                isLiked?.let { ad.isLiked = it }
                isCollected?.let { ad.isCollected = it }
            }
        }
    }

    /** 清空所有缓存 */
    fun clearAll() {
        channelItems.clear()
        channelPagination.clear()
        channelFilterTag.clear()
    }
}
