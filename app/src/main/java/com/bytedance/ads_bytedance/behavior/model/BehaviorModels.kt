package com.bytedance.ads_bytedance.behavior.model

/**
 * 用户行为类型枚举
 *
 * 每种行为有不同权重，用于用户兴趣画像计算：
 * ```
 * 标签偏好得分 = Σ(该标签下各行为次数 × 对应行为权重)
 * ```
 */
enum class BehaviorType(val weight: Int) {
    /** 点击广告卡片进入详情页 */
    CLICK(1),

    /** 点击点赞按钮 */
    LIKE(2),

    /** 点击收藏按钮 */
    COLLECT(3),

    /** 触发分享行为 */
    SHARE(2),

    /** 点击卡片上的标签 Chip */
    TAG_CLICK(1),

    /** 提交对话式搜索查询 */
    SEARCH(1)
}

/**
 * 用户行为记录（对应 Room entity + JSON 序列化）
 *
 * 每次用户产生交互（点击/点赞/收藏/分享/标签点击/搜索）时记录一条。
 * 持久化到 Room user_behaviors 表。
 *
 * @param id 行为记录唯一 ID
 * @param adId 关联的广告 ID（搜索行为可为空）
 * @param behaviorType 行为类型
 * @param tags 涉及的标签名称列表
 * @param timestamp 行为发生时间戳
 */
data class UserBehavior(
    val id: String,
    val adId: String?,
    val behaviorType: BehaviorType,
    val tags: List<String>,
    val timestamp: Long
)

/**
 * 用户兴趣画像（聚合计算结果，运行时缓存，可持久化）
 *
 * @param tagWeights 标签 → 偏好得分（按标签维度聚合的权重总和）
 * @param totalClicks 总点击次数
 * @param totalLikes 总点赞数
 * @param totalCollects 总收藏数
 * @param totalShares 总分享数
 * @param lastUpdateTime 画像最后更新时间戳
 */
data class UserProfile(
    val tagWeights: Map<String, Double>,
    val totalClicks: Int,
    val totalLikes: Int,
    val totalCollects: Int,
    val totalShares: Int,
    val lastUpdateTime: Long
)
