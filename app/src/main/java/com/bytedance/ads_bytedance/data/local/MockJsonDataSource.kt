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
            paginate(fullData.items, page, pageSize)
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
            // 不传 originalTotalPages —— paginate() 基于过滤后数据量计算 totalPages，
            // 避免过滤结果 < 原始数据时 totalPages 虚高导致 UI 误判还有更多页
            paginate(filtered, page, pageSize)
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

    override suspend fun updateInteraction(
        adId: String,
        isLiked: Boolean?,
        isCollected: Boolean?,
        incrementShare: Boolean
    ): Result<AdItem> = withContext(Dispatchers.IO) {
        runCatching {
            // 在所有频道缓存中查找并更新广告
            for (channel in Channel.entries) {
                val data = channelCache[channel] ?: loadChannelData(channel)
                val ad = data.items.find { it.id == adId }
                if (ad != null) {
                    isLiked?.let { newValue ->
                        if (ad.isLiked != newValue) {
                            ad.isLiked = newValue
                            ad.likeCount = if (newValue)
                                ad.likeCount + 1
                            else
                                (ad.likeCount - 1).coerceAtLeast(0)
                        }
                    }
                    isCollected?.let { newValue ->
                        if (ad.isCollected != newValue) {
                            ad.isCollected = newValue
                            ad.collectCount = if (newValue)
                                ad.collectCount + 1
                            else
                                (ad.collectCount - 1).coerceAtLeast(0)
                        }
                    }
                    if (incrementShare) {
                        ad.shareCount += 1
                    }
                    return@runCatching ad
                }
            }
            throw NoSuchElementException("Ad not found: $adId")
        }
    }

    override suspend fun searchAds(
        query: String,
        page: Int,
        pageSize: Int
    ): Result<AdPageResponse> = withContext(Dispatchers.IO) {
        runCatching {
            if (query.isBlank()) return@runCatching AdPageResponse(
                page = 1, totalPages = 0, pageSize = pageSize, items = emptyList()
            )

            // 收集所有频道的全量广告
            val allAds = mutableListOf<AdItem>()
            val seenIds = mutableSetOf<String>()
            for (channel in Channel.entries) {
                val data = loadChannelData(channel)
                for (ad in data.items) {
                    if (ad.id !in seenIds) {
                        allAds.add(ad)
                        seenIds.add(ad.id)
                    }
                }
            }

            // 分词
            val tokens = query.split(Regex("[\\s，,。！!？?、；;：:]+"))
                .filter { it.length >= 2 }

            // 按相关度评分排序
            val scored = allAds.map { ad ->
                var score = 0.0
                tokens.forEach { token ->
                    if (ad.title.contains(token, ignoreCase = true)) score += 3.0
                    if (ad.description.contains(token, ignoreCase = true)) score += 1.5
                }
                ad.tags.forEach { tag ->
                    tokens.forEach { token ->
                        if (tag.name.contains(token, ignoreCase = true)) score += 2.0
                    }
                }
                Pair(ad, score)
            }.filter { it.second > 0 }
             .sortedByDescending { it.second }
             .map { it.first }

            paginate(scored, page, pageSize)
        }
    }

    override suspend fun getAllAds(): Result<AdPageResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val allAds = mutableListOf<AdItem>()
            val seenIds = mutableSetOf<String>()
            for (channel in Channel.entries) {
                val data = loadChannelData(channel)
                for (ad in data.items) {
                    if (ad.id !in seenIds) {
                        allAds.add(ad)
                        seenIds.add(ad.id)
                    }
                }
            }
            AdPageResponse(
                page = 1,
                totalPages = 1,
                pageSize = allAds.size,
                items = allAds
            )
        }
    }

    override suspend fun getTrendingKeywords(): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val tagCounts = mutableMapOf<String, Int>()
            for (channel in Channel.entries) {
                val data = loadChannelData(channel)
                for (ad in data.items) {
                    for (tag in ad.tags) {
                        tagCounts[tag.name] = (tagCounts[tag.name] ?: 0) + 1
                    }
                }
            }
            tagCounts.entries
                .sortedByDescending { it.value }
                .take(10)
                .map { it.key }
        }
    }

    override suspend fun incrementExposure(adId: String): Result<AdItem> = withContext(Dispatchers.IO) {
        runCatching {
            for (channel in Channel.entries) {
                val data = channelCache[channel] ?: loadChannelData(channel)
                val ad = data.items.find { it.id == adId }
                if (ad != null) {
                    ad.exposureCount += 1
                    return@runCatching ad
                }
            }
            throw NoSuchElementException("Ad not found: $adId")
        }
    }

    override suspend fun incrementClick(adId: String): Result<AdItem> = withContext(Dispatchers.IO) {
        runCatching {
            for (channel in Channel.entries) {
                val data = channelCache[channel] ?: loadChannelData(channel)
                val ad = data.items.find { it.id == adId }
                if (ad != null) {
                    ad.clickCount += 1
                    return@runCatching ad
                }
            }
            throw NoSuchElementException("Ad not found: $adId")
        }
    }

    override suspend fun getSearchSuggestions(
        query: String,
        limit: Int
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            if (query.isBlank()) return@runCatching emptyList<String>()

            val suggestions = linkedSetOf<String>()
            for (channel in Channel.entries) {
                val data = loadChannelData(channel)
                for (ad in data.items) {
                    if (ad.title.contains(query, ignoreCase = true)) {
                        suggestions.add(ad.title)
                    }
                    for (tag in ad.tags) {
                        if (tag.name.contains(query, ignoreCase = true)) {
                            suggestions.add(tag.name)
                        }
                    }
                    if (suggestions.size >= limit) break
                }
                if (suggestions.size >= limit) break
            }
            suggestions.take(limit).toList()
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
            pageSize: Int
        ): AdPageResponse {
            val totalPages = if (allItems.isEmpty()) 0
                else (allItems.size + pageSize - 1) / pageSize
            val fromIndex = (page - 1) * pageSize
            if (fromIndex >= allItems.size) {
                return AdPageResponse(
                    page = page,
                    totalPages = totalPages,
                    pageSize = pageSize,
                    items = emptyList()
                )
            }
            val toIndex = minOf(fromIndex + pageSize, allItems.size)
            val pageItems = allItems.subList(fromIndex, toIndex)

            return AdPageResponse(
                page = page,
                totalPages = totalPages,
                pageSize = pageSize,
                items = pageItems
            )
        }
    }
}
