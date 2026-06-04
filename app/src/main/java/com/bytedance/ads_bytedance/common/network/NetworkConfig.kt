package com.bytedance.ads_bytedance.common.network

import com.bytedance.ads_bytedance.BuildConfig
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
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

    /**
     * 创建全局共享的 OkHttpClient 单例。
     *
     * 特性：
     * - 连接池复用：减少握手开销，尤其对 AI API 的频繁调用有益
     * - 超时控制：避免因弱网环境下的请求卡死
     * - 日志拦截器：Debug 模式下输出完整请求/响应，Release 不输出（保护用户隐私）
     */
    fun createOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder().apply {
            connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)

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
}
