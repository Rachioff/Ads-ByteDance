package com.bytedance.ads_bytedance.behavior.tracker

import com.bytedance.ads_bytedance.behavior.model.BehaviorType
import com.bytedance.ads_bytedance.behavior.model.UserBehavior
import com.bytedance.ads_bytedance.data.local.dao.BehaviorDao
import com.bytedance.ads_bytedance.data.local.entity.BehaviorEntity
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
 * 采集用户在 App 中的 6 种交互行为，转换为 [BehaviorEntity] 并持久化到 Room 数据库。
 *
 * ## 6 种行为类型
 * | 类型        | 触发场景                           | 权重 |
 * |------------|-----------------------------------|------|
 * | CLICK      | 点击广告卡片进入详情页               | 1    |
 * | LIKE       | 点击点赞按钮                       | 2    |
 * | COLLECT    | 点击收藏按钮                       | 3    |
 * | SHARE      | 触发分享行为                       | 2    |
 * | TAG_CLICK  | 点击卡片上的标签 Chip               | 1    |
 * | SEARCH     | 提交搜索查询（常规搜索或对话搜索）     | 1    |
 *
 * ## 使用方式
 * ```
 * val collector = BehaviorCollector(behaviorDao)
 * collector.collect(
 *     UserBehavior(
 *         adId = "ad_001",
 *         behaviorType = BehaviorType.LIKE,
 *         tags = listOf("运动", "学生党")
 *     )
 * )
 * ```
 *
 * ## 线程安全
 * 所有 collect() 调用在 [Dispatchers.IO] 上执行 Room 写入，
 * 不阻塞调用方线程。使用 SupervisorJob 确保单次写入失败不影响后续采集。
 *
 * @param behaviorDao Room BehaviorDao（Koin 注入）
 */
class BehaviorCollector(
    private val behaviorDao: BehaviorDao
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
}
