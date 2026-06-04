package com.bytedance.ads_bytedance.ai.model

import com.bytedance.ads_bytedance.data.model.Tag
import kotlinx.serialization.Serializable

// ═══════════════════════════════════════════════════════════
// AI API 请求
// ═══════════════════════════════════════════════════════════

/**
 * OpenAI 兼容 Chat Completions 请求体
 */
@Serializable
data class AiRequest(
    val model: String = "deepseek-v4-flash",
    val messages: List<AiMessage>,
    val temperature: Double = 0.3,
    val max_tokens: Int = 512
)

/**
 * Chat 消息
 */
@Serializable
data class AiMessage(
    val role: String,
    val content: String
)

// ═══════════════════════════════════════════════════════════
// AI API 响应
// ═══════════════════════════════════════════════════════════

/**
 * OpenAI 兼容 Chat Completions 响应体
 */
@Serializable
data class AiResponse(
    val id: String? = null,
    val choices: List<AiChoice> = emptyList()
)

@Serializable
data class AiChoice(
    val index: Int = 0,
    val message: AiMessage? = null
)

// ═══════════════════════════════════════════════════════════
// 业务层 AI 产出
// ═══════════════════════════════════════════════════════════

/**
 * AI 生成的广告增强内容（摘要 + 标签）
 */
data class AiGeneratedContent(
    val summary: String,
    val tags: List<Tag>
)

/**
 * AI 意图解析结果（对话式搜索场景）
 *
 * 由大模型将自然语言查询解析为结构化搜索条件，
 * 交给 AdMatchingEngine 做本地匹配。
 */
@Serializable
data class AiIntentResult(
    val categories: List<String> = emptyList(),
    val audiences: List<String> = emptyList(),
    val styles: List<String> = emptyList(),
    val scenes: List<String> = emptyList(),
    val priceRange: AiPriceRange? = null,
    val keywords: List<String> = emptyList()
)

@Serializable
data class AiPriceRange(
    val min: Int = 0,
    val max: Int = -1
)
