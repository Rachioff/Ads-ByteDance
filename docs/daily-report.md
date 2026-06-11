# 开发日报

> **项目**: AI广告推荐信息流 (Ads-ByteDance)
> **作者**: zsy

---

## Day 1：工程基础设施搭建

### 完成内容

#### 1. Gradle 构建体系配置

- 搭建 **Version Catalog**（`gradle/libs.versions.toml`），统一管理 35 个库 + 6 个插件的版本号
- 配置顶层 `build.gradle.kts`：声明 Android、Kotlin、Compose、Kotlin Serialization、Parcelize、KSP 六大插件
- 配置 `app/build.gradle.kts`：
  - 引入 Compose BOM、AndroidX 核心、Navigation Compose、OkHttp + Retrofit、Coil 3.x、Media3 ExoPlayer、Room + DataStore、Koin、Kotlin Coroutines、Kotlinx Serialization 共 11 组依赖
  - 设置 `minSdk = 26`、`compileSdk = 36`、`targetSdk = 36`
  - 通过 `buildConfigField` 实现 **DATA_MODE 开关**（mock / remote 切换）+ **AI API Key** 从 `local.properties` 注入（不提交 Git）
  - 启用 Compose + BuildConfig 构建特性
- 配置 `gradle.properties`：设置 `DATA_MODE=mock`

#### 2. 模块目录骨架搭建

按 `tech.md` 设计创建 8 个功能模块的完整包结构：
- `feed/` — 信息流模块（ui / viewmodel / model）
- `detail/` — 详情页模块（ui / viewmodel / model）
- `data/` — 数据层模块（model / repository / local / remote）
- `ai/` — AI 模块（api / model / cache）
- `search/` — 对话式搜索模块（ui / viewmodel / engine）
- `player/` — 视频播放器模块（controller / pool / ui）
- `analytics/` — 埋点统计模块（tracker / model / ui）
- `behavior/` — 用户行为模块（tracker / profile / recommend / model / storage）
- `common/` — 公共模块（network / imageloader / widget / util）
- `di/` — 依赖注入模块

#### 3. Koin 依赖注入容器配置

- 创建 `AdsApplication`：在 `onCreate()` 中初始化 Koin，注册 `appModule` + `viewModelModule`
- 创建 `AppModule`：声明全局单例（OkHttpClient、Json 解析器、Retrofit、Coil ImageLoader），按依赖顺序组织，后续模块的注入点已预留注释桩
- 创建 `ViewModelModule`：声明 ViewModel 的注入模板（FeedViewModel、DetailViewModel 等），当前为注释桩，待对应模块实现时激活
- 在 `AndroidManifest.xml` 中注册 `AdsApplication`

#### 4. 网络层与图片加载配置

- `NetworkConfig`（`common/network/`）：
  - OkHttpClient 工厂：连接/读取/写入超时 15s、Debug 模式下自动添加日志拦截器
  - Retrofit 工厂：绑定 Kotlinx Serialization 转换器
  - JSON 解析器：`ignoreUnknownKeys = true`（前向兼容）、`coerceInputValues = true`（缺省值处理）
  - `getBaseUrl()` 根据 `DATA_MODE` 自动切换 baseUrl
- `ImageLoaderConfig`（`common/imageloader/`）：
  - 一级内存缓存：LRU，上限 1/8 可用内存
  - 二级磁盘缓存：DiskLRU，上限 200MB
  - 复用全局 OkHttpClient 进行网络图片加载

### 遇到的问题与解决

1. **KSP 插件版本不存在**：使用旧格式版本号 `2.2.10-1.0.29`，实际 KSP 2.x 已改用独立版本号格式 `X.Y.Z`。修正为 `2.3.6`。
2. **kotlin-android 与 kotlin-compose 插件冲突**：`kotlin-compose` 内部已包含 `kotlin-android`，同时声明两者导致 DSL 扩展重复注册。移除显式的 `kotlin-android` 声明。
3. **kotlinOptions 访问器不可用**：移除 `kotlin-android` 插件后，该插件生成的 `kotlinOptions` DSL 访问器消失。改用新版 Kotlin Gradle API 的顶层 `kotlin { compilerOptions { } }` 块配置 JVM 目标版本。

详细记录见 `docs/troubleshooting.md`。

### 学到的内容

- KSP 版本号格式的历史演变（从 `<Kotlin版本>-<KSP子版本>` 到独立版本号）
- Kotlin Gradle Plugin 的 DSL 访问器生成机制：只有 `plugins {}` 中显式声明的插件才会生成类型安全访问器，传递应用不会生成
- `kotlin-compose` 插件的内部依赖关系（内置 kotlin-android）
- Koin 的模块化 DI 配置方式：`single<T>` 声明全局单例，`viewModel<T>` 声明 ViewModel，按依赖顺序组织模块声明
- Coil 3.x 的 API 变化：`ImageLoader.Builder` 配置方式，`maxSizePercent` 改为上下文感知的 API
- 通过 `buildConfigField` + `local.properties` 实现敏感信息的安全注入方案

---

## Day 2：数据层完整实现

### 完成内容

#### 1. 数据模型实现（9 个模型文件）

- `data/model/AdItem.kt` — AdItem 密封类 + 3 子类（LargeImageAd / SmallImageAd / VideoAd），`@SerialName` 短类型名，`@Transient` 标记运行时互动状态
- `data/model/Tag.kt` — Tag + TagCategory（4 类别枚举，@SerialName 序列化）
- `data/model/Channel.kt` — AdType / Channel / ImagePosition 枚举
- `data/model/PaginationState.kt` — PaginationState + LoadState（5 种加载态）
- `data/model/SearchCriteria.kt` — 结构化搜索条件 + `allTargetTags()` 收集方法
- `data/model/AdPageResponse.kt` — 分页响应包装（page / totalPages / pageSize / items）
- `ai/model/AiModels.kt` — AiRequest / AiResponse / AiGeneratedContent / AiIntentResult（模型默认 deepseek-v4-flash）
- `behavior/model/BehaviorModels.kt` — UserBehavior / BehaviorType（6 种行为 + 权重）/ UserProfile

#### 2. Mock JSON 数据（3 频道 × 10 条/页 × 2 页）

- `assets/mock/ads_featured.json` — 精选频道：大图 3 + 小图 4 + 视频 3 = 10 条，标签覆盖 4 品类 × 4 风格 × 4 受众 × 4 场景
- `assets/mock/ads_ecommerce.json` — 电商频道：大图 3 + 小图 4 + 视频 3 = 10 条，强调商品/价格属性
- `assets/mock/ads_local.json` — 本地频道：大图 3 + 小图 3 + 视频 4 = 10 条，强调地理/体验属性
- JSON 结构统一：`{page, totalPages, pageSize, items[{type, ...}]}`
- 图片使用 picsum.photos 占位 URL，视频使用 Google gtv-videos-bucket 示例 URL
- 所有 adItem 的 `aiSummary` 初始为 null，待 Day 6 AI 模块填充

#### 3. 数据源实现（接口 + Mock + Remote 骨架）

- `data/local/AdDataSource.kt` — 统一接口：getAds / getAdsByTag / getAdById
- `data/local/MockJsonDataSource.kt` — Mock 实现：
  - 从 assets/mock/ 读取 JSON 文件，Kotlinx Serialization 解析
  - 频道级内存缓存（`channelCache`），避免重复读文件
  - `paginate()` 内存切片模拟分页
  - `getAdsByTag()` 内存过滤
  - 所有 I/O 操作在 `Dispatchers.IO` 执行
- `data/remote/AdApiService.kt` — Retrofit API 接口定义（3 个端点）
- `data/remote/RemoteDataSource.kt` — 远程数据源实现骨架

#### 4. AdRepository 数据仓库

- 按 `BuildConfig.DATA_MODE` 自动切换 Mock / Remote 数据源
- 内存缓存：分频道维护 `channelItems`（Map<Channel, List<AdItem>>）
- 分页管理：`loadFirstPage()` / `refresh()` / `loadMore()` / `filterByTag()` / `clearFilter()`
- 状态追踪：`channelPagination`（当前页 / hasMore / LoadState）
- 跨页面状态同步：`updateInteraction()` 更新所有缓存中的点赞/收藏引用
- 全局清理：`clearAll()`

#### 5. Room 数据库

- `AppDatabase.kt` — 3 表（ads / user_behaviors / ai_cache），version=1
- Entity：
  - `AdEntity` — 展平 AdItem 密封类，差异字段可空（coverImageUrl / thumbnailUrl / videoUrl 等）
  - `BehaviorEntity` — 行为记录（id / adId / behaviorType / tagsJson / timestamp）
  - `AiCacheEntity` — AI 缓存（adId / summary / tagsJson / createdAt / expiresAt 7天）
- DAO：
  - `AdDao` — insertAll / getByChannel（Flow）/ getById / deleteByChannel / deleteAll
  - `BehaviorDao` — insert / getAll（Flow）/ getAllOnce / getByAdId / deleteAll
  - `AiCacheDao` — insert / getValidCache / deleteExpired / deleteAll
- Koin 注册：AppDatabase + 3 DAO 全部单例注入

### 遇到的问题与解决

无新增构建/编译问题。顺利推进。

### 学到的内容

- Kotlinx Serialization 密封类多态序列化：通过 `@SerialName` 设置短类型名，`classDiscriminator = "type"` 配置 JSON 判别键
- Room 中密封类展平为单表的做法：差异字段设计为可空列，根据 adType 决定取值
- Mock 数据内存分页：全量加载 → 内存切片 `subList()` → 动态计算 totalPages
- Repository 模式 + Koin DI 配合：`AdDataSource` 接口让 Mock 与 Remote 对仓库透明，通过 `if (BuildConfig.DATA_MODE)` 在 DI 层切换
- 跨页面状态同步的简单方案：Repository 持有所有 AdItem 引用，`updateInteraction()` 直接修改内存中的可变字段（`isLiked`/`isCollected` 为 `@Transient var`）

---

## Day 3：信息流 UI 层完整实现

### 完成内容

#### 1. 主题系统升级（字节风格）

- 设计完整的色彩体系：
  - 主色调：科技蓝 `#1A6FF5`（`Blue600`）替代默认紫色
  - 中性色：`Gray50`–`Gray900` 10 级灰度（浅色模式）
  - 深色模式：`DarkBg`/`DarkSurface`/`DarkCard` 深色色板
  - 语义色：`LikeRed`（点赞）、`CollectAmber`（收藏）、`ErrorRed`/`SuccessGreen`/`WarningAmber`
  - 渐变遮罩：`GradientOverlay` 用于图片叠加文字
- 排版层级：6 级 Typography（`headlineLarge` 20sp Bold → `labelSmall` 11sp Medium）
- Theme Composable：Light/Dark ColorScheme 自动切换，关闭 dynamicColor 保持品牌一致性

#### 2. 信息流架构实现

- `FeedUiState` — UI 状态数据类（ads / loadState / activeFilterTag / currentChannel）
- `FeedEvent` — 用户事件密封类（11 种事件：加载/刷新/加载更多/互动/导航/过滤）
- `FeedViewModel` — StateFlow 驱动的 ViewModel：
  - 每个频道独立实例（Koin parameter 注入 `Channel`）
  - 所有 Repository 调用在 `Dispatchers.IO` 执行
  - 分页加载：`loadFirstPage()` / `refresh()` / `loadMore()`
  - 互动状态：`toggleLike()` / `toggleCollect()` 同步更新 Repository 缓存
  - 标签过滤：`filterByTag()` / `clearFilter()`
  - 一次性事件：`SharedFlow<FeedOneTimeEvent>` 处理 Toast / 导航 / 分享

#### 3. 3 种广告卡片 Composable

- **LargeImageCard**：大图封面（3:2）+ 底部渐变遮罩 + 叠加文字（头像+名称+标题）+ 标签行 + 互动栏
- **SmallImageCard**：水平布局（左文右图），110dp 缩略图 + 标题/描述/标签 + 互动栏
- **VideoCard**：16:9 封面 + 居中播放按钮 + "视频"角标 + 渐变遮罩 + 互动栏
- **CardInteractions**：共享互动栏，点赞（爱心）+ 收藏（书签）+ 分享按钮，带弹簧缩放动画
- **TagChips**：FlowRow 标签行，蓝色浅底 Chip，点击触发过滤

#### 4. 状态视图与 FeedScreen

- **FeedScreen** 主 Composable：
  - `PullToRefreshBox` 下拉刷新
  - `LazyColumn(key = { it.id })` 高效列表渲染
  - `derivedStateOf` 监听滚动位置自动触发 loadMore（最后 3 个 item）
  - `AnimatedVisibility` 过滤标签栏
  - `when(ad)` 分发 3 种卡片
  - 底部加载指示器 + "没有更多了" Footer
- 状态视图组件：
  - `LoadingShimmer`：Shimmer 骨架屏（渐变动画，4 卡片占位）
  - `EmptyState`：居中图标+提示文案
  - `ErrorState`：错误图标+文案+重试按钮
  - `FilterBar`：激活过滤标签显示 + 清除按钮
  - `EndFooter`："— 没有更多了 —"

#### 5. DI 集成与 MainActivity

- ViewModelModule 激活：`viewModel { (channel: Channel) -> FeedViewModel(get(), channel) }`
- FeedScreen 通过 `koinViewModel(key = channel.name, parameters = { parametersOf(channel) })` 获取
- MainActivity：替换模板 Greeting → `FeedScreen(channel = Channel.FEATURED)`
- 新增依赖：`koin-androidx-compose`、`coil-compose`（AsyncImage）、`material-icons-extended`

### 遇到的问题与解决

