package com.bytedance.ads_bytedance.ai.api

import com.bytedance.ads_bytedance.ai.model.AiRequest
import com.bytedance.ads_bytedance.ai.model.AiResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * AI 大模型 API 接口（OpenAI 兼容协议）
 *
 * ## 兼容性
 * 遵循 OpenAI Chat Completions API 规范，可直接对接：
 * - OpenAI（api.openai.com）
 * - Qwen / 通义千问（dashscope.aliyuncs.com/compatible-mode）
 * - DeepSeek（api.deepseek.com）
 * - 其他 OpenAI 兼容代理
 *
 * ## 认证
 * 通过独立的 OkHttp Interceptor 注入 `Authorization: Bearer <key>` 头，
 * API Key 从 BuildConfig.AI_API_KEY 读取（由 local.properties 注入，不提交 Git）。
 *
 * @see AiRequest
 * @see AiResponse
 */
interface AiApiService {

    /**
     * Chat Completions（生成摘要 + 智能标签）
     *
     * 低 temperature（0.3）确保输出稳定可解析。
     * 通过 System Prompt 约束输出为结构化 JSON。
     *
     * @param request 包含 model、messages、temperature、max_tokens 的请求体
     * @return OpenAI 兼容的 Chat Completions 响应
     */
    @POST("v1/chat/completions")
    suspend fun chatCompletions(
        @Body request: AiRequest
    ): Response<AiResponse>
}
