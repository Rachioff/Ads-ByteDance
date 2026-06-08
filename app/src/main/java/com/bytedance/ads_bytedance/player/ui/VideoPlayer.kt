package com.bytedance.ads_bytedance.player.ui

import android.view.LayoutInflater
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.bytedance.ads_bytedance.R

/**
 * ExoPlayer [PlayerView] 的 Compose 桥接组件
 *
 * ## 为什么需要 AndroidView？
 * Compose 的渲染管线直接操作 Canvas，不经过 Android View 系统。
 * 但 ExoPlayer 的视频解码输出需要写入一个 **Surface**（Android 原生图形缓冲区），
 * 而 Surface 只能由传统 View（SurfaceView / TextureView）持有。
 *
 * [PlayerView] 内部使用 SurfaceView（或 TextureView）持有这个 Surface。
 * [AndroidView] 充当 Compose 树与传统 View 树之间的"桥梁"——
 * 它将一个 Android View 挂载到 Compose 布局节点的对应位置。
 *
 * ## SurfaceView vs TextureView — 本项目选择 TextureView
 * ```
 * SurfaceView:                          TextureView:
 * ┌─────────────────┐                   ┌─────────────────┐
 * │  Compose 覆盖层  │ ← 按钮/进度条     │  Compose 覆盖层  │ ← 按钮/进度条正常显示
 * ├─────────────────┤                   ├─────────────────┤
 * │   ■ 黑洞 ■      │ ← Surface 打洞    │  TextureView    │ ← 正常 View 层级
 * │  (视频渲染区)    │    覆盖层被遮挡    │  (视频渲染区)    │    覆盖层正确叠放
 * └─────────────────┘                   └─────────────────┘
 * ```
 * - **SurfaceView**：独立窗口渲染（GPU 直通），性能最优。但"打洞"穿透整个 View 树，
 *   所有 Compose 覆盖层（进度条、静音按钮、播放图标）都会被遮挡。
 * - **TextureView**：GPU 渲染到纹理 → 合成到 View 层，多一次纹理拷贝（~1-2ms/帧），
 *   但覆盖层正确叠放。广告短视频场景此开销可忽略。
 *
 * **实现方式**：通过 `R.layout.player_view_texture` XML 布局设置
 * `app:surface_type="texture_view"`——因为 Media3 1.x 所有版本的
 * `setSurfaceType()` 方法和 `SURFACE_TYPE_TEXTURE_VIEW` 常量均为 `@hide`，
 * 但 XML 属性是公开 API。
 *
 * ## 为什么在 View.post {} 中初始化播放？
 * ExoPlayer 的 `prepare()` 必须在 Surface 可用之后调用，否则视频渲染器
 * 在没有 Surface 的情况下初始化 → 即使后续 PlayerView 创建了 TextureView
 * 并设置 Surface，播放器也可能不重新启用视频渲染 → **黑屏**。
 *
 * 时序问题：
 * ```
 * ❌ 旧代码：startPlayback() → prepare() → exoPlayer=player
 *           → Crossfade → VideoPlayer → PlayerView 创建（此时 Surface 尚未就绪）
 *           → Surface 已错过 prepare 窗口 → 视频轨道不渲染 → 黑屏
 *
 * ✅ 新代码：startPlayback() → exoPlayer=player → Crossfade → VideoPlayer
 *           → PlayerView 创建 + attach to window
 *           → TextureView.onSurfaceTextureAvailable → PlayerView 将 Surface
 *              传给 player.setVideoSurface()
 *           → View.post {}（布局完成之后）→ setMediaItem + prepare + play
 *           → 此时 Surface 已就绪 → 视频正常渲染
 * ```
 *
 * ## 重试机制
 * [retryTrigger] 由 [com.bytedance.ads_bytedance.feed.ui.card.VideoCard] 递增。
 * 触发时：重置 `lastSetupPlayer` → 通过 `view.post {}` 重新执行 setupMedia。
 * retry 时 player 实例不变（不入池，保留 Surface 绑定），所以必须用此信号
 * 而非依赖 player 实例引用的变化。
 *
 * @param player ExoPlayer 实例（由 [com.bytedance.ads_bytedance.player.pool.PlayerPool] 提供）
 * @param videoUrl 待播放的视频 URL，在 Surface 就绪后自动 setMediaItem + prepare
 * @param isMuted 初始静音状态
 * @param retryTrigger 重试信号计数器，每次递增触发重新 setupMedia
 * @param useController 是否显示 ExoPlayer 内置控制条（外流 false / 内流 true）
 * @param resizeMode 视频缩放模式，默认 FIT（保持比例，可能留黑边）
 * @param modifier Compose Modifier
 */
