package com.bytedance.ads_bytedance.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.bytedance.ads_bytedance.data.model.AdType
import com.bytedance.ads_bytedance.data.model.Channel

/**
 * 广告数据 Room Entity
 *
 * 将 AdItem 密封类展平为单表存储。
 * 差异字段（如 coverImageUrl / thumbnailUrl / videoUrl）使用可空列，
 * 根据 adType 决定哪些列有值。
 */
@Entity(tableName = "ads")
data class AdEntity(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "channel")
    val channel: String,

    @ColumnInfo(name = "ad_type")
    val adType: String,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "description")
    val description: String,

    @ColumnInfo(name = "advertiser_name")
    val advertiserName: String,

    @ColumnInfo(name = "advertiser_avatar")
    val advertiserAvatar: String,

    /** 大图 / 视频封面 */
    @ColumnInfo(name = "cover_image_url")
    val coverImageUrl: String? = null,

    /** 小图缩略图 */
    @ColumnInfo(name = "thumbnail_url")
    val thumbnailUrl: String? = null,

    /** 图片位置（小图卡片） */
    @ColumnInfo(name = "image_position")
    val imagePosition: String? = null,

    /** 视频 URL */
    @ColumnInfo(name = "video_url")
    val videoUrl: String? = null,

    /** 标签列表 JSON（序列化 List<Tag>） */
    @ColumnInfo(name = "tags_json")
    val tagsJson: String = "[]",

    @ColumnInfo(name = "ai_summary")
    val aiSummary: String? = null,

    @ColumnInfo(name = "like_count")
    val likeCount: Int = 0,

    @ColumnInfo(name = "collect_count")
    val collectCount: Int = 0,

    @ColumnInfo(name = "share_count")
    val shareCount: Int = 0,

    @ColumnInfo(name = "exposure_count")
    val exposureCount: Int = 0,

    @ColumnInfo(name = "click_count")
    val clickCount: Int = 0,

    @ColumnInfo(name = "is_liked")
    val isLiked: Boolean = false,

    @ColumnInfo(name = "is_collected")
    val isCollected: Boolean = false,

    /** 数据更新时间戳 */
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
