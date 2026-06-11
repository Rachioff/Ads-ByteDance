package com.bytedance.ads_bytedance.feed.ui.card

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import coil3.compose.AsyncImage
import com.bytedance.ads_bytedance.data.model.AdItem
import com.bytedance.ads_bytedance.player.pool.PlayerPool
import com.bytedance.ads_bytedance.player.ui.VideoPlayer
import com.bytedance.ads_bytedance.ui.theme.Blue600
import com.bytedance.ads_bytedance.ui.theme.ErrorRed
import com.bytedance.ads_bytedance.ui.theme.GradientOverlayEnd
import com.bytedance.ads_bytedance.ui.theme.GradientOverlayStart
import com.bytedance.ads_bytedance.ui.theme.White
import kotlinx.coroutines.delay
import org.koin.core.context.GlobalContext

/**
 * 视频广告卡片 — 带 ExoPlayer 播放控制
 *
 * **状态机**：
 * ```
 *                    ┌─────────────────────────┐
 *                    │      封面态 IDLE          │
 *                    │  AsyncImage + 播放按钮    │
 *                    └───────────┬───────────────┘
 *                                │ 点击播放按钮
 *                                ▼
 *                    ┌─────────────────────────┐
 *                    │    缓冲态 BUFFERING       │
 *                    │  PlayerView + 转圈 loading│
 *                    └───────────┬───────────────┘
 *                                │ 首帧就绪
 *                     ┌──────────┴──────────┐
 *                     ▼                     ▼
 *           ┌──────────────┐      ┌──────────────────┐
 *           │  播放中 PLAYING│      │  暂停 PAUSED      │
 *           │  控制层可见    │ ───→ │ 封面覆盖 + 播放按钮 │
 *           │  静音/可拖进度   │ ←─── │ 进度保留，可继续    │
 *           └──────┬───────┘      └──────────────────┘
 *                  │ 播放完毕 / 出错
 *                  ▼
 *           ┌──────────────┐
 *           │   回到封面态   │
 *           │ (release player)│
 *           └──────────────┘
 * ```
 *
 * **暂停时为什么叠加封面而非切回 Crossfade？**
 * Crossfade 切回封面态会销毁 PlayerView（Surface 被回收）→ 继续播放
 * 时必须重建 Surface → 重新绑定 → 可能出现短暂黑闪。
 * 叠加方案保持 PlayerView 存活，只是在其上方盖一层不透明的封面图，
 * 继续播放时移除覆盖层即可，零延迟恢复。
 *
 * **TextureView（非 SurfaceView）是关键**：
 * 见 [com.bytedance.ads_bytedance.player.ui.VideoPlayer] 的 KDoc。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VideoCard(
    ad: AdItem.VideoAd,
    aiContent: com.bytedance.ads_bytedance.ai.model.AiGeneratedContent? = null,
    onLikeClick: () -> Unit,
    onCollectClick: () -> Unit,
    onShareClick: () -> Unit,
    onTagClick: (String) -> Unit,
    onCardClick: () -> Unit,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier,
    isScrollInProgress: Boolean = false,
    nextVideoCoverUrl: String? = null,  // 下一个视频的封面 URL，用于预加载
    playerPool: PlayerPool = GlobalContext.get().get()
) {
    // ═══════════════════════════════════════════════════════
    // 播放状态
    // ═══════════════════════════════════════════════════════

    /** 当前持有的 ExoPlayer 实例（null = 封面态） */
    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }

    /** 播放器是否已获取（用于 Crossfade 封面态 ↔ 播放器态） */
    val isActive = exoPlayer != null

    /** 播放器就绪状态：IDLE=0, BUFFERING=2, READY=3, ENDED=4 */
    var playbackState by remember { mutableIntStateOf(Player.STATE_IDLE) }

    /** 是否正在缓冲中 */
    val isBuffering = playbackState == Player.STATE_BUFFERING

    /** 是否播放出错 */
    var hasError by remember { mutableStateOf(false) }

    /** 错误详细信息（用于调试和用户提示） */
    var errorMessage by remember { mutableStateOf<String?>(null) }

    /** 静音状态（外流默认静音） */
    var isMuted by remember { mutableStateOf(true) }

    /** 播放进度 0f..1f */
    var progress by remember { mutableFloatStateOf(0f) }

    /** 播放器是否处于 playWhenReady=true */
    var playWhenReady by remember { mutableStateOf(true) }

    /** 是否正在拖动进度条（拖动时暂停 progress 同步，避免与手指冲突） */
    var isDragging by remember { mutableStateOf(false) }

    /**
     * 重试信号计数器 — 每次重试递增，通知 [VideoPlayer] 强制重新 setup。
     *
     * ## 为什么不能简单 release → acquire 来重试？
     * retry() 中先 release(player) 再 acquire(context) 时，池会返回**同一个**
     * ExoPlayer 实例。在同一帧内 `exoPlayer` 从 `PlayerA → null → PlayerA`
     * (同一实例)，Compose snapshot 只看到最终值没变 → Crossfade 不切换
     * → VideoPlayer 不重建 → `lastSetupPlayer` 仍匹配 → `setupMedia()` 永远
     * 不被调用 → 重试无效。
     *
     * 解决方案：**不归还池**，保留 Surface 绑定，通过递增此计数器
     * 通知 VideoPlayer 重置 `lastSetupPlayer` 并重新执行 setupMedia。
     */
    var retryTrigger by remember { mutableIntStateOf(0) }

    val context = LocalContext.current

    // ═══════════════════════════════════════════════════════
    // 生命周期：离开屏幕时归还播放器
    // ═══════════════════════════════════════════════════════

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer?.let { playerPool.release(it) }
            exoPlayer = null
        }
    }

    // ═══════════════════════════════════════════════════════
    // 播放器状态监听 + 进度同步
    // ═══════════════════════════════════════════════════════

    if (isActive) {
        LaunchedEffect(exoPlayer) {
            val listener = object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    playbackState = state
                    when (state) {
                        Player.STATE_READY -> {
                            hasError = false
                        }
                        Player.STATE_ENDED -> {
                            // 播放完毕 → 归还 → 回到封面态
                            exoPlayer?.let { playerPool.release(it) }
                            exoPlayer = null
                        }
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    hasError = true
                    errorMessage = error.localizedMessage
                        ?: "errorCode=${error.errorCodeName}"
                }
            }

            exoPlayer?.addListener(listener)

            // 进度同步循环
            while (exoPlayer != null) {
                val player = exoPlayer ?: break
                val duration = player.duration
                if (duration > 0 && !isDragging) {
                    progress = player.currentPosition.toFloat() / duration.toFloat()
                }
                delay(200)
            }

            // 清理监听器
            exoPlayer?.removeListener(listener)
        }
    }

    // ═══════════════════════════════════════════════════════
    // 播放控制回调
    // ═══════════════════════════════════════════════════════

    val startPlayback: () -> Unit = {
        val player = playerPool.acquire(context)
        exoPlayer = player
        // 设置为缓冲态，让 UI 显示 loading 指示器
        // 实际播放初始化推迟到 VideoPlayer 的 PlayerView 布局完成
        // （Surface 就绪后由 VideoPlayer.post {} 执行 setMediaItem + prepare）
        playbackState = Player.STATE_BUFFERING
        playWhenReady = true
        hasError = false
        errorMessage = null
        onPlayClick()

        // ── 预加载下一个视频的封面到磁盘缓存 ──
        // 当前视频开始播放时，后台异步缓存下一个视频封面，
        // 用户滑到下一个视频时封面已就绪，无需等待网络加载。
        nextVideoCoverUrl?.let { nextUrl ->
            try {
                val imageLoader: coil3.ImageLoader =
                    org.koin.core.context.GlobalContext.get().get()
                val preloadRequest = coil3.request.ImageRequest.Builder(context)
                    .data(nextUrl)
                    .build()
                imageLoader.enqueue(preloadRequest)
            } catch (_: Exception) {
                // Koin 未就绪或 ImageLoader 不可用，跳过预加载
            }
        }
    }

    val togglePlayPause: () -> Unit = {
        exoPlayer?.let { player ->
            val newState = !playWhenReady
            player.playWhenReady = newState
            playWhenReady = newState
        }
    }

    val toggleMute: () -> Unit = {
        isMuted = !isMuted
        exoPlayer?.volume = if (isMuted) 0f else 1f
    }

    /** 重试：重置当前 player → 重新 setup（不入池，保留 Surface 绑定） */
    val retry: () -> Unit = {
        exoPlayer?.let { player ->
            player.stop()
            player.clearMediaItems()
        }
        playbackState = Player.STATE_BUFFERING
        playWhenReady = true
        hasError = false
        errorMessage = null
        retryTrigger++
    }

    // ═══════════════════════════════════════════════════════
    // UI 布局
    // ═══════════════════════════════════════════════════════

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onCardClick)
    ) {
        // ── 视频区（16:9）──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
        ) {
            Crossfade(
                targetState = isActive,
                animationSpec = tween(durationMillis = 300),
                label = "video_player_crossfade"
            ) { active ->
                if (active) {
                    // ═══════════════════════════════════════
                    // 播放器态：PlayerView + 多层覆盖
                    // ═══════════════════════════════════════
                    val player = exoPlayer ?: return@Crossfade

                    Box(modifier = Modifier.fillMaxSize()) {
                        // ── 第0层：ExoPlayer TextureView ──
                        VideoPlayer(
                            player = player,
                            videoUrl = ad.videoUrl,
                            isMuted = isMuted,
                            retryTrigger = retryTrigger,
                            useController = false,
                            modifier = Modifier.fillMaxSize()
                        )

                        // ── 第1层：缓冲 loading ──
                        if (isBuffering) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.3f)),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(32.dp),
                                    color = White,
                                    strokeWidth = 3.dp
                                )
                            }
                        }

                        // ── 第1层 alt：暂停时的封面覆盖 ──
                        if (!playWhenReady && !isBuffering && !hasError) {
                            // 封面图 + 渐变遮罩
                            Box(modifier = Modifier.fillMaxSize()) {
                                AsyncImage(
                                    model = ad.coverImageUrl,
                                    contentDescription = ad.title,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(120.dp)
                                        .align(Alignment.BottomCenter)
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(GradientOverlayStart, GradientOverlayEnd)
                                            )
                                        )
                                )
                            }
                        }

                        // ── 第2层：错误态 ──
                        if (hasError) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.7f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "加载失败",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = White
                                    )
                                    val errMsg = errorMessage
                                    if (errMsg != null) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = errMsg,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = White.copy(alpha = 0.7f),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "点击重试",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Blue600,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(White)
                                            .clickable { retry() }
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        // ── 第3层：暂停/继续播放按钮（❌ 错误时不显示）──
                        if (!playWhenReady && !hasError) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.85f))
                                    .clickable { togglePlayPause() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = "继续播放",
                                    modifier = Modifier.size(32.dp),
                                    tint = Blue600
                                )
                            }
                        }

                        // ── 第3层：视频区域点击 + 静音 + 进度（错误时全部隐藏，避免拦截错误UI的点击事件）──
                        if (!hasError) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        togglePlayPause()
                                    }
                            )

                            // ── 静音按钮（右上角）──
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.Black.copy(alpha = 0.55f))
                                    .clickable { toggleMute() }
                                    .padding(6.dp)
                            ) {
                                Icon(
                                    imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff
                                    else Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = if (isMuted) "取消静音" else "静音",
                                    modifier = Modifier.size(20.dp),
                                    tint = White
                                )
                            }
                        }

                        // ── 第3层：可拖动进度条（底部，播放中或拖动中可见）──
                        if ((playWhenReady || isDragging) && !hasError) {
                            Slider(
                                value = progress,
                                onValueChange = { newValue ->
                                    progress = newValue
                                    isDragging = true
                                },
                                onValueChangeFinished = {
                                    isDragging = false
                                    exoPlayer?.let { player ->
                                        val duration = player.duration
                                        if (duration > 0) {
                                            player.seekTo((progress * duration).toLong())
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth(),
                                colors = SliderDefaults.colors(
                                    thumbColor = Blue600,
                                    activeTrackColor = Blue600,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.4f),
                                ),
                            )
                        }
                    }
                } else {
                    // ═══════════════════════════════════════
                    // 封面态：封面图 + 播放按钮
                    // ═══════════════════════════════════════
                    Box(modifier = Modifier.fillMaxSize()) {
                        AsyncImage(
                            model = ad.coverImageUrl,
                            contentDescription = ad.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .align(Alignment.BottomCenter)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(GradientOverlayStart, GradientOverlayEnd)
                                    )
                                )
                        )

                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.85f))
                                .clickable { startPlayback() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = "播放视频",
                                modifier = Modifier.size(32.dp),
                                tint = Blue600
                            )
                        }

                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(10.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.Black.copy(alpha = 0.55f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "视频",
                                style = MaterialTheme.typography.labelSmall,
                                color = White,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        // ── 封面下方内容（标题 / 描述 / 标签 / 互动栏）──
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            RowWithAvatar(
                avatarUrl = ad.advertiserAvatar,
                name = ad.advertiserName
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = ad.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (ad.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = ad.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // AI 摘要（优先用 AI 生成内容，否则回退到静态 aiSummary）
            val summaryText = aiContent?.summary ?: ad.aiSummary
            if (!summaryText.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                AiSummaryLabel(summary = summaryText)
            }

            if (ad.tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                TagChips(tags = ad.tags, onTagClick = onTagClick)
            }

            Spacer(modifier = Modifier.height(10.dp))

            CardInteractions(
                likeCount = ad.likeCount,
                isLiked = ad.isLiked,
                collectCount = ad.collectCount,
                isCollected = ad.isCollected,
                onLikeClick = onLikeClick,
                onCollectClick = onCollectClick,
                onShareClick = onShareClick
            )
        }
    }
}
