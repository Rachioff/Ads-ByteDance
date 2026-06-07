# 开发日报

> **项目**: AI广告推荐信息流 (Ads-ByteDance)
> **作者**: zsy

---

## Day 1 — 2026-06-03（周三）：工程基础设施搭建

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

## Day 2 — 2026-06-04（周四）：数据层完整实现

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

## Day 3 — 2026-06-05（周五）：信息流 UI 层完整实现

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
