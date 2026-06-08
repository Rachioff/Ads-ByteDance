package com.bytedance.ads_bytedance.player.pool

import android.content.Context
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * ExoPlayer 实例池 — 管理信息流中视频播放器的复用
 *
 * ## 设计目标
 * - 池大小上限 = 3（覆盖"当前播放 + 上一个（即将滑走） + 下一个（即将滑入）"场景）
 * - 同一时刻最多 1 个播放器处于 playWhenReady=true（外流特性）
 * - acquire() 优先从空闲队列取出已创建的实例，避免重复创建 ExoPlayer 的开销
 * - release() 将播放器重置后放回池中（stop + clearMediaItems + clearVideoSurface）
 *
 * ## 为什么不能无限制创建 ExoPlayer？
 * 每个 ExoPlayer 实例内部持有：
 * - 独立的解码线程（DefaultRenderersFactory 创建）
 * - 独立的媒体缓冲区（DefaultLoadControl 管理）
 * - 绑定的 Surface（用于视频渲染）
 * 3 个实例已在内存中占 ~100-150MB。无限制创建会在快速滑动多视频时触发 OOM。
 *
 * ## 线程安全
 * - [ConcurrentLinkedQueue] 保证空闲队列的并发安全
 * - acquire()/release() 可能在主线程（Compose 事件）或播放器回调线程调用
 * - ExoPlayer 的 playWhenReady/stop/clearMediaItems 等方法是线程安全的
 */
class PlayerPool(private val maxPoolSize: Int = 3) {

    /** 空闲播放器队列 */
    private val availablePlayers = ConcurrentLinkedQueue<ExoPlayer>()

    /** 当前活跃播放器（playWhenReady=true 的那个，最多 1 个） */
    @Volatile
    var activePlayer: ExoPlayer? = null
        private set

    /** 已创建总数计数器（用于日志和内存监控） */
    private val createdCount = AtomicInteger(0)

    // ═══════════════════════════════════════════════════════
    // 公共 API
    // ═══════════════════════════════════════════════════════

    /**
     * 获取一个可用播放器
     *
     * 优先级：
     * 1. 从空闲队列取出已有实例（零创建开销）
     * 2. 池中无空闲实例 → 创建新的 ExoPlayer
     *
     * 无论哪种路径，返回的播放器都已配置为：
     * - 默认静音（volume=0，外流特性）
     * - 不循环播放（REPEAT_MODE_OFF）
     *
     * 同时，如果之前有活跃播放器，将其暂停（保证最多 1 个同时播放）。
     *
     * @param context Android Context，传递给 ExoPlayer.Builder
     * @return 可用的 ExoPlayer 实例
     */
    fun acquire(context: Context): ExoPlayer {
        // 暂停当前活跃播放器（同一时刻只允许 1 个播放）
        activePlayer?.playWhenReady = false

        // 1. 尝试从空闲队列获取已有实例
        availablePlayers.poll()?.let { cached ->
            cached.volume = 0f
            cached.repeatMode = Player.REPEAT_MODE_OFF
            activePlayer = cached
            return cached
        }

        // 2. 创建新实例
        val player = ExoPlayer.Builder(context).build().apply {
            volume = 0f
            repeatMode = Player.REPEAT_MODE_OFF
        }
        createdCount.incrementAndGet()
        activePlayer = player
        return player
    }

    /**
     * 归还播放器到池中
     *
     * 执行步骤：
     * 1. stop() — 停止播放，释放解码器
     * 2. clearMediaItems() — 清除播放列表，释放 MediaSource
     * 3. clearVideoSurface() — 断开 Surface 绑定，防止 Surface 销毁后渲染崩溃
     * 4. 放入空闲队列（或超出上限时直接 release 销毁）
     *
     * @param player 要归还的播放器
     */
    fun release(player: ExoPlayer) {
        // 重置到初始状态
        player.stop()
        player.clearMediaItems()
        player.clearVideoSurface()

        if (activePlayer == player) {
            activePlayer = null
        }

        // 池未满 → 放回复用；已满 → 永久销毁
        if (availablePlayers.size < maxPoolSize) {
            availablePlayers.offer(player)
        } else {
            player.release()
        }
    }

    /**
     * 释放池中所有播放器资源
     *
     * 触发时机：Activity onDestroy / 进程被杀死前的 onTrimMemory
     */
    fun releaseAll() {
        activePlayer?.let {
            it.stop()
            it.clearMediaItems()
            it.clearVideoSurface()
            it.release()
        }
        activePlayer = null

        while (true) {
            val player = availablePlayers.poll() ?: break
            player.release()
        }
    }

    // ═══════════════════════════════════════════════════════
    // 监控 API
    // ═══════════════════════════════════════════════════════

    /** 已创建的 ExoPlayer 总数（含已销毁的） */
    fun totalCreated(): Int = createdCount.get()

    /** 当前空闲队列中的可用实例数 */
    fun availableCount(): Int = availablePlayers.size
}
