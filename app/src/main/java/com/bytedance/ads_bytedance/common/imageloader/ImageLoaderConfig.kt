package com.bytedance.ads_bytedance.common.imageloader

import android.content.Context
import android.util.Log
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.CachePolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import okio.Path.Companion.toPath

/**
 * 图片加载全局配置工厂
 *
 * 使用 Coil 3.x 配置二级缓存体系：
 * - 一级（内存）：LRU，上限为 1/8 可用内存
 * - 二级（磁盘）：DiskLRU，上限 200MB，通过 [cleanupOldCache] 实现 7 天过期清理
 *
 * ## 多线程安全
 * 本类的静态配置由 [AdsApplication] 在主线程初始化，缓存清理在 [Dispatchers.IO] 执行，
 * 不会阻塞主线程。
 */
object ImageLoaderConfig {

    /** 磁盘缓存目录名 */
    private const val CACHE_DIR_NAME = "image_cache"

    /** 磁盘缓存上限（字节） */
    private const val DISK_CACHE_MAX_SIZE_BYTES = 200L * 1024 * 1024  // 200MB

    /** 内存缓存占可用内存比例 */
    private const val MEMORY_CACHE_PERCENT = 0.125  // 1/8

    /** 缓存文件最大保留天数 */
    private const val MAX_CACHE_AGE_DAYS = 7

    private const val TAG = "ImageLoaderConfig"

    /**
     * 创建全局 ImageLoader 单例。
     *
     * @param context Application Context（非 Activity，避免泄漏）
     */
    fun createImageLoader(context: Context): ImageLoader {
        return ImageLoader.Builder(context).apply {
            // 一级缓存：内存 LRU
            memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, MEMORY_CACHE_PERCENT)
                    .build()
            }

            // 二级缓存：磁盘 DiskLRU
            diskCache {
                DiskCache.Builder()
                    .directory(File(context.cacheDir, CACHE_DIR_NAME).absolutePath.toPath())
                    .maxSizeBytes(DISK_CACHE_MAX_SIZE_BYTES)
                    .build()
            }

            // 缓存策略
            diskCachePolicy(CachePolicy.ENABLED)
            memoryCachePolicy(CachePolicy.ENABLED)
        }.build()
    }

    /**
     * 清理过期的磁盘缓存文件（超过 [MAX_CACHE_AGE_DAYS] 天未修改的文件）。
     *
     * 在 [Dispatchers.IO] 执行，不阻塞主线程。如果缓存目录不存在则跳过。
     * 此操作不影响体积限制（200MB 上限由 Coil DiskCache 自行维护）。
     *
     * ## 设计思路
     * Coil 3.x 的 DiskCache 基于 LRU 大小驱逐，不提供内建的"按时间过期"功能。
     * 本方法通过文件系统最后修改时间判断过期，补充时间维度的缓存淘汰。
     *
     * @param context Application Context
     * @param maxAgeDays 过期天数阈值，默认 7 天
     */
    suspend fun cleanupOldCache(context: Context, maxAgeDays: Int = MAX_CACHE_AGE_DAYS) {
        withContext(Dispatchers.IO) {
            try {
                val cacheDir = File(context.cacheDir, CACHE_DIR_NAME)
                if (!cacheDir.exists() || !cacheDir.isDirectory) return@withContext

                val cutoffTime = System.currentTimeMillis() - (maxAgeDays * 24 * 60 * 60 * 1000L)
                var deletedCount = 0
                var deletedBytes = 0L

                cacheDir.walkTopDown()
                    .filter { it.isFile && it.lastModified() < cutoffTime }
                    .forEach { file ->
                        val size = file.length()
                        if (file.delete()) {
                            deletedCount++
                            deletedBytes += size
                        }
                    }

                if (deletedCount > 0) {
                    Log.d(TAG, "清理过期缓存: $deletedCount 个文件, ${deletedBytes / 1024}KB")
                }
            } catch (e: Exception) {
                // 缓存清理失败不抛异常 —— 不影响主流程
                Log.w(TAG, "缓存清理失败: ${e.message}", e)
            }
        }
    }
}
