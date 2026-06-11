package com.bytedance.ads_bytedance.ai.chat.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.bytedance.ads_bytedance.ai.chat.model.ChatRole
import com.bytedance.ads_bytedance.ai.chat.model.ChatUiMessage
import com.bytedance.ads_bytedance.ai.chat.model.AdContext
import com.bytedance.ads_bytedance.ai.chat.viewmodel.ChatViewModel
import com.bytedance.ads_bytedance.data.model.AdItem
import org.koin.androidx.compose.koinViewModel

/**
 * 对话式 AI 搜索页面
 *
 * ## 布局结构
 * ```
 * Scaffold
 * ├── TopAppBar: ← 返回 | "AI 对话搜索" | 清空按钮
 * ├── ContextAdCard（有广告上下文时显示）
 * ├── Content: LazyColumn
 * │   ├── 欢迎卡片（无消息时）
 * │   ├── 用户气泡（右对齐，主色背景）
 * │   ├── AI 气泡（左对齐，surface 背景）
 * │   │   └── 嵌入广告卡片（可点击 → 详情页）
 * │   └── 加载指示器
 * └── BottomBar: TextField + 发送按钮
 * ```
 *
 * @param adId 初始广告上下文 ID（从搜索结果"和AI讨论"入口传入）
 * @param onBack 返回上一页回调
 * @param onNavigateToDetail 导航到广告详情页回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    adId: String? = null,
    onBack: () -> Unit = {},
    onNavigateToDetail: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val viewModel: ChatViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    // 新消息到达时自动滚动到底部
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "AI 对话搜索",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    if (uiState.messages.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearConversation() }) {
                            Icon(
                                imageVector = Icons.Filled.DeleteOutline,
                                contentDescription = "清空对话"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        bottomBar = {
            ChatInputBar(
                value = uiState.inputText,
                onValueChange = { viewModel.updateInputText(it) },
                onSend = { viewModel.sendMessage(uiState.inputText) },
                enabled = !uiState.isLoading
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // ── 广告上下文卡片 ──
            uiState.contextAdItem?.let { contextAd ->
                ContextAdCard(
                    ad = contextAd,
                    onClick = { onNavigateToDetail(contextAd.id) }
                )
            }

            if (uiState.isLoadingHistory) {
                // 历史加载中
                Box(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("恢复对话中...", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            if (uiState.messages.isEmpty()) {
                // ── 欢迎态 ──
                WelcomeCard(isServiceAvailable = uiState.isServiceAvailable)
            } else {
                // ── 对话消息列表 ──
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = uiState.messages,
                        key = { it.id }
                    ) { message ->
                        ChatBubble(
                            message = message,
                            onAdClick = onNavigateToDetail
                        )
                    }

                    // 加载指示器
                    if (uiState.isLoading) {
                        item(key = "loading") {
                            LoadingBubble()
                        }
                    }

                    // 底部留白
                    item(key = "bottom_spacer") {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// 欢迎卡片
// ═══════════════════════════════════════════════════════════

/**
 * 搜索页面欢迎卡片
 *
 * 用户首次进入（无对话消息）时显示。
 * 根据服务可用性显示不同的欢迎文案。
 */
