package com.bytedance.ads_bytedance.behavior.recommend

import com.bytedance.ads_bytedance.behavior.profile.UserProfileEngine
import com.bytedance.ads_bytedance.data.model.AdItem
import com.bytedance.ads_bytedance.data.model.Channel

/**
 * 个性化推荐排序器
 *
 * ## 职责
 * 根据用户画像（[UserProfile]）对广告列表进行个性化排序。
 *
 * ## 排序策略
 * | 频道       | 有行为数据时                     | 无行为数据时（新用户）            |
 * |-----------|--------------------------------|----------------------------------|
 * | **精选**   | 按画像匹配度降序（个性化推荐）     | 按曝光量降序（热度排序）           |
 * | **电商**   | 按曝光量降序（热度排序）           | 同左                              |
 * | **本地**   | 按曝光量降序（热度排序）           | 同左                              |
 *
 * ## 匹配度计算
 * ```
 * 广告匹配度 = Σ(广告每个标签在用户画像中的权重得分)
 * ```
 *
 * 例如：广告标签 ["运动"→10, "学生党"→5, "性价比"→2] → 匹配度 = 17
 *
 * ## 使用方式
 * ```
 * val ranker = RecommendRanker(profileEngine)
 * val ranked = ranker.rank(ads, Channel.FEATURED)
 * ```
 *
 * @param profileEngine 用户画像引擎（Koin 注入）
 */
class RecommendRanker(
    private val profileEngine: UserProfileEngine
) {
    /**
     * 个性化排序广告列表
     *
     * 精选频道使用标签匹配度排序；其他频道使用曝光量排序。
     * 新用户（无行为数据）在所有频道均使用曝光量排序。
     *
     * @param ads 原始广告列表
     * @param channel 目标频道
     * @return 按对应策略排序后的广告列表（不修改原列表）
     */
    suspend fun rank(ads: List<AdItem>, channel: Channel): List<AdItem> {
        if (ads.isEmpty()) return ads

        val profile = profileEngine.computeProfile()

        return when {
            // 精选频道 + 有画像数据 → 个性化排序
            channel == Channel.FEATURED && profile.tagWeights.isNotEmpty() -> {
                rankByProfileMatch(ads, profile.tagWeights)
            }
            // 其他情况 → 按热度排序（曝光量降序）
            else -> {
                ads.sortedByDescending { it.exposureCount }
            }
        }
    }

    /**
     * 按用户画像标签权重对广告排序
     *
     * 匹配度 = 广告所有标签在画像中的权重之和。
     * 匹配度相同时按曝光量降序（热门广告优先展示）。
     *
     * @param ads 原始广告列表
     * @param tagWeights 标签 → 偏好权重 Map
     * @return 按匹配度降序排列（同分按曝光量降序）
     */
    private fun rankByProfileMatch(
        ads: List<AdItem>,
        tagWeights: Map<String, Double>
    ): List<AdItem> {
        return ads.map { ad ->
            val matchScore = ad.tags.sumOf { tag ->
                tagWeights[tag.name] ?: 0.0
            }
            ad to matchScore
        }
        .sortedWith(
            compareByDescending<Pair<AdItem, Double>> { it.second }
                .thenByDescending { it.first.exposureCount }
        )
        .map { it.first }
    }
}
