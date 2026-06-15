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

    /**
     * 获取浏览历史：按行为类型筛选，去重 ad_id，按最近一次点击时间降序
     *
     * 用于统计页面"浏览记录"——每广告只出现一次，按最后点击时间排序。
     */
    @Query("""
        SELECT ad_id FROM user_behaviors
        WHERE behavior_type = 'CLICK' AND ad_id IS NOT NULL
        GROUP BY ad_id
        ORDER BY MAX(timestamp) DESC
    """)
    suspend fun getClickHistoryAdIds(): List<String>
}
