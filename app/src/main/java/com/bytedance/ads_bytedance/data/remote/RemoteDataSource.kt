package com.bytedance.ads_bytedance.data.remote

import com.bytedance.ads_bytedance.data.local.AdDataSource
import com.bytedance.ads_bytedance.data.model.AdItem
import com.bytedance.ads_bytedance.data.model.AdPageResponse
import com.bytedance.ads_bytedance.data.model.Channel

/**
 * 远程 API 数据源
 *
 * 通过 Retrofit + OkHttp 从真实后端获取数据。
 * 实现 [AdDataSource]，与 [MockJsonDataSource] 通过 Koin DI 按 `DATA_MODE` 切换。
 *
 * **切换方式**（零代码改动）：
 * ```
 * gradle.properties → DATA_MODE=mock → MockJsonDataSource
 *                   → DATA_MODE=remote → RemoteDataSource（本文）
 * ```
 * 所有 [AdDataSource] 接口方法均有完整实现——Remote 模式下没有桩，
 * 运行时失败（如无后端）是网络层面的 IOException，不是代码层面的 NotImplementedError。
 */
class RemoteDataSource(
    private val apiService: AdApiService
) : AdDataSource {

    override suspend fun getAds(
        channel: Channel,
        page: Int,
        pageSize: Int
    ): Result<AdPageResponse> = runCatching {
        apiService.getAds(
            channel = channel.name.lowercase(),
            page = page,
            pageSize = pageSize
        )
    }

    override suspend fun getAdsByTag(
        channel: Channel,
        tag: String,
        page: Int,
        pageSize: Int
    ): Result<AdPageResponse> = runCatching {
        apiService.getAdsByTag(
            channel = channel.name.lowercase(),
            tag = tag,
            page = page,
            pageSize = pageSize
        )
    }

    override suspend fun getAdById(adId: String): Result<AdItem> = runCatching {
        apiService.getAdById(adId = adId)
    }

    /**
     * 调用 POST /api/v1/ads/{adId}/interaction 更新互动状态。
     *
     * - 有后端时：服务端 UPDATE DB → 返回更新后的 AdItem → 透传给上层
     * - 无后端时：OkHttp 收到 connection refused / 404 → `Result.failure(IOException)`
     *
     * 接入真实后端所需的改为 **零行代码改动**——
     * 后端接口格式与 [InteractionRequest] 一致即可。
     */
    override suspend fun updateInteraction(
        adId: String,
        isLiked: Boolean?,
        isCollected: Boolean?,
        incrementShare: Boolean
    ): Result<AdItem> = runCatching {
        apiService.updateInteraction(
            adId = adId,
            request = InteractionRequest(isLiked, isCollected, incrementShare)
        )
    }

    override suspend fun getAllAds(): Result<AdPageResponse> = runCatching {
        apiService.getAllAds()
    }

    override suspend fun searchAds(
        query: String,
        page: Int,
        pageSize: Int
    ): Result<AdPageResponse> = runCatching {
        apiService.searchAds(query = query, page = page, pageSize = pageSize)
    }

    override suspend fun getTrendingKeywords(): Result<List<String>> = runCatching {
        apiService.getTrendingKeywords()
    }

    override suspend fun getSearchSuggestions(
        query: String,
        limit: Int
    ): Result<List<String>> = runCatching {
        apiService.getSearchSuggestions(query = query, limit = limit)
    }

    override suspend fun incrementExposure(adId: String): Result<AdItem> = runCatching {
        apiService.incrementExposure(adId = adId)
    }

    override suspend fun incrementClick(adId: String): Result<AdItem> = runCatching {
        apiService.incrementClick(adId = adId)
    }
}
