package com.bytedance.ads_bytedance.common.network

import com.bytedance.ads_bytedance.BuildConfig
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

/**
 * 网络层全局配置工厂
 *
 * 统一管理 OkHttpClient 和 Retrofit 实例的创建与参数。
 * 所有网络请求都通过这里产出的单例 OkHttpClient 发送，
 * 确保连接池复用、超时策略、拦截器行为一致。
 */
object NetworkConfig {

    // ──────────────────────────────────────────────
    // 超时配置
    // ──────────────────────────────────────────────

    /** 连接超时：建立 TCP/TLS 连接的最大等待时间 */
    private const val CONNECT_TIMEOUT_SECONDS = 15L

    /** 读取超时：等待服务端响应数据的最大时间 */
    private const val READ_TIMEOUT_SECONDS = 15L

    /** 写入超时：发送请求体的最大时间 */
    private const val WRITE_TIMEOUT_SECONDS = 15L

    // ──────────────────────────────────────────────
    // 连接池配置
    // ──────────────────────────────────────────────

    /** 最大空闲连接数（超出后被关闭） */
    private const val MAX_IDLE_CONNECTIONS = 5

    /** 空闲连接保持存活时间 */
    private const val KEEP_ALIVE_DURATION_SECONDS = 30L

    // ──────────────────────────────────────────────
    // JSON 配置
    // ──────────────────────────────────────────────

    /** Retrofit 请求/响应的 Content-Type */
    private val JSON_MEDIA_TYPE = "application/json".toMediaType()

    /**
     * 全局 JSON 解析器实例。
     *
     * 配置说明：
     * - ignoreUnknownKeys = true：服务端新增字段不会导致解析崩溃（前向兼容）
     * - coerceInputValues = true：字段缺失时使用默认值而非 null
     * - isLenient = true：容忍 JSON 格式的轻微不规范
     */
    val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    // ──────────────────────────────────────────────
    // OkHttpClient 工厂
    // ──────────────────────────────────────────────

    /** GET 请求最大重试次数 */
    private const val MAX_RETRY_COUNT = 3

    /** 重试基础等待时间（毫秒） */
    private const val RETRY_BASE_DELAY_MS = 1000L

    /**
     * 创建全局共享的 OkHttpClient 单例。
     *
     * 特性：
     * - 连接池复用：减少握手开销，尤其对 AI API 的频繁调用有益
     * - 超时控制：避免因弱网环境下的请求卡死
     * - 重试拦截器：GET 请求网络异常时最多重试 3 次，指数退避（1s→2s→4s）
     * - 日志拦截器：Debug 模式下输出完整请求/响应，Release 不输出（保护用户隐私）
     */
    fun createOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder().apply {
            connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)

            // 网络层连接失败自动重试（OkHttp 内建）
            retryOnConnectionFailure(true)

            // 自定义重试拦截器：覆盖连接失败以外的场景（超时、EOF 等）
            addInterceptor(retryInterceptor())

