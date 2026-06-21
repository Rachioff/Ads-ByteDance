package com.bytedance.ads_bytedance.ai.chat.preload

import android.util.Log
import com.bytedance.ads_bytedance.BuildConfig
import com.bytedance.ads_bytedance.ai.api.ChatBotService
import com.bytedance.ads_bytedance.ai.chat.cache.ChatMemoryCache
import com.bytedance.ads_bytedance.ai.chat.model.ChatRole
import com.bytedance.ads_bytedance.ai.chat.model.ChatUiMessage
import com.bytedance.ads_bytedance.common.engine.AdMatchingEngine
import com.bytedance.ads_bytedance.common.util.SessionManager
import com.bytedance.ads_bytedance.data.model.AdItem
import com.bytedance.ads_bytedance.data.repository.AdRepository
import java.util.UUID

/**
 * 聊天历史预加载器
 *
 * ## 职责
 * 在应用启动时（[AdsApplication.onCreate]）后台预加载聊天历史，
 * 填充 [ChatMemoryCache]，使 ChatScreen 首次打开时无需网络请求即可展示消息。
 *
 * ## 广告恢复策略
 * 服务端 Mode B（客户端匹配）下，历史消息的 `ads` 字段通常为空。
 * 但服务端持久化了 LLM 解析出的 [AiIntentResult]，
 * 本类利用该 intent 在客户端重新执行 [AdMatchingEngine.matchWithIntent]，
 * 从而恢复之前展示的匹配广告。
 *
 * ## 线程与生命周期
 * - 由 [AdsApplication.applicationScope] 在 `Dispatchers.IO` 调用
 * - 失败静默——ViewModel 在用户进入 ChatScreen 时走正常 loadHistory 路径
 * - 幂等设计——`cache.isWarm()` 时立即返回，避免重复请求
 */
class ChatPreloader(
    private val chatBotService: ChatBotService,
    private val sessionManager: SessionManager,
    private val cache: ChatMemoryCache,
    private val matchingEngine: AdMatchingEngine,
    private val repository: AdRepository
) {

    /**
     * 预加载聊天历史
     *
     * 幂等操作：缓存已预热时立即返回。
     * 网络异常或服务不可用时静默跳过——ChatViewModel 将在用户进入时处理。
     */
    suspend fun preload() {
        // 幂等：已预热则跳过
        if (cache.isWarm()) {
            Log.d(TAG, "预加载跳过：缓存已预热")
            return
        }

        // 无历史 session 则跳过
        val sessionId = sessionManager.getLastSessionId()
        if (sessionId == null) {
            Log.d(TAG, "预加载跳过：无已保存的 sessionId")
            return
        }

        try {
            Log.d(TAG, "预加载开始: sessionId=$sessionId, URL=${BuildConfig.CHATBOT_SERVICE_URL}")
            // 1. 从服务端获取历史消息
            val response = chatBotService.getHistory(sessionId, limit = 20)
            if (!response.isSuccessful || response.body()?.isSuccess != true) {
                Log.w(TAG, "预加载失败: HTTP ${response.code()}, isSuccess=${response.body()?.isSuccess}")
                return
            }

            val history = response.body()!!.data ?: return

            // 2. 加载全量广告（用于 intent 重匹配）
            val allAds = repository.getAllAds().getOrNull()?.items ?: emptyList()

            // 3. DTO → UI 消息，带 intent 重匹配
            val messages = history.messages.map { dto -> rematchDto(dto, allAds) }

            // 4. 写入缓存
            cache.setMessages(messages, sessionId)
            Log.i(TAG, "预加载完成: ${messages.size} 条消息写入缓存")
        } catch (e: Exception) {
            Log.e(TAG, "预加载异常: ${e.javaClass.simpleName}: ${e.message}", e)
        }
    }

    companion object {
        private const val TAG = "ChatPreloader"
    }

    /**
     * 将服务端 DTO 转换为 UI 消息，必要时通过 intent 重匹配广告
     *
     * @param dto 服务端返回的单条消息
     * @param allAds 全量广告列表（用于匹配引擎）
     * @return UI 层消息，assistant 消息的 ads 已通过 intent 重匹配填充
     */
    private fun rematchDto(
        dto: com.bytedance.ads_bytedance.ai.chat.model.ChatMessageDto,
        allAds: List<AdItem>
    ): ChatUiMessage {
        val role = if (dto.role == "user") ChatRole.USER else ChatRole.ASSISTANT

        // 对含有 intent 的 assistant 消息执行本地广告匹配恢复
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
}
