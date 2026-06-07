package com.bytedance.ads_bytedance.di

import com.bytedance.ads_bytedance.feed.viewmodel.FeedViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

/**
 * ViewModel 注入模块
 *
 * 所有 ViewModel 通过 Koin 的 viewModel { } 声明，
 * Compose 端通过 koinViewModel() 获取。
 */
val viewModelModule = module {

    // ── 信息流 (Day 3 实现) ──
    // 每个频道持有独立 ViewModel 实例，通过 key(channel.name) 区分
    viewModel { (channel: com.bytedance.ads_bytedance.data.model.Channel) ->
        FeedViewModel(
            repository = get(),
            channel = channel
        )
    }

    // ── 详情页 (Day 5 实现) ──
    // viewModel { (adId: String) -> DetailViewModel(get(), adId) }

    // ── 搜索 (Day 7 实现) ──
    // viewModel<SearchViewModel> { SearchViewModel(get(), get()) }

    // ── 埋点统计 (Day 9 实现) ──
    // viewModel<AnalyticsViewModel> { AnalyticsViewModel(get(), get(), get()) }
}
