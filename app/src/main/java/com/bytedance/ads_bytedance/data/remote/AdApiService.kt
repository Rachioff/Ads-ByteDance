package com.bytedance.ads_bytedance.data.remote

import com.bytedance.ads_bytedance.data.model.AdItem
import com.bytedance.ads_bytedance.data.model.AdPageResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * 广告数据 API 接口定义（Retrofit）
 *
 * 真实后端就绪后，将 baseUrl 切换为后端地址即可使用。
 * 当前 Mock 模式下，请求由 MockJsonDataSource 处理。
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
}
