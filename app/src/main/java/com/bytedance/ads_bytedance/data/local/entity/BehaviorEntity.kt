package com.bytedance.ads_bytedance.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 用户行为记录 Room Entity
 *
 * 每次用户交互（点击/点赞/收藏/分享/标签点击/搜索）记录一条。
 * 用于 UserProfileEngine 计算用户兴趣画像。
 */
@Entity(tableName = "user_behaviors")
data class BehaviorEntity(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "ad_id")
    val adId: String?,

    @ColumnInfo(name = "behavior_type")
    val behaviorType: String,

    /** 标签名称列表 JSON（序列化 List<String>） */
    @ColumnInfo(name = "tags_json")
    val tagsJson: String = "[]",

    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis()
)
