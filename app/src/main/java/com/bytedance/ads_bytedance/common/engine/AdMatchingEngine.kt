package com.bytedance.ads_bytedance.common.engine

import com.bytedance.ads_bytedance.ai.model.AiIntentResult
import com.bytedance.ads_bytedance.data.model.AdItem
import com.bytedance.ads_bytedance.data.model.SearchCriteria
import com.bytedance.ads_bytedance.data.model.Tag

/**
 * 广告匹配结果（评分 + 广告对象）
 *
 * @param ad 匹配到的广告
 * @param score 匹配度得分（越高越相关）
 */
data class AdMatchResult(
    val ad: AdItem,
    val score: Double
)

/**
 * 本地广告匹配引擎
 *
 * ## 职责
 * 在客户端本地执行广告匹配，不依赖服务端。
 * 支持两种匹配路径：
 * 1. **AI 意图匹配**：微服务返回 [AiIntentResult] → 转换 → 调用 [match]
 * 2. **关键词降级**：微服务不可用时直接对 title/description 做子串匹配
 *
 * ## 评分规则
 * ```
 * 标签精确匹配        → +3.0 / 个
 * 关键词命中标题        → +2.0 / 个
 * 关键词命中描述        → +1.0 / 个
 * 受众标签匹配          → +2.0 / 个
 * ```
 *
 * 结果按得分降序排列，得分为 0 的广告不会被返回。
 *
 * ## 使用方式
 * ```
 * val engine = AdMatchingEngine()
 * val results = engine.matchWithIntent(allAds, aiIntent)
 * ```
 */
class AdMatchingEngine {

    /**
     * 基于结构化搜索条件匹配广告
     *
     * @param ads 候选广告列表（所有已加载的广告）
     * @param criteria 结构化搜索条件
     * @return 按匹配度降序排列的结果列表（仅包含 score > 0 的广告）
     */
    fun match(ads: List<AdItem>, criteria: SearchCriteria): List<AdMatchResult> {
        return ads.map { ad -> AdMatchResult(ad, calculateScore(ad, criteria)) }
            .filter { it.score > 0 }
            .sortedByDescending { it.score }
    }

    /**
     * 基于 AI 意图解析结果匹配广告
     *
     * 将 [AiIntentResult] 转换为 [SearchCriteria] 后调用 [match]。
     *
     * @param ads 候选广告列表
     * @param intent AI 意图解析结果（来自微服务响应）
     * @return 按匹配度降序排列的结果列表
     */
    fun matchWithIntent(ads: List<AdItem>, intent: AiIntentResult): List<AdMatchResult> {
        val criteria = SearchCriteria(
            categories = intent.categories,
            audiences = intent.audiences,
            styles = intent.styles,
            scenes = intent.scenes,
            priceMin = intent.priceRange?.min ?: -1,
            priceMax = intent.priceRange?.max ?: -1,
            keywords = intent.keywords
        )
        return match(ads, criteria)
    }

    /**
     * 降级方案：纯关键词搜索
     *
     * 当微服务不可用时，将用户原始查询作为关键词，
     * 对所有已加载广告做 title/description 子串匹配。
     *
     * 分词策略：按空格 + 常见分隔符拆分查询字符串。
     *
     * @param ads 候选广告列表
     * @param query 用户原始输入的自然语言查询
     * @return 按匹配度降序排列的结果列表（可能为空）
     */
    fun keywordSearch(ads: List<AdItem>, query: String): List<AdMatchResult> {
        if (query.isBlank()) return emptyList()

        // 分词：按空格、中文标点、英文标点拆分
        val tokens = query
            .split(Regex("[\\s，,。！!？?、；;：:]+"))
            .filter { it.length >= 2 }  // 过滤单字（中文单字匹配精度低）

        if (tokens.isEmpty()) return emptyList()

        return ads.map { ad ->
            var score = 0.0
            tokens.forEach { token ->
                if (ad.title.contains(token, ignoreCase = true)) score += 3.0
                if (ad.description.contains(token, ignoreCase = true)) score += 1.5
            }
            // 广告标签名称也参与匹配
            ad.tags.forEach { tag ->
                tokens.forEach { token ->
                    if (tag.name.contains(token, ignoreCase = true)) score += 2.0
                }
            }
            AdMatchResult(ad, score)
        }.filter { it.score > 0 }
         .sortedByDescending { it.score }
    }

    // ──────────────────────────────────────────────
    // 私有方法
    // ──────────────────────────────────────────────

    /**
     * 计算单条广告对搜索条件的匹配得分
     */
    private fun calculateScore(ad: AdItem, criteria: SearchCriteria): Double {
        var score = 0.0

        // 1. 标签精确匹配（+3.0 / 个）
        val targetTags = criteria.allTargetTags()
        score += ad.tags.count { tag -> tag.name in targetTags } * TAG_MATCH_WEIGHT

        // 2. 关键词命中标题（+2.0 / 个）
        criteria.keywords.forEach { keyword ->
            if (ad.title.contains(keyword, ignoreCase = true)) {
                score += KEYWORD_TITLE_WEIGHT
            }
        }

        // 3. 关键词命中描述（+1.0 / 个）
        criteria.keywords.forEach { keyword ->
            if (ad.description.contains(keyword, ignoreCase = true)) {
                score += KEYWORD_DESC_WEIGHT
            }
        }

        // 4. 受众标签匹配（+2.0 / 个）
        score += ad.tags.count { tag -> tag.name in criteria.audiences } * AUDIENCE_MATCH_WEIGHT

        return score
    }

    companion object {
        private const val TAG_MATCH_WEIGHT = 3.0
        private const val KEYWORD_TITLE_WEIGHT = 2.0
        private const val KEYWORD_DESC_WEIGHT = 1.0
        private const val AUDIENCE_MATCH_WEIGHT = 2.0
    }
}
