package com.bytedance.ads_bytedance.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 智能标签
 *
 * 每条广告可携带多个标签，标签按类别组织（品类/风格/受众/场景），
 * 用于 AI 标签生成、标签过滤和个性化推荐匹配。
 */
@Serializable
data class Tag(
    val name: String,
    val category: TagCategory
)

/**
 * 标签类别枚举
 *
 * 序列化时使用小写名称（与 AI 输出约定一致）。
 */
@Serializable
enum class TagCategory {
    @SerialName("category") CATEGORY,
    @SerialName("style") STYLE,
    @SerialName("audience") AUDIENCE,
    @SerialName("scene") SCENE
}
