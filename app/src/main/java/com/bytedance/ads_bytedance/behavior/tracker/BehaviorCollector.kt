package com.bytedance.ads_bytedance.behavior.tracker

import com.bytedance.ads_bytedance.behavior.model.BehaviorType
import com.bytedance.ads_bytedance.behavior.model.UserBehavior
import com.bytedance.ads_bytedance.data.local.dao.BehaviorDao
import com.bytedance.ads_bytedance.data.local.dao.UserInteractionDao
import com.bytedance.ads_bytedance.data.local.entity.BehaviorEntity
import com.bytedance.ads_bytedance.data.local.entity.UserInteractionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * 用户行为采集器
 *
 * ## 职责
 * 1. 采集用户在 App 中的 6 种交互行为，转换为 [BehaviorEntity] 并持久化到 Room 数据库
 * 2. 管理用户互动状态（点赞/收藏），写入 [UserInteractionEntity] 表（模拟服务端状态管理）
 *
 * ## 6 种行为类型
 * | 类型        | 触发场景                           | 权重 |
 * |------------|-----------------------------------|------|
 * | CLICK      | 点击广告卡片进入详情页               | 1    |
 * | LIKE       | 点击点赞按钮（仅正向操作记录）        | 2    |
 * | COLLECT    | 点击收藏按钮（仅正向操作记录）        | 3    |
 * | SHARE      | 触发分享行为                       | 2    |
 * | TAG_CLICK  | 点击卡片上的标签 Chip               | 1    |
 * | SEARCH     | 提交搜索查询（常规搜索或对话搜索）     | 1    |
 *
 * ## 线程安全
 * 所有 collect() 和 updateInteraction() 调用在 [Dispatchers.IO] 上执行 Room 写入，
 * 不阻塞调用方线程。使用 SupervisorJob 确保单次写入失败不影响后续采集。
 *
 * @param behaviorDao Room BehaviorDao（Koin 注入）
 * @param interactionDao Room UserInteractionDao（Koin 注入，Day 11+）
 */
class BehaviorCollector(
    private val behaviorDao: BehaviorDao,
    private val interactionDao: UserInteractionDao
) {
    /** 专用协程作用域，SupervisorJob 确保单次失败不取消整个 scope */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** JSON 序列化器（复用全局实例，与 NetworkConfig.json 保持一致） */
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 采集一条用户行为
     *
     * 将 [UserBehavior] 转换为 [BehaviorEntity] 后异步写入 Room。
     * 写入失败时静默吞掉异常——行为采集不应影响用户主流程。
     *
     * @param behavior 用户行为记录
     */
    fun collect(behavior: UserBehavior) {
        scope.launch {
            try {
                val entity = BehaviorEntity(
                    id = behavior.id.ifBlank { UUID.randomUUID().toString() },
                    adId = behavior.adId,
                    behaviorType = behavior.behaviorType.name,
                    tagsJson = json.encodeToString(behavior.tags),
                    timestamp = if (behavior.timestamp > 0) behavior.timestamp
                        else System.currentTimeMillis()
                )
                behaviorDao.insert(entity)
            } catch (_: Exception) {
                // 静默失败：行为采集失败不影响用户主流程
            }
        }
    }

    /**
     * 批量采集行为（如搜索结果一次性记录）
     */
    fun collectAll(behaviors: List<UserBehavior>) {
        behaviors.forEach { collect(it) }
    }

    /**
     * 更新用户对某广告的互动状态（模拟服务端状态管理）
     *
     * 向 [UserInteractionEntity] 表 upsert 当前点赞/收藏状态。
     * ViewModel 在 toggle 操作时调用此方法，无论用户是执行正向还是反向操作。
     *
     * 统计页面的"总点赞"/"总收藏"通过 `UserInteractionDao.countLiked()/countCollected()`
     * 查询当前状态得到准确计数，而非依赖行为事件表。
     *
     * @param adId 广告 ID
     * @param isLiked null=不改变点赞状态, true=点赞, false=取消点赞
     * @param isCollected null=不改变收藏状态, true=收藏, false=取消收藏
     */
    fun updateInteraction(
        adId: String,
        isLiked: Boolean? = null,
        isCollected: Boolean? = null
    ) {
        scope.launch {
            try {
                val now = System.currentTimeMillis()
                // 先查询已有记录（如果存在）
                val existing = try {
                    interactionDao.getByAdId(adId)
                } catch (_: Exception) {
                    null
                }

                val entity = if (existing != null) {
                    existing.copy(
                        isLiked = isLiked ?: existing.isLiked,
                        isCollected = isCollected ?: existing.isCollected,
                        likedAt = when {
                            isLiked == true -> now
                            isLiked == false -> existing.likedAt
                            else -> existing.likedAt
                        },
                        collectedAt = when {
                            isCollected == true -> now
                            isCollected == false -> existing.collectedAt
                            else -> existing.collectedAt
                        },
                        updatedAt = now
                    )
                } else {
                    UserInteractionEntity(
                        adId = adId,
                        isLiked = isLiked ?: false,
                        isCollected = isCollected ?: false,
                        likedAt = if (isLiked == true) now else null,
                        collectedAt = if (isCollected == true) now else null,
                        updatedAt = now
                    )
                }
                interactionDao.upsert(entity)
            } catch (_: Exception) {
                // 静默失败：状态更新失败不影响用户主流程
            }
        }
    }

    /**
     * 删除指定广告的行为事件记录
     *
     * 当用户取消点赞/收藏时调用，确保 [UserProfileEngine] 的标签权重计算
     * 不再累加已取消的行为。
     *
     * 此方法删除 [BehaviorEntity]（行为事件流水），不同于 [updateInteraction]
     * 更新的是 [UserInteractionEntity]（互动状态快照）。
     *
     * @param adId 广告 ID
     * @param type 要删除的行为类型（LIKE / COLLECT）
     */
    fun removeBehavior(adId: String, type: BehaviorType) {
        scope.launch {
            try {
                behaviorDao.deleteByAdIdAndType(adId, type.name)
            } catch (_: Exception) {
                // 静默失败：行为删除失败不影响用户主流程
            }
        }
    }

    // ═══════════════════════════════════════════════════════
    // 互动状态读取（供 ViewModel hydration 使用）
    // ═══════════════════════════════════════════════════════

    /**
     * 从 Room 批量读取互动状态
     *
     * 供所有 ViewModel 在加载广告数据后调用，覆盖 [AdItem.isLiked]/[isCollected]
     * 的 [@Transient] 默认值，确保跨进程重启和跨页面状态一致。
     *
     * @param adIds 需要查询的广告 ID 列表
     * @return adId → UserInteractionEntity 的映射（不存在则不在 Map 中）
     */
    suspend fun getInteractionStates(adIds: List<String>): Map<String, UserInteractionEntity> {
        if (adIds.isEmpty()) return emptyMap()
        return try {
            interactionDao.getByAdIds(adIds).associateBy { it.adId }
        } catch (_: Exception) {
            emptyMap()
        }
    }
}
