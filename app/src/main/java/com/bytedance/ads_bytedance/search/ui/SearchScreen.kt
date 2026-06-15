package com.bytedance.ads_bytedance.search.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.ArrowOutward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.bytedance.ads_bytedance.data.model.AdItem
import com.bytedance.ads_bytedance.data.model.LoadState
import com.bytedance.ads_bytedance.search.model.SearchEvent
import com.bytedance.ads_bytedance.search.viewmodel.SearchViewModel
import org.koin.androidx.compose.koinViewModel

/**
 * 常规搜索页面
 *
 * ## 三阶段 UI + 搜索历史
 * ```
 * Phase 1 (INITIAL):  搜索框 + 搜索历史 + 热门搜索关键词标签云
 * Phase 2 (SUGGESTING): 搜索框 + 联想建议下拉列表
 * Phase 3 (RESULTS):  搜索框 + 结果计数 + 广告卡片列表 + 加载更多
 * ```
 *
 * ## 返回逻辑
 * - 在 RESULTS 阶段按返回 → 清除结果回到 INITIAL
 * - 在 INITIAL/SUGGESTING 阶段按返回 → popBackStack 回主页
 *
 * @param onBack 返回上一页（pop 导航栈）
 * @param onNavigateToDetail 导航到广告详情页（同时记录搜索历史）
 * @param onNavigateToChat 导航到 AI 对话搜索（携带广告上下文）
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit = {},
    onNavigateToDetail: (String) -> Unit = {},
    onNavigateToChat: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val viewModel: SearchViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val focusRequester = remember { FocusRequester() }

    // 页面进入时自动聚焦搜索框
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // 监听 Lifecycle：从详情页返回时自动刷新搜索历史
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshHistory()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val showSuggestions = uiState.query.isNotBlank() && !uiState.hasSearched
    val showInitial = uiState.query.isBlank() && !uiState.hasSearched
    val showResults = uiState.hasSearched ||
        uiState.searchLoadState == LoadState.LOADING

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    SearchTextField(
                        value = uiState.query,
                        onValueChange = { viewModel.onEvent(SearchEvent.UpdateQuery(it)) },
                        onSearch = { viewModel.onEvent(SearchEvent.SubmitSearch) },
                        onClear = {
                            viewModel.onEvent(SearchEvent.UpdateQuery(""))
                            viewModel.onEvent(SearchEvent.ClearResults)
                        },
                        focusRequester = focusRequester,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (viewModel.handleBackPress()) {
                            onBack()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Phase 1: 初始态（热搜 + 搜索历史）
            AnimatedVisibility(visible = showInitial, enter = fadeIn(), exit = fadeOut()) {
                Column {
                    TrendingKeywordsSection(
                        keywords = uiState.trendingKeywords,
                        isLoading = uiState.isLoadingTrending,
                        onKeywordClick = { viewModel.onEvent(SearchEvent.SelectTrending(it)) }
                    )

                    SearchHistorySection(
                        history = uiState.searchHistory,
                        isLoaded = uiState.isHistoryLoaded,
                        onItemClick = { adItem ->
                            viewModel.onEvent(SearchEvent.AddToHistory(adItem))
                            onNavigateToDetail(adItem.id)
                        },
                        onClearAll = { viewModel.onEvent(SearchEvent.ClearHistory) }
                    )
                }
            }

            // Phase 2: 联想建议
            AnimatedVisibility(visible = showSuggestions, enter = fadeIn(), exit = fadeOut()) {
                SuggestionsSection(
                    suggestions = uiState.suggestions,
                    query = uiState.query,
                    isLoading = uiState.isLoadingSuggestions,
                    onSuggestionClick = { viewModel.onEvent(SearchEvent.SelectSuggestion(it)) },
                    onSearchClick = { viewModel.onEvent(SearchEvent.SubmitSearch) }
                )
            }

            // Phase 3: 搜索结果
            AnimatedVisibility(visible = showResults, enter = fadeIn(), exit = fadeOut()) {
                ResultsSection(
                    results = uiState.results,
                    query = uiState.query,
                    loadState = uiState.searchLoadState,
                    hasMore = uiState.hasMoreResults,
                    errorMessage = uiState.errorMessage,
                    onAdClick = { adItem ->
                        // 点进详情时记录搜索历史
                        viewModel.onEvent(SearchEvent.AddToHistory(adItem))
                        onNavigateToDetail(adItem.id)
                    },
                    onNavigateToChat = onNavigateToChat,
                    onLoadMore = { viewModel.onEvent(SearchEvent.LoadMore) },
                    onRetry = { viewModel.onEvent(SearchEvent.Refresh) }
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// 搜索输入框（带"输入"按钮）
// ═══════════════════════════════════════════════════════════

@Composable
private fun SearchTextField(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.focusRequester(focusRequester),
        placeholder = {
            Text(
                text = "搜索广告...",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = "搜索",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingIcon = {
            if (value.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // "输入" 按钮
                    Text(
                        text = "输入",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onSearch)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                    // 清除按钮
                    IconButton(onClick = onClear) {
                        Icon(
                            imageVector = Icons.Filled.Clear,
                            contentDescription = "清除"
                        )
                    }
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(24.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        )
    )
}

// ═══════════════════════════════════════════════════════════
// Phase 1b: 搜索历史
// ═══════════════════════════════════════════════════════════

@Composable
private fun SearchHistorySection(
    history: List<AdItem>,
    isLoaded: Boolean,
    onItemClick: (AdItem) -> Unit,
    onClearAll: () -> Unit
) {
    if (!isLoaded) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        // 分割线（热搜与历史分隔）
        HorizontalDivider(
            modifier = Modifier.padding(bottom = 8.dp),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )

        // 标题行
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "搜索历史",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            // 清空按钮（仅有历史记录时显示）
            if (history.isNotEmpty()) {
                IconButton(onClick = onClearAll, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Clear,
                        contentDescription = "清除所有历史",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (history.isEmpty()) {
            // 空态提示
            Text(
                text = "暂无搜索历史，点击搜索结果中的广告即可记录",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            // 历史条目列表
            history.forEach { adItem ->
                SearchHistoryItem(
                    adItem = adItem,
                    onClick = { onItemClick(adItem) }
                )
            }
        }
    }
}

@Composable
private fun SearchHistoryItem(
    adItem: AdItem,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 缩略图
        val imageUrl = when (adItem) {
            is AdItem.LargeImageAd -> adItem.coverImageUrl
            is AdItem.SmallImageAd -> adItem.thumbnailUrl
            is AdItem.VideoAd -> adItem.coverImageUrl
        }

        AsyncImage(
            model = imageUrl,
            contentDescription = adItem.title,
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(6.dp)),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = adItem.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = adItem.advertiserName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════
// Phase 1a: 热门搜索
// ═══════════════════════════════════════════════════════════

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TrendingKeywordsSection(
    keywords: List<String>,
    isLoading: Boolean,
    onKeywordClick: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        // 标题
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "热门搜索",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        } else if (keywords.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "暂无热门搜索",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                keywords.forEach { keyword ->
                    SuggestionChip(
                        onClick = { onKeywordClick(keyword) },
                        label = {
                            Text(
                                text = keyword,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// Phase 2: 联想建议
// ═══════════════════════════════════════════════════════════

@Composable
private fun SuggestionsSection(
    suggestions: List<String>,
    query: String,
    isLoading: Boolean,
    onSuggestionClick: (String) -> Unit,
    onSearchClick: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        // 搜索当前输入项
        item(key = "search_query") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onSearchClick)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowOutward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "搜索 \"$query\"",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        if (isLoading) {
            item(key = "loading") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                }
            }
        } else {
            items(suggestions, key = { it }) { suggestion ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = { onSuggestionClick(suggestion) })
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = suggestion,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// Phase 3: 搜索结果
// ═══════════════════════════════════════════════════════════

@Composable
private fun ResultsSection(
    results: List<AdItem>,
    query: String,
    loadState: LoadState,
    hasMore: Boolean,
    errorMessage: String?,
    onAdClick: (AdItem) -> Unit,
    onNavigateToChat: (String) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        // 结果计数
        item(key = "result_count") {
            Text(
                text = if (results.isNotEmpty()) {
                    "共 ${results.size} 条结果"
                } else if (loadState != LoadState.LOADING) {
                    "未找到与 \"$query\" 相关的广告"
                } else {
                    ""
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 结果列表
        items(results, key = { it.id }) { ad ->
            SearchResultCard(
                ad = ad,
                onClick = { onAdClick(ad) },
                onDiscussClick = { onNavigateToChat(ad.id) }
            )
        }

        // 加载更多指示器
        if (loadState == LoadState.LOADING && results.isNotEmpty()) {
            item(key = "load_more") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }
        }

        // 加载更多触发点
        if (hasMore && loadState == LoadState.IDLE) {
            item(key = "load_more_trigger") {
                LaunchedEffect(Unit) {
                    onLoadMore()
                }
            }
        }

        // 错误态
        if (loadState == LoadState.ERROR) {
            item(key = "error") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Filled.SearchOff,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage ?: "搜索失败",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    FilterChip(
                        selected = false,
                        onClick = onRetry,
                        label = { Text("重试") },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }
        }

        // 空态
        if (results.isEmpty() && loadState == LoadState.IDLE) {
            item(key = "empty") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.SearchOff,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "未找到相关广告",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "试试其他关键词吧",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }

        // 加载中（首次）
        if (results.isEmpty() && loadState == LoadState.LOADING) {
            item(key = "first_loading") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "搜索中...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 底部留白
        item(key = "bottom_spacer") {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ═══════════════════════════════════════════════════════════
// 搜索结果卡片
// ═══════════════════════════════════════════════════════════

@Composable
private fun SearchResultCard(
    ad: AdItem,
    onClick: () -> Unit,
    onDiscussClick: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
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
                    .size(72.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(12.dp))

            // 文字信息
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = ad.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = ad.description,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = ad.advertiserName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    if (ad.tags.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = ad.tags.first().name,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // "和AI讨论"按钮
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(start = 4.dp)
            ) {
                IconButton(
                    onClick = onDiscussClick,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = "和AI讨论",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