@Composable
fun VideoPlayer(
    player: ExoPlayer,
    videoUrl: String,
    isMuted: Boolean = true,
    retryTrigger: Int = 0,
    useController: Boolean = false,
    resizeMode: @AspectRatioFrameLayout.ResizeMode Int = AspectRatioFrameLayout.RESIZE_MODE_FIT,
    modifier: Modifier = Modifier
) {
    /** 记录上一次已完成 media setup 的 player 实例，防止重复 setup */
    var lastSetupPlayer by remember { mutableStateOf<ExoPlayer?>(null) }

    /** PlayerView 引用，用于 retry 时调用 view.post {} */
    var playerView by remember { mutableStateOf<PlayerView?>(null) }

    // ── 重试：当 retryTrigger 递增时，强制重新 setup ──
    if (retryTrigger > 0) {
        LaunchedEffect(retryTrigger) {
            val view = playerView ?: return@LaunchedEffect
            // 重置跟踪标记，让 post 中的检查通过
            lastSetupPlayer = null
            // 使用 view.post 确保 TextureView Surface 可用后再 setup
            view.post {
                if (lastSetupPlayer !== player) {
                    lastSetupPlayer = player
                    setupMedia(player, videoUrl, isMuted)
                }
            }
        }
    }

    // ── 离开组合树时的清理 ──
    DisposableEffect(player) {
        onDispose {
            // 不在此释放 player（生命周期由 PlayerPool 管理）
            // VideoCard 的 DisposableEffect 在离开屏幕前已调用
            // playerPool.release() → clearVideoSurface()，安全性已保证
        }
    }

    // ── AndroidView 桥接 ──
    AndroidView(
        factory = { context ->
            // 从 XML 布局 inflate PlayerView，利用 app:surface_type="texture_view"
            // 绕过 Media3 的 @hide Java API 限制
            val inflater = LayoutInflater.from(context)
            val pv = inflater.inflate(R.layout.player_view_texture, null) as PlayerView
            pv.player = player
            pv.useController = useController
            pv.controllerAutoShow = useController
            pv.resizeMode = resizeMode
            pv.controllerShowTimeoutMs = if (useController) 3000 else 0

            // 保存引用，供 retry LaunchedEffect 使用
            playerView = pv

            // ═══════════════════════════════════════════════════
            // 关键：推迟 setMediaItem + prepare 到布局完成之后
            // ═══════════════════════════════════════════════════
            // 此时 PlayerView 刚 inflate，尚未 attach to window，
            // TextureView 的 SurfaceTexture 也尚未创建。
            // View.post { } 将任务放入主线程消息队列，在当前帧
            // 的 measure/layout/draw 全部完成后执行。
            // 此时 TextureView.onSurfaceTextureAvailable 已回调 →
            // PlayerView 已将 Surface 传给 ExoPlayer → 安全调用 prepare。
            pv.post {
                if (lastSetupPlayer !== player) {
                    lastSetupPlayer = player
                    setupMedia(player, videoUrl, isMuted)
                }
            }

            pv
        },
        modifier = modifier,
        update = { view ->
            if (view.player !== player) {
                // player 实例变了（如离开屏幕后重新创建）
                view.player = player
                playerView = view
                // PlayerView 已经完成过布局，但为了安全仍使用 post 确保
                // TextureView surface 可用。
                view.post {
                    if (lastSetupPlayer !== player) {
                        lastSetupPlayer = player
                        setupMedia(player, videoUrl, isMuted)
                    }
                }
            }
            view.useController = useController
        }
    )
}

/**
 * 为 ExoPlayer 设置媒体源并开始播放
 *
 * 使用 [MediaItem.Builder] 替代已 deprecated 的 [MediaItem.fromUri]。
 */
private fun setupMedia(player: ExoPlayer, videoUrl: String, isMuted: Boolean) {
    val mediaItem = MediaItem.Builder()
        .setUri(videoUrl)
        .build()
    player.setMediaItem(mediaItem)
    player.prepare()
    player.playWhenReady = true
    player.volume = if (isMuted) 0f else 1f
}
