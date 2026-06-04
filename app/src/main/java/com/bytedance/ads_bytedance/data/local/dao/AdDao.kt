package com.bytedance.ads_bytedance.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.bytedance.ads_bytedance.data.local.entity.AdEntity
import com.bytedance.ads_bytedance.data.model.Channel
import kotlinx.coroutines.flow.Flow

@Dao
interface AdDao {

    /** 批量插入或替换广告数据 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(ads: List<AdEntity>)

    /** 按频道获取所有广告（Flow 响应式观察） */
    @Query("SELECT * FROM ads WHERE channel = :channel ORDER BY updated_at DESC")
    fun getByChannel(channel: String): Flow<List<AdEntity>>

    /** 按 ID 获取单条广告 */
    @Query("SELECT * FROM ads WHERE id = :adId LIMIT 1")
    suspend fun getById(adId: String): AdEntity?

    /** 按频道删除所有广告 */
    @Query("DELETE FROM ads WHERE channel = :channel")
    suspend fun deleteByChannel(channel: String)

    /** 清空广告表 */
    @Query("DELETE FROM ads")
    suspend fun deleteAll()
}
