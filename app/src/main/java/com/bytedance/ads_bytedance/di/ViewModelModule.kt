package com.bytedance.ads_bytedance.di

import com.bytedance.ads_bytedance.feed.viewmodel.FeedViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module

/**
 * ViewModel 注入模块
 *
 * 所有 ViewModel 通过 Koin 的 viewModel { } 声明，
 * Compose 端通过 koinViewModel() 获取。
 */
val viewModelModule = module {

    // ── 信息流 (Day 3 实现，Day 6 增加 AI 依赖，Day 9 增加 BehaviorCollector，Day 10 增加 RecommendRanker) ──
    // 每个频道持有独立 ViewModel 实例，通过 key(channel.name) 区分
    viewModel { (channel: com.bytedance.ads_bytedance.data.model.Channel) ->
        FeedViewModel(
            repository = get(),
            channel = channel,
            aiContentGenerator = get(),
            behaviorCollector = get(),
            recommendRanker = get()
        )
    }

    // ── 详情页 (Day 5 实现，Day 6 增加 AI 依赖，Day 9 增加 BehaviorCollector) ──
    viewModel { (adId: String) ->
        com.bytedance.ads_bytedance.detail.viewmodel.DetailViewModel(
            repository = get(),
            adId = adId,
            aiContentGenerator = get(),
            behaviorCollector = get()
        )
    }

    // ── AI 对话搜索 (ChatBot，Day 7 重构，增加上下文广告 + 历史恢复 + 内存缓存，Day 10 增加 BehaviorCollector) ──
    viewModel {
        com.bytedance.ads_bytedance.ai.chat.viewmodel.ChatViewModel(
            savedStateHandle = get(),
            chatBotService = get(),
            matchingEngine = get(),
            repository = get(),
            sessionManager = get(),
            chatCache = get(),
            behaviorCollector = get()
        )
    }

    // ── 常规搜索 (Day 7 重构新增，Day 8 增加搜索历史，Day 10 增加 BehaviorCollector) ──
    viewModel {
        com.bytedance.ads_bytedance.search.viewmodel.SearchViewModel(
            repository = get(),
            historyManager = get(),
            behaviorCollector = get()
        )
    }

    // ── 埋点统计 (Day 9 实现) ──
    viewModel {
        com.bytedance.ads_bytedance.analytics.viewmodel.StatsViewModel(
            repository = get(),
            profileEngine = get()
        )
    }

    // ── 广告列表页：浏览记录 / 已点赞 / 已收藏 (Day 11) ──
    viewModel { (type: com.bytedance.ads_bytedance.analytics.viewmodel.AdListType) ->
        com.bytedance.ads_bytedance.analytics.viewmodel.AdListViewModel(
            repository = get(),
            behaviorDao = get(),
            interactionDao = get(),
            type = type
        )
    }
}
