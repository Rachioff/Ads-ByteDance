package com.bytedance.ads_bytedance.search.data

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.bytedance.ads_bytedance.data.model.AdItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * 搜索历史管理器
 *
 * ## 设计思路（类似 Apple Music 搜索历史）
 * - 不记录搜索关键词，而是记录用户从搜索结果中**点进详情页**的广告
 * - 同一广告重复点击时移动到列表顶部（去重 + 置顶）
 * - 最多保留 50 条记录
 * - 持久化到 JSON 文件（kotlinx.serialization）
 *
 * ## 线程安全
 * - 使用 [Mutex] 保护写操作，避免并发修改导致数据丢失
 * - 文件 I/O 在 [Dispatchers.IO] 上执行
 *
 * ## 数据流
 * ```
 * SearchScreen → 点击广告 → onNavigateToDetail(adId)
 *   → adRepository.getAdById(adId) → addToHistory(adItem)
 *   → 持久化到 JSON 文件
 * ```
 */
class SearchHistoryManager(
    private val context: Context
) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    private val maxEntries = 50
    private val historyFile by lazy { java.io.File(context.filesDir, FILE_NAME) }
    private val mutex = Mutex()

    /** 内存缓存 + 可观察的 StateFlow */
    private val _history = MutableStateFlow<List<AdItem>>(emptyList())
    val history: StateFlow<List<AdItem>> = _history.asStateFlow()

    /** 是否已从文件加载完成 */
    private var loaded = false

    /**
     * 异步加载历史记录（应在 Application 或 ViewModel init 中调用）
     */
    suspend fun loadAsync() {
        if (loaded) return
        withContext(Dispatchers.IO) {
            mutex.withLock {
                if (loaded) return@withContext
                _history.value = readFromFile()
                loaded = true
            }
        }
    }

    /**
     * 添加广告到搜索历史
     *
     * 去重：如果同一 ID 已存在，将其移至顶部。
     * 上限：超过 50 条时删除最旧的记录。
     */
    suspend fun addToHistory(adItem: AdItem) {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val current = _history.value.toMutableList()
                // 去重：移除旧记录
                current.removeAll { it.id == adItem.id }
                // 添加到顶部
                current.add(0, adItem)
                // 截断超出上限的条目
                if (current.size > maxEntries) {
                    val trimmed = current.take(maxEntries)
                    current.clear()
                    current.addAll(trimmed)
                }
                _history.value = current
                writeToFile(current)
            }
        }
    }

    /**
     * 清空所有搜索历史
     */
    suspend fun clearHistory() {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                _history.value = emptyList()
                writeToFile(emptyList())
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // 文件读写
    // ═══════════════════════════════════════════════════════════

    private fun readFromFile(): List<AdItem> {
        if (!historyFile.exists()) return emptyList()
        return try {
            val content = historyFile.readText()
            json.decodeFromString(ListSerializer(AdItem.serializer()), content)
        } catch (e: Exception) {
            // JSON 解析失败（格式错误、版本不兼容等）→ 丢弃旧数据
            historyFile.delete()
            emptyList()
        }
    }

    private fun writeToFile(items: List<AdItem>) {
        try {
            val content = json.encodeToString(ListSerializer(AdItem.serializer()), items)
            historyFile.writeText(content)
        } catch (e: Exception) {
            // 写入失败（磁盘满、权限等）→ 静默失败，内存数据不受影响
        }
    }

    @VisibleForTesting
    companion object {
        const val FILE_NAME = "search_history.json"
    }
}
