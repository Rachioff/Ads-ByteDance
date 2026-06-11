package com.bytedance.ads_bytedance.ai.chat.model

import com.bytedance.ads_bytedance.ai.model.AiIntentResult
import com.bytedance.ads_bytedance.data.model.AdItem
import kotlinx.serialization.Serializable

// ═══════════════════════════════════════════════════════════
// 通用 API 响应包装
// ═══════════════════════════════════════════════════════════

/**
 * Chat Bot 微服务通用响应包装
 *
 * 所有 API 端点统一使用此格式：
 * ```json
 * { "code": 0, "message": "ok", "data": { ... } }
 * ```
 */
@Serializable
data class ChatApiResponse<T>(
    val code: Int = 0,
    val message: String = "ok",
    val data: T? = null
) {
    val isSuccess: Boolean get() = code == 0
}

// ═══════════════════════════════════════════════════════════
// Session 相关
// ═══════════════════════════════════════════════════════════

/**
 * 创建 Session 的请求体（可选 title）
 */
@Serializable
data class CreateSessionRequest(
    val title: String = "新对话"
)

/**
 * 服务端返回的 Session 信息
 */
@Serializable
data class SessionInfo(
    val sessionId: String,
    val title: String = "新对话",
    val createdAt: Long = 0
)

// ═══════════════════════════════════════════════════════════
// 消息相关
// ═══════════════════════════════════════════════════════════

/**
 * 发送消息请求体
 */
@Serializable
data class SendMessageRequest(
    val content: String,
    val contextAd: AdContext? = null,              // 可选：用户当前讨论的广告上下文
    val previousMatchedAds: List<AdContext>? = null // 上轮搜索匹配到的广告，供 AI 了解上下文
)

/**
 * 服务端返回的单条消息
 *
 * 包含 AI 回复文本、匹配的广告列表、意图解析结果。
 * 用于 POST /api/sessions/{id}/messages 响应和 GET 历史响应。
 */
@Serializable
data class ChatMessageDto(
    val messageId: String = "",
    val role: String = "user",       // "user" | "assistant"
    val content: String = "",
    val ads: List<AdItem>? = null,   // 仅 assistant 消息可能有广告
    val intent: AiIntentResult? = null,
    val createdAt: Long = 0
)

/**
 * 发送消息的完整响应 data 字段
 */
@Serializable
data class SendMessageData(
    val message: ChatMessageDto? = null,
    val ads: List<AdItem>? = null,
    val intent: AiIntentResult? = null,
    val searchRequested: Boolean = false  // 服务端判断：true=客户端应执行广告匹配
)

/**
 * 对话历史响应 data 字段
 */
@Serializable
data class MessageHistoryData(
    val messages: List<ChatMessageDto> = emptyList(),
    val hasMore: Boolean = false
)

// ═══════════════════════════════════════════════════════════
// 广告上下文（对话中带入广告信息）
// ═══════════════════════════════════════════════════════════

/**
 * 发送消息时附带的广告上下文
 *
 * 当用户从搜索结果或详情页点击"和AI讨论"进入 ChatBot 时，
 * 当前查看的广告信息作为 [contextAd] 传入，
 * 微服务将其拼入 LLM prompt 以提供针对性的广告讨论。
 *
 * 注意：这是简化版模型，仅包含 LLM 需要的字段，
 * 不包含完整的互动数据和大尺寸媒体 URL。
 */
@Serializable
data class AdContext(
    val adId: String,
    val title: String,
    val description: String,
    val advertiserName: String,
    val tags: List<String> = emptyList(),
    val aiSummary: String? = null
)

// ═══════════════════════════════════════════════════════════
// UI 层消息模型
// ═══════════════════════════════════════════════════════════

/**
 * 消息角色（UI 层）
 */
enum class ChatRole { USER, ASSISTANT }

/**
 * UI 层聊天气泡消息
 *
 * 区别于 [ChatMessageDto]（网络层 DTO），这是 UI 渲染直接消费的模型。
 * `ads` 字段存储匹配到的广告对象引用，用于嵌入聊天气泡展示。
 */
data class ChatUiMessage(
    val id: String,
    val role: ChatRole,
    val content: String,
    val ads: List<AdItem> = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),
    val isFallback: Boolean = false   // 是否为降级模式（离线关键词搜索）产生
)
