package com.bytedance.ads_bytedance.data.local

import android.content.Context
import com.bytedance.ads_bytedance.data.model.AdItem
import com.bytedance.ads_bytedance.data.model.AdPageResponse
import com.bytedance.ads_bytedance.data.model.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

/**
 * 本地 Mock JSON 数据源
 *
 * 从 assets/mock/ 目录读取预置 JSON 文件，解析为 AdPageResponse。
 * 支持分页（内存切片）和标签过滤。
 *
 * JSON 文件命名约定：ads_{channel.name.lowercase()}.json
 * 例如：ads_featured.json, ads_ecommerce.json, ads_local.json
 */
class MockJsonDataSource(
    private val context: Context
) : AdDataSource {

    /** 内存缓存：已加载的频道全量数据，避免重复读文件 */
    private val channelCache = mutableMapOf<Channel, AdPageResponse>()

    override suspend fun getAds(
        channel: Channel,
        page: Int,
        pageSize: Int
    ): Result<AdPageResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val fullData = loadChannelData(channel)
            paginate(fullData.items, page, pageSize, fullData.totalPages)
        }
    }

    override suspend fun getAdsByTag(
        channel: Channel,
        tag: String,
        page: Int,
        pageSize: Int
    ): Result<AdPageResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val fullData = loadChannelData(channel)
            val filtered = fullData.items.filter { ad ->
                ad.tags.any { it.name == tag }
            }
            paginate(filtered, page, pageSize, fullData.totalPages)
        }
    }

    override suspend fun getAdById(adId: String): Result<AdItem> = withContext(Dispatchers.IO) {
        runCatching {
            // 遍历所有频道缓存查找
            for (channel in Channel.entries) {
                val data = loadChannelData(channel)
                val found = data.items.find { it.id == adId }
                if (found != null) return@runCatching found
            }
            throw NoSuchElementException("Ad not found: $adId")
        }
    }

    // ──────────────────────────────────────────────
    // 内部方法
    // ──────────────────────────────────────────────

    /** 加载频道全量数据（优先从内存缓存取） */
    private fun loadChannelData(channel: Channel): AdPageResponse {
        return channelCache.getOrPut(channel) {
            val fileName = "mock/ads_${channel.name.lowercase()}.json"
            val jsonString = context.assets.open(fileName)
                .bufferedReader()
                .use { it.readText() }
            json.decodeFromString<AdPageResponse>(jsonString)
        }
    }

    companion object {
        /** 共享 JSON 解析器——与全局配置一致 */
        @OptIn(ExperimentalSerializationApi::class)
        val json: Json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            isLenient = true
            // 密封类多态：type 字段作为类型判别键
            classDiscriminator = "type"
        }

        /** 对全量数据进行内存分页切片 */
        fun paginate(
            allItems: List<AdItem>,
            page: Int,
            pageSize: Int,
            originalTotalPages: Int = 1
        ): AdPageResponse {
            val fromIndex = (page - 1) * pageSize
            if (fromIndex >= allItems.size) {
                return AdPageResponse(
                    page = page,
                    totalPages = originalTotalPages,
                    pageSize = pageSize,
                    items = emptyList()
                )
            }
            val toIndex = minOf(fromIndex + pageSize, allItems.size)
            val pageItems = allItems.subList(fromIndex, toIndex)
            val totalPages = if (allItems.isEmpty()) 0
            else (allItems.size + pageSize - 1) / pageSize

            return AdPageResponse(
                page = page,
                totalPages = totalPages,
                pageSize = pageSize,
                items = pageItems
            )
        }
    }
}
