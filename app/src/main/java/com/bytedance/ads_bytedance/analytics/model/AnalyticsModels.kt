package com.bytedance.ads_bytedance.analytics.model

import com.bytedance.ads_bytedance.data.model.AdItem

/**
 * 单条广告的统计摘要
 *
 * @param ad 广告对象
 * @param exposureCount 曝光次数
 * @param clickCount 点击次数
 * @param ctr 点击率（%），公式：clickCount / exposureCount × 100
 */
data class AdStatItem(
    val ad: AdItem,
    val exposureCount: Int,
    val clickCount: Int
) {
    /** 点击率（百分比），曝光为 0 时返回 0.0 */
    val ctr: Double
        get() = if (exposureCount > 0) {
            (clickCount.toDouble() / exposureCount.toDouble()) * 100.0
        } else 0.0
}

/**
 * 排序维度
 */
enum class StatsSortBy(val label: String) {
    EXPOSURE("按曝光"),
    CLICK("按点击"),
    CTR("按CTR")
}

/**
 * 统计页面 Tab 页
 */
enum class StatsTab(val label: String) {
    AD_STATS("广告统计"),
    MY_PREFERENCES("我的偏好")
}

/**
 * 统计页面 UI 状态
 *
 * @param adStats 广告统计列表
 * @param sortBy 当前排序维度
 * @param activeTab 当前 Tab
 * @param totalClicks 用户总点击次数
 * @param totalLikes 用户总点赞数
 * @param totalCollects 用户总收藏数
 * @param totalShares 用户总分享数
 * @param topTags 用户 Top 偏好标签（标签名 → 权重得分）
 * @param isLoading 是否正在加载
 */
data class StatsUiState(
    val adStats: List<AdStatItem> = emptyList(),
    val sortBy: StatsSortBy = StatsSortBy.EXPOSURE,
    val activeTab: StatsTab = StatsTab.AD_STATS,
    val totalClicks: Int = 0,
    val totalLikes: Int = 0,
    val totalCollects: Int = 0,
    val totalShares: Int = 0,
    val topTags: List<Pair<String, Double>> = emptyList(),
    val isLoading: Boolean = true
)

/**
 * 统计页面用户事件
 */
sealed class StatsEvent {
    /** 切换排序方式 */
    data class ChangeSort(val sortBy: StatsSortBy) : StatsEvent()

    /** 切换 Tab */
    data class ChangeTab(val tab: StatsTab) : StatsEvent()

    /** 返回上一页 */
    data object Back : StatsEvent()
}
