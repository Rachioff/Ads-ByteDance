package com.bytedance.ads_bytedance.data.local

import com.bytedance.ads_bytedance.data.model.AdItem
import com.bytedance.ads_bytedance.data.model.AdPageResponse
import com.bytedance.ads_bytedance.data.model.Channel

/**
 * 广告数据源统一接口
 *
 * Mock 与 Remote 各自实现此接口，AdRepository 通过此接口屏蔽底层差异。
 */
interface AdDataSource {

    /**
     * 按频道分页获取广告列表
     *
     * @param channel 目标频道
     * @param page 页码（从 1 开始）
     * @param pageSize 每页条数
     * @return 分页响应；失败时返回 Result.failure
     */
    suspend fun getAds(
        channel: Channel,
        page: Int,
        pageSize: Int
    ): Result<AdPageResponse>

    /**
     * 带标签过滤的分页查询
     *
     * @param channel 目标频道
     * @param tag 过滤标签名称
     * @param page 页码
     * @param pageSize 每页条数
     */
    suspend fun getAdsByTag(
        channel: Channel,
        tag: String,
        page: Int,
        pageSize: Int
    ): Result<AdPageResponse>

    /**
     * 根据 ID 获取单条广告
     */
    suspend fun getAdById(adId: String): Result<AdItem>
}
