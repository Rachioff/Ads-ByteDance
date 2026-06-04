package com.bytedance.ads_bytedance.data.model

/**
 * 结构化搜索条件
 *
 * 由 AI 意图解析从自然语言中提取，或由标签点击过滤构造。
 * AdMatchingEngine 将此条件与本地广告数据匹配并计算得分。
 */
data class SearchCriteria(
    /** 品类约束（如 ["数码", "美妆"]） */
    val categories: List<String> = emptyList(),

    /** 受众约束（如 ["学生党", "职场人"]） */
    val audiences: List<String> = emptyList(),

    /** 风格约束（如 ["简约", "潮流"]） */
    val styles: List<String> = emptyList(),

    /** 场景约束（如 ["通勤", "户外"]） */
    val scenes: List<String> = emptyList(),

    /** 价格区间下限（-1 表示不限制） */
    val priceMin: Int = -1,

    /** 价格区间上限（-1 表示不限制） */
    val priceMax: Int = -1,

    /** 自由关键词列表 */
    val keywords: List<String> = emptyList(),

    /** 指定的过滤标签名称（标签点击过滤场景） */
    val filterTagName: String? = null
) {
    /**
     * 收集所有作为匹配目标标签名称的字符串集合。
     * 用于匹配引擎做标签匹配时遍历。
     */
    fun allTargetTags(): Set<String> {
        val set = mutableSetOf<String>()
        set.addAll(categories)
        set.addAll(audiences)
        set.addAll(styles)
        set.addAll(scenes)
        filterTagName?.let { set.add(it) }
        return set
    }
}