1. **Room 2.6.1 + KSP 2.3.6 不兼容（`unexpected jvm signature V`）**：原项目在 Kotlin 2.2.10 环境下 Room 2.6.1 的 KSP processor 无法处理新版编译器生成的 JVM 签名。升级 Room 至 2.7.0 解决。
2. **KDoc 注释中的 `/*.json` 导致编译错误**：Kotlin 2.2.10 解析器将 KDoc 内的 `/*` 误识别为块注释开头。改为 `JSON 文件顶层格式` 解决。
3. **Coil AsyncImage 不可用**：Coil 3.x 的 Compose 集成在独立模块 `coil-compose`，需单独添加依赖。
4. **`viewModel` DSL 需显式 import**：`org.koin.androidx.viewmodel.dsl.viewModel` 是 Koin 3.5.x 在 module 中使用 viewModel DSL 的必要导入。
5. **`@Transient` 警告**：抽象属性无 backing field，`@Transient` 在抽象层无效。移除基类中的 `@Transient`，仅保留子类的注解。

### 学到的内容

- Coil 3.x 模块化拆分：`coil`（核心）、`coil-compose`（Compose 集成）、`coil-network-okhttp`（网络加载）各自独立
- Kotlin 2.2.10 的 KDoc 解析器对 `/*` 更严格（可能是 bug 或有意行为）
- Room 版本与 KSP/Kotlin 编译器的兼容性需要精确匹配
- Compose `PullToRefreshBox` + `derivedStateOf` 组合实现流畅的分页加载体验
- Koin `viewModel { (params) -> }` parameter 机制实现同类型 ViewModel 的多实例注入（按频道区分）
- StateFlow 驱动的 UI：`FeedUiState` 作为唯一的真实状态源，Compose 自动重组响应变化

#### 6. 点赞/收藏状态同步的 Debug 与修复

- **问题**：点赞/收藏点击后不立即变色，需滑出再滑回
- **排查**：
  - 第一轮：将 `isLiked`/`isCollected` 从 data class body 移到 constructor（无效）
  - 第二轮：`MutableStateFlow` → `mutableStateOf` 直接驱动（无效）
  - 第三轮：定位到 **LazyColumn.items(key) 在 key 不变时跳过 content lambda 重执行**
- **最终方案**：`mutableStateMapOf<String, Boolean>` 按 adId 独立追踪状态，在 items content lambda **内部**读取 map[adId]，建立精确到单个 item 的 snapshot 依赖
- **连带修复**：`InteractionButton`/`TagChip` 移除 `indication = null`，恢复 Material ripple；扩大触摸目标 padding

#### 7. 首页 Tab 架构实现

- **HomeScreen**：`Scaffold` + `TopAppBar` + `PrimaryTabRow` + `HorizontalPager`
- 三个频道 Tab：精选（FEATURED）/ 电商（ECOMMERCE）/ 本地（LOCAL）
- 每个频道持有独立 `FeedViewModel` 实例（Koin `key` 区分）
- `MainActivity` 简化为 `HomeScreen()` 入口
- 废弃 API 警告修复：`TabRow` → `PrimaryTabRow`（Material3 推荐）

### 学到的内容（补充）

- `mutableStateMapOf` 的 key 级粒度是精确重组 LazyColumn 单个 item 的最佳方案
- Compose snapshot 系统要求状态在**同一个 Composable scope 内读取**才能建立依赖——跨 scope 读取无效
- `PrimaryTabRow` 替代 `TabRow` 是 Material3 的 API 演进方向
- `HorizontalPager` 配合 `TabRow` 实现流畅的多频道滑动切换

---

## Day 4：视频播放器 + 频道切换增强

### 完成内容

#### 1. PlayerPool — ExoPlayer 实例池（player/pool/PlayerPool.kt）

- 池大小上限 = 3，覆盖"当前播放 + 前驱 + 后继"场景
- `acquire(context)`: 优先从 `ConcurrentLinkedQueue` 取出已有实例 → 池空则创建新 ExoPlayer
  - 返回前自动暂停之前的活跃播放器（确保同时只有 1 个播放）
  - 默认 `volume=0f`（外流静音）、`repeatMode=OFF`
- `release(player)`: `stop()` → `clearMediaItems()` → `clearVideoSurface()` → 放入空闲队列
  - 池满时直接 `release()` 销毁（防止 OOM）
- `releaseAll()`: 页面销毁时释放池中所有播放器资源
- 线程安全：`ConcurrentLinkedQueue` + `@Volatile activePlayer`

#### 2. VideoPlayer Composable — Compose ↔ ExoPlayer 桥接（player/ui/VideoPlayer.kt）

- 使用 `AndroidView` 将 ExoPlayer 的 `PlayerView`（传统 View）嵌入 Compose 组合树
- `factory`: 创建 `PlayerView` 并绑定 `player` 实例
- `update`: 当 player 实例切换时重新绑定
- `DisposableEffect`: Composable 离开组合树时解绑（防止 Surface 销毁后 ExoPlayer 崩溃）

#### 3. VideoCard 播放控制集成（feed/ui/card/VideoCard.kt 重写）

- **状态机**: IDLE（封面+播放按钮） ↔ PLAYING（PlayerView + 控制层）
- `Crossfade` 动画切换：封面态时 PlayerView 不存在（节省 Surface 内存），播放态时封面 AsyncImage 被回收
- **播放控制**:
  - 点击播放按钮 → `PlayerPool.acquire()` → `setMediaItem` → `prepare` → `play`
  - 点击视频区域 → 暂停/继续（`playWhenReady` 切换）
  - 静音按钮（右上角）→ `player.volume` 0f / 1f 切换
  - 简易进度条（底部 `LinearProgressIndicator`，每 200ms 从 `player.currentPosition` 同步）
- **生命周期**:
  - `DisposableEffect`: 离开屏幕时归还播放器到 `PlayerPool`
  - `Player.Listener.onPlaybackStateChanged`: 播放到 `STATE_ENDED` 时自动归还
- **PlayerPool 注入**: 通过 `GlobalContext.get().get<PlayerPool>()` 从 Koin 获取

#### 4. HomeScreen Tab 回到顶部

- 点击已选中的 Tab → 递增该频道的 `scrollToTopTrigger` 计数器
- FeedScreen 通过 `LaunchedEffect(scrollToTopTrigger)` 监听 → `listState.animateScrollToItem(0)`
- 滑动切换 Tab（`HorizontalPager`）不触发回到顶部

#### 5. 快速滑动检测

- FeedScreen 通过 `snapshotFlow { listState.isScrollInProgress }.distinctUntilChanged()` 监听滚动状态
- 通过 `onScrollStateChanged` 回调暴露给 HomeScreen
- Day 4 为 click-to-play 模式，此检测为后续 auto-play 优化预留

#### 6. Koin DI 注册

- 在 AppModule 中激活 `single<PlayerPool> { PlayerPool() }` 全局单例声明

### 文档更新

- `schedule.md` → v1.2 Compose 适配版（全文从 XML View 术语重写为 Compose 术语）
- `req.md` → v1.1（模块划分 / 术语更新为 Compose）
- `struct.md` → 新增 player/pool/PlayerPool.kt、player/ui/VideoPlayer.kt

### 遇到的问题与解决

无新增编译问题。代码一次性编译通过，仅有 2 个 Material Icons deprecation 警告（`VolumeOff`/`VolumeUp` 迁移至 `AutoMirrored`），已修复。

### 学到的内容

- ExoPlayer 实例成本高（每个实例持有独立解码线程 + 缓冲区 + Surface），池化复用是必要的
- Compose 与 View 系统的桥接：`AndroidView` 在 Compose 布局中预留空间，传统 View 的 Surface 渲染在此空间内独立进行
- Crossfade 比 AnimatedVisibility 更适合"替换型"UI（确保非活跃子组件不在组合树中，释放资源）
- `snapshotFlow` 是 Compose state → Kotlin Flow 的桥梁，适用于需要 `distinctUntilChanged` 等 Flow 操作符的场景
- Kotlin 中 lambda 赋值给 `val` 时没有隐式 `return@label`，需要用 `?.let {}` 替代 `?: return@xxx`

#### 8. 视频播放黑屏问题的诊断与修复

- **问题**：点击播放按钮后，Crossfade 切换到播放态，视频区域显示黑屏（无画面，但无报错）
- **排查**：
  - 第一轮：怀疑 SurfaceView 打洞遮挡 Compose 覆盖层 → 确认已使用 TextureView（`app:surface_type="texture_view"`），排除
  - 第二轮：怀疑 `MediaItem.fromUri()` 已弃用导致 API 行为变化 → 切换到 `MediaItem.Builder().setUri()` 后问题依旧，排除
  - 第三轮：定位到根本原因——**`prepare()` 调用的时序问题**
- **根因**：`startPlayback()` 中同步调用 `player.setMediaItem()` + `player.prepare()` + `player.playWhenReady = true`，此时 PlayerView 尚未创建（Crossfade 尚未切换），TextureView Surface 不存在。`prepare()` 在没有 Surface 的情况下初始化视频渲染管线 → 视频轨道未启用 → 即使后续 PlayerView 创建了 Surface，播放器也可能不会重新初始化视频渲染 → **黑屏**
- **时序图**：
  ```
  ❌ 旧：acquire → setMediaItem → prepare → exoPlayer=player → Crossfade → VideoPlayer+Surface（已错过prepare窗口）
  ✅ 新：acquire → exoPlayer=player → Crossfade → VideoPlayer → PlayerView布局 → TextureView Surface就绪 → view.post{ setMediaItem+prepare }
  ```
- **最终方案**：
  - `VideoPlayer.kt`：新增 `videoUrl` 和 `isMuted` 参数，在 `AndroidView.factory` 中通过 `View.post {}` 延迟初始化播放（确保布局完成、Surface 就绪后再 `prepare`）
  - `VideoPlayer.kt`：提取 `setupMedia()` 私有函数，factory 和 update 统一复用；`lastSetupPlayer` 追踪避免重复 setup（适配 retry 时 player 实例切换）
  - `VideoCard.kt`：`startPlayback()` 移除 `setMediaItem/prepare/playWhenReady`，仅 acquire → 设 `playbackState=BUFFERING`（让 UI 在等待 Surface 就绪期间显示 loading 指示器）
  - `MediaItem.Builder().setUri()` 替代已弃用的 `MediaItem.fromUri()`

### 学到的内容（补充）

- ExoPlayer 的 `prepare()` 时机对视频渲染至关重要：必须在 Surface 可用之后调用，否则视频渲染器可能永远不会被正确初始化
- `View.post {}` 的执行时机：在当前帧的 measure/layout/draw 全部完成后执行，此时 TextureView 的 `onSurfaceTextureAvailable` 已回调完毕、Surface 已传递到 ExoPlayer
- `AndroidView.update` 在 player 实例切换时触发（如 retry），需要在此处同样处理 media setup
- `MediaItem.Builder().setUri()` 是 Media3 1.4+ 的推荐 API，替代 `MediaItem.fromUri()`

#### 9. 重试按钮点击无效的 Debug 与修复

- **问题**：播放失败后显示"加载失败 → 点击重试"，点击重试无任何反应
- **排查**：追踪 `retry()` 执行链路：
  ```
  retry() → release(player)  // 放回池
         → exoPlayer = null
         → startPlayback()
              → acquire()      // 从池中取出**同一个**实例
              → exoPlayer = player (同一实例!)
  ```
- **根因**：同一帧内 `exoPlayer` 从 `PlayerA → null → PlayerA`（同一实例），Compose snapshot 只看到最终值没变 → **不触发重组** → Crossfade 不切换 → VideoPlayer 不重建 → `lastSetupPlayer` 仍匹配 → `setupMedia()` 永远不被调用
- **最终方案**：
  - retry 时**不归还池**（`player.stop()` + `player.clearMediaItems()` 保留 Surface 绑定），避免 same-instance 陷阱
  - 新增 `retryTrigger: Int` 计数器，每次重试递增
  - `VideoPlayer` 通过 `LaunchedEffect(retryTrigger)` 观察 → 重置 `lastSetupPlayer = null` → 通过 `view.post {}` 重新 `setupMedia()`
  - 保存 `playerView` 引用到 mutableState，供 `LaunchedEffect` 调用 `post {}`
- **时序**（retry）：
  ```
  点击重试 → stop + clearMediaItems → retryTrigger++
          → LaunchedEffect(retryTrigger) 发射
              → lastSetupPlayer = null
              → view.post { setupMedia() }
                  → setMediaItem → prepare → play
  ```

### 学到的内容（补充）

- Compose `MutableState` 在同一帧内的多次写入，最终值不变时 Compose 跳过重组——这是优化但也是陷阱
- ExoPlayer 的 `stop()` + `clearMediaItems()` 不会清除 Surface 绑定（只有 `clearVideoSurface()` 才会），retry 时保持 Surface 可以避免重建 PlayerView 的开销
- 当 player 实例不变但需要重新初始化时，需要一个"信号"状态（如 `retryTrigger`）来驱动副作用，不能依赖实例引用变化
- `LaunchedEffect(key)` 的 key 递增时会取消旧的协程并启动新的，天然适合"每次递增执行一次"的语义

#### 10. 重试按钮点击无效的真正根因 — Z 轴点击拦截

- **问题**：即使修复了 retry 逻辑（问题 9），重试按钮仍然无反应
- **排查**：检查播放器态 Box 的 Z 轴层级：
  ```
  父 Box(fillMaxSize) 内子元素渲染顺序（后渲染 = 高层）:
    Layer 0: VideoPlayer (AndroidView)
    Layer 1: 缓冲 loading  (isBuffering)
    Layer 1a: 暂停封面    (!playWhenReady)
    Layer 2: 错误态        (hasError)  ← "点击重试" 按钮在这里
    Layer 3: 播放/暂停按钮  (!playWhenReady && !hasError)
    Layer 3: 全屏透明 Box   (无守卫!!) ← BUG! 永远渲染，吞噬所有点击
    Layer 3: 静音按钮      (!hasError)
    Layer 3: 进度条         (playWhenReady && !hasError)
  ```
