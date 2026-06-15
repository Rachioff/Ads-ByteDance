package com.bytedance.ads_bytedance.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.bytedance.ads_bytedance.data.local.entity.UserInteractionEntity

@Dao
interface UserInteractionDao {

    /** Upsert 一条互动记录（存在则覆盖） */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(interaction: UserInteractionEntity)

    /** 按广告 ID 查询互动状态 */
    @Query("SELECT * FROM user_interactions WHERE ad_id = :adId")
    suspend fun getByAdId(adId: String): UserInteractionEntity?

    /** 统计当前点赞总数 */
    @Query("SELECT COUNT(*) FROM user_interactions WHERE is_liked = 1")
    suspend fun countLiked(): Int

    /** 统计当前收藏总数 */
    @Query("SELECT COUNT(*) FROM user_interactions WHERE is_collected = 1")
    suspend fun countCollected(): Int

    /** 获取所有已点赞广告的 adId 列表（按点赞时间降序） */
    @Query("SELECT ad_id FROM user_interactions WHERE is_liked = 1 ORDER BY liked_at DESC")
    suspend fun getLikedAdIds(): List<String>

    /** 获取所有已收藏广告的 adId 列表（按收藏时间降序） */
    @Query("SELECT ad_id FROM user_interactions WHERE is_collected = 1 ORDER BY collected_at DESC")
    suspend fun getCollectedAdIds(): List<String>
}
