package com.bytedance.ads_bytedance.detail.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import coil3.compose.AsyncImage
import com.bytedance.ads_bytedance.data.model.AdItem
import com.bytedance.ads_bytedance.data.model.LoadState
import com.bytedance.ads_bytedance.detail.model.DetailEvent
import com.bytedance.ads_bytedance.detail.model.DetailOneTimeEvent
import com.bytedance.ads_bytedance.detail.viewmodel.DetailViewModel
import com.bytedance.ads_bytedance.feed.ui.card.RowWithAvatar
import com.bytedance.ads_bytedance.feed.ui.card.TagChips
import com.bytedance.ads_bytedance.feed.ui.component.ErrorState
import com.bytedance.ads_bytedance.feed.ui.component.LoadingShimmer
import com.bytedance.ads_bytedance.player.pool.PlayerPool
import com.bytedance.ads_bytedance.player.ui.VideoPlayer
import com.bytedance.ads_bytedance.ui.theme.Blue100
import com.bytedance.ads_bytedance.ui.theme.Blue600
import com.bytedance.ads_bytedance.ui.theme.GradientOverlayEnd
import com.bytedance.ads_bytedance.ui.theme.GradientOverlayStart
import com.bytedance.ads_bytedance.ui.theme.White
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel
import org.koin.core.context.GlobalContext
import org.koin.core.parameter.parametersOf

