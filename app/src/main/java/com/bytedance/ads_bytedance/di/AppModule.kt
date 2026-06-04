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
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
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

    // single<AiApiService> { get<Retrofit>().create(AiApiService::class.java) }
    // single<AiCacheManager> { /* AiCacheManager */ }

    // ═══════════════════════════════════════════════════════
    // 视频播放器 (Day 4 实现)
    // ═══════════════════════════════════════════════════════

    // single<PlayerPool> { PlayerPool() }

    // ═══════════════════════════════════════════════════════
    // 埋点与行为 (Day 9-10 实现)
    // ═══════════════════════════════════════════════════════

    // single<ExposureTracker>   { /* ExposureTracker */ }
    // single<BehaviorCollector> { /* BehaviorCollector */ }
    // single<UserProfileEngine> { /* UserProfileEngine */ }
    // single<RecommendRanker>   { /* RecommendRanker */ }
}
