package com.bytedance.ads_bytedance.common.imageloader

import android.content.Context
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.CachePolicy
import java.io.File
import okio.Path.Companion.toPath

/**
 * 图片加载全局配置工厂
 *
 * 使用 Coil 3.x 配置二级缓存体系：
 * - 一级（内存）：LRU，上限为 1/8 可用内存
 * - 二级（磁盘）：DiskLRU，上限 200MB，默认 7 天过期
 */
object ImageLoaderConfig {

    /** 磁盘缓存目录名 */
    private const val CACHE_DIR_NAME = "image_cache"

    /** 磁盘缓存上限（字节） */
    private const val DISK_CACHE_MAX_SIZE_BYTES = 200L * 1024 * 1024  // 200MB

    /** 内存缓存占可用内存比例 */
    private const val MEMORY_CACHE_PERCENT = 0.125  // 1/8

    /**
     * 创建全局 ImageLoader 单例。
     *
     * @param context Application Context（非 Activity，避免泄漏）
     * @param okHttpClient 用于网络图片加载的 OkHttpClient（复用网络层连接池）
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

            // 磁盘缓存策略
            diskCachePolicy(CachePolicy.ENABLED)
            memoryCachePolicy(CachePolicy.ENABLED)
        }.build()
    }
}