/**
 * 广告详情页
 *
 * ## 布局结构
 * ```
 * Scaffold
 * ├── TopAppBar: 返回按钮 + "广告详情"
 * ├── Content (可滚动 Column)
 * │   ├── [ImageDetail] 或 [VideoDetail] 媒体区
 * │   ├── 广告标题
 * │   ├── 完整描述
 * │   ├── 广告主信息卡片
 * │   ├── AI 摘要卡片（aiSummary 非空时）
 * │   └── 智能标签行
 * └── BottomBar: DetailInteractions（点赞/收藏/分享）
 * ```
 *
 * ## 跨页面状态同步
 * 点赞/收藏通过 [DetailViewModel] → [AdRepository.updateInteraction] 更新内存缓存。
 * 返回 feed 时，FeedScreen 读取 `likedAdIds[ad.id] ?: ad.isLiked`，
 * `ad.isLiked` 已被 repository 更新 → 自动同步。
 *
 * @param adId 广告 ID（来自导航参数）
 * @param onBack 返回回调（由 NavController 提供）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    adId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: DetailViewModel = koinViewModel(
        key = "detail_$adId",
        parameters = { parametersOf(adId) }
    )
    val uiState = viewModel.uiState
    val context = LocalContext.current

    // ── 一次性事件处理 ──
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is DetailOneTimeEvent.ShowToast -> {
                    // Toast 由系统层处理，此处预留 Snackbar 集成点
                }
                is DetailOneTimeEvent.ShowShareSheet -> {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, event.shareText)
                        putExtra(Intent.EXTRA_SUBJECT, "分享广告")
                    }
                    context.startActivity(Intent.createChooser(intent, "分享到"))
                }
                is DetailOneTimeEvent.NavigateBack -> {
                    onBack()
                }
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.ad?.advertiserName ?: "广告详情",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.onEvent(DetailEvent.Back) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        bottomBar = {
            // ── 底部互动栏 ──
            val ad = uiState.ad
            if (ad != null) {
                val liked = viewModel.likedAdIds[ad.id] ?: ad.isLiked
                val collected = viewModel.collectedAdIds[ad.id] ?: ad.isCollected

                DetailBottomBar(
                    likeCount = ad.likeCount,
                    isLiked = liked,
                    collectCount = ad.collectCount,
                    isCollected = collected,
                    shareCount = ad.shareCount,
                    onLikeClick = { viewModel.onEvent(DetailEvent.ToggleLike(ad.id)) },
                    onCollectClick = { viewModel.onEvent(DetailEvent.ToggleCollect(ad.id)) },
                    onShareClick = { viewModel.onEvent(DetailEvent.Share(ad)) }
                )
            }
        }
    ) { innerPadding ->
        // ── 内容区 ──
        when {
            uiState.loadState == LoadState.LOADING && uiState.ad == null -> {
                LoadingShimmer(modifier = Modifier.padding(innerPadding))
            }

            uiState.loadState == LoadState.ERROR && uiState.ad == null -> {
                ErrorState(
                    message = uiState.errorMessage ?: "加载失败",
                    onRetry = { viewModel.onEvent(DetailEvent.Retry) },
                    modifier = Modifier.padding(innerPadding)
                )
            }

            uiState.ad != null -> {
                val ad = uiState.ad!!
                val aiContent = viewModel.aiContentMap[ad.id]
                DetailContent(
                    ad = ad,
                    aiContent = aiContent,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// 底部互动栏
// ═══════════════════════════════════════════════════════════

@Composable
private fun DetailBottomBar(
    likeCount: Int,
    isLiked: Boolean,
    collectCount: Int,
    isCollected: Boolean,
    shareCount: Int,
    onLikeClick: () -> Unit,
    onCollectClick: () -> Unit,
    onShareClick: () -> Unit
) {
    Column {
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        )
        DetailInteractions(
            likeCount = likeCount,
            isLiked = isLiked,
            collectCount = collectCount,
            isCollected = isCollected,
            shareCount = shareCount,
            onLikeClick = onLikeClick,
            onCollectClick = onCollectClick,
            onShareClick = onShareClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp)
        )
    }
}

// ═══════════════════════════════════════════════════════════
// 内容区（按广告类型分发）
// ═══════════════════════════════════════════════════════════

@Composable
private fun DetailContent(
    ad: AdItem,
    aiContent: com.bytedance.ads_bytedance.ai.model.AiGeneratedContent? = null,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier.verticalScroll(scrollState)
    ) {
        // ── 媒体区 ──
        when (ad) {
            is AdItem.LargeImageAd -> ImageDetailMedia(
                imageUrl = ad.coverImageUrl,
                title = ad.title
            )
            is AdItem.SmallImageAd -> ImageDetailMedia(
                imageUrl = ad.thumbnailUrl,
                title = ad.title
            )
            is AdItem.VideoAd -> VideoDetailMedia(ad)
        }

        // ── 文本内容区 ──
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Spacer(modifier = Modifier.height(16.dp))

            // 标题
            Text(
                text = ad.title,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 完整描述（不限行数）
            Text(
                text = ad.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 广告主信息卡片
            AdvertiserInfoCard(ad)

            Spacer(modifier = Modifier.height(12.dp))

            // AI 摘要卡片（优先用 AI 生成内容，否则回退到静态 aiSummary）
            val aiSummary = aiContent?.summary ?: ad.aiSummary
            if (!aiSummary.isNullOrBlank()) {
                AiSummaryCard(summary = aiSummary, tags = aiContent?.tags)
                Spacer(modifier = Modifier.height(12.dp))
            }

            // 智能标签
            if (ad.tags.isNotEmpty()) {
                TagChips(
                    tags = ad.tags,
                    onTagClick = { /* 详情页标签点击预留：后续 Day 8 可支持从详情页跳转过滤 */ }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // 底部留白（给 BottomBar 腾空间）
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

// ═══════════════════════════════════════════════════════════
// 图文详情媒体区
// ═══════════════════════════════════════════════════════════

