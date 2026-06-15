package com.bytedance.ads_bytedance.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.bytedance.ads_bytedance.data.local.dao.AdDao
import com.bytedance.ads_bytedance.data.local.dao.AiCacheDao
import com.bytedance.ads_bytedance.data.local.dao.BehaviorDao
import com.bytedance.ads_bytedance.data.local.dao.UserInteractionDao
import com.bytedance.ads_bytedance.data.local.entity.AdEntity
import com.bytedance.ads_bytedance.data.local.entity.AiCacheEntity
import com.bytedance.ads_bytedance.data.local.entity.BehaviorEntity
import com.bytedance.ads_bytedance.data.local.entity.UserInteractionEntity

/**
 * Room 数据库
 *
 * 四张表：
 * - ads：广告数据缓存
 * - user_behaviors：用户行为事件记录
 * - user_interactions：用户互动状态（点赞/收藏当前值，模拟服务端）
 * - ai_cache：AI 生成结果缓存（摘要 + 标签）
 */
@Database(
    entities = [
        AdEntity::class,
        BehaviorEntity::class,
        UserInteractionEntity::class,
        AiCacheEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun adDao(): AdDao
    abstract fun behaviorDao(): BehaviorDao
    abstract fun userInteractionDao(): UserInteractionDao
    abstract fun aiCacheDao(): AiCacheDao

    companion object {
        /** v1 → v2：新增 user_interactions 表 */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `user_interactions` (
                        `ad_id` TEXT NOT NULL,
                        `is_liked` INTEGER NOT NULL DEFAULT 0,
                        `is_collected` INTEGER NOT NULL DEFAULT 0,
                        `liked_at` INTEGER,
                        `collected_at` INTEGER,
                        `updated_at` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`ad_id`)
                    )
                """.trimIndent())
            }
        }
    }
}