- **根因**：全屏透明 Box 的 `Modifier.fillMaxSize().clickable{}` **没有任何条件守卫**，始终渲染在错误 UI 之上。用户点击"重试"时，点击事件被此 Box 的 `clickable` 修饰符消费——即使 `onClick` 里做了 `if (!hasError) togglePlayPause()`（hasError 时不做任何事），**`clickable` 仍然消费了事件**，下层错误 UI 永远收不到。
- **最终方案**：
  - 将全屏透明 click Box + 静音按钮包裹在 `if (!hasError)` 条件内，错误时完全不渲染这些覆盖层
  - 新增 `errorMessage` 状态变量，在 `onPlayerError` 中捕获 `PlaybackException.localizedMessage`，展示在错误 UI 中（半透明白色小字），便于诊断根因
  - `retry()` 中同时清除 `errorMessage`

### 学到的内容（补充）

- Compose Box 的子元素按声明顺序从下到上叠放，后声明的元素会拦截先声明元素的点击事件
- `clickable { }` 即使 onClick lambda 为空或不做任何事，仍然会消费点击事件——它不会"透传"到下层
- 当需要在 Box 中使用全屏可点击覆盖层时，必须先考虑它对其他交互元素的影响，并在不合适时完全移除（而非依赖 lambda 内的条件判断）
- Z 轴层级调试技巧：检查 Box 子元素的声明顺序，越靠后越在上层

#### 11. 视频 URL 全部失效 — GCS Bucket 403 Access Denied

- **问题**：所有 mock 数据中的视频 URL（`commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBigger*.mp4`）返回 403 AccessDenied，Google Cloud Storage 不再允许匿名访问该 bucket
- **排查**：浏览器直接打开 URL → `<Error><Code>AccessDenied</Code></Error>`
- **根因**：GCS bucket 权限变更，匿名读取 (`storage.objects.get`) 被拒绝（同日测试 `storage.googleapis.com` 同样 403）
- **最终方案**：全部 9 个视频 URL → `https://www.w3schools.com/html/mov_bbb.mp4`（788KB Big Buck Bunny，已验证 200 OK）
- **影响文件**：`assets/mock/ads_featured.json`、`ads_ecommerce.json`、`ads_local.json`

#### 12. 进度条升级 — LinearProgressIndicator → 可拖动 Slider

- **需求**：外流（信息流中）也应有可拖动的进度条（非仅展示），用户可能想要 seek 到视频的特定位置
- **实现方案**：
  - 将 `LinearProgressIndicator`（只读）替换为 Material3 `Slider`（交互式）
  - 新增 `isDragging` 状态变量：
    - 拖动中：每 200ms 的进度同步循环跳过 `progress` 更新（避免与手指位置冲突）
    - 松手时：`onValueChangeFinished` → 计算目标位置 `(progress * duration).toLong()` → `player.seekTo()` 跳转
  - 视频**不暂停**（拖动时继续播放，仅视觉受手指控制，松手后立即 seek 到目标位置继续播放）
  - 可见条件：`(playWhenReady || isDragging) && !hasError`
- **交互流程**：
  ```
  手指触摸 Slider → onValueChange: progress=newValue, isDragging=true
      ↓（手指滑动）
  进度同步循环检测 !isDragging → 跳过自动更新，仅手指位置控制 progress
      ↓（手指抬起）
  onValueChangeFinished: isDragging=false → seekTo(progress*duration)
      ↓
  播放器跳转 + 继续播放，进度同步循环恢复更新
  ```
- **改动文件**：`feed/ui/card/VideoCard.kt`（4 处修改：import / 新增 isDragging / 同步循环守卫 / Slider 替换）

---

## Day 5：详情页模块 + 导航 + 跨页状态同步 + 分享

### 完成内容

#### 1. 详情页状态模型（detail/model/DetailUiState.kt）

- `DetailUiState(ad, loadState, errorMessage)` — 详情页 UI 状态数据类
- `DetailEvent` — 用户事件密封类（ToggleLike / ToggleCollect / Share / Back / Retry）
- `DetailOneTimeEvent` — 一次性事件密封类（ShowToast / ShowShareSheet / NavigateBack），通过 `MutableSharedFlow` 发送确保只消费一次

#### 2. DetailViewModel（detail/viewmodel/DetailViewModel.kt）

- **构造参数**：`adId: String`（Koin parameter 注入）+ `repository: AdRepository`
- **数据加载**：`init` 中通过 `repository.getAdById(adId)` 异步加载广告数据（IO 线程）
- **互动状态管理**：`likedAdIds` / `collectedAdIds`: `mutableStateMapOf<String, Boolean>`（与 FeedViewModel 模式一致）
- **跨页面状态同步**：`toggleLike()` / `toggleCollect()` 调用 `repository.updateInteraction()` 更新内存缓存中的 `isLiked`/`isCollected` 字段
- **分享**：构建分享文案（标题 + 描述 + 广告主）→ 发送 `ShowShareSheet` 一次性事件

#### 3. DetailScreen 主 Composable（detail/ui/DetailScreen.kt）

**整体布局**（Scaffold 三段式）：
```
Scaffold
├── TopAppBar: 返回按钮 + 广告主名称
├── Content (可滚动 Column)
│   ├── 媒体区（ImageDetailMedia / VideoDetailMedia）
│   ├── 广告标题（headlineLarge + Bold）
│   ├── 完整描述（不限行数，全面展示）
│   ├── 广告主信息卡片（44dp 头像 + 名称 + "广告主"蓝色徽章 + 频道名）
│   ├── AI 摘要卡片（aiSummary 非空时，渐变背景 + AutoAwesome 图标）
│   └── 智能标签行（FlowRow，复用 TagChips）
└── BottomBar: DetailInteractions 增强互动栏
```

**媒体区分发**：
- `ImageDetailMedia`（图文）：`AsyncImage` 3:2 比例 + 底部渐变遮罩
- `VideoDetailMedia`（视频）：
  - 进入时 `PlayerPool.acquire()` 获取播放器，默认有声（`volume=1f`）
  - `useController = true`：完整的 ExoPlayer 控制条
  - 离开时 `DisposableEffect.onDispose` → `playerPool.release()` 归还池
  - 独立的静音按钮 + 缓冲 loading + 错误态 + 重试

**状态分发**：LOADING → `LoadingShimmer` / ERROR → `ErrorState` / IDLE → 完整内容

#### 4. 增强互动按钮（detail/ui/DetailInteractions.kt）

比卡片版 `CardInteractions` 更突出（28dp 图标、完整计数、粒子动效）：

- **点赞爱心扩散**（`HeartBurstParticles`）：
  - 触发条件：`isLiked` 从 false → true 时
  - 6 个小爱心从中心向四周扩散（cos/sin 角度均匀分布，20-60dp 随机距离）
  - 4 个 `Animatable` 并行驱动：offsetX / offsetY / scale（0.6→1.4）/ alpha（1.0→0.0）

- **收藏弹簧缩放**：`animateFloatAsState(spring(dampingRatio=0.3f))`，比 CardInteractions 更明显弹性

- **分享按钮**：`Icons.Filled.Share` 图标 + 完整计数（不缩写）

#### 5. 导航架构改造（MainActivity.kt 重写）

- 引入 **Navigation Compose NavHost** 管理路由：
  ```
  NavHost(startDestination = "home")
    ├── composable("home")                  → HomeScreen(onNavigateToDetail)
    └── composable("detail/{adId}")         → DetailScreen(adId, onBack)
  ```
- `adId` 通过 URL path 参数传递（`navArgument("adId") { type = NavType.StringType }`）
- 返回栈管理：`popBackStack()` 返回，HomeScreen 的 `LazyListState` 天然保持滚动位置

**导航回调链**：
```
FeedScreen.onCardClick → FeedViewModel → SharedFlow → FeedScreen 收集
  → onNavigateToDetail(adId) → HomeScreen 回调 → navController.navigate("detail/$adId")
```

#### 6. 分享功能实现

两处均可触发系统分享（FeedScreen / DetailScreen），通过 `Intent.ACTION_SEND` + `createChooser`：
```
{广告标题}
{广告描述}
— 来自 {广告主名称}
```

#### 7. 组件复用与解耦

| 详情页使用 | 来源 |
|---|---|
| `RowWithAvatar` | `feed.ui.card` |
| `TagChips` | `feed.ui.card.CardComponents` |
| `LoadingShimmer` / `ErrorState` | `feed.ui.component.FeedStates` |
| `VideoPlayer` | `player.ui.VideoPlayer` |
| `PlayerPool` | `player.pool.PlayerPool` (Koin) |
| `AdRepository.getAdById()` / `updateInteraction()` | `data.repository.AdRepository` |

#### 8. DI 注册（ViewModelModule.kt）

- 激活 `DetailViewModel` 的 Koin 声明：`viewModel { (adId: String) -> DetailViewModel(repository = get(), adId = adId) }`
- Compose 端通过 `koinViewModel(key = "detail_$adId", parameters = { parametersOf(adId) })` 获取

### 遇到的问题与解决

1. **Bookmark 图标路径错误**：Material Icons 中 `Bookmark` 和 `BookmarkBorder` 位于 `Icons.Filled` / `Icons.Outlined`（非 `AutoMirrored`），修正 import 和引用。
2. **`launch` 无法解析**：`kotlinx.coroutines.launch` 是 `CoroutineScope` 的扩展函数，FQN 引用不合法。修正为 `import kotlinx.coroutines.launch` + 直接使用 `launch {}`。
3. **`aiSummary` smart cast 失败**：`ad.aiSummary` 是 sealed class 的抽象属性（有 open getter），Kotlin 编译器无法 smart cast。修正：先赋值到局部变量 `val aiSummary = ad.aiSummary`。
4. **未使用 import 清理**：移除 DetailScreen 中未使用的 `horizontalScroll`、`AdUnits`、`PlayArrow` 等 import。

### 学到的内容

- Navigation Compose 的 `navArgument` + `NavType` 提供类型安全的路由参数传递
- `NavBackStackEntry` 级别的 ViewModel 生命周期管理：HomeScreen 在 detail 弹出后未被销毁，状态完整保留
- `mutableStateMapOf` 的跨页面一致性：FeedScreen 和 DetailScreen 各自使用独立的 map 实例，但通过共同的 Repository 内存缓存实现状态同步——Repository 是唯一的"真相源"
- `Animatable` vs `animateFloatAsState`：需要并行多个动画时用 `Animatable` + `launch {}`，简单单属性用 `animateFloatAsState`
- Kotlin sealed class 的抽象属性无法被 smart cast（因为 getter 可被覆盖），必须赋值到局部 val
- Material Icons 分类：`Bookmark` 不是镜像图标，属于 `Filled`/`Outlined`

### 设计决策记录

| 决策项 | 内容 |
|--------|------|
| 导航框架 | 采用 Navigation Compose，利用 `NavBackStackEntry` 的 ViewModel 作用域 |
| 视频跨页面复用 | 简化方案：详情页独立 acquire/release（PlayerPool 确保复用） |
| 底部互动栏 | 独立组件 `DetailInteractions`（更大的图标 + 完整计数 + 爱心粒子动效） |
| 标签点击 | 详情页中标签仅展示不可点击过滤，避免与 feed 过滤状态歧义 |
| 图片轮播 | 数据模型仅单图，UI 预留 Box 结构，后续可扩展 `HorizontalPager` |

### 文档更新

- `struct.md`：更新 detail/ 目录结构为 4 个实际文件
- `docs/daily-report.md`：Day 5 日报

---

## Day 6：AI 增强 — 摘要/标签生成 + 三级缓存 + UI 展示

### 完成内容

#### 1. AI API 网络层（ai/api/AiApiService.kt + NetworkConfig 扩展）

- **AiApiService**：OpenAI 兼容的 Chat Completions Retrofit 接口
  - `POST v1/chat/completions` — 支持 OpenAI / Qwen / DeepSeek 等兼容 API
  - 请求/响应模型复用 Day 2 已定义的 `AiRequest` / `AiResponse`
- **NetworkConfig 扩展**：
  - `createAiRetrofit()` — 独立的 Retrofit 实例 + 60s 长超时 + `authInterceptor()` 自动注入 `Authorization: Bearer <key>`
  - `getAiBaseUrl()` / `getAiApiKey()` — 从 BuildConfig 读取（由 local.properties 注入）
- **AppModule** 注册：`aiRetrofit`（`named` qualifier）+ `AiApiService`

#### 2. Prompt 构造器（ai/api/AiPromptBuilder.kt）

- **System Prompt**：角色设定（广告分析助手）+ 输出约束（严格 JSON + summarize < 80 字 + 3-5 标签 + 4 类别枚举）
- **User Message 模板**：广告标题 / 描述 / 广告主 / 类型 → 格式化为 prompt
- **搜索意图解析 Prompt**（`buildSearchMessages`）：为 Day 7 对话式搜索预留

#### 3. JSON 响应解析 + 校验 + 降级（ai/api/AiResponseParser.kt）

- **多格式容错**：
  - 直接 JSON
  - markdown 代码块包裹（```json...```）
  - 前后有说明文字（提取首 `{` ～ 末 `}`）
- **字段校验**：
  - summary 缺失 → 尝试中文 key（"摘要"）→ 仍失败则解析失败
  - tags 缺失 / category 非法 → 默认 `CATEGORY`
  - tags 最多取 5 个
- **降级静态内容**：
  - `fallbackSummary(description)`：描述前 80 字
  - `fallbackTags(existingTags)`：复用 Mock 数据中的静态标签

#### 4. AI 三级缓存（ai/cache/AiCacheManager.kt）