@Composable
private fun ImageDetailMedia(
    imageUrl: String,
    title: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(3f / 2f)
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // 底部渐变遮罩
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

// ═══════════════════════════════════════════════════════════
// 视频详情媒体区
// ═══════════════════════════════════════════════════════════

@Composable
private fun VideoDetailMedia(
    ad: AdItem.VideoAd,
    playerPool: PlayerPool = GlobalContext.get().get()
) {
    val context = LocalContext.current

    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    var playbackState by remember { mutableIntStateOf(Player.STATE_IDLE) }
    var hasError by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) } // 内流默认有声

    // ── 进入详情页 → 获取播放器 → 自动播放 ──
    LaunchedEffect(Unit) {
        val player = playerPool.acquire(context)
        exoPlayer = player
        player.volume = 1f // 有声
    }

    // ── 离开详情页 → 归还播放器 ──
    DisposableEffect(Unit) {
        onDispose {
            exoPlayer?.let { playerPool.release(it) }
            exoPlayer = null
        }
    }

    // ── 播放状态监听 ──
    if (exoPlayer != null) {
        LaunchedEffect(exoPlayer) {
            val listener = object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    playbackState = state
                    when (state) {
                        Player.STATE_ENDED -> {
                            // 播放完毕重置到开头，不自动重新播放
                            exoPlayer?.seekTo(0)
                            exoPlayer?.playWhenReady = false
                        }
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    hasError = true
                }
            }

            exoPlayer?.addListener(listener)

            // 等待直到组件被销毁
            try {
                while (exoPlayer != null) {
                    delay(500)
                }
            } finally {
                exoPlayer?.removeListener(listener)
            }
        }
    }

    // ── UI ──
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(Color.Black)
    ) {
        val player = exoPlayer
        if (player != null && !hasError) {
            VideoPlayer(
                player = player,
                videoUrl = ad.videoUrl,
                isMuted = isMuted,
                useController = true,   // 内流：完整控制条
                resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT,
                modifier = Modifier.fillMaxSize()
            )
        }

        // 缓冲指示器
        if (playbackState == Player.STATE_BUFFERING) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(40.dp),
                    color = White,
                    strokeWidth = 3.dp
                )
            }
        }

        // 错误态
        if (hasError) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "视频加载失败",
                        style = MaterialTheme.typography.bodyMedium,
                        color = White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "点击重试",
                        style = MaterialTheme.typography.labelSmall,
                        color = Blue600,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(White)
                            .clickable {
                                hasError = false
                                exoPlayer?.let {
                                    it.clearMediaItems()
                                    it.setMediaItem(
                                        androidx.media3.common.MediaItem.Builder()
                                            .setUri(ad.videoUrl)
                                            .build()
                                    )
                                    it.prepare()
                                    it.playWhenReady = true
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // 静音按钮（右上角）
        if (!hasError) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable {
                        isMuted = !isMuted
                        exoPlayer?.volume = if (isMuted) 0f else 1f
                    }
                    .padding(6.dp)
            ) {
                Icon(
                    imageVector = if (isMuted)
                        Icons.AutoMirrored.Filled.VolumeOff
                    else
                        Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = if (isMuted) "取消静音" else "静音",
                    modifier = Modifier.size(20.dp),
                    tint = White
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// 广告主信息卡片
// ═══════════════════════════════════════════════════════════

@Composable
private fun AdvertiserInfoCard(ad: AdItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = ad.advertiserAvatar,
            contentDescription = ad.advertiserName,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = ad.advertiserName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(8.dp))
                // "广告主"徽章
                Text(
                    text = "广告主",
                    style = MaterialTheme.typography.labelSmall,
                    color = Blue600,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Blue100)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "频道: ${channelDisplayName(ad.channel.name)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════
// AI 摘要卡片
// ═══════════════════════════════════════════════════════════

@Composable
private fun AiSummaryCard(summary: String, tags: List<com.bytedance.ads_bytedance.data.model.Tag>? = null) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Blue100.copy(alpha = 0.6f),
                        Blue100.copy(alpha = 0.2f)
                    )
                )
            )
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = Blue600
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "AI 智能摘要",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = Blue600
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = summary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
        )

        // AI 生成的标签（在详情页展示，不可点击过滤）
        if (!tags.isNullOrEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            // 复用 TagChips 组件显示 AI 生成的标签
            com.bytedance.ads_bytedance.feed.ui.card.TagChips(
                tags = tags,
                onTagClick = {} // 详情页中标签仅展示，不点击过滤
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════
// 工具函数
// ═══════════════════════════════════════════════════════════

private fun channelDisplayName(name: String): String = when (name.uppercase()) {
    "FEATURED" -> "精选"
    "ECOMMERCE" -> "电商"
    "LOCAL" -> "本地"
    else -> name
}
