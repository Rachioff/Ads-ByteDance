package com.bytedance.ads_bytedance

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.bytedance.ads_bytedance.ai.chat.ui.ChatScreen
import com.bytedance.ads_bytedance.analytics.ui.AdListScreen
import com.bytedance.ads_bytedance.analytics.ui.StatsScreen
import com.bytedance.ads_bytedance.analytics.viewmodel.AdListType
import com.bytedance.ads_bytedance.analytics.model.StatsEvent
import com.bytedance.ads_bytedance.detail.ui.DetailScreen
import com.bytedance.ads_bytedance.feed.ui.HomeScreen
import com.bytedance.ads_bytedance.search.ui.SearchScreen
import com.bytedance.ads_bytedance.ui.theme.AdsByteDanceTheme
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * 主 Activity — 使用 Navigation Compose 管理路由
 *
 * ## 路由表
 * ```
 * "home"                → HomeScreen（信息流三频道）
 * "detail/{adId}"       → DetailScreen（图文/视频详情页）
 * "search"              → SearchScreen（常规搜索：热搜 + 联想 + 结果列表）
 * "chat"                → ChatScreen（AI 对话搜索，无上下文）
 * "chat?adId={adId}"    → ChatScreen（带入广告上下文）
 * "stats"               → StatsScreen（数据统计 + 我的偏好）
 * "stats/history"       → AdListScreen（浏览记录）
 * "stats/liked"         → AdListScreen（已点赞广告列表）
 * "stats/collected"     → AdListScreen（已收藏广告列表）
 * ```
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AdsByteDanceTheme {
                AppNavHost()
            }
        }
    }
}

/**
 * 应用级导航宿主
 */
@Composable
private fun AppNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "home"
    ) {
        // ── 首页（信息流）──
        composable("home") {
            HomeScreen(
                onNavigateToDetail = { adId ->
                    navController.navigate("detail/$adId")
                },
                onNavigateToSearch = {
                    navController.navigate("search")
                },
                onNavigateToChat = {
                    navController.navigate("chat")
                },
                onNavigateToStats = {
                    navController.navigate("stats")
                }
            )
        }

        // ── 详情页 ──
        composable(
            route = "detail/{adId}",
            arguments = listOf(
                navArgument("adId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val adId = backStackEntry.arguments?.getString("adId") ?: return@composable
            DetailScreen(
                adId = adId,
                onBack = { navController.popBackStack() }
            )
        }

        // ── 常规搜索 ──
        composable("search") {
            SearchScreen(
                onBack = { navController.popBackStack() },
                onNavigateToDetail = { adId ->
                    navController.navigate("detail/$adId")
                },
                onNavigateToChat = { adId ->
                    navController.navigate("chat?adId=$adId")
                }
            )
        }

        // ── AI 对话搜索（可选广告上下文）──
        composable(
            route = "chat?adId={adId}",
            arguments = listOf(
                navArgument("adId") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { backStackEntry ->
            val adId = backStackEntry.arguments?.getString("adId")
                ?.takeIf { it.isNotEmpty() }
            ChatScreen(
                adId = adId,
                onBack = { navController.popBackStack() },
                onNavigateToDetail = { id ->
                    navController.navigate("detail/$id")
                }
            )
        }

        // ── 数据统计 ──
        composable("stats") {
            val viewModel: com.bytedance.ads_bytedance.analytics.viewmodel.StatsViewModel =
                koinViewModel()

            // 监听 Lifecycle：从详情页/列表页返回时自动刷新统计数字
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        viewModel.refreshStats()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            val uiState = viewModel.uiState
            StatsScreen(
                uiState = uiState,
                onEvent = { event ->
                    when (event) {
                        is StatsEvent.Back -> navController.popBackStack()
                        is StatsEvent.ShowHistory -> navController.navigate("stats/history")
                        is StatsEvent.ShowLikedAds -> navController.navigate("stats/liked")
                        is StatsEvent.ShowCollectedAds -> navController.navigate("stats/collected")
                        else -> viewModel.onEvent(event)
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        // ── 浏览记录 ──
        composable("stats/history") {
            val viewModel: com.bytedance.ads_bytedance.analytics.viewmodel.AdListViewModel =
                koinViewModel { parametersOf(AdListType.HISTORY) }

            // 监听 Lifecycle：从详情页返回时自动刷新列表
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        viewModel.refresh()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            AdListScreen(
                type = AdListType.HISTORY,
                ads = viewModel.ads,
                isLoading = viewModel.isLoading,
                onBack = { navController.popBackStack() },
                onNavigateToDetail = { adId -> navController.navigate("detail/$adId") }
            )
        }

        // ── 已点赞广告列表 ──
        composable("stats/liked") {
            val viewModel: com.bytedance.ads_bytedance.analytics.viewmodel.AdListViewModel =
                koinViewModel { parametersOf(AdListType.LIKED) }

            // 监听 Lifecycle：从详情页取消点赞后返回时自动刷新列表
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        viewModel.refresh()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            AdListScreen(
                type = AdListType.LIKED,
                ads = viewModel.ads,
                isLoading = viewModel.isLoading,
                onBack = { navController.popBackStack() },
                onNavigateToDetail = { adId -> navController.navigate("detail/$adId") }
            )
        }

        // ── 已收藏广告列表 ──
        composable("stats/collected") {
            val viewModel: com.bytedance.ads_bytedance.analytics.viewmodel.AdListViewModel =
                koinViewModel { parametersOf(AdListType.COLLECTED) }

            // 监听 Lifecycle：从详情页取消收藏后返回时自动刷新列表
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        viewModel.refresh()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            AdListScreen(
                type = AdListType.COLLECTED,
                ads = viewModel.ads,
                isLoading = viewModel.isLoading,
                onBack = { navController.popBackStack() },
                onNavigateToDetail = { adId -> navController.navigate("detail/$adId") }
            )
        }
    }
}