- **Level 1 — 内存 LRU**：`LinkedHashMap(accessOrder=true, maxSize=50)`，线程安全（`synchronized`）
- **Level 2 — Room 磁盘缓存**：`AiCacheDao.getValidCache(adId)`（7 天过期），命中后回填内存
- **Level 3 — 网络 API**：由 `AiContentGenerator` 调用，成功后写入 L1 + L2
- **批量查询**：`getCachedBatch(adIds)` 先查内存再批量查 Room
- **维护方法**：`cleanExpired()`（启动时在 AdsApplication 调用）、`clearMemory()`、`clearAll()`

#### 5. AI 内容生成器（ai/api/AiContentGenerator.kt）

- **编排流程**：`generate(ad)` → 查缓存 → 调用 API → 解析 → 存储 → 返回（失败降级）
- **批量生成**：`generateBatch(ads, onProgress)` — 串行调用（避免 API 限流），单条失败不影响其他
- **强制刷新**：`refresh(ad)` — 忽略缓存，重新调用 API
- **线程安全**：所有 I/O 在 `Dispatchers.IO` 执行

#### 6. ViewModel + UI 集成

**FeedViewModel 变更**：
- 新增 `aiContentGenerator` 构造参数（Koin 注入）
- 新增 `aiContentMap = mutableStateMapOf<String, AiGeneratedContent>()`（Compose snapshot map，精确重组）
- `loadFirstPage()` / `refresh()` / `loadMore()` 成功后异步调用 `generateAiContent()`
- `generateAiContent()` 完成后写入 `aiContentMap` → 触发卡片局部重组

**DetailViewModel 变更**：
- 新增 `aiContentGenerator` 构造参数 + `aiContentMap`
- `loadAd()` 成功后异步调用 `generateAiContent()`

**3 种卡片 UI 增强**：
- 所有卡片新增 `aiContent: AiGeneratedContent?` 参数
- 新增 `AiSummaryLabel` Composable（蓝色渐变背景 + AutoAwesome 图标 + 摘要文字）
- 显示优先级：AI 生成摘要 > `ad.aiSummary` 静态降级 > 不显示

**详情页 AI 增强**：
- `AiSummaryCard` 新增 `tags` 参数：AI 生成的标签在摘要卡片内以 `TagChips` 展示
- `DetailContent` 接受 `aiContent` 参数传递

**DI 变更**：
- `ViewModelModule`：FeedViewModel / DetailViewModel 的 Koin 声明新增 `aiContentGenerator = get()` 参数
- `AppModule`：注册 `AiCacheManager` + `AiContentGenerator` 作为全局单例

#### 7. 启动时缓存清理

- `AdsApplication.onCreate()` 新增 `cleanExpiredAiCache()`：后台 IO 线程清理 Room 中过期的 AI 缓存

### 架构设计要点

#### AI 数据流

```
┌──────────────────────────────────────────────────┐
│  卡片 / 详情页 Composable                         │
│    读取 aiContentMap[adId] → 建立 snapshot 依赖    │
└──────────────────┬───────────────────────────────┘
                   │ 状态观察
┌──────────────────▼───────────────────────────────┐
│  FeedViewModel / DetailViewModel                 │
│    loadFirstPage → generateAiContent(ads)         │
│    完成后 → aiContentMap[adId] = AiGeneratedContent│
└──────────────────┬───────────────────────────────┘
                   │ 业务调度
┌──────────────────▼───────────────────────────────┐
│  AiContentGenerator (编排层)                      │
│    generate(ad) → generateBatch(ads)             │
│    ├─ cacheManager.getCached(adId)  ← 缓存命中    │
│    ├─ fetchFromApi(ad)             ← API 调用     │
│    └─ buildFallbackContent(ad)     ← 降级兜底    │
└──────────────────┬───────────────────────────────┘
                   │ 三级缓存
┌──────────────────▼───────────────────────────────┐
│  AiCacheManager                                  │
│    L1: LinkedHashMap(accessOrder, max 50)        │
│    L2: Room AiCacheDao (7天过期)                  │
│    L3: (由 AiContentGenerator 调用 API)           │
└──────────────────────────────────────────────────┘
```

#### 关键设计决策

| 决策项 | 内容 | 理由 |
|--------|------|------|
| AI 内容存储策略 | `mutableStateMapOf` 独立于 AdItem 存储 | AdItem.aiSummary 为 `val`（不可变），AI 内容通过独立的 Compose snapshot map 追踪，与 likedAdIds/collectedAdIds 模式一致，确保精确重组 |
| API 并发策略 | 串行调用（`generateBatch` 逐条） | 避免触发免费 API 的速率限制（如 Qwen 免费版 QPS 限制） |
| 降级策略 | 解析失败 → 描述前 80 字 + 静态标签 | 即使 AI API 完全不可用，UI 也不会空白 |
| AI Retrofit 超时 | 60s 读取超时（vs 广告 API 的 15s） | 大模型生成耗时较长；通过独立的 OkHttpClient 实例隔离 |
| 认证方式 | OkHttp Interceptor 注入 Bearer Token | 对 Retrofit 接口透明，API Key 仅在 `local.properties` 配置（不提交 Git） |

### 遇到的问题与解决

无新增编译问题。代码一次性编译通过（修复 3 个初始化错误后）。

### 学到的内容

- Koin 的 `named()` qualifier：同一类型（`Retrofit`）的多实例注入方案，通过 `get<Retrofit>(named("aiRetrofit"))` 精确选择
- `LinkedHashMap(accessOrder=true)` 实现简单 LRU 缓存：每次 `get`/`put` 将条目移到链表尾部，`removeEldestEntry` 在 put 后自动剔除最老条目
- Kotlin `const val` 的限制：multiline raw string 不能作为 `const val`（编译器要求编译期常量）
- AI 响应的鲁棒解析：大模型返回值不可靠（可能含 markdown 包装、中文 key、非法枚举值），解析器需要多层容错
- OkHttp `newBuilder()` 创建独立实例：基于全局 OkHttpClient 派生 AI 专用实例（共享连接池但独立超时），避免创建多个连接池

### 文档更新

- `struct.md`：新增 AI 模块 4 个文件路径
- `docs/daily-report.md`：Day 6 日报

---

## Day 7：对话式搜索 — Chat Bot 微服务对接 + 本地匹配引擎 + 聊天气泡 UI

### 完成内容

#### 1. 设备级用户标识（common/util/SessionManager.kt）

- SharedPreferences 持久化 UUID，首次启动生成，卸载重装重置
- `val userId` 懒加载属性，通过 `X-User-Id` 请求头传递给微服务
- `regenerateUserId()` 调试方法（会丢失历史 session 数据）

#### 2. 聊天数据模型（search/model/SearchModels.kt + SearchUiState.kt）

**SearchModels.kt — 网络层 DTO**：
- `ChatApiResponse<T>` — 微服务通用响应包装 `{ code, message, data }`
- `SessionInfo` / `CreateSessionRequest` — Session 创建
- `SendMessageRequest` / `ChatMessageDto` / `SendMessageData` — 消息发送/接收
- `MessageHistoryData` — 对话历史
- `ChatUiMessage` — UI 层消费的消息模型（role + content + ads + isFallback）

**SearchUiState.kt — UI 状态**：
- `messages: List<ChatUiMessage>` — 对话消息列表
- `sessionId: String?` — 当前 session（null = 服务不可用）
- `isServiceAvailable: Boolean` — 降级开关
- `inputText` / `isLoading` / `errorMessage`

#### 3. Chat Bot 微服务 Retrofit 接口（ai/api/ChatBotService.kt）

```
POST   /api/sessions                  → createSession()
POST   /api/sessions/{id}/messages    → sendMessage()       ← 核心
GET    /api/sessions/{id}/messages    → getHistory()
DELETE /api/sessions/{id}             → deleteSession()
```

- `X-User-Id` 头通过 OkHttp Interceptor 自动注入（来自 SessionManager.userId）
- 所有返回类型使用 `Response<ChatApiResponse<T>>` 处理成功/错误码

#### 4. NetworkConfig + AppModule 扩展（Chat Bot 网络层）

**NetworkConfig.kt 新增**：
- `getChatBotBaseUrl()` — 从 `BuildConfig.CHATBOT_SERVICE_URL` 读取
- `createChatBotRetrofit(client, userId)` — 独立 Retrofit 实例 + `userIdInterceptor()`
- `userIdInterceptor(userId)` — 注入 `X-User-Id` 请求头

**build.gradle.kts 新增**：
- `buildConfigField("CHATBOT_SERVICE_URL", ...)` — 从 `local.properties` 注入 `chatbot.service.url`

**AppModule.kt 新增**：
- `single<SessionManager>` — 设备 UUID 管理器
- `single<Retrofit>(named("chatbotRetrofit"))` — 微服务 Retrofit 实例
- `single<ChatBotService>` — 微服务 API 接口
- `single<AdMatchingEngine>` — 本地匹配引擎

#### 5. AdMatchingEngine 本地匹配引擎（search/engine/AdMatchingEngine.kt）

**三条匹配路径**：

| 路径 | 方法 | 触发场景 |
|------|------|---------|
| **AI 意图匹配** | `matchWithIntent(ads, intent)` | 微服务返回 `AiIntentResult` → 转换为 `SearchCriteria` → `match()` |
| **结构化匹配** | `match(ads, criteria)` | 直接传入 `SearchCriteria`（如标签过滤） |
| **关键词降级** | `keywordSearch(ads, query)` | 微服务不可用时，分词后在 title/description/tag 中子串匹配 |

**评分规则**：
```
标签精确匹配       → +3.0 / 个
关键词命中标题     → +2.0 / 个  (降级模式: +3.0)
关键词命中描述     → +1.0 / 个  (降级模式: +1.5)
受众标签匹配       → +2.0 / 个
标签名称命中关键词 → +2.0 / 个  (仅降级模式)
```

- 分词策略：按空格 + 中英文标点拆分，过滤单字（< 2 字符）
- 结果按 `score` 降序排列，score = 0 的广告不返回
- `AdMatchResult(ad, score)` 包装

#### 6. SearchViewModel（search/viewmodel/SearchViewModel.kt）

**核心流程**：

```
init → createSession()
  ├─ 成功 → isServiceAvailable=true, sessionId=xxx
  └─ 失败 → isServiceAvailable=false（后续走降级）

sendMessage(query):
  ├─ 添加用户气泡到 messages
  ├─ isServiceAvailable=true:
  │   ├─ POST /api/sessions/{id}/messages
  │   ├─ 成功 → 提取 intent → AdMatchingEngine.matchWithIntent()
  │   │         → 添加 AI 气泡（文本 + 广告卡片）
  │   └─ 失败 → AdMatchingEngine.keywordSearch() 降级
  └─ isServiceAvailable=false:
      └─ AdMatchingEngine.keywordSearch() 直接降级

clearConversation():
  ├─ DELETE /api/sessions/{id}
  ├─ POST /api/sessions（新 session）
  └─ 清空 messages
```

**数据获取**：
- `getAllAds()` — 遍历 3 个频道，优先使用 Repository 内存缓存
- 缓存为空时异步加载首页数据（确保匹配引擎有数据可用）
- 按 `adId` 去重（同一广告可能出现在多个频道）

**降级策略**：
- 微服务不可用时 AI 气泡显示"搜索服务暂不可用，以下是本地关键词匹配结果"
- `ChatUiMessage.isFallback = true` 标记降级消息
- UI 在气泡内显示 "⚠ 本地匹配结果" 小字提示

#### 7. SearchScreen 聊天气泡 UI（search/ui/SearchScreen.kt）

**页面布局**：

```
Scaffold
├── TopAppBar: ← 返回 | "AI 搜索" | 清空按钮
├── Content:
│   ├── 欢迎态（无消息时）
│   │   ├── 服务可用：AutoAwesome 图标 + 使用引导文案
│   │   └── 服务不可用：SearchOff 图标 + 本地模式提示
│   └── 对话态：LazyColumn
│       ├── 用户气泡：右对齐，primary 蓝色背景，白色文字
│       ├── AI 气泡：左对齐，surfaceVariant 背景
│       │   ├── AI 文本
│       │   ├── 降级标记（isFallback 时显示）
│       │   └── 嵌入广告卡片（标题 + 缩略图 + 广告主 → 可点击到详情页）
│       └── 加载气泡："思考中..." + spinner
└── BottomBar: 圆角 TextField + Send 图标按钮
```

**Composable 组件树**：
- `WelcomeCard` — 欢迎卡片（服务可用/不可用两种状态）
- `ChatBubble` — 聊天气泡分发（USER / ASSISTANT）
- `EmbeddedAdCard` — 对话流中嵌入的简版广告卡片（56dp 缩略图 + 标题 + 广告主 + 箭头）
- `AiAvatar` / `UserAvatar` — 头像（32dp 圆形）
- `LoadingBubble` — AI 思考中动画
- `SearchInputBar` — 底部输入栏（圆角 TextField + 发送按钮）

**交互细节**：
- 新消息到达时 LazyColumn 自动滚动到底部（`LaunchedEffect(messages.size)`）
- 发送后清空输入框 → 显示加载气泡 → AI 回复到达后替换
- 点击嵌入广告卡片 → `onNavigateToDetail(adId)` → 导航到详情页
- 清空按钮仅在 `messages.isNotEmpty()` 时显示

#### 8. 搜索入口集成

**HomeScreen.kt**：
- TopAppBar 新增搜索图标（`Icons.Filled.Search`）
- 新增 `onNavigateToSearch` 回调参数

**MainActivity.kt**：
- NavHost 新增 `composable("search")` 路由
- `SearchScreen(onBack, onNavigateToDetail)` 完整连线
- 路由表更新：`home → detail/{adId} → search`

**ViewModelModule.kt**：
- 注册 `SearchViewModel(chatBotService, matchingEngine, repository)`

### 架构设计要点

#### 整体数据流

