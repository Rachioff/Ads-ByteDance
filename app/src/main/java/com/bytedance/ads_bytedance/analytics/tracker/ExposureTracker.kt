package com.bytedance.ads_bytedance.analytics.tracker

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import com.bytedance.ads_bytedance.data.model.AdItem
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Compose 曝光追踪器
 *
 * ## 曝光判定口径
 * 与 tech.md §9.1.1 一致：
 * | 条件       | 说明                                              |
 * |-----------|---------------------------------------------------|
 * | 可见比例   | 广告卡片在屏幕可视区域内的可见部分 ≥ 50%              |
 * | 停留时长   | 满足可见比例的状态持续 ≥ 1000ms                      |
 * | 去重       | 同一广告在同一次信息流会话中只计 1 次曝光              |
 *
 * ## 实现原理
 * 基于 `LazyListState.layoutInfo.visibleItemsInfo` 获取当前可见 item 列表，
 * 通过 `snapshotFlow` 转为 Flow 持续监听滚动事件。
 * 对每个 item 计算可见比例（item.offset + item.size vs viewportSize.height），
 * 满足 ≥50% 时开始计时，持续 ≥1s 后触发曝光回调。
 *
 * ## Compose 适配
 * 替代传统 RecyclerView.OnScrollListener + Handler 延时方案。
 * snapshotFlow 在 LazyListState 变化时自动触发，天然适合 Compose 声明式范式。
 *
 * ## 使用方式
 * ```kotlin
 * val exposureTracker = remember { ExposureTracker() }
 *
 * // 在 FeedScreen 内部调用
 * TrackExposures(
 *     lazyListState = listState,
 *     ads = ads,
 *     onExposed = { adId -> viewModel.onEvent(FeedEvent.Expose(adId)) }
 * )
 * ```
 */
class ExposureTracker {
    /** 已曝光广告 ID 集合（去重） */
    private val exposedIds = mutableSetOf<String>()

    /** 每个 item index → 首次进入满足阈值状态的时间戳 */
    private val visibilityStartTime = mutableMapOf<Int, Long>()

    /** 清除当前会话的曝光记录（下拉刷新时调用） */
    fun reset() {
        exposedIds.clear()
        visibilityStartTime.clear()
    }

    /** 当前已曝光数 */
    val exposedCount: Int get() = exposedIds.size

    /**
     * 启动曝光追踪（Composable 副作用）
     *
     * 监听 [LazyListState] 滚动事件，对满足曝光条件的广告触发 [onExposed] 回调。
     * 使用 `snapshotFlow` + `distinctUntilChanged` 避免重复计算，
     * 使用 `collectLatest` 自动取消上一次未完成的收集。
     *
     * @param lazyListState LazyColumn 的滚动状态
     * @param ads 当前广告列表（用于按 index 查找 adId）
     * @param onExposed 广告满足曝光条件时的回调（adId → Unit）
     */
    @Composable
    fun Track(
        lazyListState: LazyListState,
        ads: List<AdItem>,
        onExposed: (String) -> Unit
    ) {
        // 使用 remember 确保状态在重组中保持
        val tracker = remember { this }

        LaunchedEffect(Unit) {
            snapshotFlow { lazyListState.layoutInfo }
                .distinctUntilChanged()
                .collectLatest { layoutInfo ->
                    val now = System.currentTimeMillis()
                    val viewportHeight = layoutInfo.viewportSize.height
                    if (viewportHeight <= 0) return@collectLatest

                    val currentVisibleIndices = mutableSetOf<Int>()

                    layoutInfo.visibleItemsInfo.forEach { itemInfo ->
                        val index = itemInfo.index
                        if (index < 0 || index >= ads.size) return@forEach

                        currentVisibleIndices.add(index)

                        // ═══ 计算可见比例 ═══
                        val itemTop = itemInfo.offset
                        val itemBottom = itemInfo.offset + itemInfo.size
                        val visibleTop = maxOf(0, itemTop)
                        val visibleBottom = minOf(viewportHeight, itemBottom)
                        val visibleHeight = visibleBottom - visibleTop
                        val ratio = if (itemInfo.size > 0) {
                            visibleHeight.toFloat() / itemInfo.size.toFloat()
                        } else 0f

                        if (ratio >= EXPOSURE_RATIO_THRESHOLD) {
                            // 满足 ≥50% 可见 → 开始计时
                            if (index !in tracker.visibilityStartTime) {
                                tracker.visibilityStartTime[index] = now
                            }

                            val adId = ads[index].id
                            val startTime = tracker.visibilityStartTime[index] ?: now
                            val duration = now - startTime

                            // 停留 ≥1s → 触发曝光
                            if (duration >= EXPOSURE_DURATION_MS && adId !in tracker.exposedIds) {
                                tracker.exposedIds.add(adId)
                                onExposed(adId)
                            }
                        } else {
                            // 不满足 50% 可见 → 取消计时
                            tracker.visibilityStartTime.remove(index)
                        }
                    }

                    // 清理已离开屏幕的 item 计时器
                    tracker.visibilityStartTime.keys.removeAll { it !in currentVisibleIndices }
                }
        }
    }

    companion object {
        /** 曝光可见比例阈值：≥ 50% */
        private const val EXPOSURE_RATIO_THRESHOLD = 0.5f

        /** 曝光停留时长阈值：≥ 1000ms */
        private const val EXPOSURE_DURATION_MS = 1000L
    }
}
