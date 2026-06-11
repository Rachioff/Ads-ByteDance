package com.bytedance.ads_bytedance.ai.chat.cache

import com.bytedance.ads_bytedance.ai.chat.model.ChatUiMessage

/**
 * 对话历史内存缓存（纯内存，不持久化）
 *
 * ## 设计意图
 * 解决 ChatScreen 每次进入都重新从网络加载历史记录的问题。
 * 聊天消息在服务端持久化的同时，在此处以内存形式缓存一份，
 * ChatViewModel 创建时优先读取缓存，避免不必要的网络请求。
 *
 * ## 生命周期
 * - 应用启动 → ChatPreloader 预加载填充 → 缓存预热
 * - ChatViewModel.init → 检查 isWarm() → 命中则直接展示
 * - sendMessage 成功 → 同步追加新消息到缓存
 * - clearConversation → 清空缓存并标记 isLoaded=false
 * - 应用进程终止 → 缓存自动清除（不持久化）
 *
 * ## 线程安全
 * 所有可变操作使用 @Synchronized 保护，
 * 确保 IO 预加载线程与主线程 UI 读取之间不会出现竞态。
 * getMessages() 返回防御性拷贝，避免外部修改内部列表。
 *
 * ## 依赖
 * 零业务依赖——仅持有 ChatUiMessage 列表和 sessionId，
 * 不依赖任何 Service/Repository/Engine。
 */
class ChatMemoryCache {

    /** 消息列表（@Volatile 保证可见性，@Synchronized 保证原子性） */
    @Volatile
    private var _messages: List<ChatUiMessage> = emptyList()

    /** 当前 session ID */
    @Volatile
    private var _sessionId: String? = null

    /** 缓存是否已加载（区分"有数据"和"已清空/未初始化"） */
    @Volatile
    private var _isLoaded: Boolean = false

    /**
     * 缓存是否已预热
     *
     * true = 已通过 preload 或 loadHistory 填充，可直接使用。
     * false = 未初始化或已通过 clear() 重置。
     */
    @Synchronized
    fun isWarm(): Boolean = _isLoaded

    /** 获取当前 session ID（无防御性拷贝——String 不可变） */
    @Synchronized
    fun getSessionId(): String? = _sessionId

    /**
     * 获取消息列表的防御性拷贝
     *
     * 调用方获得的列表可安全遍历，不受后续缓存更新影响。
     */
    @Synchronized
    fun getMessages(): List<ChatUiMessage> = _messages.toList()

    /**
     * 原子替换消息列表和 sessionId
     *
     * 用于 loadHistory / preload 完成后的整批写入。
     * 调用后 [isWarm] 返回 true。
     *
     * @param messages 完整的消息列表
     * @param sessionId 当前 session ID
     */
    @Synchronized
    fun setMessages(messages: List<ChatUiMessage>, sessionId: String?) {
        _messages = messages.toList()
        _sessionId = sessionId
        _isLoaded = true
    }

    /**
     * 原子追加一条消息
     *
     * 用于 sendMessage 成功后保持缓存与 UI 状态同步。
     *
     * @param message 要追加的单条消息（用户或 AI 气泡）
     */
    @Synchronized
    fun addMessage(message: ChatUiMessage) {
        _messages = _messages + message
    }

    /**
     * 清空缓存
     *
     * 用于 clearConversation 时防止下次进入读到已删除的旧数据。
     * 将 [isWarm] 置为 false，确保下次 ChatViewModel.init 走冷启动路径。
     */
    @Synchronized
    fun clear() {
        _messages = emptyList()
        _sessionId = null
        _isLoaded = false
    }
}