```
┌───────────────────────────────────────────────────────────┐
│  SearchScreen (Compose UI)                                 │
│    观察 SearchUiState → 渲染聊天气泡 + 广告卡片              │
└──────────────────────┬────────────────────────────────────┘
                       │ StateFlow<SearchUiState>
┌──────────────────────▼────────────────────────────────────┐
│  SearchViewModel                                           │
│    sendMessage → 路径选择:                                  │
│    ├─ 正常路径: ChatBotService → AiIntentResult            │
│    │            → AdMatchingEngine.matchWithIntent()       │
│    └─ 降级路径: AdMatchingEngine.keywordSearch()           │
└──────────────────────┬────────────────────────────────────┘
                       │
        ┌──────────────┼──────────────┐
        ▼              ▼              ▼
┌──────────────┐ ┌───────────┐ ┌──────────────┐
│ChatBotService│ │AdMatching │ │ AdRepository │
│(微服务 API)   │ │Engine     │ │(本地广告数据) │
└──────┬───────┘ └───────────┘ └──────────────┘
       │
       ▼
┌──────────────────────────────────────────────┐
│  Chat Bot 微服务 (localhost:8080)             │
│  ├─ /v1/chat/completions (AI 摘要/标签)       │
│  └─ /api/sessions/* (对话搜索)                │
└──────────────────────────────────────────────┘
```

#### 关键设计决策

| 决策项 | 内容 | 理由 |
|--------|------|------|
| 广告匹配位置 | 客户端本地执行 AdMatchingEngine | 本项目数据量小（30条/频道），客户端匹配无延迟；不依赖微服务端维护广告数据副本（chatbot-api.md §8.2.2 模式 B） |
| Session 管理 | 客户端仅维护 sessionId | 对话历史由微服务端存储（多轮上下文需服务端维护），客户端保存轻量 sessionId 用于后续消息发送 |
| 降级策略 | 微服务不可用 → 本地关键词搜索 | 用户体验不中断：即使微服务未启动，仍可通过关键词搜索获得结果 |
| ChatBot Retrofit 独立性 | 独立 Retrofit 实例（独立 BaseUrl + X-User-Id 拦截器） | 与 AI 摘要 API / 广告 API 隔离——微服务故障不影响其他功能 |
| 搜索页面广告数据来源 | AdRepository 三频道缓存 + 按需加载 | 避免搜索时无数据匹配；优先复用内存缓存，减少 IO |
| 嵌入广告卡片 | 简化版 Composable（56dp 缩略图 + 标题 + 广告主） | 对话流中不宜展示完整 feed 卡片（太大打断阅读流），简版卡片提供足够信息 + 点击跳转详情 |

### 遇到的问题与解决

1. **JVM 签名冲突（`getUserId()`）**：`val userId` 属性自动生成 `getUserId()` getter，与手动定义的 `fun getUserId()` 方法签名相同。删除手动方法，仅保留属性访问。

2. **StateFlow 在 Compose 中的访问方式**：`viewModel.uiState` 返回 `StateFlow<SearchUiState>`，在 Composable 中直接访问 `.messages` 等属性会编译失败。使用 `by viewModel.uiState.collectAsState()` 将 StateFlow 转换为 Compose State。

3. **struct.md 文件缩进格式**：struct.md 使用 tab + 树状字符的混合缩进，Edit 工具难以精确匹配。使用 PowerShell 脚本做内容替换。

### 学到的内容

- **Kotlinx Serialization 泛型处理**：`ChatApiResponse<T>` 在 Retrofit 中通过 `Response<ChatApiResponse<SessionInfo>>` 指定具体类型参数，Retrofit + Kotlinx Serialization converter 能在运行时正确解析参数化类型。
- **StateFlow → Compose State 桥接**：`collectAsState()` 是 Compose 中观察 StateFlow 的标准方式，将响应式流转换为 Compose snapshot 系统可追踪的 State 对象。
- **OkHttp Interceptor 链设计**：不同 API 通过不同的 Interceptor 注入不同的认证头（AI：`Authorization: Bearer`；ChatBot：`X-User-Id`），共享底层连接池但独立认证逻辑。
- **降级设计的层次化**：Day 7 实现了"微服务不可用 → 本地关键词搜索"的降级路径，加上 Day 6 的"AI API 失败 → 静态内容降级"，形成了 AI 功能的多层降级体系。
- **LazyColumn 在聊天场景的使用**：通过 `LaunchedEffect(messages.size)` 监听消息数量变化并自动滚动到底部，配合 `key = { it.id }` 确保消息气泡不重复渲染。

### 文档更新

- `struct.md`：新增 Day 7 全部 8 个文件路径
- `docs/daily-report.md`：Day 7 日报
- `app/build.gradle.kts`：新增 `CHATBOT_SERVICE_URL` BuildConfig 字段

---

## Day 7 补充：聊天历史内存缓存 + 应用启动预加载 + 搜索结果恢复

### 背景问题

1. **每次进入 ChatScreen 都重新加载历史** — ViewModel 随 Compose 导航重建时总是发起 `GET /api/sessions/{id}/messages` 网络请求
2. **搜索匹配的广告结果在重载历史时消失** — 广告匹配在客户端执行（Mode B），服务端 `ads` 字段为空；`loadHistory()` 直接使用 `dto.ads ?: emptyList()` 导致广告卡片丢失
3. **冷启动无预加载** — 用户退出应用重进时，必须等进入 ChatScreen 才开始加载历史

### 完成内容

#### 1. ChatMemoryCache（ai/chat/cache/ChatMemoryCache.kt）

- Koin `single`，纯内存缓存（不持久化到 Room）
- 线程安全设计：所有可变操作使用 `@Synchronized`，`getMessages()` 返回防御性拷贝
- 三个核心操作：`setMessages`（整批写入）、`addMessage`（单条追加）、`clear`（清空 + 标记未加载）
- `isWarm()` 标志区分"缓存有数据"和"已清空/未初始化"

#### 2. ChatPreloader（ai/chat/preload/ChatPreloader.kt）

- 应用启动时在 `Dispatchers.IO` 后台预加载历史
- 幂等设计：`cache.isWarm()` 时立即返回
- **广告恢复核心逻辑**：利用服务端持久化的 `AiIntentResult`，通过 `AdMatchingEngine.matchWithIntent()` 重新执行本地匹配，恢复之前展示的广告卡片
- 失败静默：网络不可用时由 ChatViewModel 在用户进入时走正常 loadHistory 路径

#### 3. ChatViewModel 修改

- **init**：优先检查 `chatCache.isWarm()` → 命中则直接展示（无加载动画、无网络请求）
- **loadHistory**：提取 `rematchDtoToUiMessage()` 方法，加载全量广告后批量重匹配 + 写入缓存
- **sendMessage**：用户消息 + AI 回复均同步写入 `chatCache` 保持一致性
- **clearConversation**：调用 `chatCache.clear()` 防止下次进入读到已删除数据

#### 4. AdsApplication 启动预加载

- 新增 `preloadChatHistory()` 方法，复用 `applicationScope`（IO 线程）
- 在 `cleanExpiredAiCache()` 之后调用
- 使用 `GlobalContext.get().get()` 获取 Koin 管理的 `ChatPreloader`

#### 5. DI 模块更新

- `AppModule.kt`：注册 `ChatMemoryCache`（零依赖单例）+ `ChatPreloader`（5 依赖单例）
- `ViewModelModule.kt`：ChatViewModel 构造增加 `chatCache = get()`

### 数据流

```
App Start
  └─ preloadChatHistory() [IO]
       └─ ChatPreloader.preload()
            ├─ GET /api/sessions/{id}/messages
            ├─ getAllAds() → matchWithIntent() 重匹配 → 恢复广告卡片
            └─ cache.setMessages() → cache warm

User → ChatScreen
  └─ ChatViewModel.init()
       ├─ cache.isWarm()? yes → 直接展示，无网络请求  ← 关键优化
       └─ no → loadHistory() → rematchDtoToUiMessage() → cache.setMessages()

User sends message
  └─ sendMessage()
       ├─ cache.addMessage(userMsg)
       ├─ POST → matchWithIntent()
       └─ cache.addMessage(aiMsg)  ← 实时同步

User clears conversation
  └─ clearConversation()
       ├─ cache.clear()  ← 防止脏读
       ├─ DELETE session
       └─ createSession()
```

### 关键设计决策

| 决策项 | 内容 | 理由 |
|--------|------|------|
| 缓存持久化策略 | 纯内存（不写 Room） | 用户明确要求"不用永久存储，只用存在内存即可"；聊天历史由服务端持久化，本地缓存仅加速热启动 |
| 广告恢复策略 | 利用服务端 `intent` 字段本地重匹配 | Mode B 下服务端不存储完整广告列表，但 LLM 解析出的 `AiIntentResult` 在服务端持久化；重匹配保证了广告卡片不丢失 |
| 线程安全方案 | `@Synchronized` 方法级锁 | 操作轻量（消息列表替换/追加），`@Synchronized` 比 `Mutex` 开销更小，代码更简洁 |
| 预加载失败处理 | 静默跳过 | ViewModel 在用户进入 ChatScreen 时走正常 loadHistory 路径做兜底 |
| 重复预加载防护 | `cache.isWarm()` 幂等检查 | ChatPreloader.preload() 和 ChatViewModel.init 的缓存路径互不干扰 |

### 文件变更

| Action | File | 
|--------|------|
| CREATE | `ai/chat/cache/ChatMemoryCache.kt` |
| CREATE | `ai/chat/preload/ChatPreloader.kt` |
| MODIFY | `ai/chat/viewmodel/ChatViewModel.kt` — 缓存优先 + rematchDtoToUiMessage + 同步 |
| MODIFY | `di/AppModule.kt` — 注册新单例 |
| MODIFY | `di/ViewModelModule.kt` — 注入 chatCache |
| MODIFY | `AdsApplication.kt` — 启动预加载 |
| MODIFY | `struct.md` — 新增 2 个文件路径 |

### 文档更新

- `struct.md`：新增 `chat/cache/` 和 `chat/preload/` 目录
- `docs/daily-report.md`：Day 7 补充

---

## Day 8：搜索功能增强 — 输入按钮 + 空态修复 + 返回逻辑修复 + 搜索历史

### 问题诊断

1. **搜索结果为空时无提示**：`showResults` 条件 `results.isNotEmpty() || LOADING || ERROR` 导致搜索结果为空且 loadState 回到 IDLE 时整个 ResultsSection 不渲染，空态 UI 永远不会显示
2. **返回逻辑错误**：`onBack` 直接 `popBackStack()`，从搜索结果页返回直接回到主页，而非回到搜索初始页
3. **缺少显式的搜索提交按钮**：只能通过键盘 IME 提交搜索，不符合中文输入习惯
4. **缺少搜索历史**：缺少类似 Apple Music 的搜索历史功能（记录用户点进详情的广告，而非搜索关键词）

### 完成内容

#### 1. SearchHistoryManager（search/data/SearchHistoryManager.kt）— 新建

- **JSON 文件持久化**：使用 `kotlinx.serialization` 序列化 `List<AdItem>` 到 `filesDir/search_history.json`
- **去重 + 置顶**：同一广告重复点击时移到列表顶部
- **上限 50 条**：超过则删除最旧的记录
- **线程安全**：`Mutex` 保护写操作，文件 I/O 在 `Dispatchers.IO` 上执行
- **StateFlow 暴露**：ViewModel 可观察历史列表变化并驱动 UI 重组
- **容错设计**：JSON 解析失败时删除旧文件静默降级

#### 2. SearchUiState + SearchEvent 更新（search/model/SearchUiState.kt）

**新增字段**：
- `hasSearched: Boolean` — 标记用户是否已提交搜索（解决空态不显示的问题）
- `searchHistory: List<AdItem>` — 搜索历史列表
- `isHistoryLoaded: Boolean` — 历史是否已加载

**新增事件**：
- `AddToHistory(adItem)` — 点击搜索结果或历史条目时记录
- `ClearHistory` — 清空所有历史
- `LoadHistory` — 加载历史（ViewModel init 触发）
- `GoBack` — 返回按钮（UI 层可处理）

#### 3. SearchViewModel 更新（search/viewmodel/SearchViewModel.kt）

**新增依赖**：`SearchHistoryManager`（Koin 注入）

**返回逻辑 `handleBackPress()`**：
```
有搜索结果 (hasSearched=true) → clearResults() → 回到初始态 → return false（消费事件）
无搜索结果 (hasSearched=false) → return true（UI 层执行 popBackStack）
```

**搜索历史管理**：
- `loadHistory()` → 异步加载文件 → 收集 `historyManager.history` StateFlow → 更新 UI
- `addToHistory(adItem)` → 追加/置顶 + 持久化
- `clearHistory()` → 清空内存 + 文件

**搜索执行调整**：
- `onSubmitSearch()` / `executeSearch()` / `onSelectTrending()` / `onSelectSuggestion()` → 设置 `hasSearched = true`
- `clearResults()` → 重置 `hasSearched = false`，保留热搜和历史

#### 4. SearchScreen UI 更新（search/ui/SearchScreen.kt）

**"输入"按钮**：
- 搜索框 `trailingIcon` 区域显示 `Row(Text("输入") + ClearButton)`
- 仅在输入框有文字时显示
- 点击触发 `SubmitSearch`

**搜索历史区域**：
- 历史图标 + "搜索历史"标题 + 清空按钮
- 每条历史：缩略图 + 广告标题 + 广告主名称
- 点击历史条目 → 记录历史（置顶）+ 导航到详情页
- 历史为空时不显示

**空态修复**：
- `showResults` = `hasSearched || searchLoadState == LOADING`
- 搜索完成且结果为空时正常显示空态 UI（SearchOff 图标 + "未找到相关广告"）

**返回逻辑修复**：
- 返回按钮 `onClick` → `viewModel.handleBackPress()` → 结果页回初始，初始页回主页

