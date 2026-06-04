package com.bytedance.ads_bytedance.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.bytedance.ads_bytedance.data.local.entity.BehaviorEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BehaviorDao {

    /** 插入单条行为记录 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(behavior: BehaviorEntity)

    /** 获取所有行为记录（Flow 响应式） */
    @Query("SELECT * FROM user_behaviors ORDER BY timestamp DESC")
    fun getAll(): Flow<List<BehaviorEntity>>

    /** 一次性获取所有行为记录 */
    @Query("SELECT * FROM user_behaviors ORDER BY timestamp DESC")
    suspend fun getAllOnce(): List<BehaviorEntity>

    /** 按广告 ID 获取行为记录 */
    @Query("SELECT * FROM user_behaviors WHERE ad_id = :adId ORDER BY timestamp DESC")
    suspend fun getByAdId(adId: String): List<BehaviorEntity>

    /** 清空行为记录 */
    @Query("DELETE FROM user_behaviors")
    suspend fun deleteAll()
}
