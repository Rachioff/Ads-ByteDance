package com.bytedance.ads_bytedance.data.remote

import com.bytedance.ads_bytedance.data.model.AdItem
import com.bytedance.ads_bytedance.data.model.AdPageResponse
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * 广告数据 API 接口定义（Retrofit）
 *
 * 所有端点均由 Koin 通过 [RemoteDataSource] 统一调用。
 * 切换 Mock/Remote 模式只需修改 [gradle.properties] → `DATA_MODE`，
 * 无需改动任何 DataSource 层代码。
 */
interface AdApiService {

    /** 分页获取指定频道广告列表 */
    @GET("api/v1/ads/{channel}")
    suspend fun getAds(
        @Path("channel") channel: String,
        @Query("page") page: Int,
        @Query("page_size") pageSize: Int
    ): AdPageResponse

    /** 带标签过滤的分页查询 */
    @GET("api/v1/ads/{channel}/filter")
    suspend fun getAdsByTag(
        @Path("channel") channel: String,
        @Query("tag") tag: String,
        @Query("page") page: Int,
        @Query("page_size") pageSize: Int
    ): AdPageResponse

    /** 根据 ID 获取单条广告 */
    @GET("api/v1/ads/detail/{adId}")
    suspend fun getAdById(
        @Path("adId") adId: String
    ): AdItem

    /**
     * 更新广告互动状态（点赞/收藏）
     *
     * POST /api/v1/ads/{adId}/interaction
     * 服务端收到请求后执行：
     *   UPDATE ads SET is_liked = ?, like_count = like_count ± 1 WHERE id = ?
     * 返回更新后的完整 AdItem，客户端以此为准刷 UI。
     *
     * Mock 模式下请求不经过 Retrofit（由 [MockJsonDataSource] 直接处理），
     * Remote 模式下 Retrofit 发 HTTP POST，无后端时调用方收到 IOException。
     */
    @POST("api/v1/ads/{adId}/interaction")
    suspend fun updateInteraction(
        @Path("adId") adId: String,
        @Body request: InteractionRequest
    ): AdItem

    /** 获取全量广告（无过滤） */
    @GET("api/v1/ads/all")
    suspend fun getAllAds(): AdPageResponse

    /** 跨频道关键词搜索 */
    @GET("api/v1/search")
    suspend fun searchAds(
        @Query("q") query: String,
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 20
    ): AdPageResponse

    /** 获取热门搜索关键词 */
    @GET("api/v1/trending")
    suspend fun getTrendingKeywords(): List<String>

    /** 输入联想建议 */
    @GET("api/v1/suggestions")
    suspend fun getSearchSuggestions(
        @Query("q") query: String,
        @Query("limit") limit: Int = 10
    ): List<String>

    /** 递增广告曝光计数 */
    @POST("api/v1/ads/{adId}/exposure")
    suspend fun incrementExposure(
        @Path("adId") adId: String
    ): AdItem

    /** 递增广告点击计数 */
    @POST("api/v1/ads/{adId}/click")
    suspend fun incrementClick(
        @Path("adId") adId: String
    ): AdItem
}

/** POST /api/v1/ads/{adId}/interaction 请求体 */
@Serializable
data class InteractionRequest(
    val isLiked: Boolean? = null,
    val isCollected: Boolean? = null,
    val incrementShare: Boolean = false
)
