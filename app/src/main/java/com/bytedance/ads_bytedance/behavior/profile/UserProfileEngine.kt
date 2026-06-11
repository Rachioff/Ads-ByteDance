package com.bytedance.ads_bytedance.behavior.profile

import com.bytedance.ads_bytedance.behavior.model.BehaviorType
import com.bytedance.ads_bytedance.behavior.model.UserProfile
import com.bytedance.ads_bytedance.data.local.dao.BehaviorDao
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
 * ## 使用方式
 * ```
 * val engine = UserProfileEngine(behaviorDao)
 * val profile = engine.computeProfile()
 * // profile.tagWeights: Map<"运动"→10.0, "学生党"→5.0, ...>
 * ```
 *
 * @param behaviorDao Room BehaviorDao（Koin 注入）
 */
class UserProfileEngine(
    private val behaviorDao: BehaviorDao
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 计算用户兴趣画像
     *
     * 遍历所有行为记录，按标签聚合加权得分，同时统计各行为类型的总次数。
     * 空行为数据（新用户）返回空画像（所有 tagWeights 为空 map，计数为 0）。
     *
     * @return 计算完成的 [UserProfile]，无行为时返回空画像
     */
    suspend fun computeProfile(): UserProfile = withContext(Dispatchers.IO) {
        val allBehaviors = behaviorDao.getAllOnce()

        if (allBehaviors.isEmpty()) {
            return@withContext UserProfile(
                tagWeights = emptyMap(),
                totalClicks = 0,
                totalLikes = 0,
                totalCollects = 0,
                totalShares = 0,
                lastUpdateTime = System.currentTimeMillis()
            )
        }

        // ── 按标签聚合权重 ──
        val tagWeightMap = mutableMapOf<String, Double>()

        // ── 行为计数器 ──
        var totalClicks = 0
        var totalLikes = 0
        var totalCollects = 0
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
                BehaviorType.CLICK -> totalClicks++
                BehaviorType.LIKE -> totalLikes++
                BehaviorType.COLLECT -> totalCollects++
                BehaviorType.SHARE -> totalShares++
                else -> { /* TAG_CLICK / SEARCH 不计入总数统计 */ }
            }
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
