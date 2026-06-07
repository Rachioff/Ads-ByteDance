package com.bytedance.ads_bytedance

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.bytedance.ads_bytedance.feed.ui.HomeScreen
import com.bytedance.ads_bytedance.ui.theme.AdsByteDanceTheme

/**
 * 主 Activity — 广告信息流宿主
 *
 * HomeScreen 包含 TabRow（精选/电商/本地）+ HorizontalPager 多频道滑动。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AdsByteDanceTheme {
                HomeScreen()
            }
        }
    }
}