**UI 三阶段重定义**：
```
Phase 1 (INITIAL):    query.isBlank() && !hasSearched → 搜索历史 + 热门关键词
Phase 2 (SUGGESTING):  query.isNotBlank() && !hasSearched → 联想建议
Phase 3 (RESULTS):    hasSearched || LOADING → 结果列表
```

#### 5. DI 模块更新

- `AppModule.kt`：注册 `SearchHistoryManager` 单例（依赖 Application Context）
- `ViewModelModule.kt`：`SearchViewModel` 构造新增 `historyManager = get()`

### 架构设计要点

#### 搜索历史数据流

```
SearchScreen: 点击广告
  → onAdClick(adItem)
  → viewModel.onEvent(AddToHistory(adItem))
  → SearchViewModel.addToHistory()
  → SearchHistoryManager.addToHistory()
     ├─ 去重（同 ID 删除旧条目）
     ├─ 置顶（add(0, adItem)）
     ├─ 截断（> 50 条）
     ├─ 写入 JSON 文件
     └─ _history.value = newList → StateFlow emit → UI 重组
```

### 关键设计决策

| 决策项 | 内容 | 理由 |
|--------|------|------|
| 搜索历史内容 | 记录点进详情的广告，非搜索关键词 | 类似 Apple Music 搜索历史：用户关心的不是搜索了什么词，而是对哪些内容感兴趣 |
| 历史持久化方式 | JSON 文件（kotlinx.serialization） | 复用已有的序列化基础设施，利用 AdItem 的 `@Serializable` 多态支持；无需引入新依赖 |
| 返回逻辑实现 | `handleBackPress()` 返回 Boolean 决策 | ViewModel 知道当前状态（hasSearched），但 popBackStack 是 UI 层能力；通过返回值协调两层 |
| 空态修复 | 引入 `hasSearched` 标记 vs 修改 showResults 条件 | `hasSearched` 语义更清晰，不与 LOADING/ERROR 状态耦合 |
| "输入"按钮样式 | 文字按钮（非图标按钮） | 中文字"输入"更直观，符合用户习惯；放在 trailingIcon 区域利用现有布局 |

### 文件变更

| Action | File |
|--------|------|
| CREATE | `search/data/SearchHistoryManager.kt` |
| MODIFY | `search/model/SearchUiState.kt` — 新增字段 + 事件 |
| MODIFY | `search/viewmodel/SearchViewModel.kt` — 历史管理 + handleBackPress |
| MODIFY | `search/ui/SearchScreen.kt` — 输入按钮 + 历史区域 + 空态修复 + 返回修复 |
| MODIFY | `di/AppModule.kt` — 注册 SearchHistoryManager |
| MODIFY | `di/ViewModelModule.kt` — 注入 historyManager |
| MODIFY | `struct.md` — 新增 search/data/ 目录 |

### 文档更新

- `struct.md`：更新 search/ 目录结构 + Day 8 日期
- `docs/daily-report.md`：Day 8 日报

---

## Day 8（续）：标签过滤完善 + 图片/视频加载优化

### 完成内容

#### 1. 修复 MockJsonDataSource 过滤分页 bug

**问题**：`getAdsByTag()` 调用 `paginate(filtered, page, pageSize, fullData.totalPages)` 将未过滤的 `totalPages` 传给 `paginate()`。当过滤结果集远小于原始数据时（如 30 条→3 条），`fromIndex >= allItems.size` 的分支返回原始 totalPages（3 页），而实际只有 1 页。

**修复**：
- `paginate()` 移除 `originalTotalPages` 参数，始终基于实际数据量 `allItems.size` 计算 totalPages
- `getAdsByTag()` 调用改为 `paginate(filtered, page, pageSize)`
- `getAds()` 同步更新调用

**影响**：过滤后列表底部正确显示"— 没有更多了 —"，而非触发空数据加载请求。

#### 2. ImageRequestHelper 图片请求辅助工具（新建）

**文件**：`common/imageloader/ImageRequestHelper.kt`

- 统一构建优化的 `ImageRequest`，封装 Coil 3.x 的 `ImageRequest.Builder`
- **精确尺寸解码**：`targetWidthDp` / `targetHeightDp` → 内部转为 px → `builder.size(Size(pxW, pxH))`，避免加载大图后在内存中缩放到小尺寸
- **skipLoading 参数**：`true` 时跳过非必要加载（用于快速滑动优化）
- **Coil 3.x 适配**：Coil 3.0.0 移除了 `Priority` API，改为通过 `skipLoading` 控制加载行为

#### 3. ImageLoaderConfig 磁盘缓存清理器

**新增方法**：`cleanupOldCache(context, maxAgeDays = 7)`

- 在 `Dispatchers.IO` 执行，不阻塞主线程
- 遍历 `image_cache` 目录，删除最后修改时间超过 7 天的文件
- 失败静默处理（目录不存在、无权限等）
- 设计理由：Coil 3.x 的 DiskCache 基于 LRU 大小驱逐（200MB 上限），不提供内建的"按时间过期"功能，此方法补充时间维度淘汰

#### 4. OkHttp 全局重试拦截器

**文件**：`common/network/NetworkConfig.kt`

**重试策略**：
| 条件 | 行为 |
|------|------|
| GET 请求 + IOException | 指数退避重试（1s→2s→4s），最多 3 次 |
| POST/PUT/DELETE 请求 | 不重试（避免重复提交） |
| 非 IOException（HTTP 4xx/5xx） | 不重试（由上层业务处理） |

- 添加 `retryOnConnectionFailure(true)`（OkHttp 内建）
- 自定义 `retryInterceptor()` 覆盖超时、EOF 等连接失败以外的场景
- 主要受益场景：Coil 图片加载（通过 OkHttp 发送 GET 请求）

#### 5. 卡片图片加载优化

**LargeImageCard**：
- 封面图使用 `ImageRequestHelper.buildOptimizedRequest()` + `targetWidthDp=400` 精确解码
- 减少内存占用（避免加载 1920px 原图后在 Compose 中缩放）

**SmallImageCard**：
- 缩略图使用 `ImageRequestHelper.buildOptimizedRequest()` + `targetWidthDp=110, targetHeightDp=110`
- 解码尺寸与显示尺寸一致，零浪费

**VideoCard**：
- 封面图（IDLE 态 + 暂停态）统一使用 `ImageRequestHelper` + `targetWidthDp=400`
- 快速滑动时 `skipLoading = isScrollInProgress` → 跳过封面加载，让位给交互帧
- 视频开始播放时通过 `imageLoader.enqueue()` 预加载下一个视频封面到磁盘缓存

#### 6. 视频加载优化

**快速滑动检测**：
- HomeScreen 追踪每个频道的 `scrollStates: Map<Channel, Boolean>`（通过 `LazyListState.isScrollInProgress`）
- 链路：`HomeScreen → FeedScreen(isScrollInProgress) → VideoCard(isScrollInProgress)`
- 快速滑动时封面 `skipLoading = true`，停止后恢复正常加载

**视频封面预加载**：
- `VideoCard` 新增 `nextVideoCoverUrl: String?` 参数
- `FeedScreen` 在渲染 VideoCard 时从列表中查找下一个 VideoAd 的封面 URL
- 视频开始播放时，通过 Koin 获取 `ImageLoader` 并 `enqueue()` 预加载请求

#### 7. AdsApplication 启动时缓存清理

- 新增 `cleanExpiredImageCache()` 方法
- 在 `cleanExpiredAiCache()` 之后调用
- 复用 `applicationScope`（SupervisorJob + IO Dispatcher）

### 架构设计要点

#### 图片加载优化链路

```
Card Composable
  └─ AsyncImage(model = ImageRequestHelper.buildOptimizedRequest(url, ...))
       └─ ImageRequest.Builder
            ├─ size(width, height)        ← 精确解码，降低内存
            ├─ memoryCachePolicy(ENABLED) ← L1 缓存命中
            ├─ diskCachePolicy(ENABLED)   ← L2 缓存命中
            └─ skipLoading                ← 快速滑动时跳过

请求进入 OkHttpClient
  └─ retryInterceptor()                  ← GET 请求异常重试 3 次
       └─ 指数退避: 1s → 2s → 4s

Coil ImageLoader
  ├─ 内存缓存 (1/8 RAM LRU)
  ├─ 磁盘缓存 (200MB DiskLRU)           ← cleanupOldCache 每 7 天清理
  └─ 网络请求 → OkHttpClient → OK
```

#### 视频优化流程

```
用户滚动信息流
  ├─ LazyListState.isScrollInProgress → snapshotFlow → distinctUntilChanged
  ├─ FeedScreen.onScrollStateChanged → HomeScreen.scrollStates[channel] = isScrolling
  ├─ FeedScreen(isScrollInProgress = scrollStates[channel])
  │   └─ VideoCard(isScrollInProgress)
  │        ├─ isScrollInProgress=true  → skipLoading=true  → 封面不加载
  │        └─ isScrollInProgress=false → skipLoading=false → 封面正常加载
  │
  └─ 用户点击播放按钮 → 视频开始播放
       └─ nextVideoCoverUrl?.let { imageLoader.enqueue(preloadRequest) }
            └─ 下一个视频封面后台缓存到磁盘
```

### 涉及文件清单

| 操作 | 文件 |
|------|------|
| MODIFY | `data/local/MockJsonDataSource.kt` — 修复分页 totalPages 计算 |
| CREATE | `common/imageloader/ImageRequestHelper.kt` — 图片请求辅助工具 |
| MODIFY | `common/imageloader/ImageLoaderConfig.kt` — 新增 7 天过期缓存清理 |
| MODIFY | `common/network/NetworkConfig.kt` — 新增 GET 请求重试拦截器 |
| MODIFY | `feed/ui/card/LargeImageCard.kt` — 封面图精确解码 |
| MODIFY | `feed/ui/card/SmallImageCard.kt` — 缩略图精确解码 |
| MODIFY | `feed/ui/card/VideoCard.kt` — 封面优化 + 快速滑动 + 预加载 |
| MODIFY | `feed/ui/FeedScreen.kt` — 传递 isScrollInProgress + 计算 nextVideoCoverUrl |
| MODIFY | `feed/ui/HomeScreen.kt` — 传递 scrollStates 到 FeedScreen |
| MODIFY | `AdsApplication.kt` — 启动时清理过期图片缓存 |

### 关键设计决策

| 决策项 | 内容 | 理由 |
|--------|------|------|
| Coil Priority 替代方案 | 使用 `skipLoading` 而非 Priority | Coil 3.0.0 移除了 `Priority` API，`skipLoading` 更直接地控制"此时是否加载" |
| 图片精确解码尺寸 | 大图 400dp→px、缩略图 110dp→px | 与布局尺寸一致，避免 Coil 解码 1920px 原图后在 Compose 中缩放至 ~360dp |
| 视频封面预加载触发时机 | `startPlayback` 中同步执行 | 用户在观看当前视频时，下一个封面在后台静默缓存，滑动时即时展示 |
| 磁盘缓存 7 天过期 | 独立文件扫描 + 删除 | Coil DiskCache 无内建 TTL 机制，通过文件系统时间戳补充时间维度淘汰 |
| 重试仅限 GET | 检查 `request.method` | POST 重试可能导致服务端重复创建资源（如重复发送 AI 请求） |

### 文档更新

- `struct.md`：更新 Day 8 日期标识 + 新增文件路径
- `docs/daily-report.md`：Day 8（续）日报

---

## Day 9：行为采集 + 画像引擎 + 曝光追踪 + 个性化推荐 + 统计页面

### 完成内容

#### 1. BehaviorCollector 用户行为采集器（新建）

**文件**：`behavior/tracker/BehaviorCollector.kt`

- 采集 6 种用户行为：CLICK / LIKE / COLLECT / SHARE / TAG_CLICK / SEARCH
- 使用 `SupervisorJob + Dispatchers.IO` 异步写入 Room，不阻塞主线程
- 单次写入失败不影响后续采集（静默吞掉异常）
- 提供 `collect(behavior)` 单条采集 + `collectAll(behaviors)` 批量采集
- UserBehavior → BehaviorEntity 转换 + tags JSON 序列化

#### 2. UserProfileEngine 用户画像引擎（新建）

**文件**：`behavior/profile/UserProfileEngine.kt`

- 从 Room BehaviorDao 读取所有行为记录
- 按标签维度聚合加权得分：`得分 = Σ(行为次数 × 行为权重)`
- 同时统计行为总览：总点击/点赞/收藏/分享数
- 新用户（无行为数据）返回空画像
- 计算在 `Dispatchers.IO` 上执行

#### 3. RecommendRanker 个性化推荐排序器（新建）

**文件**：`behavior/recommend/RecommendRanker.kt`

- **精选频道**（有画像数据）：按标签匹配度降序排列
  - 匹配度 = 广告的每个标签在用户画像中的权重之和
  - 同分按曝光量降序（热门广告优先）
- **精选频道**（新用户）及其他频道：按曝光量降序（热度排序）
- 排序不修改原列表，返回新列表

#### 4. ExposureTracker 曝光检测器（新建，Compose 适配版）

**文件**：`analytics/tracker/ExposureTracker.kt`

- 基于 `LazyListState.layoutInfo.visibleItemsInfo` 实时检测可见 item
- **曝光判定**（与 tech.md §9.1.1 一致）：
  - 可见比例 ≥ 50%（item.offset + item.size vs viewportSize.height）
  - 停留时长 ≥ 1000ms
  - 同会话去重（`Set<String>` 存储已曝光 adId）
- `snapshotFlow` + `distinctUntilChanged` 避免重复计算
- `collectLatest` 自动取消上一次未完成的收集
- `Track(lazyListState, ads, onExposed)` Composable 函数，嵌入 FeedScreen

#### 5. StatsScreen + StatsViewModel 统计页面（新建）

**StatsViewModel**：`analytics/viewmodel/StatsViewModel.kt`
- 并行加载全量广告（AdRepository.getAllAds()）+ 用户画像（UserProfileEngine）
- 构建 `AdStatItem` 列表（曝光数 + 点击数 + CTR）
- 支持按曝光/点击/CTR 三种维度排序
- 管理 Tab 切换：广告统计 / 我的偏好