@Composable
private fun WelcomeCard(isServiceAvailable: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = if (isServiceAvailable) {
                        Icons.Filled.AutoAwesome
                    } else {
                        Icons.Filled.SearchOff
                    },
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = if (isServiceAvailable) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (isServiceAvailable) {
                        "你好！我是 AI 广告推荐助手 🎯"
                    } else {
                        "在线匹配模式"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (isServiceAvailable) {
                        "你可以用自然语言描述你想找的广告类型，\n" +
                            "例如：「推荐适合学生党的平价数码产品」\n\n" +
                            "我会理解你的需求并为你匹配最相关的广告。"
                    } else {
                        "AI 对话服务暂不可用，\n" +
                            "你的输入将通过在线数据匹配。\n\n" +
                            "试试输入关键词如「数码」「美食」「户外」等。"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// 聊天气泡
// ═══════════════════════════════════════════════════════════

/**
 * 聊天气泡组件
 *
 * 根据 [ChatUiMessage.role] 分发两种样式：
 * - USER：右对齐，主色背景，白色文字
 * - ASSISTANT：左对齐，surface 背景，带嵌入广告卡片
 *
 * @param message 消息数据
 * @param onAdClick 广告卡片点击回调
 */
@Composable
private fun ChatBubble(
    message: ChatUiMessage,
    onAdClick: (String) -> Unit
) {
    val isUser = message.role == ChatRole.USER

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        // AI 头像（仅 AI 消息显示）
        if (!isUser) {
            AiAvatar()
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            modifier = Modifier.widthIn(max = 300.dp),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            // 消息文本气泡
            Surface(
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isUser) 16.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 16.dp
                ),
                color = if (isUser) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                shadowElevation = 1.dp
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = message.content,
                        color = if (isUser) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 20.sp
                    )

                    // 降级标记
                    if (message.isFallback) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "⚠ 在线匹配结果",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            // 嵌入广告卡片（仅 AI 消息）
            if (!isUser && message.ads.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                message.ads.forEach { ad ->
                    EmbeddedAdCard(
                        ad = ad,
                        onClick = { onAdClick(ad.id) }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }

        if (isUser) {
            Spacer(modifier = Modifier.width(8.dp))
            // 用户头像占位（与 AI 侧对齐）
            UserAvatar()
        }
    }
}

// ═══════════════════════════════════════════════════════════
// 嵌入广告卡片
// ═══════════════════════════════════════════════════════════

/**
 * 对话流中嵌入的广告卡片
 *
 * 简化版卡片，展示缩略图 + 标题 + 广告主名称，
 * 点击后导航到详情页。
 */
@Composable
private fun EmbeddedAdCard(
    ad: AdItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 缩略图
            val imageUrl = when (ad) {
                is AdItem.LargeImageAd -> ad.coverImageUrl
                is AdItem.SmallImageAd -> ad.thumbnailUrl
                is AdItem.VideoAd -> ad.coverImageUrl
            }

            AsyncImage(
                model = imageUrl,
                contentDescription = ad.title,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(10.dp))

            // 文字信息
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = ad.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = ad.advertiserName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 箭头指示
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════
// 广告上下文卡片
// ═══════════════════════════════════════════════════════════

/**
 * 对话页顶部的广告上下文卡片
 *
 * 当用户从搜索结果或详情页点击"和AI讨论"进入时显示，
 * 提示当前讨论的广告对象，可点击进入详情页。
 */
@Composable
private fun ContextAdCard(
    ad: AdItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "正在讨论",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = ad.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// 头像
// ═══════════════════════════════════════════════════════════

@Composable
private fun AiAvatar() {
    Surface(
        modifier = Modifier.size(32.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = "AI",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun UserAvatar() {
    Surface(
        modifier = Modifier.size(32.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = "我",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════
// 加载指示器气泡
// ═══════════════════════════════════════════════════════════

@Composable
private fun LoadingBubble() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        AiAvatar()
        Spacer(modifier = Modifier.width(8.dp))
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = 4.dp,
                bottomEnd = 16.dp
            ),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shadowElevation = 1.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "思考中...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// 底部输入栏
// ═══════════════════════════════════════════════════════════

/**
 * 对话输入栏
 *
 * 圆角 TextField + 发送按钮，
 * 固定在 Scaffold.bottomBar 位置。
 */
@Composable
private fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean
) {
    Surface(
        shadowElevation = 4.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        text = "描述你想要的广告类型...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                },
                enabled = enabled,
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                    disabledIndicatorColor = Color.Transparent
                ),
                maxLines = 3
            )

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = {
                    if (value.isNotBlank() && enabled) {
                        onSend()
                    }
                },
                enabled = value.isNotBlank() && enabled
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "发送",
                    tint = if (value.isNotBlank() && enabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    }
                )
            }
        }
    }
}
