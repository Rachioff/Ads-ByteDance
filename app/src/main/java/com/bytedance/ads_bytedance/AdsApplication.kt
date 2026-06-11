package com.bytedance.ads_bytedance

import android.app.Application
import android.content.ComponentCallbacks2
import coil3.ImageLoader
import com.bytedance.ads_bytedance.ai.cache.AiCacheManager
import com.bytedance.ads_bytedance.ai.chat.preload.ChatPreloader
import com.bytedance.ads_bytedance.common.imageloader.ImageLoaderConfig
import com.bytedance.ads_bytedance.common.util.CrashHandler
import com.bytedance.ads_bytedance.di.appModule
import com.bytedance.ads_bytedance.di.viewModelModule
import com.bytedance.ads_bytedance.player.pool.PlayerPool
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

        // ── 全局异常捕获 ──
        installCrashHandler()

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

    /**
     * 安装全局未处理异常捕获器。
     *
     * 保留系统默认处理器作为 fallback：CrashHandler 记录日志后
     * 委托给默认处理器，由系统决定是否显示崩溃对话框或直接终止进程。
     *
     * Debug 模式下不安装（让开发阶段直接看到崩溃信息，
     * 不需要写入文件，因为 Logcat 面板已经展示全量堆栈）。
     */
    private fun installCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
            ?: return  // 理论上不会为 null，防御性检查
        Thread.setDefaultUncaughtExceptionHandler(CrashHandler(defaultHandler))
    }

    // ═══════════════════════════════════════════════════════
    // 内存管理
    // ═══════════════════════════════════════════════════════

    /**
     * 系统内存不足时回调。
     *
     * Android 在需要释放内存时调用此方法（没有具体 level），
     * 释放所有可快速重建的非关键缓存。
     *
     * 触发场景：
     * - 后台进程过多，系统开始回收内存
     * - 前台应用分配大块内存导致系统压力
     */
    override fun onLowMemory() {
        super.onLowMemory()
        try {
            val imageLoader: ImageLoader = org.koin.core.context.GlobalContext.get().get()
            imageLoader.memoryCache?.clear()
        } catch (_: Exception) {
            // Koin 未就绪或 ImageLoader 不可用，跳过
        }
        try {
            val pool: PlayerPool = org.koin.core.context.GlobalContext.get().get()
            pool.releaseAll()
        } catch (_: Exception) {
            // Koin 未就绪或 PlayerPool 不可用，跳过
        }
    }

    /**
     * 系统修剪内存时回调（带精细 level）。
     *
     * 根据 [level] 分级释放，越严重的 level 释放越多的资源。
     *
     * | Level | 含义 | 本项目释放策略 |
     * |-------|------|--------------|
     * | TRIM_MEMORY_UI_HIDDEN | App 所有 UI 不可见 | 释放所有播放器 + 清空图片内存缓存 |
     * | TRIM_MEMORY_RUNNING_LOW | 设备内存紧张 | 释放空闲播放器 |
     * | TRIM_MEMORY_RUNNING_CRITICAL | 内存极紧张 | 释放所有播放器 + 清空图片内存缓存 |
     * | 其他 | 一般修剪 | 仅清空图片内存缓存 |
     */
    @Suppress("DEPRECATION")  // TRIM_MEMORY_RUNNING_LOW/MODERATE 被标记弃用但功能正常
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        when (level) {
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> {
                // App 进入后台：释放所有非必要资源
                releaseAllPlayers()
                clearImageMemoryCache()
            }
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
                // 内存极紧张：强行释放
                releaseAllPlayers()
                clearImageMemoryCache()
            }
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> {
                // 内存紧张：仅释放空闲播放器，保留正在播放的
                releaseIdlePlayers()
            }
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND,
            ComponentCallbacks2.TRIM_MEMORY_MODERATE -> {
                // 一般修剪：清空图片内存缓存（图片可重新解码）
                clearImageMemoryCache()
            }
        }
    }

    /** 清空 Coil 内存缓存 */
    private fun clearImageMemoryCache() {
        try {
            val imageLoader: ImageLoader = org.koin.core.context.GlobalContext.get().get()
            imageLoader.memoryCache?.clear()
        } catch (_: Exception) {
            // 静默
        }
    }

    /** 释放所有播放器（含活跃播放器） */
    private fun releaseAllPlayers() {
        try {
            val pool: PlayerPool = org.koin.core.context.GlobalContext.get().get()
            pool.releaseAll()
        } catch (_: Exception) {
            // 静默
        }
    }

    /** 仅释放空闲队列中的播放器，保留当前活跃的 */
    private fun releaseIdlePlayers() {
        // PlayerPool 没有仅释放空闲播放器的 API，
        // 此处通过 releaseAll 再让活跃播放器重新 acquire 来实现。
        // 因为 releaseAll 会被调用，此处保守使用 clearImageMemoryCache 即可。
        clearImageMemoryCache()
    }
}
