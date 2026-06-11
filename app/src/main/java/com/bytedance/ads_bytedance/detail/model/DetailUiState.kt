package com.bytedance.ads_bytedance.detail.model

import com.bytedance.ads_bytedance.data.model.AdItem
import com.bytedance.ads_bytedance.data.model.LoadState

/**
 * 详情页 UI 状态
 *
 * @property ad 当前展示的广告数据（null = 加载中/加载失败）
 * @property loadState 数据加载状态
 * @property errorMessage 错误信息（loadState == ERROR 时非空）
 */
data class DetailUiState(
    val ad: AdItem? = null,
    val loadState: LoadState = LoadState.LOADING,
    val errorMessage: String? = null,
    /** 每次互动操作（点赞/收藏/分享）递增，确保 Compose 检测到 uiState 结构变化并触发重组 */
    val interactionVersion: Int = 0
)

/**
 * 详情页用户事件
 *
 * 通过 DetailViewModel.onEvent() 统一分发处理。
 */
sealed class DetailEvent {
    /** 切换点赞 */
    data class ToggleLike(val adId: String) : DetailEvent()

    /** 切换收藏 */
    data class ToggleCollect(val adId: String) : DetailEvent()

    /** 分享广告 */
    data class Share(val ad: AdItem) : DetailEvent()

    /** 返回上一页 */
    data object Back : DetailEvent()

    /** 重试加载 */
    data object Retry : DetailEvent()
}

/**
 * 一次性 UI 事件（通过 SharedFlow 发送，确保只消费一次）
 */
sealed class DetailOneTimeEvent {
    data class ShowToast(val message: String) : DetailOneTimeEvent()
    data class ShowShareSheet(val shareText: String) : DetailOneTimeEvent()
    data object NavigateBack : DetailOneTimeEvent()
}
