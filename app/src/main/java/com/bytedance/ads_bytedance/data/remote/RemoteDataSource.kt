package com.bytedance.ads_bytedance.data.remote

import com.bytedance.ads_bytedance.data.local.AdDataSource
import com.bytedance.ads_bytedance.data.model.AdItem
import com.bytedance.ads_bytedance.data.model.AdPageResponse
import com.bytedance.ads_bytedance.data.model.Channel

/**
 * 远程 API 数据源
 *
 * 通过 Retrofit + OkHttp 从真实后端获取数据。
 * 实现 AdDataSource，与 MockJsonDataSource 可互换。
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
}
