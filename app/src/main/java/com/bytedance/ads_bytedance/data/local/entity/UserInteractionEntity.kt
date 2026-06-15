package com.bytedance.ads_bytedance.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 用户互动状态 Room Entity（模拟服务端状态管理）
 *
 * 以广告 ID 为主键，存储用户对该广告的点赞/收藏当前状态。
 * 用于统计页面的"总点赞"/"总收藏"计数，以及查看已点赞/已收藏广告列表。
 *
 * 与 [BehaviorEntity] 的区别：
 * - BehaviorEntity：行为事件流（用于画像计算，仅记录正向事件）
 * - UserInteractionEntity：当前状态快照（用于统计计数，upsert 更新）
 */
@Entity(tableName = "user_interactions")
data class UserInteractionEntity(
    @PrimaryKey
    @ColumnInfo(name = "ad_id")
    val adId: String,

    @ColumnInfo(name = "is_liked")
    val isLiked: Boolean = false,

    @ColumnInfo(name = "is_collected")
    val isCollected: Boolean = false,

    /** 点赞时间（isLiked=true 时记录，取消时不更新） */
    @ColumnInfo(name = "liked_at")
    val likedAt: Long? = null,

    /** 收藏时间（isCollected=true 时记录，取消时不更新） */
    @ColumnInfo(name = "collected_at")
    val collectedAt: Long? = null,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