**StatsScreen**：`analytics/ui/StatsScreen.kt`
- **Tab "广告统计"**：
  - 排序 FilterChip：按曝光 / 按点击 / 按 CTR
  - LazyColumn 展示每广告：标题 + 曝光图标 + 点击图标 + CTR% + 动画进度条
  - 空态：居中图标 + 提示文案
- **Tab "我的偏好"**：
  - 行为总览卡片：4 个小卡片（总点击/总点赞/总收藏/总分享），彩色图标 + 数字
  - Top 标签权重柱状图：标签名 + 动画水平条 + 权重数值，颜色强度随权重变化
  - 标签云（`FlowRow`）：SuggestionChip 自动换行，字号/字重/背景透明度随权重变化
  - 空态：提示"还没有偏好数据"

**AnalyticsModels**：`analytics/model/AnalyticsModels.kt`
- `AdStatItem`（ad + exposureCount + clickCount + ctr）
- `StatsSortBy`（EXPOSURE / CLICK / CTR）
- `StatsTab`（AD_STATS / MY_PREFERENCES）
- `StatsUiState` + `StatsEvent`（UDF 模式）

#### 6. 数据层扩展——曝光/点击计数

**AdDataSource 接口新增方法**：
- `incrementExposure(adId)` — 递增曝光计数
- `incrementClick(adId)` — 递增点击计数

**实现**：
- `MockJsonDataSource`：直接在内存缓存中 `ad.exposureCount += 1` / `ad.clickCount += 1`
- `RemoteDataSource`：调用对应 Retrofit API 端点
- `AdApiService`：定义 `POST /api/v1/ads/{adId}/exposure` 和 `/click` 端点

**AdRepository 新增方法**：
- `incrementExposure(adId)` / `incrementClick(adId)`
- 更新 DataSource 后同步 Repository 本地 `channelItems` 缓存（`syncLocalCache`）
- 确保 StatsScreen 读取到最新计数

#### 7. FeedViewModel + DetailViewModel 行为采集集成

**FeedViewModel**：
- 新增构造参数 `BehaviorCollector`
- `toggleLike` → 采集 LIKE 行为
- `toggleCollect` → 采集 COLLECT 行为
- `share` → 采集 SHARE 行为
- `filterByTag` → 采集 TAG_CLICK 行为（不关联特定广告）
- `navigateToDetail` → 采集 CLICK 行为 + 调用 `repository.incrementClick`
- `onAdExposed` → 调用 `repository.incrementExposure`
- 新增 `FeedEvent.Expose(adId)` 事件

**DetailViewModel**：
- 新增构造参数 `BehaviorCollector`
- `toggleLike` / `toggleCollect` / `share` → 采集对应行为

#### 8. FeedScreen 曝光追踪集成

- `remember { ExposureTracker() }`
- `exposureTracker.Track(lazyListState, ads, onExposed = { viewModel.onEvent(FeedEvent.Expose(it)) })`
- 嵌入在 LaunchedEffect 链中，与上拉加载检测并列

#### 9. 导航与入口

- **MainActivity**：新增 `"stats"` 路由 → StatsScreen
- **HomeScreen**：TopAppBar 新增柱状图图标按钮（BarChart）→ `onNavigateToStats`

### 架构设计要点

#### 数据流全景

```
用户交互
  ├─ 信息流 (FeedScreen)
  │   ├─ 点击卡片 → FeedEvent.CardClick
  │   │   ├─ BehaviorCollector.collect(CLICK) → Room
  │   │   └─ AdRepository.incrementClick() → DataSource
  │   ├─ 点赞/收藏/分享 → BehaviorCollector → Room
  │   ├─ 标签点击 → BehaviorCollector.collect(TAG_CLICK) → Room
  │   └─ 曝光追踪 → ExposureTracker.Track()
  │       ├─ LazyListState.layoutInfo → snapshotFlow
  │       ├─ 可见比例 ≥50% + 停留 ≥1s → onExposed
  │       └─ AdRepository.incrementExposure() → DataSource
  │
  ├─ 详情页 (DetailScreen)
  │   └─ 点赞/收藏/分享 → BehaviorCollector → Room
  │
  └─ 统计页 (StatsScreen)
      ├─ AdRepository.getAllAds() → 广告曝光/点击数据
      ├─ UserProfileEngine.compute() → 用户画像
      │   └─ BehaviorDao.getAllOnce() → 标签权重聚合
      └─ UI 展示：广告统计 + 我的偏好
```

#### 个性化推荐链路

```
用户互动 → BehaviorCollector → Room user_behaviors
    ↓
UserProfileEngine.computeProfile()
    ↓ tagWeights: Map<"运动"→10.0, "学生党"→5.0, ...>
    ↓
RecommendRanker.rank(ads, Channel.FEATURED)
    ↓
    每条广告 matchScore = Σ(tagWeights[tag.name])
    ↓
    按 matchScore DESC, exposureCount DESC 排序
    ↓
FeedViewModel → uiState.ads → LazyColumn
```

#### 曝光检测 Compose 实现 vs RecyclerView 方案

| 维度 | RecyclerView (tech.md 原方案) | Compose (Day 9 实现) |
|------|------|------|
| 可见性检测 | `OnScrollListener + findFirstVisibleItemPosition` | `snapshotFlow { layoutInfo.visibleItemsInfo }` |
| 延时机制 | `Handler.postDelayed(runnable, 1000)` | 时间戳差值比较（`now - startTime >= 1000`） |
| 去重 | `mutableSetOf<String>()` | 同 |
| 取消机制 | `handler.removeCallbacks(it)` | 不满足 50% 时 `visibilityStartTime.remove(index)` |
| 帧率影响 | View 测量（layout / measure） | Compose snapshot 通知，无额外 View 测量 |

### 涉及文件清单

| 操作 | 文件 |
|------|------|
| CREATE | `behavior/tracker/BehaviorCollector.kt` — 6 种行为采集 + Room 写入 |
| CREATE | `behavior/profile/UserProfileEngine.kt` — 标签权重聚合 → UserProfile |
| CREATE | `behavior/recommend/RecommendRanker.kt` — 个性化推荐排序 |
| CREATE | `analytics/tracker/ExposureTracker.kt` — Compose 曝光检测 |
| CREATE | `analytics/model/AnalyticsModels.kt` — 统计模型 |
| CREATE | `analytics/viewmodel/StatsViewModel.kt` — 统计页 ViewModel |
| CREATE | `analytics/ui/StatsScreen.kt` — 统计页 Composable |
| MODIFY | `data/local/AdDataSource.kt` — 新增 incrementExposure / incrementClick 接口 |
| MODIFY | `data/local/MockJsonDataSource.kt` — 实现曝光/点击计数递增 |
| MODIFY | `data/remote/RemoteDataSource.kt` — 桩实现曝光/点击计数 |
| MODIFY | `data/remote/AdApiService.kt` — 新增曝光/点击 Retrofit 端点 |
| MODIFY | `data/repository/AdRepository.kt` — 新增曝光/点击方法 + syncLocalCache |
| MODIFY | `feed/model/FeedUiState.kt` — 新增 FeedEvent.Expose 事件 |
| MODIFY | `feed/viewmodel/FeedViewModel.kt` — 注入 BehaviorCollector + 行为采集 + 曝光/点击 |
| MODIFY | `detail/viewmodel/DetailViewModel.kt` — 注入 BehaviorCollector + 行为采集 |
| MODIFY | `feed/ui/FeedScreen.kt` — 集成 ExposureTracker |
| MODIFY | `feed/ui/HomeScreen.kt` — 新增统计入口按钮 |
| MODIFY | `di/AppModule.kt` — 注册 BehaviorCollector / UserProfileEngine / RecommendRanker |
| MODIFY | `di/ViewModelModule.kt` — 更新 FeedViewModel / DetailViewModel / 注册 StatsViewModel |
| MODIFY | `MainActivity.kt` — 新增 stats 路由 |
| UPDATE | `struct.md` — 更新 Day 9 日期 + 新增文件路径 |
| UPDATE | `docs/daily-report.md` — Day 9 日报 |

### 关键设计决策

| 决策项 | 内容 | 理由 |
|--------|------|------|
| BehaviorCollector 在 ViewModel 构造注入 | 通过 Koin 注入到 FeedViewModel + DetailViewModel | 行为采集属于业务逻辑层，不应在 UI 层（Composable）直接调用 |
| 曝光/点击计数放 DataSource 层 | 在 AdDataSource 接口新增 `incrementExposure`/`incrementClick` | 与 `updateInteraction` 同级，支持 Mock/Remote 切换 |
| ExposureTracker Track() 为 Composable 函数 | `@Composable fun Track(lazyListState, ads, onExposed)` | Compose 中无法在类方法中直接使用 LaunchedEffect，通过 Composable 扩展函数桥接 |
| StatsScreen 使用 viewModel.uiState 而非 collectAsState | `val uiState = viewModel.uiState` | 与 FeedScreen 保持一致，ViewModel 中 `by mutableStateOf` 的 getter 读取即被 Compose 追踪 |
| 标签云使用 FlowRow | `ExperimentalLayoutApi.FlowRow` | 标签数量不固定，FlowRow 自动换行，无需预计算行数 |
| 权重柱状图使用 Box + animateFloatAsState | 自定义实现，非第三方图表库 | 避免引入额外依赖（MPAndroidChart 等），Compose 动画 API 足以实现 |

### 文档更新

- `struct.md`：更新 Day 9 日期 + 新增 analytics/、behavior/ 子目录文件
- `docs/daily-report.md`：Day 9 日报

---

## Day 10：行为系统集成 — RecommendRanker 打通 + SEARCH 行为采集覆盖

### 背景

Day 9 实现了 behavior 模块的全部构建块（BehaviorCollector / UserProfileEngine / RecommendRanker），但存在以下**集成缺口**：

1. **RecommendRanker 已实现但未接入信息流** — FeedViewModel 加载广告后未调用排序
2. **SEARCH 行为未覆盖全部搜索入口** — SearchViewModel 和 ChatViewModel 没有 BehaviorCollector
3. **个性化推荐不可感知** — 精选频道行为数据已采集但排序未生效

Day 10 的核心工作是**打通端到端数据流**，让 Day 9 的构建块真正生效。

### 完成内容

#### 1. RecommendRanker 集成到 FeedViewModel（FeedViewModel.kt）

**改动点**：
- 新增构造参数 `recommendRanker: RecommendRanker`
- `loadFirstPage(channel)` — 精选频道加载后调用 `recommendRanker.rank(items, channel)` 排序
- `refresh()` — 精选频道刷新后同样应用排序
- `loadMore()` — 精选频道上拉加载后合并全量列表重排（确保最匹配广告始终在前）
- 电商/本地频道不做个性化排序，保持热度排序

**排序生效路径**：
```
用户行为 → BehaviorCollector → Room user_behaviors
    ↓
UserProfileEngine.computeProfile() → tagWeights
    ↓
FeedViewModel.loadFirstPage(FEATURED)
    → repository.loadFirstPage() → items
    → recommendRanker.rank(items, FEATURED)
        → profileEngine.computeProfile()
        → 有画像: rankByProfileMatch (匹配度降序)
        → 无画像: sortedByDescending(exposureCount) (热度降序)
    → uiState.ads = ranked → LazyColumn 渲染
```

#### 2. SearchViewModel 添加 SEARCH 行为采集（SearchViewModel.kt）

**改动点**：
- 新增构造参数 `behaviorCollector: BehaviorCollector`
- `onSubmitSearch(query)` — 提交搜索时 `collectSearchBehavior(query)`
- `executeSearch(query)` — 热搜/联想点击搜索时同样采集
- 新增 `collectSearchBehavior(query: String)` 方法：
  - 将搜索查询按空格/中文逗号/英文逗号分词
  - 过滤空字符串和单字符
  - 构造 `UserBehavior(adId=null, type=SEARCH, tags=keywords)`
  - 委托 `BehaviorCollector.collect()` 异步写入 Room

#### 3. ChatViewModel 添加 SEARCH 行为采集（ChatViewModel.kt）

**改动点**：
- 新增构造参数 `behaviorCollector: BehaviorCollector`
- `sendMessage(content)` — 添加用户消息气泡后调用 `collectSearchBehavior(trimmed)`
- 新增 `collectSearchBehavior(query: String)` 方法（与 SearchViewModel 实现一致）
- 对话式搜索和常规搜索均记录为 SEARCH 行为，区别在于 tags 字段（自然语言 vs 关键词）

#### 4. ViewModelModule DI 配置更新（ViewModelModule.kt）

| ViewModel | 新增参数 | 说明 |
|-----------|---------|------|
| FeedViewModel | `recommendRanker = get()` | 精选频道个性化排序 |
| ChatViewModel | `behaviorCollector = get()` | 对话搜索行为采集 |
| SearchViewModel | `behaviorCollector = get()` | 常规搜索行为采集 |

### 6 种行为采集覆盖矩阵

| 行为类型 | 触发场景 | 采集位置 | Day 9 状态 | Day 10 状态 |
|---------|---------|---------|-----------|------------|
| CLICK | 点击广告卡片进入详情 | FeedViewModel.navigateToDetail | ✅ | ✅ |
| LIKE | 点击点赞按钮 | FeedViewModel / DetailViewModel | ✅ | ✅ |
| COLLECT | 点击收藏按钮 | FeedViewModel / DetailViewModel | ✅ | ✅ |
| SHARE | 触发分享行为 | FeedViewModel / DetailViewModel | ✅ | ✅ |
| TAG_CLICK | 点击卡片标签 Chip | FeedViewModel.filterByTag | ✅ | ✅ |
| SEARCH | 常规搜索 | SearchViewModel | ❌ | ✅ |
| SEARCH | 对话式搜索 | ChatViewModel | ❌ | ✅ |

