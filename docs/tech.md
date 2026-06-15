# AI广告推荐信息流 — 技术文档

> **版本**: v1.0  
> **作者**: 赵诗阳 
> **日期**: 2026-06-03

---

## 目录

1. [架构设计](#1-架构设计)
2. [技术选型](#2-技术选型)
3. [模块设计](#3-模块设计)
4. [数据层设计](#4-数据层设计)
5. [信息流引擎设计](#5-信息流引擎设计)
6. [视频播放器设计](#6-视频播放器设计)
7. [AI 集成方案](#7-ai-集成方案)
8. [缓存体系设计](#8-缓存体系设计)
9. [埋点与统计设计](#9-埋点与统计设计)
10. [用户行为与个性化推荐](#10-用户行为与个性化推荐)
11. [性能优化策略](#11-性能优化策略)
12. [方案对比与决策](#12-方案对比与决策)

---

## 1. 架构设计

### 1.1 整体架构：MVVM + Repository + 模块化

本项目采用 **MVVM（Model-View-ViewModel）** 架构模式，结合 **Repository 模式** 实现数据层的抽象与隔离，并通过**功能模块化**拆分业务边界。

```
┌─────────────────────────────────────────────────────────┐
│                      UI Layer (View)                     │
│  Compose Activity / Composable 函数                      │
│  声明式 UI，通过 collectAsStateWithLifecycle 观察状态    │
├─────────────────────────────────────────────────────────┤
│                   ViewModel Layer                        │
│  FeedViewModel / DetailViewModel / SearchViewModel ...   │
│  持有 UI 状态 (StateFlow)，处理业务逻辑，调度 Repository  │
├─────────────────────────────────────────────────────────┤
│                  Repository Layer                        │
│  AdRepository / UserBehaviorRepository / AiRepository    │
│  统一数据访问接口，协调本地与远程数据源                    │
├─────────────────────┬───────────────────────────────────┤
│   Local DataSource   │    Remote DataSource              │
│   Mock JSON / Room   │    OkHttp + Retrofit              │
│   SharedPreferences  │    DeepSeek API / OpenAI Compatible   │
│   Disk Cache         │                                   │
└─────────────────────┴───────────────────────────────────┘
```

**选型理由**：

- **MVVM** 是 Google 官方推荐的 Android 架构，ViewModel 天然感知 Activity/Fragment 生命周期，避免内存泄漏；StateFlow 提供响应式状态通知。
- **Repository 模式** 将数据来源（Mock / Remote / Cache）对上层透明，支持通过 BuildConfig 一键切换数据源。
- **模块化** 按功能边界拆分（feed / detail / search / ai / player / analytics），模块间通过接口解耦。

### 1.2 架构对比

| 维度 | MVC | MVP | MVVM |
|------|-----|-----|-------------------|
| **View 职责** | 持有 Controller，处理 UI + 部分逻辑 | 通过 Presenter 接口解耦 | 仅渲染 UI，观察 ViewModel 状态 |
| **逻辑层** | Controller（与 View 紧耦合） | Presenter（通过接口回调 View） | ViewModel（通过 StateFlow 推送状态） |
| **生命周期感知** | 需手动管理 | 需手动管理 | ViewModel 自动感知，onCleared 释放资源 |
| **数据驱动** | 命令式更新 | 命令式更新 | 声明式 (StateFlow / LiveData)，UI 自动响应 |
| **测试性** | 差 | 较好（Presenter 可单测） | 好（ViewModel 可独立单测，不依赖 Android） |
| **学习成本** | 低 | 中 | 中（需理解协程 + Flow） |

**决策结论**：MVVM 在 Google 官方支持、Jetpack 集成度、生命周期安全和代码可测试性方面均优于 MVC/MVP，适合本项目中复杂的跨页面状态同步（如信息流 ↔ 详情页点赞/收藏状态）和 AI 异步调用场景。

---

## 2. 技术选型

### 2.1 技术栈全景

| 类别 | 技术选型 | 版本/说明 |
|------|---------|----------|
| **语言** | Kotlin | 100% Kotlin，充分利用协程、扩展函数、密封类等特性 |
| **最低 SDK** | API 26 (Android 8.0) | 覆盖 95%+ 活跃设备 |
| **构建工具** | Gradle Kotlin DSL | 类型安全的构建配置，Version Catalog 统一管理依赖版本 |
| **UI 布局** | Jetpack Compose + Material3 | 声明式 UI，Composable 函数构建，ConstraintLayout 用于复杂约束场景 |
| **列表组件** | LazyColumn + items() | Compose 原生懒加载列表，内建复用机制 |
| **页面导航** | Navigation Compose | Compose 原生导航，支持共享元素过渡动画、返回栈管理 |
| **网络层** | OkHttp 4.x + Retrofit 2.x | 拦截器链、连接池复用、超时重试、Mock 拦截器切换 |
| **JSON 解析** | Kotlinx Serialization | Kotlin 原生序列化方案，编译期安全，无反射开销 |
| **图片加载** | Coil 3.x | Kotlin 协程原生支持，轻量级（~200KB），内存/磁盘双缓存 |
| **视频播放** | ExoPlayer (Media3) | Google 官方推荐，支持 DASH/HLS/RTMP，播放器实例池化管理 |
| **异步处理** | Kotlin Coroutines + Flow | 结构化并发，StateFlow 用于 UI 状态管理，Channel 用于单次事件 |
| **本地存储** | Room (SQLite) + DataStore | Room 管理结构化数据（广告数据/行为记录），DataStore 管理键值偏好 |
| **依赖注入** | Koin | 轻量级 DI，Kotlin DSL 配置，无需注解处理器 |
| **AI 集成** | OkHttp + 自定义拦截器 | 统一网络层，AI API 通过 Retrofit 接口定义 |

### 2.2 关键选型对比

#### 2.2.1 图片加载库：Glide vs Coil

| 维度 | Glide | Coil (本项目选择) |
|------|-------|-------------------|
| **体积** | ~2MB | ~200KB |
| **协程支持** | 需额外适配 | 原生 Kotlin 协程支持 |
| **API 设计** | Java 风格 (Builder) | Kotlin DSL 风格 |
| **内存管理** | 基于 BitmapPool | 基于协程自动取消（lifecycleScope） |
| **GIF 支持** | 内置 | 需扩展 |
| **Google 推荐** | 传统选择 | 现代 Android 推荐 |

**决策结论**：本项目为全新 Kotlin 项目，无历史包袱，Coil 的 KTX 集成、协程取消机制和体积优势与项目目标高度契合。

#### 2.2.2 JSON 解析：Gson vs Moshi vs Kotlinx Serialization

| 维度 | Gson | Moshi | Kotlinx Serialization (本项目选择) |
|------|------|-------|-----------------------------------|
| **反射** | 使用反射 | 可选（codegen） | 编译期生成，无反射 |
| **Kotlin 支持** | 部分（null 安全不足） | 较好 | 原生支持（默认值、密封类、data class） |
| **性能** | 较慢（反射） | 快 | 快（编译期序列化器生成） |
| **多态序列化** | 支持 | 需适配器 | 原生支持（密封类层级） |
| **体积** | 较大 | 中等 | 小（Kotlin 标准库扩展） |

**决策结论**：项目广告数据模型包含多种卡片的差异字段（大图/小图/视频），使用 Kotlinx Serialization 的密封类多态序列化可完美建模，且编译期安全避免运行时 JSON 解析崩溃。

#### 2.2.3 视频播放器：系统 MediaPlayer vs ExoPlayer

| 维度 | MediaPlayer | ExoPlayer (本项目选择) |
|------|------------|----------------------|
| **格式支持** | 有限（依赖设备） | 广泛（DASH/HLS/RTMP/MP4） |
| **自定义性** | 低（黑盒封装） | 高（组件可插拔） |
| **播放器实例池** | 不支持 | 可自定义 |
| **错误恢复** | 弱 | 强（自动重试、降级） |
| **维护状态** | 基本停滞 | Google 积极维护（Media3） |

**决策结论**：需求明确要求播放器实例池化和资源复用，ExoPlayer 的组件化设计允许自定义 PlayerPool 和播放器复用逻辑，MediaPlayer 无法满足。

---

## 3. 模块设计

### 3.1 模块划分总览

```
app/
├── feed/              # 信息流模块
│   ├── ui/            #   FeedActivity, FeedFragment, Adapter, ViewHolders
│   ├── viewmodel/     #   FeedViewModel
│   └── model/         #   信息流特有 UI 状态
│
├── detail/            # 详情页模块
│   ├── ui/            #   DetailActivity, DetailFragment
│   ├── viewmodel/     #   DetailViewModel
│   └── model/         #   详情页 UI 状态
│
├── data/              # 数据层模块
│   ├── model/         #   数据模型（AdItem, Channel, Tag 等）
│   ├── repository/    #   数据仓库（AdRepository, BehaviorRepository）
│   ├── local/         #   本地数据源（MockJsonDataSource, Room DAO）
│   └── remote/        #   远程数据源（ApiService, MockInterceptor）
│
├── ai/                # AI 模块
│   ├── api/           #   AI API 接口定义
│   ├── model/         #   AI 请求/响应模型（SummaryRequest, TagResult 等）
│   └── cache/         #   AI 结果缓存（AiCacheManager）
│
├── search/            # 对话式搜索模块
│   ├── ui/            #   SearchActivity, ChatAdapter, 聊天气泡
│   ├── viewmodel/     #   SearchViewModel
│   └── engine/        #   意图解析引擎 + 匹配引擎
│
├── player/            # 视频播放器模块
│   ├── controller/    #   播放控制（PlaybackController）
│   ├── pool/          #   播放器实例池（PlayerPool）
│   └── ui/            #   播放器 UI 组件（PlayerView 封装）
│
├── analytics/         # 埋点统计模块
│   ├── tracker/       #   曝光/点击/行为追踪器
│   ├── model/         #   统计模型
│   └── ui/            #   统计展示页面
│
├── behavior/          # 用户行为模块（新增）
│   ├── tracker/       #   行为采集器（BehaviorCollector）
│   ├── profile/       #   用户画像引擎（UserProfileEngine）
│   ├── recommend/     #   个性化推荐排序引擎（RecommendRanker）
│   ├── model/         #   行为/画像模型
│   └── storage/       #   行为持久化存储
│
└── common/            # 公共模块
    ├── network/       #   网络层封装（OkHttpClient 工厂、Retrofit 工厂、拦截器）
    ├── imageloader/   #   图片加载封装（Coil 配置、缓存策略）
    ├── widget/        #   公共 UI 组件（AdCardView 基类、状态视图等）
    └── util/          #   工具类（时间、字符串、MD5 等）
```

### 3.2 模块间通信规范

- **UI → ViewModel**：通过方法调用和事件传递（用户点击 → `viewModel.onEvent(Event)`）
- **ViewModel → UI**：通过 `StateFlow<UiState>` 单向推送，UI 使用 `collectAsStateWithLifecycle()` 收集
- **ViewModel → Repository**：通过协程调用，Repository 返回 `Result<T>` 或 `Flow<T>`
- **跨模块通信**：通过公共模块定义的接口（如 `AdItem` 各模块共享），跨页面状态同步通过共享 ViewModel 或 Room Flow 观察实现
- **AI 模块 → 数据模块**：AI 模块生成摘要/标签后，写入 Repository 缓存，通知 UI 刷新

---

## 4. 数据层设计

### 4.1 数据模型

#### 4.1.1 核心数据模型（Kotlin 多态序列化）

```kotlin
// 广告卡片类型枚举
@Serializable
enum class AdType { LARGE_IMAGE, SMALL_IMAGE, VIDEO }

// 广告频道
@Serializable
enum class Channel { FEATURED, ECOMMERCE, LOCAL }

// 广告基类（密封类实现多态）
@Serializable
sealed class AdItem {
    abstract val id: String
    abstract val title: String
    abstract val description: String
    abstract val advertiserName: String
    abstract val advertiserAvatar: String
    abstract val channel: Channel
    abstract val tags: List<Tag>
    abstract val aiSummary: String?
    abstract val likeCount: Int
    abstract val collectCount: Int
    abstract val shareCount: Int
    abstract val exposureCount: Int
    abstract val clickCount: Int
    abstract var isLiked: Boolean
    abstract var isCollected: Boolean

    @Serializable
    data class LargeImageAd(
        override val id: String,
        override val title: String,
        val coverImageUrl: String,  // 大尺寸封面图
        override val description: String,
        override val advertiserName: String,
        override val advertiserAvatar: String,
        override val channel: Channel,
        override val tags: List<Tag>,
        override val aiSummary: String? = null,
        override val likeCount: Int = 0,
        override val collectCount: Int = 0,
        override val shareCount: Int = 0,
        override val exposureCount: Int = 0,
        override val clickCount: Int = 0,
        override var isLiked: Boolean = false,
        override var isCollected: Boolean = false,
    ) : AdItem()

    @Serializable
    data class SmallImageAd(
        override val id: String,
        override val title: String,
        val thumbnailUrl: String,   // 小尺寸缩略图
        val imagePosition: ImagePosition, // LEFT / RIGHT
        override val description: String,
        override val advertiserName: String,
        override val advertiserAvatar: String,
        override val channel: Channel,
        override val tags: List<Tag>,
        override val aiSummary: String? = null,
        override val likeCount: Int = 0,
        override val collectCount: Int = 0,
        override val shareCount: Int = 0,
        override val exposureCount: Int = 0,
        override val clickCount: Int = 0,
        override var isLiked: Boolean = false,
        override var isCollected: Boolean = false,
    ) : AdItem()

    @Serializable
    data class VideoAd(
        override val id: String,
        override val title: String,
        val videoUrl: String,
        val coverImageUrl: String,  // 视频封面图
        override val description: String,
        override val advertiserName: String,
        override val advertiserAvatar: String,
        override val channel: Channel,
        override val tags: List<Tag>,
        override val aiSummary: String? = null,
        override val likeCount: Int = 0,
        override val collectCount: Int = 0,
        override val shareCount: Int = 0,
        override val exposureCount: Int = 0,
        override val clickCount: Int = 0,
        override var isLiked: Boolean = false,
        override var isCollected: Boolean = false,
    ) : AdItem()
}

// 智能标签
@Serializable
data class Tag(
    val name: String,
    val category: TagCategory
)

@Serializable
enum class TagCategory {
    @SerialName("category") CATEGORY,     // 品类
    @SerialName("style") STYLE,           // 风格
    @SerialName("audience") AUDIENCE,     // 受众
    @SerialName("scene") SCENE            // 场景
}

// 图片位置（小图卡片）
@Serializable
enum class ImagePosition { LEFT, RIGHT }
```

#### 4.1.2 用户行为数据模型

```kotlin
// 用户行为记录
@Entity(tableName = "user_behaviors")
data class UserBehavior(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val adId: String,
    val behaviorType: BehaviorType,
    val tags: List<String>,          // 涉及的标签名称列表
    val timestamp: Long = System.currentTimeMillis()
)

enum class BehaviorType(val weight: Int) {
    CLICK(1),        // 点击
    LIKE(2),         // 点赞
    COLLECT(3),      // 收藏
    SHARE(2),        // 分享
    TAG_CLICK(1),    // 标签点击
    SEARCH(1)        // 搜索
}

// 用户兴趣画像（聚合结果，可缓存）
data class UserProfile(
    val tagWeights: Map<String, Double>,  // 标签 → 偏好得分
    val totalClicks: Int,
    val totalLikes: Int,
    val totalCollects: Int,
    val totalShares: Int,
    val lastUpdateTime: Long
)
```

### 4.2 数据源切换机制

```
                   ┌──────────────┐
                   │   BuildConfig │
                   │   .DATA_MODE  │
                   └──────┬───────┘
                          │
          ┌───────────────┴───────────────┐
          ▼                               ▼
   DATA_MODE = MOCK                DATA_MODE = REMOTE
          │                               │
          ▼                               ▼
┌──────────────────┐           ┌──────────────────┐
│ MockJsonDataSource│           │ RemoteDataSource  │
│ (assets/*.json)   │           │ (Retrofit API)    │
└────────┬─────────┘           └────────┬─────────┘
         │                              │
         └──────────────┬───────────────┘
                        ▼
              ┌──────────────────┐
              │    AdRepository   │
              │  (统一数据接口)    │
              └──────────────────┘
```

**Mock 数据规范**：
- `assets/mock/ads_featured.json` — 精选频道广告数据（含分页）
- `assets/mock/ads_ecommerce.json` — 电商频道广告数据
- `assets/mock/ads_local.json` — 本地频道广告数据
- JSON 结构包含 `page`、`totalPages`、`items` 字段，支持分页模拟

**切换方式**：`gradle.properties` 中配置 `DATA_MODE=mock|remote`，编译时通过 BuildConfig 注入。

### 4.3 数据生命周期管理

| 阶段 | 操作 | 内存缓存 | 磁盘缓存 |
|------|------|---------|---------|
| **首次加载** | 加载首页数据 | 存入 AdCache (LinkedHashMap) | 写入 Room DB |
| **下拉刷新** | 清除当前页缓存 → 重新加载第一页 | 清除对应频道内存缓存 | 更新 Room 数据 |
| **上拉加载** | 追加下一页数据 | 追加到 AdCache | 写入 Room DB |
| **页面销毁** | Activity/Fragment onDestroy | 释放 AdCache（非 LRU 部分） | 保留 Room 数据 |
| **进程杀死** | — | 全部释放 | 保留 Room 数据，下次冷启动恢复 |

---

## 5. 信息流引擎设计

### 5.1 LazyColumn 多类型卡片

信息流包含 3 种卡片类型，使用 Compose `LazyColumn` + `when` 分发不同 Composable：

```
AdItem (sealed class)
├── LargeImageAd  →  LargeImageCard()  Composable
├── SmallImageAd  →  SmallImageCard()  Composable
└── VideoAd       →  VideoCard()       Composable
```

**实现策略**：LazyColumn 内通过 `items(adList, key = { it.id })` 渲染列表。`key` 参数确保 Compose 正确追踪每个 item 的身份，避免重组时状态错乱。卡片类型通过 `when(adItem)` 分发：

```kotlin
@Composable
fun FeedList(ads: List<AdItem>, ...) {
    LazyColumn {
        items(items = ads, key = { it.id }) { ad ->
            when (ad) {
                is AdItem.LargeImageAd -> LargeImageCard(ad, ...)
                is AdItem.SmallImageAd -> SmallImageCard(ad, ...)
                is AdItem.VideoAd      -> VideoCard(ad, ...)
            }
        }
    }
}
```

### 5.2 卡片动态化方案

**方案设计**：卡片样式由数据模型 `AdType` 字段决定，Composable 层仅做 UI 渲染。

```
后端返回 AdType → Compose when(adType) 分发 → 渲染对应 Composable
```

**扩展性**：新增卡片类型只需：
1. 在 `AdItem` 密封类中添加新子类
2. 实现对应的 `@Composable` 卡片函数
3. 在 `when` 分支中新增映射

**卡片统一交互元素**：所有卡片共享 `AdCardActions` 回调接口（`onLike`、`onCollect`、`onShare`、`onTagClick`），互动按钮通过 Material3 `IconButton` 实现，动画通过 `animateFloatAsState` 驱动。

### 5.3 频道切换方案

**实现方式**：`HorizontalPager` + `TabRow`（Material3 组件）

- `HorizontalPager` 管理页面滑动手势，`TabRow` 渲染顶部标签栏
- 每个 Tab 页内嵌独立的 `FeedList` Composable
- Tab 切换时通过 `pagerState` 监听到 `onPageChanged` 触发数据重载
- 每个频道持有独立的 `FeedViewModel` 实例（通过 `key(channel)` 注入区分）

**Tab 切换数据刷新流程**：
```
用户点击/滑动 Tab
    → HorizontalPager 页切换
    → LaunchedEffect(pageIndex) 触发
    → FeedViewModel.loadFeed(channel)  // 重新加载对应频道数据
    → 列表滚动到顶部 (LazyListState.scrollToItem(0))
```

### 5.4 列表滚动位置保持

**问题**：从详情页返回信息流时，需要恢复到原来的滚动位置。

**方案**：
1. `LazyListState` 的 `firstVisibleItemIndex` + `firstVisibleItemScrollOffset` 天然支持跨重组保持
2. 进入详情页时，`LazyListState` 保持在内存中不被销毁（单 Activity 架构，FeedScreen 被 DetailScreen 覆盖而非销毁）
3. 返回时 `LazyListState` 自动恢复，无需额外代码

### 5.5 下拉刷新与上拉加载

| 组件 | 实现 | 说明 |
|------|------|------|
| **下拉刷新** | Material3 `pullToRefresh` modifier | `PullToRefreshBox` 包装 LazyColumn，`isRefreshing` 绑定 StateFlow |
| **上拉加载** | `LazyListState` + `LaunchedEffect` | 监听 `layoutInfo.visibleItemsInfo.lastOrNull()?.index` 接近列表末尾时触发 `viewModel.loadMore()` |
| **空态** | `EmptyState` Composable | 居中显示图标（`ic_empty`）+ 提示文案 |
| **错误态** | `ErrorState` Composable | 显示错误提示 + "重试"`TextButton` |
| **加载完成** | "没有更多了" Footer | `LoadState.END` 时列表末尾显示灰色提示文字 |

**分页参数设计**：
```kotlin
data class PaginationState(
    val currentPage: Int = 1,
    val pageSize: Int = 10,
    val hasMore: Boolean = true,
    val loadState: LoadState = LoadState.IDLE
)

enum class LoadState { IDLE, LOADING, REFRESHING, END, ERROR }
```

---

## 6. 视频播放器设计

### 6.1 播放器实例池（PlayerPool）

**设计目标**：同一时刻信息流中最多 1 个活跃播放器，滚动时复用实例。

```
┌────────────────────────────────┐
│          PlayerPool             │
│  ┌──────────────────────────┐  │
│  │  availablePlayers: Queue  │  │  ← 空闲播放器
│  │  activePlayer: Player?    │  │  ← 当前活跃（最多 1 个）
│  │  maxPoolSize: Int = 3     │  │
│  └──────────────────────────┘  │
│                                │
│  acquire(): Player             │  ← 获取可用播放器
│  release(player: Player)       │  ← 归还播放器
│  releaseAll()                  │  ← 释放所有播放器
└────────────────────────────────┘
```

**复用流程**：
```
1. 用户滑到视频卡片 A
2. PlayerPool.acquire() → 获取播放器 P1
3. P1 绑定 VideoAd A → 开始播放
4. 用户滑走，视频卡片 A 离开屏幕
5. PlayerPool.release(P1) → P1 重置（stop + clearSurface）
6. 用户滑到视频卡片 B
7. PlayerPool.acquire() → 复用 P1，绑定 VideoAd B
```

### 6.2 外流与内流播放行为

| 行为 | 外流（信息流中） | 内流（详情页） |
|------|-----------------|---------------|
| **初始状态** | 暂停，显示封面图 + 播放按钮 | 自动播放 |
| **播放/暂停** | 点击播放按钮开始；滑走暂停 | 点击视频区域切换 |
| **静音** | 默认静音，可切换 | 默认有声，可切换 |
| **进度条** | 简单进度条 | 完整控制条（拖拽、时间显示）|
| **播放器实例** | 从 PlayerPool 获取 | 复用外流播放器实例 |

**播放器实例跨页面传递**：进入视频详情页时，通过全局 `PlayerPool` 将当前活跃播放器传递给 `DetailActivity`，详情页直接接管播放器，避免重新创建和加载。

---

## 7. AI 集成方案

### 7.1 大模型 API 接入

#### 7.1.1 API 设计

**接口定义**：

```kotlin
interface AiApiService {
    @POST("v1/chat/completions")
    suspend fun generateSummaryAndTags(
        @Body request: AiRequest
    ): AiResponse
}

@Serializable
data class AiRequest(
    val model: String = "qwen-turbo",
    val messages: List<AiMessage>,
    val temperature: Double = 0.3,  // 低温度确保输出稳定
    val max_tokens: Int = 512,
    val response_format: ResponseFormat? = null  // JSON 模式
)

@Serializable
data class AiMessage(
    val role: String,
    val content: String
)
```

#### 7.1.2 AI 输出约束（Prompt Engineering）

**关键设计**：通过 System Prompt + JSON Mode 严格约束输出格式，确保客户端可解析。

```
System Prompt:
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

**输入构造**（将广告信息拼接为 Prompt）：

```kotlin
fun buildPrompt(ad: AdItem): String = buildString {
    append("广告标题：${ad.title}\n")
    append("广告描述：${ad.description}\n")
    append("广告主：${ad.advertiserName}\n")
    append("广告类型：${ad.type.name}\n")
    append("\n请为以上广告生成摘要和智能标签。")
}
```

#### 7.1.3 响应解析与校验

```kotlin
// 解析大模型返回的 content 字符串
fun parseAiResponse(rawContent: String): AiGeneratedContent? {
    return try {
        val json = Json.parseToJsonElement(rawContent).jsonObject
        val summary = json["summary"]?.jsonPrimitive?.content ?: return null
        val tags = json["tags"]?.jsonArray?.mapNotNull { element ->
            val obj = element.jsonObject
            val name = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val category = try {
                TagCategory.valueOf(obj["category"]?.jsonPrimitive?.content?.uppercase() ?: "category")
            } catch (e: Exception) { TagCategory.CATEGORY }
            Tag(name, category)
        } ?: emptyList()
        AiGeneratedContent(summary, tags)
    } catch (e: Exception) {
        null  // 解析失败返回 null，触发降级
    }
}
```

#### 7.1.4 降级方案

```
AI 调用成功 → 使用 AI 生成内容
AI 调用失败 / 网络超时 → 降级为预设静态摘要和标签
```

**静态降级数据**：每条广告在 Mock 数据中预设 `fallbackSummary` 和 `fallbackTags` 字段。

### 7.2 AI 结果缓存策略

| 缓存层级 | 存储位置 | Key | 过期策略 | 说明 |
|---------|---------|-----|---------|------|
| **内存缓存** | LinkedHashMap (LRU) | adId | 进程存活期间 | 同一条广告的 AI 结果仅请求一次 |
| **磁盘缓存** | Room DB (ai_cache 表) | adId 的 MD5 | 7天（可配置） | 冷启动后优先使用缓存，过期后重新请求 |
| **网络请求** | API 调用 | — | — | 仅在内存和磁盘均未命中时触发 |

**缓存命中流程**：
```
UI 请求 AdItem 的 AI 内容
    → 检查内存缓存 (LRU)
        → 命中：直接返回
        → 未命中：检查 Room 磁盘缓存
            → 命中且未过期：返回 + 写入内存缓存
            → 未命中/已过期：发起 API 请求
                → 成功：写入内存 + Room → 返回
                → 失败：使用降级静态内容
```

### 7.3 对话式搜索

#### 7.3.1 意图解析

**流程**：

```
用户输入自然语言
    → 调用大模型 API 解析意图
    → 输出结构化搜索条件
    → 匹配引擎将条件与本地广告数据匹配
    → 按匹配度排序返回结果
```

**意图解析 Prompt 约束输出格式**：

```
{
  "categories": ["数码"],        // 品类约束
  "audiences": ["学生党"],       // 受众约束
  "styles": [],                  // 风格约束
  "scenes": [],                  // 场景约束
  "priceRange": {"min": 0, "max": 500},
  "keywords": ["性价比", "平板"]  // 自由关键词
}
```

#### 7.3.2 匹配引擎

```kotlin
class AdMatchingEngine {
    fun match(ads: List<AdItem>, criteria: SearchCriteria): List<AdMatchResult> {
        return ads.map { ad ->
            var score = 0.0

            // 标签精确匹配
            score += ad.tags.count { tag ->
                tag.name in criteria.allTargetTags() ||
                tag.category.name.lowercase() in criteria.categories
            } * 3.0

            // 关键词匹配（标题 + 描述）
            criteria.keywords.forEach { keyword ->
                if (ad.title.contains(keyword, ignoreCase = true)) score += 2.0
                if (ad.description.contains(keyword, ignoreCase = true)) score += 1.0
            }

            // 受众匹配
            score += ad.tags.count { tag ->
                tag.name in criteria.audiences
            } * 2.0

            AdMatchResult(ad, score)
        }.filter { it.score > 0 }
         .sortedByDescending { it.score }
    }
}
```

#### 7.3.3 多轮对话

- 对话历史存储在 `SearchViewModel` 的 `List<ChatMessage>` 中
- 每轮携带最近 N 条（最多 10 条）对话上下文调用 AI API
- 支持"清空对话"重置上下文

---

## 8. 缓存体系设计

### 8.1 图片缓存（Coil）

```
请求图片
    ↓
┌─── 一级：内存缓存 (LRU, 1/8 可用内存) ───┐
│   命中 → 直接返回 Bitmap                   │
│   未命中 ↓                                  │
└────────────────────────────────────────────┘
    ↓
┌─── 二级：磁盘缓存 (DiskLRU, 200MB) ────────┐
│   命中 → 解码 → 写入内存缓存 → 返回         │
│   未命中 ↓                                  │
└────────────────────────────────────────────┘
    ↓
┌─── 三级：网络/本地资源加载 ────────────────┐
│   成功 → 解码 → 写入磁盘缓存 + 内存缓存     │
│   失败 → 显示占位图 → 重试 3 次            │
│   仍失败 → 显示错误占位图                   │
└────────────────────────────────────────────┘
```

**Coil 配置**：

```kotlin
val imageLoader = ImageLoader.Builder(context)
    .memoryCache {
        MemoryCache.Builder(context)
            .maxSizePercent(0.125)  // 1/8 可用内存
            .build()
    }
    .diskCache {
        DiskCache.Builder()
            .directory(cacheDir.resolve("image_cache"))
            .maxSizeBytes(200 * 1024 * 1024)  // 200MB
            .build()
    }
    .build()
```

### 8.2 广告数据缓存

| 层级 | 存储 | 内容 | 过期 |
|------|------|------|------|
| **内存** | AdMemoryCache (LinkedHashMap, MaxSize=200) | 最近访问的广告数据对象 | 下拉刷新时清除 |
| **持久化** | Room DB (ads 表 + ai_cache 表) | 广告基础数据 + AI 结果 | 默认 7 天过期 |

### 8.3 视频缓存策略

- **预加载**：当前视频播放时，预加载队列中下一个视频的首帧/封面
- **快速滑动跳过**：使用 `RecyclerView.OnScrollListener` 检测快速滑动（`SCROLL_STATE_SETTLING`），跳过视频加载
- **停止后加载**：用户停止滑动（`SCROLL_STATE_IDLE`）后，加载当前可见视频

---

## 9. 埋点与统计设计

### 9.1 曝光统计

#### 9.1.1 曝光判定口径

| 条件 | 说明 |
|------|------|
| **可见比例** | 广告卡片在屏幕可视区域内的可见部分 ≥ 50% |
| **停留时长** | 满足可见比例的状态持续 ≥ 1000ms |
| **去重** | 同一广告在同一次信息流会话中只计 1 次曝光 |

#### 9.1.2 实现方案

使用 `RecyclerView.OnScrollListener` + `Handler` 延时检测：

```kotlin
class ExposureTracker(
    private val recyclerView: RecyclerView,
    private val onExposed: (AdItem) -> Unit
) {
    private val exposedIds = mutableSetOf<String>()  // 已曝光去重
    private val pendingExposures = mutableMapOf<String, Runnable>()
    private val handler = Handler(Looper.getMainLooper())

    fun onScroll() {
        val layoutManager = recyclerView.layoutManager as LinearLayoutManager
        val firstVisible = layoutManager.findFirstVisibleItemPosition()
        val lastVisible = layoutManager.findLastVisibleItemPosition()

        for (position in firstVisible..lastVisible) {
            val viewHolder = recyclerView.findViewHolderForAdapterPosition(position)
                as? BaseAdViewHolder ?: continue
            val adItem = viewHolder.adItem ?: continue

            if (adItem.id in exposedIds) continue

            // 计算可见比例
            val visibleRatio = viewHolder.calculateVisibleRatio(recyclerView)
            if (visibleRatio >= 0.5f) {
                // 延迟 1000ms 确认停留
                val runnable = Runnable {
                    exposedIds.add(adItem.id)
                    onExposed(adItem)
                }
                pendingExposures[adItem.id]?.let { handler.removeCallbacks(it) }
                pendingExposures[adItem.id] = runnable
                handler.postDelayed(runnable, 1000)
            } else {
                // 不满足 50% 则取消计时
                pendingExposures.remove(adItem.id)?.let {
                    handler.removeCallbacks(it)
                }
            }
        }
    }
}
```

#### 9.1.3 曝光统计口径对比

| 方案 | 判定条件 | 优点 | 缺点 |
|------|---------|------|------|
| **方案 A：仅可见比例** | 卡片 ≥ 50% 可见即曝光 | 实现简单 | 快速划过会误统计 |
| **方案 B：可见比例 + 停留时长 (本项目选择)** | ≥ 50% 可见 + ≥ 1s 停留 | 更接近真实曝光意图 | 需延时检测机制 |
| **方案 C：逐帧检测** | 基于 UI 刷新帧检测 | 精度最高 | 性能开销大，过度设计 |

**决策**：方案 B 平衡了精度和实现复杂度，符合训练营课题要求的曝光统计口径。

### 9.2 点击统计

- 用户点击广告卡片进入详情页 → 记录点击事件
- 去重：同一广告在同一次会话中点击多次只计 1 次

### 9.3 统计可视化

**统计页面展示**：
- 每个广告的曝光次数、点击次数
- 点击率（CTR = 点击数 / 曝光数 × 100%）
- 按曝光数、点击数、CTR 可排序
- 用户行为总览（总点击/点赞/收藏/分享数）
- 偏好标签云/Top 标签柱状图

---

## 10. 用户行为与个性化推荐

### 10.1 行为采集架构

```
用户交互事件
    ↓
BehaviorCollector（行为采集器）
    ↓
写入 Room DB (user_behaviors 表)
    ↓
UserProfileEngine（画像引擎）→ 聚合计算 → 更新 UserProfile
    ↓
RecommendRanker（推荐排序器）→ 基于画像重新排序广告列表
    ↓
FeedViewModel → 推送新排序结果到 UI
```

### 10.2 用户兴趣画像计算

```kotlin
class UserProfileEngine(private val behaviorDao: BehaviorDao) {

    suspend fun computeProfile(): UserProfile {
        val allBehaviors = behaviorDao.getAllBehaviors()

        // 按标签聚合权重
        val tagWeightMap = mutableMapOf<String, Double>()
        allBehaviors.forEach { behavior ->
            val weight = behavior.behaviorType.weight.toDouble()
            behavior.tags.forEach { tag ->
                tagWeightMap[tag] = (tagWeightMap[tag] ?: 0.0) + weight
            }
        }

        return UserProfile(
            tagWeights = tagWeightMap,
            totalClicks = allBehaviors.count { it.behaviorType == BehaviorType.CLICK },
            totalLikes = allBehaviors.count { it.behaviorType == BehaviorType.LIKE },
            totalCollects = allBehaviors.count { it.behaviorType == BehaviorType.COLLECT },
            totalShares = allBehaviors.count { it.behaviorType == BehaviorType.SHARE },
            lastUpdateTime = System.currentTimeMillis()
        )
    }
}
```

### 10.3 个性化推荐排序

```kotlin
class RecommendRanker(private val profileEngine: UserProfileEngine) {

    suspend fun rank(ads: List<AdItem>): List<AdItem> {
        val profile = profileEngine.computeProfile()

        // 新用户：无行为数据 → 默认排序（按热度/时间降序）
        if (profile.tagWeights.isEmpty()) {
            return ads.sortedByDescending { it.exposureCount }  // 按热度
        }

        // 有行为数据：按标签匹配度降序
        return ads.map { ad ->
            val matchScore = ad.tags.sumOf { tag ->
                profile.tagWeights[tag.name] ?: 0.0
            }
            ad to matchScore
        }.sortedByDescending { it.second }
         .map { it.first }
    }
}
```

**排序策略差异**：

| 频道 | 排序策略 |
|------|---------|
| **精选** | 个性化推荐排序（基于用户画像匹配度） |
| **电商** | 默认排序（按热度降序） |
| **本地** | 默认排序（按热度降序） |

---

## 11. 性能优化策略

### 11.1 列表滚动优化

| 优化项 | 实现方式 | 目标 |
|--------|---------|------|
| **ViewHolder 复用** | RecyclerView 内置 ViewHolder 回收池 | 避免重复 inflate |
| **setHasFixedSize** | `recyclerView.setHasFixedSize(true)` | 减少 requestLayout 调用 |
| **预加载** | `recyclerView.setItemViewCacheSize(5)` | 缓存离开屏幕的 ViewHolder |
| **RecycledViewPool** | 同类 ViewType 共享回收池 | 跨 Tab 复用 ViewHolder |
| **图片解码** | Coil 自动下采样到 View 尺寸 | 减少内存占用 |
| **层级扁平化** | ConstraintLayout 替代嵌套 LinearLayout | 减少测量/布局时间 |

### 11.2 内存管理

| 策略 | 实现 |
|------|------|
| **图片内存缓存上限** | Coil LRU 1/8 可用内存 |
| **播放器池上限** | 最多 3 个播放器实例 |
| **onLowMemory** | 清除图片内存缓存，释放空闲播放器 |
| **onTrimMemory(TRIM_MEMORY_UI_HIDDEN)** | 释放所有非必要缓存 |
| **LeakCanary** | Debug 模式下检测 Activity/Fragment 内存泄漏 |

### 11.3 性能指标目标

| 指标 | 目标值 | 测量工具 |
|------|--------|---------|
| 列表滚动帧率 | ≥ 55fps | Android Profiler GPU Rendering |
| 首屏加载时间 | < 2s（冷启动） | 计时埋点 |
| 单张图片加载 | < 500ms | Coil EventListener |
| 运行时内存 | < 200MB | Memory Profiler |
| APK 体积 | < 30MB | Build Analyzer |

---

## 12. 方案对比与决策

### 12.1 架构模式：MVC vs MVP vs MVVM

→ 见 [1.2 架构对比](#12-架构对比)，最终选择 **MVVM**。

### 12.2 数据源切换：Mock 拦截器 vs BuildConfig 分支

| 方案 | 实现方式 | 优点 | 缺点 |
|------|---------|------|------|
| **Mock 拦截器** | OkHttp Interceptor 拦截请求返回本地 JSON | 对上层透明，网络请求走真实路径 | ApiService 仍需定义，需维护 Mock JSON |
| **BuildConfig + Repository 分支 (本项目选择)** | Repository 层根据 BuildConfig 选择 Local/Remote DataSource | Repository 职责清晰，切换无侵入 | 需维护两套 DataSource 接口一致性 |

**决策**：本项目以 Mock 为主且需支持未来切换到真实 API，Repository 分支方案使 DataSource 切换在数据层内部闭环，不影响 ViewModel 和 UI 层。

### 12.3 跨页面状态同步：EventBus vs 共享 ViewModel vs 数据库观察

| 方案 | 实现 | 优点 | 缺点 |
|------|------|------|------|
| **EventBus** | 发布/订阅事件 | 解耦 | 事件泛滥难维护，生命周期不安全 |
| **共享 ViewModel (本项目选择)** | Activity 级别 ViewModel 共享 | 生命周期安全，数据流清晰 | 仅限同一 Activity 内 Fragment |
| **数据库观察 (本项目补充)** | Room Flow 观察数据表变化 | 跨进程、持久化 | 读写开销，适合持久状态 |

**决策**：详情页和信息流在同一 Activity 内的 Fragment 间时，使用共享 ViewModel；对于需要持久化的收藏/点赞状态，通过 Room + Flow 实现数据库驱动的状态同步。

### 12.4 UI 框架：ViewBinding (XML) vs Jetpack Compose

| 维度 | ViewBinding + XML | Jetpack Compose (本项目选择) |
|------|-------------------|------------------------------|
| **代码量** | 卡片布局 XML ~100 行 + Kotlin ~50 行 | Composable 函数 ~40 行 |
| **状态驱动** | 命令式：adapter.notifyDataSetChanged() | 声明式：StateFlow.collectAsState() 自动驱动 |
| **多类型列表** | getItemViewType() + 多个 ViewHolder + DiffUtil | when(type) 直接分发 Composable |
| **动画** | XML Animator / Transition 框架 | animateXxxAsState / AnimatedVisibility 等 |
| **ExoPlayer 集成** | PlayerView 直接嵌入 | AndroidView 包装 PlayerView |
| **学习曲线** | 训练营课程覆盖 | 需自学，但更契合现代 Android 生态 |
| **Google 态度** | 维护模式 | 主推方向 |

**决策**：采用 **Jetpack Compose**。MVVM + StateFlow + Compose 形成完美的声明式数据驱动闭环，UI 自动响应状态变化。信息流多类型卡片通过 `LazyColumn` + `when(type)` 分发更简洁，动画 API 更直观。ExoPlayer 通过 `AndroidView` 桥接。此决策同步更新于 2026-06-03。

### 12.5 AI API 调用：客户端直连 vs 服务端代理

| 方案 | 架构 | 优点 | 缺点 |
|------|------|------|------|
| **客户端直连 (本项目选择)** | 客户端 → AI API | 实现简单，无额外服务 | API Key 暴露风险（可通过混淆缓解） |
| **服务端代理** | 客户端 → 自建服务 → AI API | API Key 安全，可做请求控制 | 需额外搭建服务，增加复杂度 |

**决策**：本项目为训练营作品，功能演示优先，客户端直连方案满足需求且实现成本低。AI API Key 通过 `local.properties` 注入（不提交 Git）。

---

## 附录

### A. 依赖版本清单（Version Catalog）

```toml
[versions]
kotlin = "2.0.0"
agp = "8.5.0"
coroutines = "1.8.1"
okhttp = "4.12.0"
retrofit = "2.11.0"
coil = "3.0.0"
media3 = "1.3.1"
room = "2.6.1"
navigation = "2.7.7"
koin = "3.5.6"
kotlinx-serialization = "1.7.0"
datastore = "1.1.1"

[libraries]
# ... 具体依赖声明
```

### B. 关键类清单

| 模块 | 关键类 | 职责 |
|------|--------|------|
| feed | `FeedScreen()`, `FeedViewModel`, `LargeImageCard()`, `SmallImageCard()`, `VideoCard()` | 信息流展示与控制 |
| detail | `DetailScreen()`, `DetailViewModel` | 详情页展示与状态同步 |
| data | `AdRepository`, `MockDataSource`, `RemoteDataSource` | 数据获取与缓存管理 |
| ai | `AiApiService`, `AiCacheManager`, `AiContentGenerator` | AI 摘要/标签生成与缓存 |
| search | `SearchScreen()`, `SearchViewModel`, `AdMatchingEngine` | 对话式搜索 |
| player | `PlayerPool`, `PlaybackController` | 播放器实例池与播放控制 |
| analytics | `ExposureTracker`, `AnalyticsViewModel` | 曝光/点击/行为统计 |
| behavior | `BehaviorCollector`, `UserProfileEngine`, `RecommendRanker` | 行为采集/画像/推荐排序 |

---

> **文档维护说明**：本文档随项目开发持续更新。重大架构调整和技术决策变更需同步更新本文档对应章节。
