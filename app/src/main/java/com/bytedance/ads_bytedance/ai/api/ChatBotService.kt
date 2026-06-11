package com.bytedance.ads_bytedance.ai.api

import com.bytedance.ads_bytedance.ai.chat.model.ChatApiResponse
import com.bytedance.ads_bytedance.ai.chat.model.CreateSessionRequest
import com.bytedance.ads_bytedance.ai.chat.model.MessageHistoryData
import com.bytedance.ads_bytedance.ai.chat.model.SendMessageData
import com.bytedance.ads_bytedance.ai.chat.model.SendMessageRequest
import com.bytedance.ads_bytedance.ai.chat.model.SessionInfo
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Chat Bot 微服务 REST API 接口
 *
 * ## 架构定位
 * 对接本地 Chat Bot 微服务（默认 `localhost:8080`），
 * 用于对话式搜索功能——管理 session、收发消息、获取历史。
 *
 * ## 端点总览
 * ```
 * POST   /api/sessions                  → 创建新对话 session
 * POST   /api/sessions/{id}/messages    → 发送消息（核心）
 * GET    /api/sessions/{id}/messages    → 获取对话历史
 * DELETE /api/sessions/{id}             → 删除会话
 * ```
 *
 * ## 认证
 * 通过 OkHttp Interceptor 注入 `X-User-Id` 请求头，
 * 值由 [com.bytedance.ads_bytedance.common.util.SessionManager.getUserId] 提供。
 *
 * ## 降级
 * 微服务不可用时（连接失败 / 503 / 超时），
 * [com.bytedance.ads_bytedance.ai.chat.viewmodel.ChatViewModel] 捕获异常
 * 并回退到数据层 [com.bytedance.ads_bytedance.data.repository.AdRepository.searchAds] 进行在线匹配。
 *
 * @see chatbot-api.md 完整 API 文档
 */
interface ChatBotService {

    /**
     * 创建新对话 session
     *
     * 客户端进入搜索页面时调用。
     * 成功后保存返回的 [SessionInfo.sessionId] 用于后续发消息。
     *
     * @param request 可选的 title 字段（默认 "新对话"）
     * @return 201 Created → ChatApiResponse<SessionInfo>
     */
    @POST("api/sessions")
    suspend fun createSession(
        @Body request: CreateSessionRequest = CreateSessionRequest()
    ): Response<ChatApiResponse<SessionInfo>>

    /**
     * 发送消息（核心接口）
     *
     * 用户输入自然语言查询 → 服务端解析意图 → 返回 AI 回复 + 匹配的广告。
     *
     * @param sessionId 当前对话 session ID
     * @param request { content: "用户输入的自然语言" }
     * @return 200 OK → ChatApiResponse<SendMessageData>
     *         { message: { role, content }, ads: [...], intent: {...} }
     */
    @POST("api/sessions/{sessionId}/messages")
    suspend fun sendMessage(
        @Path("sessionId") sessionId: String,
        @Body request: SendMessageRequest
    ): Response<ChatApiResponse<SendMessageData>>

    /**
     * 获取对话历史
     *
     * @param sessionId 会话 ID
     * @param limit 返回条数（默认 20）
     * @param before 时间戳（毫秒），获取此时间之前的消息（分页）
     * @return 200 OK → ChatApiResponse<MessageHistoryData>
     */
    @GET("api/sessions/{sessionId}/messages")
    suspend fun getHistory(
        @Path("sessionId") sessionId: String,
        @Query("limit") limit: Int = 20,
        @Query("before") before: Long? = null
    ): Response<ChatApiResponse<MessageHistoryData>>

    /**
     * 删除会话（清空对话）
     *
     * "清空对话"按钮触发：先调用此接口 → 再调用 [createSession] 创建新 session。
     *
     * @param sessionId 要删除的会话 ID
     * @return 200 OK
     */
    @DELETE("api/sessions/{sessionId}")
    suspend fun deleteSession(
        @Path("sessionId") sessionId: String
    ): Response<ChatApiResponse<Unit>>
}
