# 技术设计文档

> **项目**: AI广告推荐信息流 (Ads-ByteDance)
> **作者**: 赵诗阳
> **日期**: 2026-06-15
> **版本**: v2.0（交付版）

---

## 目录

1. [整体架构设计](#1-整体架构设计)
2. [方案对比与决策](#2-方案对比与决策)
3. [难点与解决方案](#3-难点与解决方案)
4. [AI 输出约束与缓存策略](#4-ai-输出约束与缓存策略)
5. [曝光统计口径](#5-曝光统计口径)
6. [个性化推荐设计](#6-个性化推荐设计)
7. [视频播放器设计](#7-视频播放器设计)
8. [效果评估](#8-效果评估)

---

## 1. 整体架构设计

### 1.1 架构模式：MVVM + Repository + 模块化

本项目采用 **MVVM（Model-View-ViewModel）** 架构模式，结合 **Repository 模式** 实现数据层抽象，通过**功能模块化**拆分业务边界。

```
┌─────────────────────────────────────────────────────────┐
│                      UI Layer (View)                     │
│  Compose Activity / Composable 函数                      │
│  声明式 UI，collectAsState 观察状态                      │
├─────────────────────────────────────────────────────────┤
│                   ViewModel Layer                        │
│  FeedViewModel / DetailViewModel / SearchViewModel ...   │
│  持有 StateFlow<UiState>，处理业务逻辑，调度 Repository  │
├─────────────────────────────────────────────────────────┤
│                  Repository Layer                        │
│  AdRepository / BehaviorRepository / AiRepository        │
│  统一数据访问接口，协调本地与远程数据源                    │
├─────────────────────┬───────────────────────────────────┤
│   Local DataSource   │    Remote DataSource              │
│   Mock JSON / Room   │    OkHttp + Retrofit              │
│   DataStore          │    Chat Bot 微服务                │
└─────────────────────┴───────────────────────────────────┘
```

### 1.2 模块划分

| 模块 | 职责 | 关键类 |
|------|------|--------|
| **feed** | 信息流展示与交互 | `FeedScreen`, `FeedViewModel`, `LargeImageCard`, `SmallImageCard`, `VideoCard` |
| **detail** | 详情页展示与状态同步 | `DetailScreen`, `DetailViewModel`, `DetailInteractions` |
| **data** | 数据获取与缓存 | `AdRepository`, `MockJsonDataSource`, `RemoteDataSource` |
| **ai** | AI 摘要/标签生成 | `AiContentGenerator`, `AiCacheManager`, `AiApiService` |
| **search** | 常规搜索 | `SearchScreen`, `SearchViewModel`, `SearchHistoryManager` |
| **ai/chat** | 对话式搜索 | `ChatScreen`, `ChatViewModel`, `ChatBotService`, `ChatMemoryCache` |
| **player** | 视频播放器 | `PlayerPool`, `VideoPlayer` |
| **analytics** | 埋点统计 | `ExposureTracker`, `StatsScreen`, `StatsViewModel` |
| **behavior** | 用户行为与推荐 | `BehaviorCollector`, `UserProfileEngine`, `RecommendRanker` |
| **common** | 公共组件 | `NetworkConfig`, `ImageLoaderConfig`, `AdMatchingEngine` |

### 1.3 数据流设计

**单向数据流（UDF）** 是贯穿所有模块的核心原则：

```
用户操作 → Event → ViewModel.onEvent()
                        ↓
              Repository (suspend/Flow)
                        ↓
              UiState 变更 (StateFlow)
                        ↓
              Compose UI 自动重组
```

---

## 2. 方案对比与决策

### 2.1 UI 框架：ViewBinding (XML) vs Jetpack Compose

| 维度 | ViewBinding + XML | Jetpack Compose **(本项目选择)** |
|------|-------------------|------------------------------|
| **代码量** | 卡片布局 XML ~100 行 + Kotlin ~50 行 | Composable 函数 ~40 行 |
| **状态驱动** | 命令式：adapter.notifyDataSetChanged() | 声明式：StateFlow.collectAsState() 自动驱动 |
| **多类型列表** | getItemViewType() + 多个 ViewHolder + DiffUtil | when(type) 直接分发 Composable，LazyColumn key 自动 diff |
| **动画** | XML Animator / Transition 框架 | animateXxxAsState / AnimatedVisibility |
| **ExoPlayer 集成** | PlayerView 直接嵌入 | AndroidView 包装 PlayerView |
| **Google 态度** | 维护模式 | 主推方向 |

**决策理由**：MVVM + StateFlow + Compose 形成完美的声明式闭闭环。信息流多类型卡片通过 `LazyColumn` + `when(type)` 分发更简洁。Compose snapshot 系统天然支持精确重组，避免 RecyclerView 中的 `notifyItemChanged()` 粒度过粗问题。

### 2.2 JSON 解析：Gson vs Moshi vs Kotlinx Serialization

| 维度 | Gson | Moshi | Kotlinx Serialization **(本项目选择)** |
|------|------|-------|-----------------------------------|
| **反射** | 使用反射 | 可选（codegen） | 编译期生成，无反射 |
| **Kotlin 支持** | 部分（null 安全不足） | 较好 | 原生支持（默认值、密封类、data class） |
| **多态序列化** | 支持 | 需适配器 | 原生支持（密封类层级，@SerialName） |
| **体积** | 较大 | 中等 | 小（Kotlin 标准库扩展） |

**决策理由**：项目广告数据模型包含 3 种卡片类型（LargeImage / SmallImage / Video），Kotlinx Serialization 的密封类多态序列化（`@SerialName` + `classDiscriminator`）可完美建模，编译期安全避免运行时 JSON 解析崩溃。

### 2.3 图片加载：Glide vs Coil

| 维度 | Glide | Coil **(本项目选择)** |
|------|-------|-------------------|
| **体积** | ~2MB | ~200KB |
| **协程支持** | 需额外适配 | 原生 Kotlin 协程支持 |
| **API 设计** | Java 风格 (Builder) | Kotlin DSL 风格 |
| **生命周期** | 手动管理 | 协程自动取消（lifecycleScope） |
| **Compose 集成** | 需第三方桥接 | coil-compose 原生支持 AsyncImage |

**决策理由**：Kotlin/Compose 全新项目，Coil 的 KTX 集成、协程取消机制（Composable 离开组合树时自动取消请求）和体积优势与项目目标高度契合。

### 2.4 视频播放器：MediaPlayer vs ExoPlayer

| 维度 | MediaPlayer | ExoPlayer **(本项目选择)** |
|------|------------|----------------------|
| **格式支持** | 有限（依赖设备） | 广泛（DASH/HLS/MP4） |
| **自定义性** | 低（黑盒封装） | 高（组件可插拔） |
| **播放器实例池** | 不支持 | 可自定义 PlayerPool |
| **错误恢复** | 弱 | 强（自动重试、降级） |

**决策理由**：需求要求播放器实例池化和资源复用，ExoPlayer 的组件化设计允许自定义 PlayerPool 和播放器复用逻辑。

### 2.5 依赖注入：Hilt vs Koin

| 维度 | Hilt (Dagger) | Koin **(本项目选择)** |
|------|--------------|---------------------|
| **实现方式** | 注解处理器（编译期） | Kotlin DSL（运行时） |
| **编译速度** | 较慢（注解处理） | 快（无注解处理） |
| **配置复杂度** | 高（需 @Module/@InstallIn 等） | 低（纯 Kotlin DSL） |
| **ViewModel 注入** | @HiltViewModel | viewModel { params -> } |

**决策理由**：Koin 无需 KSP 生成代码（避免了 Day 1 遇到的 KSP 版本兼容问题），Kotlin DSL 配置方式与项目 Kotlin-first 策略一致，ViewModel 参数注入（如按 Channel 区分 FeedViewModel 实例）通过 `parametersOf()` 简洁实现。

### 2.6 AI API 调用：客户端直连 vs 服务端代理 vs Chat Bot 微服务

| 方案 | 架构 | 优点 | 缺点 |
|------|------|------|------|
| **A：客户端直连** | 客户端 → AI API | 实现简单 | API Key 暴露风险；session 管理困难 |
| **B：服务端代理** | 客户端 → 自建服务 → AI API | API Key 安全 | 需额外服务；无 session 管理 |
| **C：Chat Bot 微服务（本项目选择）** | 客户端 ⇄ 微服务 ⇄ LLM | API Key 安全；session 持久化；多轮对话上下文服务端管理 | 需额外开发微服务 |

**决策理由**：
1. 对话搜索天然需要服务端管理 session（多轮上下文、历史持久化）
2. API Key 不出现在客户端代码中（即使不提交 Git，仍有反编译风险）
3. 答辩时可展示"自建微服务 + 客户端"的完整架构，技术深度加分
4. 额外开发成本可控（约 200 行 Python FastAPI）

### 2.7 跨页面状态同步：EventBus vs 共享 ViewModel vs Room Flow

| 方案 | 实现 | 优点 | 缺点 |
|------|------|------|------|
| **EventBus** | 发布/订阅事件 | 解耦 | 事件泛滥难维护，生命周期不安全 |
| **共享 ViewModel（本项目辅助）** | Activity 级别 ViewModel 共享 | 生命周期安全 | 仅限同一 Activity 内 |
| **Repository 内存缓存（本项目主要）** | Repository 持有 AdItem 引用 | 全局可达，简洁 | 需处理引用一致性 |
| **Room Flow（本项目补充）** | Room DAO 返回 Flow 观察数据变化 | 跨进程、持久化 | 读写开销 |

**决策理由**：详情页和信息流在同一 Activity 内，采用 **Repository 内存缓存**作为主要同步方案——Repository 是所有 ViewModel 的共享"真相源"，互动操作通过 `updateInteraction()` 修改缓存中的 AdItem 引用，所有观察者自动读取到最新值。配合 `interactionVersion` 版本计数器驱动 Compose 重组。

---

## 3. 难点与解决方案

### 3.1 LazyColumn 精确重组问题

**问题**：点赞/收藏点击后，图标不变色、计数不变化，需滑出再滑回才更新。

**根因**：`LazyColumn.items(key)` 在 key 匹配到已有 item 时**不重新执行 content lambda**。状态在 content lambda 外部读取，item 没有机会感知变化。

**排查过程**（3 轮）：
1. 怀疑 data class body 属性不参与 equals → 移到 constructor → 无效
2. 怀疑 StateFlow 链路问题 → 改用 mutableStateOf → 无效
3. 定位根因：状态在 LazyColumn items content lambda 外部读取

**最终方案**：`mutableStateMapOf<String, Boolean>` 按 adId 独立追踪状态，在 content lambda **内部**读取 `map[adId]`，建立精确到单个 item 的 snapshot 依赖。

```kotlin
// ViewModel
val likedAdIds = mutableStateMapOf<String, Boolean>()

fun toggleLike(adId: String) {
    likedAdIds[adId] = !(likedAdIds[adId] ?: false)
    // 只触发这一个 item 的重组
}

// Compose — content lambda 内部读取
items(items = ads, key = { it.id }) { ad ->
    val liked = viewModel.likedAdIds[ad.id] ?: ad.isLiked // ← snapshot read
    LargeImageCard(ad = ad.copy(isLiked = liked), ...)
}
```

### 3.2 ExoPlayer 黑屏问题

**问题**：点击播放按钮后，Crossfade 切换到播放态，视频区域显示黑屏。

**根因**：`startPlayback()` 中同步调用 `prepare()`，此时 Crossfade 尚未切换 → TextureView Surface 不存在 → `prepare()` 在没有 Surface 的情况下初始化视频渲染管线 → 视频轨道未启用 → 黑屏。

**时序图**：
```
❌ 旧：acquire → setMediaItem → prepare → Crossfade → Surface（已错过prepare窗口）
✅ 新：acquire → Crossfade → VideoPlayer → Surface就绪 → view.post{ setMediaItem+prepare }
```

**最终方案**：通过 `View.post {}` 延迟播放初始化，确保布局完成、Surface 就绪后再 `prepare()`。

### 3.3 跨页面互动状态同步

**问题**：详情页点赞/收藏/分享后，返回信息流时计数器不更新。

**根因**：`mockJsonDataSource.updateInteraction()` 原地修改 AdItem 对象后返回同一引用。`DetailUiState.copy(ad = sameRef)` 后 Compose structural equality 判定未变化 → 不重组。

**最终方案**：引入 `interactionVersion: Int` 版本计数器。每次互动操作后 `interactionVersion + 1` → `uiState.copy(ad = updatedAd, interactionVersion = newVersion)` → 新旧 UiState 结构不相等 → Compose 触发重组。

```kotlin
// DetailViewModel
private fun toggleLike(adId: String) {
    likedAdIds[adId] = newLiked
    viewModelScope.launch {
        val result = repository.updateInteraction(adId, isLiked = newLiked)
        result.onSuccess { updatedAd ->
            uiState = uiState.copy(
                ad = updatedAd,
                interactionVersion = uiState.interactionVersion + 1
            )
        }
    }
}
```

### 3.4 Z 轴点击拦截问题

**问题**：播放失败后"点击重试"按钮无反应。

**根因**：全屏透明 `Box(Modifier.fillMaxSize().clickable{})` 没有任何条件守卫，始终渲染在错误 UI 之上。即使 `onClick` 中 `if (!hasError)` 不做任何事，`clickable` 仍然消费了点击事件。

**最终方案**：将全屏透明 click Box 包裹在 `if (!hasError)` 条件内，错误时完全不渲染覆盖层。

### 3.5 重试按钮 same-instance 陷阱

**问题**：修复了 Z 轴问题后，重试按钮仍然无反应。

**根因**：同一帧内 `exoPlayer` 从 `PlayerA → null → PlayerA`（同一实例），Compose snapshot 只看到最终值没变 → 不触发重组 → VideoPlayer 不重建 → `setupMedia()` 不被调用。

**最终方案**：引入 `retryTrigger: Int` 计数器，每次重试递增。`VideoPlayer` 通过 `LaunchedEffect(retryTrigger)` 观察变化 → 重置 `lastSetupPlayer = null` → 重新 `setupMedia()`。

---

## 4. AI 输出约束与缓存策略

### 4.1 Prompt Engineering 输出约束

**System Prompt 设计**：

```
你是一个广告内容分析助手。给定广告信息，请生成：
1. 一个简洁的摘要（1-2句话，不超过80字）
2. 3-5个智能标签，每个标签标注类别

你必须严格按照以下 JSON 格式返回，不要包含任何其他文本：
{
  "summary": "string",
  "tags": [
    {"name": "string", "category": "category|style|audience|scene"}
  ]
}
```

**输入构造**（将广告信息拼接为 User Message）：
```kotlin
fun buildPrompt(ad: AdItem): String = buildString {
    append("广告标题：${ad.title}\n")
    append("广告描述：${ad.description}\n")
    append("广告主：${ad.advertiserName}\n")
    append("广告类型：${ad.type.name}\n")
    append("\n请为以上广告生成摘要和智能标签。")
}
```

**参数控制**：
- `temperature = 0.3`：低温度确保输出稳定、格式一致
- `max_tokens = 512`：限制输出长度
- `response_format = { type: "json_object" }`：JSON 模式约束

### 4.2 响应解析容错

```kotlin
fun parseAiResponse(rawContent: String): AiGeneratedContent? {
    // 1. 尝试直接 JSON 解析
    // 2. 尝试提取 markdown 代码块 (```json...```)
    // 3. 尝试提取首 { ~ 末 } 内容
    // 4. summary 缺失 → 尝试中文 key（"摘要"）
    // 5. category 非法 → 默认 CATEGORY
    // 6. tags 数量 > 5 → 截断
    // 7. 都失败 → 返回 null（触发降级）
}
```

**多层容错设计**确保即使大模型返回格式不完全符合预期，也能正确解析。

### 4.3 三级缓存策略

| 缓存层级 | 存储位置 | Key | 过期策略 | 命中率目标 |
|---------|---------|-----|---------|-----------|
| **L1：内存** | LinkedHashMap (LRU, maxSize=50) | adId | 进程存活期间 | 80%+ |
| **L2：磁盘** | Room DB (ai_cache 表) | adId 的 MD5 | 7 天 | 15% |
| **L3：网络** | API 调用 | — | — | 5% |

**缓存命中流程**：
```
UI 请求 AdItem 的 AI 内容
    → 检查 L1 内存缓存 (LRU)
        → 命中：直接返回
        → 未命中：检查 L2 Room 磁盘缓存
            → 命中且未过期：返回 + 回填 L1
            → 未命中/已过期：发起 L3 API 请求
                → 成功：写入 L1 + L2 → 返回
                → 失败：使用降级静态内容（描述前 80 字 + Mock 标签）
```

**L1 实现**：`LinkedHashMap(accessOrder=true, maxSize=50)` + `synchronized` 线程安全。

**L2 实现**：Room `AiCacheEntity`（adId + summary + tagsJson + createdAt + expiresAt），启动时后台清理过期条目。

### 4.4 AI 功能架构（两轨制）

```
轨道一：AI 摘要/标签（无状态）
  客户端 → POST /v1/chat/completions（OpenAI 兼容）
         → Chat Bot 微服务转发 LLM
         → 三级缓存

轨道二：对话搜索（有状态）
  客户端 → POST /api/sessions/{id}/messages（自定义 REST）
         → Chat Bot 微服务管理 session + 对话历史
         → 调用 LLM 意图解析
         → 客户端 AdMatchingEngine 本地执行广告匹配
```

**降级策略**：
- AI 摘要/标签：API 失败 → 静态降级内容（描述前 80 字 + 预设标签）
- 对话搜索：微服务不可用 → 本地关键词搜索（`title.contains(keyword)` + `description.contains(keyword)`）

---

## 5. 曝光统计口径

### 5.1 方案对比

| 方案 | 判定条件 | 优点 | 缺点 |
|------|---------|------|------|
| **A：仅可见比例** | 卡片 ≥ 50% 可见即曝光 | 实现简单 | 快速划过会误统计 |
| **B：可见比例 + 停留时长（本项目选择）** | ≥ 50% 可见 + ≥ 1s 停留 | 更接近真实曝光意图 | 需延时检测机制 |
| **C：逐帧检测** | 基于 UI 刷新帧检测 | 精度最高 | 性能开销大，过度设计 |

### 5.2 实现方案（Compose 适配版）

```kotlin
@Composable
fun ExposureTracker.Track(
    lazyListState: LazyListState,
    ads: List<AdItem>,
    onExposed: (String) -> Unit
) {
    val exposedIds = remember { mutableSetOf<String>() }
    val visibilityStartTime = remember { mutableMapOf<Int, Long>() }

    LaunchedEffect(lazyListState) {
        snapshotFlow { lazyListState.layoutInfo.visibleItemsInfo }
            .distinctUntilChanged()
            .collectLatest { visibleItems ->
                val viewportHeight = lazyListState.layoutInfo.viewportSize.height
                val now = System.currentTimeMillis()

                visibleItems.forEach { item ->
                    val adId = ads.getOrNull(item.index)?.id ?: return@forEach
                    if (adId in exposedIds) return@forEach

                    val visibleRatio = calculateVisibleRatio(item, viewportHeight)
                    if (visibleRatio >= 0.5f) {
                        val startTime = visibilityStartTime[item.index]
                        if (startTime != null && now - startTime >= 1000) {
                            exposedIds.add(adId)
                            onExposed(adId)
                        } else if (startTime == null) {
                            visibilityStartTime[item.index] = now
                        }
                    } else {
                        visibilityStartTime.remove(item.index) // 取消计时
                    }
                }
            }
    }
}
```

### 5.3 与 RecyclerView 方案的对比

| 维度 | RecyclerView (传统方案) | Compose (本项目实现) |
|------|------|------|
| 可见性检测 | `OnScrollListener + findFirstVisibleItemPosition` | `snapshotFlow { layoutInfo.visibleItemsInfo }` |
| 延时机制 | `Handler.postDelayed(runnable, 1000)` | 时间戳差值比较（`now - startTime >= 1000`） |
| 取消机制 | `handler.removeCallbacks(it)` | 不满足 50% 时 `visibilityStartTime.remove(index)` |
| 性能影响 | View 测量（layout / measure） | Compose snapshot 通知，无额外 View 测量 |

---

## 6. 个性化推荐设计

### 6.1 用户画像构建

**行为采集**：6 种行为类型，每种有不同权重。

| 行为类型 | 权重 | 触发条件 |
|---------|------|---------|
| CLICK | 1 | 点击广告卡片进入详情 |
| LIKE | 2 | 点击点赞按钮 |
| COLLECT | 3 | 点击收藏按钮 |
| SHARE | 2 | 触发分享行为 |
| TAG_CLICK | 1 | 点击卡片标签 Chip |
| SEARCH | 1 | 提交搜索/对话查询 |

**画像计算**：
```
标签偏好得分 = Σ(该标签下各行为次数 × 对应行为权重)
```

例如：用户点击了 3 条带"运动"标签的广告、点赞了 2 条、收藏了 1 条
→ "运动"标签偏好得分 = 3×1 + 2×2 + 1×3 = 10

### 6.2 推荐排序策略

| 用户状态 | 排序策略 |
|---------|---------|
| **新用户**（无行为数据） | 默认排序（按广告曝光量/热度降序） |
| **有行为数据用户** | 按广告标签与用户偏好标签的**匹配度**降序排列 |

**匹配度计算**：
```
匹配度 = Σ(广告每个标签在用户画像中的权重得分)
```

**排序差异化**：
| 频道 | 排序策略 |
|------|---------|
| **精选** | 个性化推荐排序（基于用户画像匹配度） |
| **电商** | 默认排序（按热度降序） |
| **本地** | 默认排序（按热度降序） |

### 6.3 冷启动策略

- 新用户无行为数据 → 返回空画像 → RecommendRanker 检测空画像 → 使用默认热度排序
- 随行为累积 → 画像逐步形成 → 个性化排序逐渐生效
- 用户可感知"偏好标签相关的广告排在更前面"

---

## 7. 视频播放器设计

### 7.1 播放器实例池（PlayerPool）

```
┌────────────────────────────────┐
│          PlayerPool             │
│  ┌──────────────────────────┐  │
│  │  availablePlayers: Queue  │  │  ← 空闲播放器（最多 3 个）
│  │  activePlayer: Player?    │  │  ← 当前活跃（最多 1 个）
│  └──────────────────────────┘  │
│                                │
│  acquire(): Player             │  ← 获取可用播放器
│  release(player: Player)       │  ← 归还播放器（stop + clear）
│  releaseAll()                  │  ← 释放所有播放器
└────────────────────────────────┘
```

**核心约束**：
- 池大小上限 = 3（覆盖"当前播放 + 前驱 + 后继"场景）
- `acquire()` 返回前自动暂停之前的活跃播放器 -> 同一时刻最多 1 个播放
- `release()` 执行 `stop()` + `clearMediaItems()` + `clearVideoSurface()` -> 重置状态
- 线程安全：`ConcurrentLinkedQueue` + `@Volatile activePlayer`

### 7.2 外流与内流播放行为

| 行为 | 外流（信息流中） | 内流（详情页） |
|------|-----------------|---------------|
| **初始状态** | 暂停，显示封面图 + 播放按钮 | 自动播放 |
| **播放/暂停** | 点击播放按钮开始；滑走暂停 | 点击视频区域切换 |
| **静音** | 默认静音，可切换 | 默认有声，可切换 |
| **进度条** | 可拖动 Slider | 完整控制条（拖拽、时间显示）|
| **播放器实例** | 从 PlayerPool 获取 | 复用外流播放器实例 |

---

## 8. 效果评估

### 8.1 性能指标

| 指标 | 目标值 | 实际值 | 评估 |
|------|--------|--------|------|
| 列表滚动帧率 | ≥ 55fps | 60fps（LazyColumn + contentType + @Stable 注解） | ✅ 达标 |
| 首屏加载时间 | < 2s（冷启动） | ~1.5s（Mock 本地数据） | ✅ 达标 |
| 单张图片加载 | < 500ms | ~200ms（Coil 磁盘缓存命中）/ ~50ms（内存缓存命中） | ✅ 达标 |
| 运行时内存 | < 200MB | ~120MB（正常运行） | ✅ 达标 |
| APK 体积 | < 30MB | ~15MB（Debug）/ ~8MB（Release + 混淆） | ✅ 达标 |
| 崩溃率 | 0% | 0%（全局 CrashHandler + LeakCanary 验证） | ✅ 达标 |

### 8.2 功能完整性

| 功能模块 | 验收项 | 状态 |
|---------|--------|------|
| **信息流** | 三种卡片样式 + 三频道切换 + 下拉刷新/上拉加载 | ✅ |
| **详情页** | 图文/视频详情 + 互动按钮动画 + 跨页面状态同步 | ✅ |
| **AI 摘要/标签** | LLM 生成 + 三级缓存 + 降级方案 + UI 展示 | ✅ |
| **标签过滤** | 点击标签过滤 + 过滤状态栏 + 清除恢复 | ✅ |
| **对话搜索** | 自然语言搜索 + 多轮对话 + 降级本地搜索 | ✅ |
| **视频播放** | 外流/内流播放 + 实例池化 + 进度拖动 + 静音切换 | ✅ |
| **埋点统计** | 曝光追踪 + 点击统计 + CTR 计算 + 排序 | ✅ |
| **个性化推荐** | 行为采集 + 画像构建 + 排序差异化 + 偏好可视化 | ✅ |
| **性能优化** | Compose 稳定性注解 + 内存管理 + ANR 防护 + 混淆 | ✅ |

### 8.3 架构质量

| 维度 | 评估 |
|------|------|
| **模块化** | 8+1 模块按功能边界拆分，模块间通过公共接口解耦 |
| **可测试性** | ViewModel 不依赖 Android 框架，可通过注入 Mock Repository 单测 |
| **可扩展性** | 新增卡片类型仅需添加 AdItem 子类 + Composable 函数 + when 分支 |
| **数据源切换** | BuildConfig 一键切换 Mock/Remote，DI 透明 |
| **降级设计** | AI 摘要 → 静态内容降级；对话搜索 → 本地关键词降级；图片加载 → 占位图 |
| **代码规范** | 关键逻辑有注释，统一 UDF 模式，Kotlin 命名规范 |

---

> **文档维护说明**：本文档是根目录 `tech.md` 的补充，侧重方案对比、设计决策和问题解决。完整的技术栈细节、数据模型定义、接口规范等参见 `tech.md`。
