package com.bytedance.ads_bytedance.data.model

import kotlinx.serialization.Serializable

/**
 * 广告卡片类型
 *
 * 决定信息流中的卡片布局样式和三子类分发。
 */
@Serializable
enum class AdType {
    LARGE_IMAGE,
    SMALL_IMAGE,
    VIDEO
}

/**
 * 广告内容频道
 *
 * 对应顶部 Tab 栏的三个频道：精选（推荐排序）、电商、本地。
 */
@Serializable
enum class Channel {
    FEATURED,
    ECOMMERCE,
    LOCAL
}

/**
 * 小图卡片的图片位置
 *
 * 用于 SmallImageAd 子类，决定左文右图还是左图右文布局。
 */
@Serializable
enum class ImagePosition {
    LEFT,
    RIGHT
}
