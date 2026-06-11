package com.bytedance.ads_bytedance.di

import androidx.room.Room
import coil3.ImageLoader
import com.bytedance.ads_bytedance.BuildConfig
import com.bytedance.ads_bytedance.common.imageloader.ImageLoaderConfig
import com.bytedance.ads_bytedance.common.network.NetworkConfig
import com.bytedance.ads_bytedance.data.local.AdDataSource
import com.bytedance.ads_bytedance.data.local.AppDatabase
import com.bytedance.ads_bytedance.data.local.MockJsonDataSource
import com.bytedance.ads_bytedance.data.remote.AdApiService
import com.bytedance.ads_bytedance.data.remote.RemoteDataSource
import com.bytedance.ads_bytedance.data.repository.AdRepository
import com.bytedance.ads_bytedance.player.pool.PlayerPool
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.koin.core.qualifier.named
import org.koin.dsl.module
import retrofit2.Retrofit

/**
 * Koin 全局依赖模块
 *
 * 按功能分组声明所有需要 DI 容器管理的单例和工厂。
 * 依赖声明从上到下遵循依赖顺序：底层基础设施 → 上层业务。
 */
val appModule = module {

    // ═══════════════════════════════════════════════════════
    // 网络层
    // ═══════════════════════════════════════════════════════

    /** 全局共享 OkHttpClient——所有 HTTP 请求共用连接池和超时策略 */
    single<OkHttpClient> {
        NetworkConfig.createOkHttpClient()
    }

    /** 全局 JSON 解析器——与 Retrofit 转换器共享同一实例 */
    single<Json> {
        NetworkConfig.json
    }

    /** Retrofit 实例——通过工厂方法创建，baseUrl 由 BuildConfig.DATA_MODE 决定 */
    single<Retrofit> {
        NetworkConfig.createRetrofit(
            client = get(),
            baseUrl = NetworkConfig.getBaseUrl()
        )
    }

    // ═══════════════════════════════════════════════════════
    // 图片加载
    // ═══════════════════════════════════════════════════════

    /** 全局 Coil ImageLoader——内存 LRU + 磁盘 DiskLRU 二级缓存 */
    single<ImageLoader> {
        ImageLoaderConfig.createImageLoader(context = get())
    }

    // ═══════════════════════════════════════════════════════
    // 本地存储 (Day 2 实现)
    // ═══════════════════════════════════════════════════════

    /** Room 数据库——广告数据 / 用户行为 / AI 缓存 */
    single<AppDatabase> {
        Room.databaseBuilder(
            get(),
            AppDatabase::class.java,
            "ads_bytedance.db"
        ).build()
    }

    /** DAO 实例——由 Room Database 提供 */
    single { get<AppDatabase>().adDao() }
    single { get<AppDatabase>().behaviorDao() }
    single { get<AppDatabase>().aiCacheDao() }

    // ═══════════════════════════════════════════════════════
    // 数据源与仓库 (Day 2 实现)
    // ═══════════════════════════════════════════════════════

    /** 广告数据源：Mock 与 Remote 由 BuildConfig.DATA_MODE 切换 */
    single<AdDataSource> {
        if (BuildConfig.DATA_MODE == "mock") {
            MockJsonDataSource(context = get())
        } else {
            RemoteDataSource(apiService = get())
        }
    }

    /** Retrofit API 接口（Remote 模式使用，Mock 模式下不会被调用但 Koin 仍创建） */
    single<AdApiService> {
        get<Retrofit>().create(AdApiService::class.java)
    }

    /** 广告数据仓库——所有广告数据的唯一入口 */
    single<AdRepository> {
        AdRepository(dataSource = get())
    }

    // ═══════════════════════════════════════════════════════
    // AI 模块 (Day 6 实现)
    // ═══════════════════════════════════════════════════════

    /** AI API 专用 Retrofit 实例——独立 BaseUrl 指向 AI 服务端点 */
    single<Retrofit>(named("aiRetrofit")) {
        NetworkConfig.createAiRetrofit(client = get())
    }

    /** AI API 接口——OpenAI 兼容 Chat Completions */
    single<com.bytedance.ads_bytedance.ai.api.AiApiService> {
        get<Retrofit>(named("aiRetrofit")).create(com.bytedance.ads_bytedance.ai.api.AiApiService::class.java)
    }

    // single<AiCacheManager> { AiCacheManager(aiCacheDao = get()) }
    // single<AiContentGenerator> { AiContentGenerator(aiApiService = get(), aiCacheManager = get()) }

    /** AI 结果三级缓存管理器——内存 LRU + Room DB */
    single<com.bytedance.ads_bytedance.ai.cache.AiCacheManager> {
        com.bytedance.ads_bytedance.ai.cache.AiCacheManager(aiCacheDao = get())
    }

    /** AI 内容生成器——编排缓存检查 → API 调用 → 降级 */
    single<com.bytedance.ads_bytedance.ai.api.AiContentGenerator> {
        com.bytedance.ads_bytedance.ai.api.AiContentGenerator(
            aiApiService = get(),
            cacheManager = get()
        )
    }

    // ═══════════════════════════════════════════════════════
    // 视频播放器 (Day 4 实现)
    // ═══════════════════════════════════════════════════════

    /** PlayerPool — ExoPlayer 实例池，全局单例（最多 3 实例复用） */
    single<PlayerPool> { PlayerPool() }

    // ═══════════════════════════════════════════════════════
    // 埋点与行为 (Day 9 实现)
    // ═══════════════════════════════════════════════════════

    /** 用户行为采集器——6 种行为采集 + Room 持久化 */
    single<com.bytedance.ads_bytedance.behavior.tracker.BehaviorCollector> {
        com.bytedance.ads_bytedance.behavior.tracker.BehaviorCollector(behaviorDao = get())
    }

    /** 用户画像引擎——标签维度权重聚合 → UserProfile */
    single<com.bytedance.ads_bytedance.behavior.profile.UserProfileEngine> {
        com.bytedance.ads_bytedance.behavior.profile.UserProfileEngine(behaviorDao = get())
    }

    /** 个性化推荐排序器——精选频道按画像匹配度排序 */
    single<com.bytedance.ads_bytedance.behavior.recommend.RecommendRanker> {
        com.bytedance.ads_bytedance.behavior.recommend.RecommendRanker(profileEngine = get())
    }

    // ═══════════════════════════════════════════════════════
    // Chat Bot 对话搜索 (Day 7 实现)
    // ═══════════════════════════════════════════════════════

    /** 设备级用户标识管理器——生成/持久化 UUID */
    single<com.bytedance.ads_bytedance.common.util.SessionManager> {
        com.bytedance.ads_bytedance.common.util.SessionManager(context = get())
    }

    /** Chat Bot 微服务 Retrofit 实例——独立 BaseUrl 指向微服务端点 */
    single<Retrofit>(named("chatbotRetrofit")) {
        val userId = get<com.bytedance.ads_bytedance.common.util.SessionManager>().userId
        NetworkConfig.createChatBotRetrofit(client = get(), userId = userId)
    }

    /** Chat Bot 微服务 API 接口 */
    single<com.bytedance.ads_bytedance.ai.api.ChatBotService> {
        get<Retrofit>(named("chatbotRetrofit")).create(com.bytedance.ads_bytedance.ai.api.ChatBotService::class.java)
    }

    /** 本地广告匹配引擎——标签/关键词/受众多维度匹配 + 评分排序 */
    single<com.bytedance.ads_bytedance.common.engine.AdMatchingEngine> {
        com.bytedance.ads_bytedance.common.engine.AdMatchingEngine()
    }

    /** 聊天历史内存缓存——不持久化，应用进程存活期间有效 */
    single<com.bytedance.ads_bytedance.ai.chat.cache.ChatMemoryCache> {
        com.bytedance.ads_bytedance.ai.chat.cache.ChatMemoryCache()
    }

    /** 聊天历史预加载器——应用启动时后台拉取历史 + intent 重匹配广告 */
    single<com.bytedance.ads_bytedance.ai.chat.preload.ChatPreloader> {
        com.bytedance.ads_bytedance.ai.chat.preload.ChatPreloader(
            chatBotService = get(),
            sessionManager = get(),
            cache = get(),
            matchingEngine = get(),
            repository = get()
        )
    }

    // ═══════════════════════════════════════════════════════
    // 搜索模块 (Day 8 增强)
    // ═══════════════════════════════════════════════════════

    /** 搜索历史管理器——JSON 文件持久化 */
    single<com.bytedance.ads_bytedance.search.data.SearchHistoryManager> {
        com.bytedance.ads_bytedance.search.data.SearchHistoryManager(context = get())
    }
}