            // Debug 模式下添加日志拦截器
            if (BuildConfig.DEBUG) {
                addInterceptor(
                    HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BODY
                    }
                )
            }
        }.build()
    }

    /**
     * 请求重试拦截器
     *
     * ## 重试策略
     * | 条件 | 行为 |
     * |------|------|
     * | GET 请求 + IOException | 指数退避重试（1s → 2s → 4s），最多 3 次 |
     * | POST/PUT/DELETE 请求 | 不重试（避免重复提交） |
     * | 非 IOException（如 HTTP 4xx/5xx） | 不重试（由上层业务处理） |
     *
     * ## 为什么仅重试 GET？
     * - GET 是幂等操作，重复执行无副作用
     * - POST 可能创建资源 → 重试导致重复提交
     * - 图片加载（Coil 通过 OkHttp 发送 GET 请求）是重试的主要受益场景
     */
    private fun retryInterceptor(): Interceptor {
        return Interceptor { chain ->
            val request = chain.request()
            var exception: java.io.IOException? = null

            // 仅对 GET 请求重试
            val maxAttempts = if (request.method == "GET") MAX_RETRY_COUNT else 1

            for (attempt in 1..maxAttempts) {
                try {
                    return@Interceptor chain.proceed(request)
                } catch (e: java.io.IOException) {
                    exception = e
                    if (attempt < maxAttempts) {
                        // 指数退避：1s → 2s → 4s
                        val delayMs = RETRY_BASE_DELAY_MS * (1L shl (attempt - 1))
                        Thread.sleep(delayMs)
                    }
                }
            }

            // 所有重试耗尽，抛出最后一次的异常
            throw exception!!
        }
    }

    // ──────────────────────────────────────────────
    // Retrofit 工厂
    // ──────────────────────────────────────────────

    /**
     * 创建 Retrofit 实例。
     *
     * @param client OkHttpClient 实例（全局共享）
     * @param baseUrl API 基础地址
     *
     * Mock 模式下 baseUrl 可填任意有效地址（请求被 MockDataSource 拦截），
     * 通常用 "http://localhost/" 占位。
     */
    fun createRetrofit(client: OkHttpClient, baseUrl: String): Retrofit {
        return Retrofit.Builder()
            .client(client)
            .baseUrl(baseUrl)
            .addConverterFactory(json.asConverterFactory(JSON_MEDIA_TYPE))
            .build()
    }

    // ──────────────────────────────────────────────
    // 便捷方法
    // ──────────────────────────────────────────────

    /**
     * 根据 BuildConfig.DATA_MODE 返回当前使用的 BaseUrl。
     * - mock 模式 → localhost 占位（请求实际由 MockInterceptor/MockDataSource 处理）
     * - remote 模式 → 从 BuildConfig 读取真实 API 地址
     */
    fun getBaseUrl(): String {
        return if (BuildConfig.DATA_MODE == "mock") {
            "http://localhost/"
        } else {
            // 远程模式：后续通过 BuildConfig 配置真实 API 地址
            "https://api.example.com/"
        }
    }

    // ──────────────────────────────────────────────
    // AI API Retrofit 工厂
    // ──────────────────────────────────────────────

    /**
     * AI API Base URL（从 BuildConfig 注入，默认 OpenAI 兼容地址）
     *
     * 通过 local.properties 的 `ai.api.base.url` 配置，
     * 支持 OpenAI、Qwen、DeepSeek 等兼容 API。
     */
    fun getAiBaseUrl(): String {
        val configured = BuildConfig.AI_API_BASE_URL
        return if (configured.isNotBlank()) configured else "https://api.openai.com"
    }

    /**
     * 获取 AI API Key（从 BuildConfig 注入）
     *
     * @return API Key 字符串；空字符串时 AI 功能降级
     */
    fun getAiApiKey(): String = BuildConfig.AI_API_KEY

    /**
     * 创建 AI API 专用的 Retrofit 实例。
     *
     * 与广告数据 API 共享 OkHttpClient（连接池复用），
     * 但使用独立的 BaseUrl 指向 AI 服务端点，
     * 并通过 [authInterceptor] 自动注入 Bearer Token。
     *
     * @param client OkHttpClient 实例（全局共享）
     */
    fun createAiRetrofit(client: OkHttpClient): Retrofit {
        // AI API 调用耗时较长，在 OkHttpClient 上额外增加超时
        val aiClient = client.newBuilder()
            .readTimeout(60, TimeUnit.SECONDS)   // AI 生成可能需要更长时间
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(authInterceptor())    // Bearer Token 认证
            .build()

        return Retrofit.Builder()
            .client(aiClient)
            .baseUrl(getAiBaseUrl())
            .addConverterFactory(json.asConverterFactory(JSON_MEDIA_TYPE))
            .build()
    }

    /**
     * AI API 认证拦截器
     *
     * 注入 `Authorization: Bearer <key>` 请求头。
     * API Key 来自 BuildConfig.AI_API_KEY（由 local.properties 注入）。
     *
     * 若未配置 API Key（空字符串），拦截器不注入头，
     * 请求仍会发出但会被 AI 服务端拒绝——由 [AiContentGenerator] 捕获异常后降级。
     */
    private fun authInterceptor(): Interceptor {
        return Interceptor { chain ->
            val apiKey = getAiApiKey()
            val request = if (apiKey.isNotBlank()) {
                chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .build()
            } else {
                chain.request()
            }
            chain.proceed(request)
        }
    }

    // ──────────────────────────────────────────────
    // Chat Bot 微服务 Retrofit 工厂 (Day 7)
    // ──────────────────────────────────────────────

    /**
     * Chat Bot 微服务 Base URL（从 BuildConfig 注入）
     *
     * 通过 local.properties 的 `chatbot.service.url` 配置，
     * 默认指向本地开发环境 `http://localhost:8080`。
     */
    fun getChatBotBaseUrl(): String = BuildConfig.CHATBOT_SERVICE_URL

    /**
     * 创建 Chat Bot 微服务专用 Retrofit 实例。
     *
     * 与广告 API / AI API 共享底层 OkHttpClient（连接池复用），
     * 但使用独立的 BaseUrl 指向微服务端点。
     * 通过 [userIdInterceptor] 自动注入 X-User-Id 请求头。
     *
     * @param client 全局共享 OkHttpClient
     * @param userId 设备级用户标识（来自 SessionManager）
     */
    fun createChatBotRetrofit(client: OkHttpClient, userId: String): Retrofit {
        val chatbotClient = client.newBuilder()
            .addInterceptor(userIdInterceptor(userId))
            .build()

        return Retrofit.Builder()
            .client(chatbotClient)
            .baseUrl(getChatBotBaseUrl())
            .addConverterFactory(json.asConverterFactory(JSON_MEDIA_TYPE))
            .build()
    }

    /**
     * 用户身份注入拦截器
     *
     * 为每个请求注入 `X-User-Id` 头。
     * 微服务据此隔离不同设备的 session 和对话历史。
     *
     * @param userId 设备级 UUID（SessionManager 生成）
     */
    private fun userIdInterceptor(userId: String): Interceptor {
        return Interceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("X-User-Id", userId)
                .addHeader("Content-Type", "application/json")
                .build()
            chain.proceed(request)
        }
    }
}
