package com.bytedance.ads_bytedance.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * AI 结果磁盘缓存 Entity
 *
 * 缓存大模型生成的广告摘要和标签，避免重复调用 API。
 * 默认 7 天过期，由 AiCacheManager 管理过期清理。
 */
@Entity(tableName = "ai_cache")
data class AiCacheEntity(
    @PrimaryKey
    @ColumnInfo(name = "ad_id")
    val adId: String,

    @ColumnInfo(name = "summary")
    val summary: String,

    /** 标签列表 JSON（序列化 List<Tag>） */
    @ColumnInfo(name = "tags_json")
    val tagsJson: String = "[]",

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    /** 过期时间戳（毫秒），默认 7 天 */
    @ColumnInfo(name = "expires_at")
    val expiresAt: Long = System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000L
)
