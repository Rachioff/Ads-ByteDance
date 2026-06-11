package com.bytedance.ads_bytedance.common.util

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

/**
 * 设备级用户身份管理器
 *
 * ## 设计原则
 * 本项目不做登录注册系统，使用设备级 UUID 标识用户。
 * UUID 在首次启动时生成并持久化到 SharedPreferences，
 * 通过 `X-User-Id` 请求头传递给 Chat Bot 微服务，
 * 用于服务端隔离不同设备的 session 和对话历史。
 *
 * ## 使用方式
 * ```
 * val userId = sessionManager.getUserId()
 * // 注入到 OkHttp Interceptor → X-User-Id 头
 * ```
 *
 * ## 持久化保证
 * - SharedPreferences 存储，进程重启后仍保持同一 UUID
 * - 卸载应用后 UUID 丢失（符合预期——新安装 = 新设备）
 */
class SessionManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 获取或生成设备唯一标识 */
    val userId: String by lazy {
        prefs.getString(KEY_USER_ID, null) ?: run {
            val newId = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_USER_ID, newId).apply()
            newId
        }
    }

    /**
     * 重新生成用户 ID（仅供调试使用）
     *
     * 注意：这会丢失所有历史 session 数据（微服务端按旧 UUID 存储）。
     */
    fun regenerateUserId(): String {
        val newId = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_USER_ID, newId).apply()
        return newId
    }

    /**
     * 保存上次使用的对话 session ID
     *
     * ChatViewModel 在成功创建 session 后调用，
     * 下次进入 ChatScreen 时恢复对话历史。
     */
    fun saveLastSessionId(sessionId: String) {
        prefs.edit().putString(KEY_LAST_SESSION_ID, sessionId).apply()
    }

    /**
     * 获取上次使用的对话 session ID
     *
     * @return 上次 session ID，首次使用时返回 null
     */
    fun getLastSessionId(): String? =
        prefs.getString(KEY_LAST_SESSION_ID, null)

    /**
     * 清除暂存的 session ID（用户主动清空对话时调用）
     */
    fun clearLastSessionId() {
        prefs.edit().remove(KEY_LAST_SESSION_ID).apply()
    }

    companion object {
        private const val PREFS_NAME = "ads_bytedance_session"
        private const val KEY_USER_ID = "device_user_id"
        private const val KEY_LAST_SESSION_ID = "last_chat_session_id"
    }
}
