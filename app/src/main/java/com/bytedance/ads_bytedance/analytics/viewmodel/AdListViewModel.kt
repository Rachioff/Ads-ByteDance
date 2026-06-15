package com.bytedance.ads_bytedance.analytics.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bytedance.ads_bytedance.data.local.dao.BehaviorDao
import com.bytedance.ads_bytedance.data.local.dao.UserInteractionDao
import com.bytedance.ads_bytedance.data.model.AdItem
import com.bytedance.ads_bytedance.data.repository.AdRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 广告列表 ViewModel（浏览记录 / 已点赞 / 已收藏）
 *
 * ## 职责
 * 根据 [AdListType] 从不同数据源加载 adId 列表 → 获取完整 AdItem → 展示
 *
 * ## 三种类型
 * | 类型      | adId 来源                        | 排序                  |
 * |----------|----------------------------------|----------------------|
 * | HISTORY  | BehaviorDao.getClickHistoryAdIds() | 最近点击时间降序       |
 * | LIKED    | UserInteractionDao.getLikedAdIds() | 点赞时间降序          |
 * | COLLECTED| UserInteractionDao.getCollectedAdIds() | 收藏时间降序      |
 *
 * @param repository 广告数据仓库（Koin 注入）
 * @param behaviorDao 行为 DAO（Koin 注入）
 * @param interactionDao 互动状态 DAO（Koin 注入）
 * @param type 列表类型（Koin parameter 注入）
 */
class AdListViewModel(
    private val repository: AdRepository,
    private val behaviorDao: BehaviorDao,
    private val interactionDao: UserInteractionDao,
    private val type: AdListType
) : ViewModel() {

    /** 广告列表 */
    var ads by mutableStateOf<List<AdItem>>(emptyList())
        private set

    /** 是否加载中 */
    var isLoading by mutableStateOf(true)
        private set

    init {
        loadAds()
    }

    /**
     * 刷新广告列表（Lifecycle ON_RESUME 时由外部调用）
     *
     * 当用户从详情页返回（可能修改了点赞/收藏状态）时，
     * 需要重新加载列表以展示最新数据。
     */
    fun refresh() {
        loadAds()
    }

    private fun loadAds() {
        viewModelScope.launch {
            isLoading = true
            try {
                val adIds = withContext(Dispatchers.IO) {
                    when (type) {
                        AdListType.HISTORY -> behaviorDao.getClickHistoryAdIds()
                        AdListType.LIKED -> interactionDao.getLikedAdIds()
                        AdListType.COLLECTED -> interactionDao.getCollectedAdIds()
                    }
                }

                val adItems = withContext(Dispatchers.IO) {
                    adIds.mapNotNull { id ->
                        repository.getAdById(id).getOrNull()
                    }
                }

                ads = adItems
            } catch (_: Exception) {
                ads = emptyList()
            } finally {
                isLoading = false
            }
        }
    }
}

/**
 * 广告列表类型
 */
enum class AdListType(val title: String) {
    /** 浏览记录——用户点击过的广告 */
    HISTORY("浏览记录"),

    /** 已点赞——用户点赞的广告 */
    LIKED("已点赞"),

    /** 已收藏——用户收藏的广告 */
    COLLECTED("已收藏")
}
