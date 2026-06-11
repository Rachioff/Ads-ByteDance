package com.bytedance.ads_bytedance

import android.app.Application
import com.bytedance.ads_bytedance.ai.cache.AiCacheManager
import com.bytedance.ads_bytedance.ai.chat.preload.ChatPreloader
import com.bytedance.ads_bytedance.common.imageloader.ImageLoaderConfig
import com.bytedance.ads_bytedance.di.appModule
import com.bytedance.ads_bytedance.di.viewModelModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class AdsApplication : Application() {

    /** 应用级协程作用域（SupervisorJob 确保子协程失败不影响其他） */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@AdsApplication)
            modules(appModule, viewModelModule)
        }

        // 启动时清理过期的 AI 缓存（Room 磁盘层）
        cleanExpiredAiCache()

        // 启动时清理过期的图片磁盘缓存（Coil，7 天阈值）
        cleanExpiredImageCache()

        // 启动时预加载聊天历史（后台 IO 线程，不阻塞 UI）
        preloadChatHistory()
    }

    /**
     * 清理过期的 AI 缓存条目
     *
     * 在后台 IO 线程执行，不阻塞主线程。
     * 失败静默处理——缓存清理不是关键路径。
     */
    private fun cleanExpiredAiCache() {
        applicationScope.launch {
            try {
                val aiCacheManager: AiCacheManager =
                    org.koin.core.context.GlobalContext.get().get()
                aiCacheManager.cleanExpired()
            } catch (_: Exception) {
                // Koin 尚未完全初始化或 Room 数据库不可用，静默跳过
            }
        }
    }

    /**
     * 清理过期的图片磁盘缓存（Coil image_cache 目录）
     *
     * 删除 7 天前最后修改的缓存文件，释放磁盘空间。
     * Coil 自身的 DiskCache 基于 LRU 大小驱逐（200MB 上限），
     * 此方法补充时间维度的缓存淘汰。
     *
     * 在后台 IO 线程执行，不阻塞主线程。
     */
    private fun cleanExpiredImageCache() {
        applicationScope.launch {
            try {
                ImageLoaderConfig.cleanupOldCache(this@AdsApplication)
            } catch (_: Exception) {
                // 缓存目录不存在或无权限，静默跳过
            }
        }
    }

    /**
     * 预加载聊天历史到内存缓存
     *
     * 在应用启动时后台拉取最近一次会话的历史消息，
     * 通过 intent 重匹配恢复广告卡片，填入 [ChatMemoryCache]。
     *
     * ChatScreen 首次打开时若缓存已预热，可立即展示历史消息，
     * 无需网络请求和加载动画。
     *
     * 失败静默处理——ChatViewModel 在用户进入时走正常 loadHistory 路径。
     */
    private fun preloadChatHistory() {
        applicationScope.launch {
            try {
                val preloader: ChatPreloader =
                    org.koin.core.context.GlobalContext.get().get()
                preloader.preload()
            } catch (_: Exception) {
                // Koin 尚未完全初始化或网络不可用，静默跳过
            }
        }
    }
}
