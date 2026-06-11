package com.bytedance.ads_bytedance

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.bytedance.ads_bytedance.ai.chat.ui.ChatScreen
import com.bytedance.ads_bytedance.analytics.ui.StatsScreen
import com.bytedance.ads_bytedance.analytics.model.StatsEvent
import com.bytedance.ads_bytedance.detail.ui.DetailScreen
import com.bytedance.ads_bytedance.feed.ui.HomeScreen
import com.bytedance.ads_bytedance.search.ui.SearchScreen
import com.bytedance.ads_bytedance.ui.theme.AdsByteDanceTheme
import org.koin.androidx.compose.koinViewModel

/**
 * 主 Activity — 使用 Navigation Compose 管理路由
 *
 * ## 路由表
 * ```
 * "home"                → HomeScreen（信息流三频道）
 * "detail/{adId}"       → DetailScreen（图文/视频详情页）
 * "search"              → SearchScreen（常规搜索：热搜 + 联想 + 结果列表）
 * "chat"                → ChatScreen（AI 对话搜索，无上下文）
 * "chat?adId={adId}"    → ChatScreen（带入广告上下文，搜索结果"和AI讨论"入口）
 * "stats"               → StatsScreen（数据统计 + 我的偏好）
 * ```
 *
 * ## 跨页面状态同步
 * DetailScreen 调用 [com.bytedance.ads_bytedance.data.repository.AdRepository.updateInteraction]
 * 更新内存缓存中的 isLiked/isCollected。
 * 返回 HomeScreen 时 FeedScreen 通过 likedAdIds[adId] ?: ad.isLiked 自动获取最新状态。
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
 *
 * 使用 [rememberNavController] 管理返回栈，
 * "home" 路由不通过 popBackStack 恢复（保留滚动位置由 LazyListState 天然保持）。
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

        // ── 数据统计（Day 9 新增）──
        composable("stats") {
            val viewModel: com.bytedance.ads_bytedance.analytics.viewmodel.StatsViewModel =
                koinViewModel()
            val uiState = viewModel.uiState
            StatsScreen(
                uiState = uiState,
                onEvent = { event ->
                    if (event is StatsEvent.Back) {
                        navController.popBackStack()
                    } else {
                        viewModel.onEvent(event)
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }
    }
}
