package com.bytedance.ads_bytedance.ai.chat.model

import com.bytedance.ads_bytedance.data.model.AdItem

/**
 * 对话搜索页面 UI 状态
 *
 * 单一数据源，由 ChatViewModel 的 StateFlow 驱动，
 * Compose 端通过 collectAsStateWithLifecycle() 观察。
 *
 * @property messages 聊天气泡消息列表
 * @property sessionId 当前对话 session ID（null = 微服务不可用）
 * @property isLoading 是否正在等待 AI 回复
 * @property isServiceAvailable 微服务是否可用（首次创建 session 时确定）
 * @property inputText 输入框当前文本
 * @property errorMessage 错误提示（显示在 UI 顶部或 Snackbar）
 * @property currentContextAd 当前讨论的广告上下文（从搜索/详情页传入）
 * @property contextAdItem 广告上下文对应的完整 AdItem（UI 渲染用）
 * @property isLoadingHistory 是否正在从微服务加载历史消息
 */
data class ChatUiState(
    val messages: List<ChatUiMessage> = emptyList(),
    val sessionId: String? = null,
    val isLoading: Boolean = false,
    val isServiceAvailable: Boolean = true,
    val inputText: String = "",
    val errorMessage: String? = null,
    val currentContextAd: AdContext? = null,
    val contextAdItem: AdItem? = null,
    val isLoadingHistory: Boolean = false
)
