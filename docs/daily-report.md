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
