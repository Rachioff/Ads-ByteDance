package com.bytedance.ads_bytedance.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.bytedance.ads_bytedance.data.local.dao.AdDao
import com.bytedance.ads_bytedance.data.local.dao.AiCacheDao
import com.bytedance.ads_bytedance.data.local.dao.BehaviorDao
import com.bytedance.ads_bytedance.data.local.entity.AdEntity
import com.bytedance.ads_bytedance.data.local.entity.AiCacheEntity
import com.bytedance.ads_bytedance.data.local.entity.BehaviorEntity

/**
 * Room 数据库
 *
 * 三张表：
 * - ads：广告数据缓存
 * - user_behaviors：用户行为记录
 * - ai_cache：AI 生成结果缓存（摘要 + 标签）
 */
@Database(
    entities = [
        AdEntity::class,
        BehaviorEntity::class,
        AiCacheEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun adDao(): AdDao
    abstract fun behaviorDao(): BehaviorDao
    abstract fun aiCacheDao(): AiCacheDao
}