（SEARCH 行为在 Day 9 已有 BehaviorType 枚举和采集器支持，但 SearchViewModel 和 ChatViewModel 未注入 BehaviorCollector）

### 架构设计要点

#### 个性化推荐端到端数据流

```
┌─────────────────────────────────────────────────────────────┐
│ 用户交互层                                                    │
│  FeedScreen ←→ FeedViewModel                                │
│      │ 点赞/收藏/分享/点击/标签/曝光                           │
│      ▼                                                       │
│  BehaviorCollector.collect(UserBehavior)                     │
│      │ 异步写入 Room (user_behaviors 表)                      │
│      ▼                                                       │
│  UserProfileEngine.computeProfile()                          │
│      │ 标签维度权重聚合                                        │
│      │ tagWeights: {"运动"→10, "学生党"→5, "性价比"→2}        │
│      ▼                                                       │
│  RecommendRanker.rank(ads, FEATURED)                         │
│      │ 有画像: Σ(tagWeights[ad.tag.name]) 降序                │
│      │ 无画像: exposureCount 降序                              │
│      ▼                                                       │
│  FeedViewModel.uiState.ads → LazyColumn                      │
│      │ 精选频道按个性化排序展示                                 │
│      └ 用户可感知偏好标签相关的广告排在前面                      │
└─────────────────────────────────────────────────────────────┘
```

#### 搜索行为 → 用户画像关联链路

```
常规搜索: SearchScreen → 输入 "运动鞋" → onSubmitSearch
    → BehaviorCollector.collect(SEARCH, tags=["运动鞋"])
    → Room user_behaviors

对话搜索: ChatScreen → 输入 "适合学生党的平价数码" → sendMessage
    → BehaviorCollector.collect(SEARCH, tags=["适合学生党的平价数码"])
    → Room user_behaviors

用户画像计算:
    → UserProfileEngine.computeProfile()
    → tagWeights: {"运动鞋"→+1, "适合学生党的平价数码"→+1, ...}
    → 虽然自然语言查询的完整文本作为 tag 效果有限，
       但搜索行为权重(×1)较低，不影响主要兴趣标签的聚合效果
```

### 涉及文件清单

| 操作 | 文件 | 变更说明 |
|------|------|---------|
| MODIFY | `feed/viewmodel/FeedViewModel.kt` | 注入 RecommendRanker + loadFirstPage/refresh/loadMore 应用排序 |
| MODIFY | `search/viewmodel/SearchViewModel.kt` | 注入 BehaviorCollector + 搜索行为采集 |
| MODIFY | `ai/chat/viewmodel/ChatViewModel.kt` | 注入 BehaviorCollector + 对话搜索行为采集 |
| MODIFY | `di/ViewModelModule.kt` | 更新 3 个 ViewModel 的 Koin 注入声明 |

### 关键设计决策

| 决策项 | 内容 | 理由 |
|--------|------|------|
| loadMore 时整体重排 vs 仅追加 | 合并全量列表后整体重排 | 新数据可能包含用户偏好标签匹配度更高的广告，仅追加会导致高匹配度广告被埋在后面 |
| 搜索 query 分词作为 tags | 按空格和标点拆分关键词作为行为标签 | SEARCH 权重(×1)较低，且搜索词本身反映用户当前兴趣，对画像有补充价值 |
| 个性化排序仅作用于精选频道 | 电商/本地频道保持热度排序 | 与 tech.md §10.3 设计一致——频道本身已是内容分类，精选频道才需要跨品类个性化 |
| 搜索行为 adId 为 null | 不关联特定广告 | 搜索是全局行为（与具体广告无关），关键词作为 tags 记录即可参与画像聚合 |

### 验证结果

- `./gradlew compileDebugKotlin` — **BUILD SUCCESSFUL** (8 actionable tasks)
- 所有 4 个文件修改编译通过，无新增错误

### 文档更新

- `struct.md`：更新日期为 Day 10
- `docs/daily-report.md`：Day 10 日报

---

## Day 11：性能优化与稳定性提升

### 完成内容

#### 1. LazyColumn contentType 优化（FeedScreen.kt）

- 在 `items(items = uiState.ads, key = { it.id })` 添加 `contentType = { it::class }`
- 按 `LargeImageAd::class`、`SmallImageAd::class`、`VideoAd::class` 分组布局复用
- Compose 可为同类型 item 复用布局节点，减少重组时的布局计算

#### 2. Compose 稳定性注解（AdItem.kt / Tag.kt / Channel.kt）

**@Stable 注解**：
- `AdItem` 密封类 + `LargeImageAd` / `SmallImageAd` / `VideoAd` 三个子类
- `AdType` / `Channel` / `ImagePosition` / `TagCategory` 四个枚举类

**@Immutable 注解**：
- `Tag` data class（所有字段为 val，不存在可变状态）

**设计原理**：
- `@Stable`/`@Immutable` 是 Compose runtime 的契约注解，告诉编译器"跳过不必要的相等性检查和重组"
- AdItem 虽有 `var` 字段（`isLiked`/`isCollected`），但互动状态通过 `mutableStateMapOf` 独立追踪（不依赖 AdItem.equals()），所以 `@Stable` 安全
- 效果：卡片 Composable 读取 AdItem 属性时不会因"对象不稳定"而触发父级重组

#### 3. 内存管理增强（AdsApplication.kt）

**onLowMemory() 重写**：
- 清空 Coil 内存缓存（`imageLoader.memoryCache?.clear()`）
- 释放 PlayerPool 中所有播放器（`pool.releaseAll()`）
- 两者均通过 Koin `GlobalContext.get().get()` 获取，异常静默

**onTrimMemory(level) 重写**：
| Memory Level | 释放策略 |
|-------------|---------|
| TRIM_MEMORY_UI_HIDDEN | 释放所有播放器 + 清空图片内存缓存 |
| TRIM_MEMORY_RUNNING_CRITICAL | 释放所有播放器 + 清空图片内存缓存 |
| TRIM_MEMORY_RUNNING_LOW | 仅清空图片内存缓存 |
| TRIM_MEMORY_BACKGROUND / MODERATE | 仅清空图片内存缓存 |

**@Suppress("DEPRECATION")**：API 36 中 TRIM_MEMORY_RUNNING_LOW/MODERATE 被标记弃用但功能仍正常。

#### 4. LeakCanary 集成

- `gradle/libs.versions.toml`：新增 `leakcanary = "2.14"` 版本声明
- `gradle/libs.versions.toml`：新增 `leakcanary-android` 依赖声明
- `app/build.gradle.kts`：`debugImplementation(libs.leakcanary.android)`
- LeakCanary 2.x 通过 ContentProvider 自动初始化，无需修改 Application

#### 5. 全局异常捕获 CrashHandler（新建）

**文件**：`common/util/CrashHandler.kt`

- 实现 `Thread.UncaughtExceptionHandler` 接口
- 捕获未处理异常后：
  1. 写入 logcat（`Log.e(TAG, ...)` + 完整堆栈）
  2. 委托给系统默认处理器（让进程正常终止）
- `AdsApplication.onCreate()` 中通过 `installCrashHandler()` 安装
- 防御设计：`installCrashHandler` 中 defaultHandler 为 null 时跳过

#### 6. ANR 检查：互动操作 Dispatchers.IO 包裹（FeedViewModel.kt）

将以下方法的 Repository 调用显式包裹在 `withContext(Dispatchers.IO)` 中：

| 方法 | 修复的调用 | 说明 |
|------|----------|------|
| `toggleLike()` | `repository.updateInteraction(adId, isLiked)` | Room 写入 + Mock 内存同步 |
| `toggleCollect()` | `repository.updateInteraction(adId, isCollected)` | 同上 |
| `share()` | `repository.updateInteraction(adId, incrementShare)` | 同上 |
| `navigateToDetail()` | `repository.incrementClick(ad.id)` | Room 写入 |

此前这些操作在 `viewModelScope.launch {}` 中运行（继承父协程上下文，可能为 Main），
现在确保磁盘 I/O 在 `Dispatchers.IO` 执行。

#### 7. ProGuard/R8 混淆规则（proguard-rules.pro 完整重写）

从空模板重写为覆盖 8 大类依赖的完整 keep 规则：

| 类别 | 规则内容 |
|------|---------|
| **通用** | `SourceFile` / `LineNumberTable` / `Signature` / `Annotations` / `Exceptions` |
| **Kotlin** | `kotlin.Metadata` 伴生对象保留 |
| **Kotlinx Serialization** | `$$serializer` 类 + `serializer()` 方法 + `@Serializable` 注解类 |
| **Retrofit** | API 接口保留（动态代理调用）+ `Continuation` 保留 |
| **OkHttp** | okhttp3 / okio 包保留 |
| **Coil 3.x** | coil3 包保留 |
| **Room** | Entity / DAO / `AppDatabase_Impl` 保留 |
| **Koin** | DI 模块声明 + ViewModel 子类保留（反射创建） |
| **ExoPlayer/Media3** | `androidx.media3` 包保留（反射加载渲染器） |
| **Coroutines** | `MainDispatcherFactory` / `CoroutineExceptionHandler` / volatile fields |
| **Compose** | `androidx.compose.*` 全保留 + Material3 保留 |
| **Navigation** | `androidx.navigation.*` 保留 |
| **Lifecycle** | `androidx.lifecycle.*` 保留 |
| **其他** | `BuildConfig` / `R$` class members / `Parcelable.CREATOR` / 枚举 `values()`/`valueOf()` |

#### 8. NetworkConfig 连接池配置应用（NetworkConfig.kt）

- 在 `createOkHttpClient()` 的 `OkHttpClient.Builder` 中添加 `connectionPool(...)`
- 参数：`maxIdleConnections=5`（30 秒 keep-alive）
- 此前 `MAX_IDLE_CONNECTIONS` / `KEEP_ALIVE_DURATION_SECONDS` 已定义但未应用（死代码）

### 架构设计要点

#### Compose 重组优化链路

```
Compose 编译器分析
  ├─ @Stable/Immutable 注解：跳过不必要的重组检查
  │   └─ AdItem + Tag + AdType + Channel + ImagePosition + TagCategory
  ├─ contentType：按类型分组布局节点
  │   └─ LargeImageAd / SmallImageAd / VideoAd 各自独立的布局缓存
  └─ mutableStateMapOf：精确重组
      └─ likedAdIds / collectedAdIds / aiContentMap 按 adId 独立粒度
```

#### 内存管理响应链

```
系统低内存事件
  ├─ onLowMemory()
  │   ├─ Coil memoryCache.clear()
  │   └─ PlayerPool.releaseAll()
  │
  └─ onTrimMemory(level)
      ├─ TRIM_MEMORY_UI_HIDDEN/CRITICAL → 全部释放
      ├─ TRIM_MEMORY_RUNNING_LOW → 仅清图片缓存
      └─ TRIM_MEMORY_BACKGROUND/MODERATE → 仅清图片缓存
```

#### ANR 防护检查清单

| 操作类别 | 是否在 Dispatchers.IO | 位置 |
|---------|---------------------|------|
| 广告数据加载 | ✅ `withContext(IO)` | FeedViewModel (loadFirstPage/refresh/loadMore/filterByTag) |
| 互动状态更新 | ✅ `withContext(IO)` | FeedViewModel (toggleLike/toggleCollect/share) — Day 11 修复 |
| 点击计数递增 | ✅ `withContext(IO)` | FeedViewModel (navigateToDetail) — Day 11 修复 |
| 曝光计数递增 | ❌ `launch {}`（Repository 内部无 IO 操作，仅内存递增）| FeedViewModel (onAdExposed) |
| AI 内容生成 | ✅ 内部 `AiContentGenerator` 在 IO 线程调度 | FeedViewModel (generateAiContent) |
| 行为采集 | ✅ `BehaviorCollector` 内部 IO 线程 | FeedViewModel/DetailViewModel/ChatViewModel |

### 涉及文件清单

| 操作 | 文件 | 变更说明 |
|------|------|---------|
| MODIFY | `feed/ui/FeedScreen.kt` | 添加 `contentType = { it::class }` |
| MODIFY | `data/model/AdItem.kt` | 添加 `@Stable` 注解（sealed class + 3 子类） + import |
| MODIFY | `data/model/Tag.kt` | 添加 `@Immutable`（Tag）+ `@Stable`（TagCategory）+ import |
| MODIFY | `data/model/Channel.kt` | 添加 `@Stable`（AdType/Channel/ImagePosition）+ import |
| MODIFY | `AdsApplication.kt` | 新增 onLowMemory/onTrimMemory + installCrashHandler + import |
| CREATE | `common/util/CrashHandler.kt` | 全局未处理异常捕获器 |
| MODIFY | `feed/viewmodel/FeedViewModel.kt` | 4 个互动方法包裹 `withContext(Dispatchers.IO)` |
| MODIFY | `common/network/NetworkConfig.kt` | OkHttpClient.Builder 添加 connectionPool + import ConnectionPool |
| MODIFY | `app/proguard-rules.pro` | 从空模板重写为完整混淆规则（180+ 行） |
| MODIFY | `gradle/libs.versions.toml` | 新增 `leakcanary = "2.14"` + 依赖声明 |
| MODIFY | `app/build.gradle.kts` | 新增 `debugImplementation(libs.leakcanary.android)` |

### 验证结果

- `./gradlew assembleDebug` — **BUILD SUCCESSFUL** (39 tasks, 首次 2m19s, 后续缓存)
- 修复后无编译错误，仅 `TRIM_MEMORY_RUNNING_LOW` / `TRIM_MEMORY_MODERATE` 弃用警告（已加 @Suppress）

### 文档更新

- `struct.md`：更新日期 + 新增 CrashHandler.kt
- `docs/daily-report.md`：Day 11 日报

---

