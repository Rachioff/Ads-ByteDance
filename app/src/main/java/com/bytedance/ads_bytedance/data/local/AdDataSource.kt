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

    /**
     * 更新广告互动状态（点赞/收藏）
     *
     * Mock 实现：直接修改内存缓存中的计数器，模拟服务端行为。
     * Remote 实现：发送 POST /api/ads/{adId}/interaction 请求，
     * 服务端返回更新后的数据后覆盖本地缓存。
     *
     * 接入真实服务端时，只需替换 [RemoteDataSource] 的实现，
     * Repository 层和 ViewModel 层无需改动。
     *
     * @param adId 广告 ID
     * @param isLiked null=不改变点赞状态, true=点赞, false=取消点赞
     * @param isCollected null=不改变收藏状态, true=收藏, false=取消收藏
     * @param incrementShare true=分享计数 +1（分享无取消操作，始终累加）
     * @return 更新后的广告对象；失败时 Result.failure
     */
    suspend fun updateInteraction(
        adId: String,
        isLiked: Boolean? = null,
        isCollected: Boolean? = null,
        incrementShare: Boolean = false
    ): Result<AdItem>

    /**
     * 跨频道关键词搜索广告
     *
     * 实现（Mock 或 Remote）对调用方透明。
     * Mock：遍历所有频道全量数据做关键词匹配 + 相关度排序 + 分页。
     * Remote：调用服务端搜索 API。
     *
     * @param query 搜索关键词
     * @param page 页码（从 1 开始）
     * @param pageSize 每页条数
     * @return 分页搜索结果；失败时 Result.failure
     */
    suspend fun searchAds(
        query: String,
        page: Int = 1,
        pageSize: Int = 20
    ): Result<AdPageResponse>

    /**
     * 获取全量广告（不做任何过滤）
     *
     * 将所有频道的广告汇总返回，不进行关键词匹配或相关度排序。
     * 主要用于 AI 意图匹配场景：AI 已提取结构化搜索条件，
     * 匹配由 [com.bytedance.ads_bytedance.common.engine.AdMatchingEngine] 完成，
     * 数据源只需提供全量候选集。
     *
     * @return 包含所有频道全部广告的分页响应（单一页面）；失败时 Result.failure
     */
    suspend fun getAllAds(): Result<AdPageResponse>

    /**
     * 获取热门搜索关键词
     *
     * Mock：从广告标签中按频次统计 Top 8~10 返回。
     * Remote：调用服务端热搜接口。
     */
    suspend fun getTrendingKeywords(): Result<List<String>>

    /**
     * 输入联想建议
     *
     * 用户输入过程中实时调用，返回匹配的标题/标签建议。
     * Mock：匹配广告 title 和 tags.name 的 prefix/substring。
     * Remote：调用服务端 suggestion 接口。
     *
     * @param query 当前输入文本
     * @param limit 返回条数上限
     */
    suspend fun getSearchSuggestions(
        query: String,
        limit: Int = 10
    ): Result<List<String>>

    /**
     * 递增广告曝光计数
     *
     * 每次广告满足曝光条件（≥50% 可见 + ≥1s 停留）时调用。
     * Mock 实现：直接修改内存缓存中的 exposureCount。
     * Remote 实现：发送 POST /api/ads/{adId}/exposure 请求。
     *
     * @param adId 广告 ID
     * @return 更新后的广告对象；失败时 Result.failure
     */
    suspend fun incrementExposure(adId: String): Result<AdItem>

    /**
     * 递增广告点击计数
     *
     * 每次用户点击广告卡片进入详情页时调用。
     * Mock 实现：直接修改内存缓存中的 clickCount。
     * Remote 实现：发送 POST /api/ads/{adId}/click 请求。
     *
     * @param adId 广告 ID
     * @return 更新后的广告对象；失败时 Result.failure
     */
    suspend fun incrementClick(adId: String): Result<AdItem>
}
