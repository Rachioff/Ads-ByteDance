package com.bytedance.ads_bytedance.ai.cache

import com.bytedance.ads_bytedance.ai.model.AiGeneratedContent
import com.bytedance.ads_bytedance.data.local.dao.AiCacheDao
import com.bytedance.ads_bytedance.data.local.entity.AiCacheEntity
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Collections

/**
 * AI 结果三级缓存管理器
 *
 * ## 缓存层次
 * ```
 * 请求 AI 内容(adId)
 *   → Level 1: 内存 LRU (LinkedHashMap, access-order, maxSize=50)
 *       → 命中 → 返回
 *       → 未命中 ↓
 *   → Level 2: Room 磁盘缓存 (AiCacheDao, 7 天过期)
 *       → 命中 + 未过期 → 写入内存 LRU → 返回
 *       → 未命中 / 已过期 ↓
 *   → Level 3: 网络 API (由 [com.bytedance.ads_bytedance.ai.api.AiContentGenerator] 调用)
 *       → 成功 → 写入内存 LRU + Room DB → 返回
 *       → 失败 → 降级静态内容
 * ```
 *
 * ## 线程安全
 * - 内存缓存操作通过 `synchronized` 保证线程安全
 * - Room DAO 调用在协程 IO 线程执行（调用方负责调度）
 *
 * @param aiCacheDao Room AI 缓存 DAO
 * @param maxMemorySize 内存 LRU 最大条目数
 */
class AiCacheManager(
    private val aiCacheDao: AiCacheDao,
    private val maxMemorySize: Int = 50
) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    /**
     * 内存 LRU 缓存
     *
     * LinkedHashMap 构造参数：
     * - accessOrder=true → 按访问顺序排序（LRU 语义）
     *
     * `removeEldestEntry` 在每次 put 后回调，
     * 返回 true 时自动移除最老的条目。
     */
    private val memoryCache = object : LinkedHashMap<String, AiGeneratedContent>(
        16, 0.75f, true  // access-order
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, AiGeneratedContent>): Boolean {
            return size > maxMemorySize
        }
    }

    // ═══════════════════════════════════════════════════════
    // 读取
    // ═══════════════════════════════════════════════════════

    /**
     * 从缓存中获取 AI 内容（仅查缓存，不触发网络请求）
     *
     * @param adId 广告 ID
     * @return 命中返回 [AiGeneratedContent]，未命中返回 null
     */
    suspend fun getCached(adId: String): AiGeneratedContent? {
        // Level 1: 内存缓存
        synchronized(memoryCache) {
            memoryCache[adId]?.let { return it }
        }

        // Level 2: Room 磁盘缓存
        val entity = aiCacheDao.getValidCache(adId)
        if (entity != null) {
            val content = entityToContent(entity)
            if (content != null) {
                synchronized(memoryCache) {
                    memoryCache[adId] = content
                }
                return content
            }
        }

        return null
    }

    /**
     * 批量获取缓存内容（用于信息流列表场景）
     *
     * @param adIds 广告 ID 列表
     * @return 命中的缓存内容 Map（key=adId）
     */
    suspend fun getCachedBatch(adIds: List<String>): Map<String, AiGeneratedContent> {
        val result = mutableMapOf<String, AiGeneratedContent>()

        // 先查内存
        val missed = mutableListOf<String>()
        synchronized(memoryCache) {
            for (adId in adIds) {
                val cached = memoryCache[adId]
                if (cached != null) {
                    result[adId] = cached
                } else {
                    missed.add(adId)
                }
            }
        }

        // 再查 Room（批量查询效率更高）
        if (missed.isNotEmpty()) {
            for (adId in missed) {
                val entity = aiCacheDao.getValidCache(adId)
                if (entity != null) {
                    val content = entityToContent(entity)
                    if (content != null) {
                        result[adId] = content
                        synchronized(memoryCache) {
                            memoryCache[adId] = content
                        }
                    }
                }
            }
        }

        return result
    }

    // ═══════════════════════════════════════════════════════
    // 写入
    // ═══════════════════════════════════════════════════════

    /**
     * 存储 AI 生成内容到两级缓存
     *
     * @param adId 广告 ID
     * @param content AI 生成的内容
     * @param ttlDays 缓存有效期（天），默认 7 天
     */
    suspend fun store(adId: String, content: AiGeneratedContent, ttlDays: Int = 7) {
        // Level 1: 内存
        synchronized(memoryCache) {
            memoryCache[adId] = content
        }

        // Level 2: Room
        val now = System.currentTimeMillis()
        val tagsJson = json.encodeToString(content.tags)
        val entity = AiCacheEntity(
            adId = adId,
            summary = content.summary,
            tagsJson = tagsJson,
            createdAt = now,
            expiresAt = now + ttlDays * 24L * 60 * 60 * 1000
        )
        aiCacheDao.insert(entity)
    }

    // ═══════════════════════════════════════════════════════
    // 维护
    // ═══════════════════════════════════════════════════════

    /**
     * 清理过期缓存（建议在 AdsApplication.onCreate 中调用）
     */
    suspend fun cleanExpired() {
        aiCacheDao.deleteExpired()
    }

    /**
     * 清空所有内存缓存（保留磁盘缓存，供下拉刷新使用）
     */
    fun clearMemory() {
        synchronized(memoryCache) {
            memoryCache.clear()
        }
    }

    /**
     * 清空所有缓存（内存 + 磁盘）
     */
    suspend fun clearAll() {
        synchronized(memoryCache) {
            memoryCache.clear()
        }
        aiCacheDao.deleteAll()
    }

    /**
     * 获取内存缓存大小（调试用）
     */
    fun memorySize(): Int = synchronized(memoryCache) { memoryCache.size }

    // ═══════════════════════════════════════════════════════
    // 内部工具
    // ═══════════════════════════════════════════════════════

    /**
     * 将 Room Entity 转换为业务模型
     */
    private fun entityToContent(entity: AiCacheEntity): AiGeneratedContent? {
        return try {
            val tags = json.decodeFromString<List<com.bytedance.ads_bytedance.data.model.Tag>>(entity.tagsJson)
            AiGeneratedContent(
                summary = entity.summary,
                tags = tags
            )
        } catch (e: Exception) {
            // tagsJson 解析失败 → 返回仅含 summary 的内容
            AiGeneratedContent(
                summary = entity.summary,
                tags = emptyList()
            )
        }
    }
}
