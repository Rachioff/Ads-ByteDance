package com.bytedance.ads_bytedance.behavior.profile

import com.bytedance.ads_bytedance.behavior.model.BehaviorType
import com.bytedance.ads_bytedance.behavior.model.UserProfile
import com.bytedance.ads_bytedance.data.local.dao.BehaviorDao
import com.bytedance.ads_bytedance.data.local.dao.UserInteractionDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * 用户画像引擎
 *
 * ## 职责
 * 从 Room 读取所有用户行为记录，按标签维度聚合权重得分，生成 [UserProfile]。
 *
 * ## 计算规则
 * ```
 * 标签偏好得分 = Σ(该标签下各行为次数 × 对应行为权重)
 * ```
 *
 * ## 计数规则（Day 11 修正 + Day 15 修正）
 * - **totalClicks**：去重统计独立浏览广告数（与 `BehaviorDao.getClickHistoryAdIds()` 的 GROUP BY 一致），
 *   同一广告多次点击只计 1 次
 * - **totalShares**：从行为事件表统计（每次 share 都记录，无取消操作）
 * - **totalLikes / totalCollects**：从互动状态表统计（`UserInteractionDao.countLiked()/countCollected()`）
 *   反映当前有效状态，取消点赞/收藏后计数会减少
 *
 * ### 示例
 * 用户对含"运动"标签的广告：
 * - 点击 3 次 → 3 × 1 = 3
 * - 点赞 2 次 → 2 × 2 = 4
 * - 收藏 1 次 → 1 × 3 = 3
 * → "运动"偏好得分 = 10
 *
 * ## 线程安全
 * 计算在 [Dispatchers.IO] 上执行（涉及 Room 查询 + JSON 解析）。
 *
 * @param behaviorDao Room BehaviorDao（Koin 注入）
 * @param interactionDao Room UserInteractionDao（Koin 注入，Day 11+）
 */
class UserProfileEngine(
    private val behaviorDao: BehaviorDao,
    private val interactionDao: UserInteractionDao
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 计算用户兴趣画像
     *
     * 遍历所有行为记录，按标签聚合加权得分。
     * 标签权重仅累加正向行为（LIKE/COLLECT/CLICK/SHARE/TAG_CLICK/SEARCH），
     * 取消点赞/收藏不会记录到行为表，因此不会虚增标签偏好得分。
     *
     * totalLikes / totalCollects 从 user_interactions 表读取当前状态，
     * 确保"取消"操作会反映在统计数字中。
     *
     * totalClicks 使用去重独立广告数（Set），与浏览记录列表的 GROUP BY 逻辑一致，
     * 避免"卡片显示8次，列表只有3条"的数据不一致问题。
     *
     * @return 计算完成的 [UserProfile]，无行为时返回空画像
     */
    suspend fun computeProfile(): UserProfile = withContext(Dispatchers.IO) {
        val allBehaviors = behaviorDao.getAllOnce()

        // ── 按标签聚合权重 ──
        val tagWeightMap = mutableMapOf<String, Double>()

        // ── 行为计数器 ──
        // totalClicks：去重统计独立浏览广告数（与 BehaviorDao.getClickHistoryAdIds() GROUP BY 一致）
        // totalShares：分享事件次数（无取消操作）
        val clickedAdIds = mutableSetOf<String>()
        var totalShares = 0

        allBehaviors.forEach { entity ->
            val behaviorType = try {
                BehaviorType.valueOf(entity.behaviorType)
            } catch (_: IllegalArgumentException) {
                return@forEach  // 跳过无法解析的行为类型
            }

            val weight = behaviorType.weight.toDouble()

            // 解析标签 JSON
            val tags = try {
                json.decodeFromJsonElement<List<String>>(
                    json.parseToJsonElement(entity.tagsJson)
                )
            } catch (_: Exception) {
                emptyList()
            }

            // 累加每个标签的权重
            tags.forEach { tag ->
                tagWeightMap[tag] = (tagWeightMap[tag] ?: 0.0) + weight
            }

            // 统计行为计数
            when (behaviorType) {
                BehaviorType.CLICK -> {
                    entity.adId?.let { clickedAdIds.add(it) }
                }
                BehaviorType.SHARE -> totalShares++
                else -> { /* LIKE/COLLECT/TAG_CLICK/SEARCH 不在事件表计数 */ }
            }
        }
        val totalClicks = clickedAdIds.size

        // Day 11：总点赞/总收藏从互动状态表读取（反映当前有效状态）
        val totalLikes = try {
            interactionDao.countLiked()
        } catch (_: Exception) {
            0
        }
        val totalCollects = try {
            interactionDao.countCollected()
        } catch (_: Exception) {
            0
        }

        UserProfile(
            tagWeights = tagWeightMap,
            totalClicks = totalClicks,
            totalLikes = totalLikes,
            totalCollects = totalCollects,
            totalShares = totalShares,
            lastUpdateTime = System.currentTimeMillis()
        )
    }
}
