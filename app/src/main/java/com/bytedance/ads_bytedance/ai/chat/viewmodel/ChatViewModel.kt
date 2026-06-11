package com.bytedance.ads_bytedance.ai.chat.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bytedance.ads_bytedance.ai.api.ChatBotService
import com.bytedance.ads_bytedance.ai.chat.cache.ChatMemoryCache
import com.bytedance.ads_bytedance.ai.chat.model.AdContext
import com.bytedance.ads_bytedance.ai.chat.model.ChatMessageDto
import com.bytedance.ads_bytedance.ai.chat.model.ChatRole
import com.bytedance.ads_bytedance.ai.chat.model.ChatUiMessage
import com.bytedance.ads_bytedance.ai.chat.model.ChatUiState
import com.bytedance.ads_bytedance.ai.chat.model.CreateSessionRequest
import com.bytedance.ads_bytedance.ai.chat.model.SendMessageRequest
import com.bytedance.ads_bytedance.behavior.model.BehaviorType
import com.bytedance.ads_bytedance.behavior.model.UserBehavior
import com.bytedance.ads_bytedance.behavior.tracker.BehaviorCollector
import com.bytedance.ads_bytedance.common.engine.AdMatchResult
import com.bytedance.ads_bytedance.common.engine.AdMatchingEngine
import com.bytedance.ads_bytedance.common.util.SessionManager
import com.bytedance.ads_bytedance.data.model.AdItem
import com.bytedance.ads_bytedance.data.repository.AdRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * 对话式 AI 搜索 ViewModel
 *
 * ## 核心流程
 * ```
 * 1. init → chatCache.isWarm() → 命中 → 直接展示缓存消息（无网络请求）
 *         └─ 未命中 → loadHistory() → rematchDtoToUiMessage() → createSession() → loadContextAd(adId)
 *              ├─ 历史恢复成功 → 复用旧 sessionId + 历史消息 + intent 重匹配恢复广告
 *              └─ 历史恢复失败 → createSession（新 session）
 *
 * 2. sendMessage(query)
 *    ├─ 添加用户气泡到 messages + chatCache（携带 contextAd）
 *    ├─ isServiceAvailable=true:
 *    │   ├─ POST /api/sessions/{id}/messages { content, contextAd }
 *    │   ├─ 成功 → 提取 intent → AdMatchingEngine.matchWithIntent()
 *    │   └─ 失败 → 降级到 repository.searchAds()
 *    └─ isServiceAvailable=false:
 *        └─ 降级到 repository.searchAds()
 *    └─ 成功后同步 chatCache.addMessage() 保持缓存一致
 *
 * 3. clearConversation()
 *    ├─ chatCache.clear()——防止下次进入读到已删除的旧数据
 *    ├─ DELETE /api/sessions/{id}
 *    ├─ POST /api/sessions（创建新 session）
 *    ├─ 清除保存的 sessionId
 *    └─ 清空 messages（保留 contextAd）
 * ```
 *
 * ## 内存缓存策略
 * [ChatMemoryCache] 作为 ViewModel 的外部单例，在 ViewModel 因导航销毁重建时
 * 保持数据不丢失。应用启动时由 [ChatPreloader] 在 IO 线程预热缓存，
 * 确保冷启动后进入 ChatScreen 也能立即展示历史。
 *
 * ## 广告恢复策略
 * 服务端 Mode B（客户端匹配）下，历史消息的 ads 字段通常为空。
 * 但 LLM 解析出的 [AiIntentResult] 在服务端持久化，
 * [rematchDtoToUiMessage] 通过 [AdMatchingEngine.matchWithIntent] 重新执行匹配，
 * 从而恢复之前展示的广告卡片。
 *
 * ## 行为采集 (Day 10)
 * 用户发送对话消息时通过 [BehaviorCollector] 记录 SEARCH 行为，
 * 搜索关键词作为标签关联，用于用户画像计算与个性化推荐。
 */
