package com.bytedance.ads_bytedance.data.model

/**
 * 分页加载状态
 *
 * 由 FeedViewModel 持有并通过 StateFlow 推送给 UI。
 * currentPage / pageSize / hasMore 驱动上拉加载逻辑。
 */
data class PaginationState(
    val currentPage: Int = 1,
    val pageSize: Int = 10,
    val hasMore: Boolean = true,
    val loadState: LoadState = LoadState.IDLE
)

/**
 * 数据加载状态枚举
 *
 * IDLE     — 初始状态 / 加载完成无后续操作
 * LOADING  — 上拉加载更多中
 * REFRESHING — 下拉刷新中
 * END      — 全部数据加载完毕（"没有更多了"）
 * ERROR    — 加载出错
 */
enum class LoadState { IDLE, LOADING, REFRESHING, END, ERROR }
