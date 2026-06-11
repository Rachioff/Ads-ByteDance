package com.bytedance.ads_bytedance.ai.api

import com.bytedance.ads_bytedance.ai.cache.AiCacheManager
import com.bytedance.ads_bytedance.ai.model.AiGeneratedContent
import com.bytedance.ads_bytedance.ai.model.AiRequest
import com.bytedance.ads_bytedance.common.network.NetworkConfig
import com.bytedance.ads_bytedance.data.model.AdItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * AI 内容生成器（编排层）
 *
 * 职责：
 * 1. 编排三级缓存流程：内存 → Room → API
 * 2. 构造请求 → 调用 AI API → 解析响应 → 存储缓存
 * 3. 全部失败时提供降级静态内容
 *
 * ## 架构
 * ```
 * App 调用 generate(ad)
 *   → AiCacheManager.getCached(adId)
 *       → 命中 → 返回
 *       → 未命中 → fetchFromApi(ad)
 *           → 成功 → AiCacheManager.store() + 返回
 *           → 失败 → fallback(ad) + 返回
 * ```
 *
 * ## 线程模型
 * - 网络调用在 IO 线程执行
 * - 结果返回到调用协程上下文
 *
 * @param aiApiService OpenAI 兼容 API 接口
 * @param cacheManager 三级缓存管理器
 */
class AiContentGenerator(
    private val aiApiService: AiApiService,
    private val cacheManager: AiCacheManager
) {

    /**
     * 为单条广告生成 AI 内容（含缓存检查）
     *
     * 这是主要入口方法。先查缓存，未命中则调用 AI API。
     * API 不可用或解析失败时，返回基于广告描述构造的降级内容。
     *
     * @param ad 广告数据
     * @return AI 生成内容（或降级静态内容）
     */
    suspend fun generate(ad: AdItem): AiGeneratedContent = withContext(Dispatchers.IO) {
        // Level 1+2: 查缓存
        val cached = cacheManager.getCached(ad.id)
        if (cached != null) {
            return@withContext cached
        }

        // Level 3: 调用 AI API
        try {
            val content = fetchFromApi(ad)
            cacheManager.store(ad.id, content)
            content
        } catch (e: Exception) {
            // 降级：使用静态内容
            buildFallbackContent(ad)
        }
    }

    /**
     * 批量预生成 AI 内容（用于信息流首屏加载后）
     *
     * 注意事项：
     * - 仅对缓存未命中的广告发起 API 请求
     * - 串行调用以避免 API 限流（不并发）
     * - 单条失败不影响其他广告
     *
     * @param ads 广告列表
     * @param onProgress 单条完成回调（可选，用于通知 UI 刷新）
     */
    suspend fun generateBatch(
        ads: List<AdItem>,
        onProgress: ((adId: String, content: AiGeneratedContent) -> Unit)? = null
    ) = withContext(Dispatchers.IO) {
        for (ad in ads) {
            try {
                val content = generate(ad)
                onProgress?.invoke(ad.id, content)
            } catch (_: Exception) {
                // 单条失败静默跳过，不阻塞其他广告
            }
        }
    }

    /**
     * 仅刷新缓存（强制重新调用 API，忽略已有缓存）
     *
     * 用于下拉刷新场景。
     */
    suspend fun refresh(ad: AdItem): AiGeneratedContent = withContext(Dispatchers.IO) {
        try {
            val content = fetchFromApi(ad)
            cacheManager.store(ad.id, content)
            content
        } catch (e: Exception) {
            buildFallbackContent(ad)
        }
    }

    // ═══════════════════════════════════════════════════════
    // 内部方法
    // ═══════════════════════════════════════════════════════

    /**
     * 调用 AI API 生成内容
     */
    private suspend fun fetchFromApi(ad: AdItem): AiGeneratedContent {
        // 检查 API Key 是否配置
        if (NetworkConfig.getAiApiKey().isBlank()) {
            throw IllegalStateException("AI API Key 未配置，请在 local.properties 中设置 ai.api.key")
        }

        val messages = AiPromptBuilder.buildMessages(ad)
        val request = AiRequest(
            model = "deepseek-v4-flash",
            messages = messages,
            temperature = 0.3,
            max_tokens = 512
        )

        val response = aiApiService.chatCompletions(request)

        if (!response.isSuccessful) {
            throw Exception("AI API 请求失败: HTTP ${response.code()} ${response.message()}")
        }

        val body = response.body()
            ?: throw Exception("AI API 返回空响应体")

        val rawContent = body.choices
            .firstOrNull()
            ?.message
            ?.content
            ?: throw Exception("AI API 响应无有效内容")

        // 解析 + 校验
        val parsed = AiResponseParser.parse(rawContent)
            ?: throw Exception("AI 响应 JSON 解析失败")

        return parsed
    }

    /**
     * 构造降级静态内容
     *
     * 当 AI API 不可用时，基于广告的原始描述和标签生成兜底内容：
     * - 摘要：取描述前 80 字
     * - 标签：复用 Mock 数据中的静态标签
     */
    private fun buildFallbackContent(ad: AdItem): AiGeneratedContent {
        return AiGeneratedContent(
            summary = AiResponseParser.fallbackSummary(ad.description),
            tags = AiResponseParser.fallbackTags(ad.tags)
        )
    }
}
