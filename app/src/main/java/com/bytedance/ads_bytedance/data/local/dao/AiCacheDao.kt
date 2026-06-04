package com.bytedance.ads_bytedance.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.bytedance.ads_bytedance.data.local.entity.AiCacheEntity

@Dao
interface AiCacheDao {

    /** 插入或更新 AI 缓存 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(cache: AiCacheEntity)

    /** 按广告 ID 获取缓存（未过期） */
    @Query("SELECT * FROM ai_cache WHERE ad_id = :adId AND expires_at > :now LIMIT 1")
    suspend fun getValidCache(adId: String, now: Long = System.currentTimeMillis()): AiCacheEntity?

    /** 删除过期缓存 */
    @Query("DELETE FROM ai_cache WHERE expires_at <= :now")
    suspend fun deleteExpired(now: Long = System.currentTimeMillis())

    /** 清空 AI 缓存 */
    @Query("DELETE FROM ai_cache")
    suspend fun deleteAll()
}
