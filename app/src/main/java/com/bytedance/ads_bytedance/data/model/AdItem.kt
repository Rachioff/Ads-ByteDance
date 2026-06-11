package com.bytedance.ads_bytedance.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 广告数据基类（密封类实现多态序列化）
 *
 * Kotlinx Serialization 原生支持密封类的多态序列化。
 * 信息流中通过 `when (adItem)` 分发不同卡片 Composable。
 *
 * 三个子类各含差异字段：
 * - LargeImageAd: 大尺寸封面图（String URL/路径）
 * - SmallImageAd: 小尺寸缩略图 + 图片位置
 * - VideoAd: 视频 URL + 封面图 URL
 *
 * 统一字段在此定义为抽象属性。
 * isLiked / isCollected 为运行时可变状态，标记 @Transient 不参与序列化。
 */
@Serializable
sealed class AdItem {
    abstract val id: String
    abstract val title: String
    abstract val adType: AdType
    abstract val description: String
    abstract val advertiserName: String
    abstract val advertiserAvatar: String
    abstract val channel: Channel
    abstract val tags: List<Tag>
    abstract val aiSummary: String?
    abstract var exposureCount: Int
    abstract var clickCount: Int

    // 运行时可变状态（互动计数 + 布尔标记），@Transient 不参与序列化
    // likeCount/collectCount/shareCount 为 var 以支持 Repository.updateInteraction() 模拟服务端计数更新
    abstract var likeCount: Int
    abstract var collectCount: Int
    abstract var shareCount: Int
    abstract var isLiked: Boolean
    abstract var isCollected: Boolean

    // ═══════════════════════════════════════════════════════════
    // 子类
    // ═══════════════════════════════════════════════════════════

    @Serializable
    @SerialName("large_image")
    data class LargeImageAd(
        override val id: String,
        override val title: String,
        override val adType: AdType = AdType.LARGE_IMAGE,
        val coverImageUrl: String,
        override val description: String,
        override val advertiserName: String,
        override val advertiserAvatar: String,
        override val channel: Channel,
        override val tags: List<Tag> = emptyList(),
        override val aiSummary: String? = null,
        override var likeCount: Int = 0,
        override var collectCount: Int = 0,
        override var shareCount: Int = 0,
        override var exposureCount: Int = 0,
        override var clickCount: Int = 0,
        @kotlinx.serialization.Transient
        override var isLiked: Boolean = false,
        @kotlinx.serialization.Transient
        override var isCollected: Boolean = false,
    ) : AdItem()

    @Serializable
    @SerialName("small_image")
    data class SmallImageAd(
        override val id: String,
        override val title: String,
        override val adType: AdType = AdType.SMALL_IMAGE,
        val thumbnailUrl: String,
        val imagePosition: ImagePosition = ImagePosition.LEFT,
        override val description: String,
        override val advertiserName: String,
        override val advertiserAvatar: String,
        override val channel: Channel,
        override val tags: List<Tag> = emptyList(),
        override val aiSummary: String? = null,
        override var likeCount: Int = 0,
        override var collectCount: Int = 0,
        override var shareCount: Int = 0,
        override var exposureCount: Int = 0,
        override var clickCount: Int = 0,
        @kotlinx.serialization.Transient
        override var isLiked: Boolean = false,
        @kotlinx.serialization.Transient
        override var isCollected: Boolean = false,
    ) : AdItem()

    @Serializable
    @SerialName("video")
    data class VideoAd(
        override val id: String,
        override val title: String,
        override val adType: AdType = AdType.VIDEO,
        val videoUrl: String,
        val coverImageUrl: String,
        override val description: String,
        override val advertiserName: String,
        override val advertiserAvatar: String,
        override val channel: Channel,
        override val tags: List<Tag> = emptyList(),
        override val aiSummary: String? = null,
        override var likeCount: Int = 0,
        override var collectCount: Int = 0,
        override var shareCount: Int = 0,
        override var exposureCount: Int = 0,
        override var clickCount: Int = 0,
        @kotlinx.serialization.Transient
        override var isLiked: Boolean = false,
        @kotlinx.serialization.Transient
        override var isCollected: Boolean = false,
    ) : AdItem()
}
