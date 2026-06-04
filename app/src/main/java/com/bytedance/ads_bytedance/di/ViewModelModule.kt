package com.bytedance.ads_bytedance.di

import org.koin.dsl.module

/**
 * ViewModel 注入模块
 *
 * 所有 ViewModel 通过 Koin 的 viewModel { } 声明，
 * Compose 端通过 koinViewModel() 或 koinNavViewModel() 获取。
 */
val viewModelModule = module {

    // ── 信息流 (Day 3 实现) ──

    // viewModel<FeedViewModel> { FeedViewModel(get(), get(), get()) }

    // ── 详情页 (Day 5 实现) ──

    // viewModel<DetailViewModel> { DetailViewModel(get()) }

    // ── 搜索 (Day 7 实现) ──

    // viewModel<SearchViewModel> { SearchViewModel(get(), get()) }

    // ── 埋点统计 (Day 9 实现) ──

    // viewModel<AnalyticsViewModel> { AnalyticsViewModel(get(), get(), get()) }
}