class ChatViewModel(
    savedStateHandle: SavedStateHandle,
    private val chatBotService: ChatBotService,
    private val matchingEngine: AdMatchingEngine,
    private val repository: AdRepository,
    private val sessionManager: SessionManager,
    private val chatCache: ChatMemoryCache,
    private val behaviorCollector: BehaviorCollector
) : ViewModel() {

    /** 从导航参数中提取的初始广告 ID */
    private val initialAdId: String? = savedStateHandle.get<String>("adId")
        ?.takeIf { it.isNotEmpty() }

    /** 上轮搜索匹配到的广告上下文，下一轮消息发送时带入供 AI 参考 */
    private var lastMatchedAdContexts: List<AdContext>? = null

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        // 1. 优先检查内存缓存（应用启动时由 ChatPreloader 预热）
        if (chatCache.isWarm()) {
            _uiState.update {
                it.copy(
                    sessionId = chatCache.getSessionId(),
                    isServiceAvailable = true,
                    messages = chatCache.getMessages(),
                    isLoadingHistory = false
                )
            }
        } else {
            // 缓存未命中 → 尝试恢复历史 session
            val savedSessionId = sessionManager.getLastSessionId()
            if (savedSessionId != null) {
                loadHistory(savedSessionId)
            } else {
                createSession()
            }
        }

        // 2. 加载广告上下文
        if (initialAdId != null) {
            loadContextAd(initialAdId)
        }
    }

    // ═══════════════════════════════════════════════════════
    // 公开方法
    // ═══════════════════════════════════════════════════════

    /** 更新输入框文本 */
    fun updateInputText(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    /**
     * 发送消息
     *
     * 每次请求均携带当前 [AdContext]（如果有），
     * 确保微服务在 LLM prompt 中注入广告信息。
     *
     * @param content 用户输入的自然语言查询
     */
    fun sendMessage(content: String) {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return

        // 清空输入框
        _uiState.update { it.copy(inputText = "", isLoading = true, errorMessage = null) }

        // 添加用户消息气泡
        val userMsg = ChatUiMessage(
            id = UUID.randomUUID().toString(),
            role = ChatRole.USER,
            content = trimmed
        )
        _uiState.update { it.copy(messages = it.messages + userMsg) }
        chatCache.addMessage(userMsg)

        // Day 10: 采集搜索行为（对话式搜索）
        collectSearchBehavior(trimmed)

        // 根据服务可用性选择路径
        viewModelScope.launch {
            if (_uiState.value.isServiceAvailable && _uiState.value.sessionId != null) {
                sendToService(trimmed)
            } else {
                fallbackToOnlineSearch(trimmed)
            }
        }
    }

    /**
     * 清空对话
     *
     * 删除当前 session → 创建新 session → 清空消息列表。
     * 失败时仅清空本地消息（确保 UI 可用）。
     * 注意：不清除广告上下文——用户可能想在新对话中继续讨论同一条广告。
     */
    fun clearConversation() {
        chatCache.clear()
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                _uiState.value.sessionId?.let { sessionId ->
                    try {
                        chatBotService.deleteSession(sessionId)
                    } catch (_: Exception) {
                        // 静默失败——本地清空即可
                    }
                }
            }
            sessionManager.clearLastSessionId()
            _uiState.update { it.copy(messages = emptyList(), errorMessage = null) }
            createSession()
        }
    }

    /** 清除错误提示 */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    /**
     * 更新当前讨论的广告上下文
     *
     * 用于 ChatScreen 外部入口（如搜索结果"和AI讨论"），
     * 也可用于微服务返回推荐广告后让用户切换讨论对象。
     */
    fun setContextAd(adContext: AdContext) {
        _uiState.update { it.copy(currentContextAd = adContext) }
    }

    // ═══════════════════════════════════════════════════════
    // 私有方法
    // ═══════════════════════════════════════════════════════

    /**
     * 从微服务恢复对话历史
     *
     * 尝试通过已保存的 sessionId 加载最近消息。
     * 成功 → 复用 sessionId + 展示历史消息
     * 失败 → 走 createSession 创建新 session
     */
    private fun loadHistory(savedSessionId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingHistory = true) }
            try {
                val response = withContext(Dispatchers.IO) {
                    chatBotService.getHistory(savedSessionId, limit = 20)
                }
                if (response.isSuccessful && response.body()?.isSuccess == true) {
                    val history = response.body()!!.data!!

                    // 加载全量广告，用于 intent 重匹配（一次性获取，分摊到所有消息）
                    val allAds = withContext(Dispatchers.IO) {
                        repository.getAllAds().getOrNull()?.items ?: emptyList()
                    }

                    val restoredMessages = history.messages.map { dto ->
                        rematchDtoToUiMessage(dto, allAds)
                    }

                    // 写入内存缓存——下次进入无需网络请求
                    chatCache.setMessages(restoredMessages, savedSessionId)

                    _uiState.update {
                        it.copy(
                            sessionId = savedSessionId,
                            isServiceAvailable = true,
                            messages = restoredMessages,
                            isLoadingHistory = false
                        )
                    }
                } else {
                    // 历史恢复失败——创建新 session
                    _uiState.update { it.copy(isLoadingHistory = false) }
                    createSession()
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(isLoadingHistory = false) }
                createSession()
            }
        }
    }

    /**
     * 将服务端 DTO 转换为 UI 消息，通过 intent 恢复匹配广告
     *
     * ## 背景
     * 服务端 Mode B（客户端匹配）下，历史消息的 [ChatMessageDto.ads] 通常为空。
     * 但 LLM 解析出的 [AiIntentResult] 在服务端持久化，
     * 本地通过 [AdMatchingEngine.matchWithIntent] 重新执行匹配即可恢复广告。
     *
     * 此方法与 [ChatPreloader.rematchDto] 逻辑一致，保持两端行为统一。
     */
    private fun rematchDtoToUiMessage(
        dto: ChatMessageDto,
        allAds: List<AdItem>
    ): ChatUiMessage {
        val role = if (dto.role == "user") ChatRole.USER else ChatRole.ASSISTANT
        val ads = if (role == ChatRole.ASSISTANT && dto.intent != null) {
            matchingEngine.matchWithIntent(allAds, dto.intent).map { it.ad }
        } else {
            dto.ads ?: emptyList()
        }
        return ChatUiMessage(
            id = dto.messageId.ifEmpty { UUID.randomUUID().toString() },
            role = role,
            content = dto.content,
            ads = ads
        )
    }

    /**
     * 创建对话 session
     *
     * 在无历史可恢复或恢复失败时调用。
     * 成功后持久化 sessionId 到 SessionManager，方便下次进入时恢复。
     */
    private fun createSession() {
        viewModelScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    chatBotService.createSession(CreateSessionRequest())
                }
                if (response.isSuccessful && response.body()?.isSuccess == true) {
                    val session = response.body()!!.data!!
                    sessionManager.saveLastSessionId(session.sessionId)
                    _uiState.update {
                        it.copy(
                            sessionId = session.sessionId,
                            isServiceAvailable = true
                        )
                    }
                } else {
                    markServiceUnavailable()
                }
            } catch (_: Exception) {
                markServiceUnavailable()
            }
        }
    }

    /**
     * 加载广告上下文
     *
     * 从 Repository 获取完整 AdItem → 构建简化的 [AdContext] → 更新 UI 状态。
     */
    private fun loadContextAd(adId: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                repository.getAdById(adId)
            }
            result.onSuccess { ad ->
                val context = AdContext(
                    adId = ad.id,
                    title = ad.title,
                    description = ad.description,
                    advertiserName = ad.advertiserName,
                    tags = ad.tags.map { it.name },
                    aiSummary = ad.aiSummary
                )
                _uiState.update {
                    it.copy(
                        currentContextAd = context,
                        contextAdItem = ad
                    )
                }
            }
        }
    }

    /** 标记微服务不可用 */
    private fun markServiceUnavailable() {
        _uiState.update {
            it.copy(
                sessionId = null,
                isServiceAvailable = false
            )
        }
    }

    /**
     * 通过微服务发送消息（正常路径）
     *
     * 构建 [SendMessageRequest] 时携带当前 [AdContext]，
     * 微服务将其拼入 LLM prompt 以提供上下文感知的回复。
     */
    private suspend fun sendToService(query: String) {
        try {
            val sessionId = _uiState.value.sessionId ?: run {
                fallbackToOnlineSearch(query)
                return
            }

            // 构建请求——携带广告上下文 + 上轮搜索结果
            val previousAds = lastMatchedAdContexts
            lastMatchedAdContexts = null  // 发送后清除，避免重复发送
            val request = SendMessageRequest(
                content = query,
                contextAd = _uiState.value.currentContextAd,
                previousMatchedAds = previousAds
            )

            val response = withContext(Dispatchers.IO) {
                chatBotService.sendMessage(sessionId, request)
            }

            if (response.isSuccessful && response.body()?.isSuccess == true) {
                val data = response.body()!!.data!!
                val aiText = data.message?.content ?: ""
                val searchRequested = data.searchRequested

                // 仅当服务端判定为搜索意图时，才执行本地广告匹配
                val matchedAds = if (searchRequested) {
                    val intent = data.intent
                    // 加载全量广告，不做关键词预过滤——AI intent 已提供结构化搜索条件
                    val allAdsResult = repository.getAllAds()
                    val allAds = allAdsResult.getOrNull()?.items ?: emptyList()

                    val matchResults = if (intent != null) {
                        matchingEngine.matchWithIntent(allAds, intent)
                    } else {
                        matchingEngine.keywordSearch(allAds, query)
                    }
                    matchResults.map { it.ad }
                } else {
                    emptyList()
                }

                // 保存匹配结果摘要，下一轮消息时回传给 AI
                lastMatchedAdContexts = if (matchedAds.isNotEmpty()) {
                    matchedAds.map { ad ->
                        AdContext(
                            adId = ad.id,
                            title = ad.title,
                            description = ad.description,
                            advertiserName = ad.advertiserName,
                            tags = ad.tags.map { it.name },
                            aiSummary = ad.aiSummary
                        )
                    }
                } else {
                    null
                }

                val aiMsg = ChatUiMessage(
                    id = UUID.randomUUID().toString(),
                    role = ChatRole.ASSISTANT,
                    content = aiText + if (searchRequested && matchedAds.isEmpty()) "\n\n抱歉，没有找到匹配的广告，试试换个说法？" else "",
                    ads = matchedAds
                )

                _uiState.update {
                    it.copy(
                        messages = it.messages + aiMsg,
                        isLoading = false
                    )
                }
                chatCache.addMessage(aiMsg)
            } else {
                fallbackToOnlineSearch(query)
            }
        } catch (_: Exception) {
            fallbackToOnlineSearch(query)
        }
    }

    // ═══════════════════════════════════════════════════════
    // 行为采集 (Day 10)
    // ═══════════════════════════════════════════════════════

    /**
     * 采集对话式搜索行为
     *
     * 将用户查询关键词拆分为标签进行记录，
     * 委托给 [BehaviorCollector] 异步写入 Room。
     *
     * @param query 用户输入的自然语言查询
     */
    private fun collectSearchBehavior(query: String) {
        val keywords = query.trim().split(" ", "，", ",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
        behaviorCollector.collect(
            UserBehavior(
                id = UUID.randomUUID().toString(),
                adId = null,  // 对话搜索不关联特定广告
                behaviorType = BehaviorType.SEARCH,
                tags = keywords,  // 搜索关键词作为标签关联
                timestamp = System.currentTimeMillis()
            )
        )
    }

    /**
     * 降级为数据层关键词搜索
     *
     * 当微服务不可用时，通过 AdRepository 在数据源中搜索。
     */
    private suspend fun fallbackToOnlineSearch(query: String) {
        val result = withContext(Dispatchers.IO) {
            repository.searchAds(query, page = 1, pageSize = 50)
        }
        val matchedAds = result.getOrNull()?.items ?: emptyList()

        val content = buildString {
            append("搜索服务暂不可用，以下是在线匹配结果：")
            if (matchedAds.isEmpty()) {
                append("\n\n未找到匹配的广告，请尝试其他关键词。")
            }
        }

        val fallbackMsg = ChatUiMessage(
            id = UUID.randomUUID().toString(),
            role = ChatRole.ASSISTANT,
            content = content,
            ads = matchedAds,
            isFallback = true
        )

        _uiState.update {
            it.copy(
                messages = it.messages + fallbackMsg,
                isLoading = false
            )
        }
        chatCache.addMessage(fallbackMsg)
    }
}
