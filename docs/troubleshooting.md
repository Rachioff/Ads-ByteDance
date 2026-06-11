# 问题解决记录

> **说明**：本目录用于记录项目开发过程中遇到的所有技术问题、根因分析及解决方案。

---

## 记录 #1：Gradle Sync 构建环境三连错

> **日期**：2026-06-03 (Day 1)
> **阶段**：基础设施搭建 — Gradle 依赖声明与插件配置
> **影响范围**：Gradle 构建脚本

---

### 问题 1：KSP 插件版本不存在

**错误信息**：
```
Plugin [id: 'com.google.devtools.ksp', version: '2.2.10-1.0.29', apply: false]
was not found in any of the following sources
```

**根因**：

KSP (Kotlin Symbol Processing) 的版本号格式已经从旧格式变为新格式：

| 时期 | 版本号格式 | 示例 |
|------|-----------|------|
| **KSP1（已废弃）** | `<Kotlin版本>-<KSP子版本>` | `2.1.0-1.0.29` |
| **KSP2（当前）** | 独立版本号 `X.Y.Z` | `2.3.6` |

错误的版本号 `2.2.10-1.0.29` 是按旧格式猜的，但 Kotlin 2.2.10 对应的旧格式 KSP 版本根本不存在。

KSP 从 **v2.3.0** 开始与 Kotlin 编译器版本**完全解耦**，使用独立版本号。现在的 KSP 2.x 支持广泛的 Kotlin 编译器版本，不再需要按 Kotlin 版本去找对应的 KSP 版本。

**解决方法**：

`gradle/libs.versions.toml` 中：
```diff
- ksp = "2.2.10-1.0.29"
+ ksp = "2.3.6"
```

**参考资料**：[KSP GitHub Releases](https://github.com/google/ksp/releases)

---

### 问题 2：kotlin-android 和 kotlin-compose 插件冲突

**错误信息**：
```
Cannot add extension with name 'kotlin',
as there is an extension already registered with that name.
```

**根因**：

`kotlin-compose` 插件**内部已经包含了 `kotlin-android`**。当 `app/build.gradle.kts` 中同时显式声明两者：

```kotlin
plugins {
    alias(libs.plugins.kotlin.android)   // 第 1 次注册 "kotlin" DSL 扩展
    alias(libs.plugins.kotlin.compose)   // 第 2 次注册 → 冲突！
}
```

`kotlin-android` 先注册了 `kotlin {}` Gradle DSL 扩展，紧接着 `kotlin-compose` 在内部再次应用 `kotlin-android` 时试图注册同名扩展，Gradle 的 `ExtensionsStorage` 拒绝创建重复名称的扩展。

**解决方法**：

从 `app/build.gradle.kts` 中移除显式的 `kotlin-android`，只保留 `kotlin-compose`（它会传递应用 kotlin-android）：

```diff
plugins {
    alias(libs.plugins.android.application)
-   alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)  // kotlin-compose 内置 kotlin-android
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.ksp)
}
```

> **注意**：根 `build.gradle.kts` 中的 `kotlin-android apply false` 不受影响——它只是将插件加入 classpath 供子模块使用，并不应用。

---

### 问题 3：kotlinOptions 访问器不可用

**错误信息**：
```
Unresolved reference 'kotlinOptions'.
```

**根因**：

Gradle Kotlin DSL 的**类型安全访问器**（如 `android {}`、`kotlin {}`、`kotlinOptions {}`）是由 Gradle 在 `.gradle/kotlin-dsl/` 目录下**编译时生成**的，生成依据是 `plugins {}` 块中**显式声明**的插件。

`kotlinOptions` 访问器由 `kotlin-android` 插件生成。虽然 `kotlin-compose` 内部**应用**了 `kotlin-android`，但 `kotlin-android` 不在 `plugins {}` 块中显式声明 → 访问器不生成 → `Unresolved reference`。

这是一个经典陷阱：**插件传递应用 ≠ DSL 访问器生成**。

**解决方法**：

用新版 Kotlin Gradle API（顶层 `kotlin {}` 块）替代 `android { kotlinOptions {} }`：

```diff
- android {
-     kotlinOptions {
-         jvmTarget = "11"
-     }
- }

+ kotlin {
+     compilerOptions {
+         jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
+     }
+ }
```

顶层 `kotlin {}` DSL 块由 `kotlin-compose` 插件直接提供，不依赖隐式的 `kotlin-android` 访问器生成。

---

### 总结：Gradle 插件声明黄金法则

| 法则 | 说明 |
|------|------|
| **1. 不重复声明传递插件** | 如果插件 A 内部包含插件 B，不要在 `plugins {}` 中同时声明两者 |
| **2. DSL 访问器只看显式声明** | `apply false` 的插件不生成访问器；传递应用的插件也不生成；只有 `plugins {}` 中显式 `id("xxx")` 的才生成 |
| **3. 版本号不要"猜"** | 特别是 KSP 这种版本格式发生过变化的工具，先查官方文档或 GitHub Releases |
| **4. 顶层 kotlin {} 优于 android { kotlinOptions {} }** | 新版 Kotlin Gradle Plugin 推荐的 JVM 目标配置方式 |

---

### 涉及的提交（如有）

- `a3360da`（假设）：基础 Gradle 配置提交

---

## 记录 #2：点赞/收藏即时响应失效（LazyColumn 重组问题）

> **日期**：2026-06-05 (Day 3)
> **阶段**：信息流 UI 实现 — 互动按钮状态同步
> **影响范围**：FeedViewModel / FeedScreen / CardInteractions / AdItem

---

### 问题描述

点击点赞（❤️）或收藏（⭐）按钮后，只有 Material ripple 按压反馈，图标不变色、计数不变化，需要将卡片滑出屏幕再滑回才会更新。

### 排查过程

#### 阶段 1：怀疑 data class body 属性不参与 copy/equals

**假设**：`isLiked`/`isCollected` 声明在 data class body 中，不参与 `copy()` 和 `equals()` 生成，导致 Compose diff 认为数据未变化。

**操作**：将 3 个 AdItem 子类的 `isLiked`/`isCollected` 从 class body 移到 constructor 参数中（保留 `@Transient`）。

**结果**：❌ 问题依旧。

#### 阶段 2：怀疑 StateFlow → collectAsState 链路不可靠

**假设**：`MutableStateFlow.update()` → `collectAsStateWithLifecycle()` → `LazyColumn.items()` 链路中，LazyColumn 对"整个列表引用变化但 key 未变"的情况跳过了 content lambda 重执行。

**操作**：将 `MutableStateFlow` 替换为 `mutableStateOf`（Compose snapshot 系统直接驱动），在 Composable 中直接读取 `viewModel.uiState`。

**结果**：❌ 问题依旧。

#### 阶段 3：根因定位 ✅

**根因**：LazyColumn 的 `items(key)` 机制在 key 匹配到已有 item 时**不重新执行 content lambda**，因此 content lambda 中无法建立对新状态的 snapshot 读依赖。写入 `mutableStateOf` 通知了外层 `FeedScreen` 重组，但 LazyColumn 内部的 item composable 没有重新执行，读不到新值。

问题的本质不是"状态存储类型"（StateFlow vs mutableStateOf），而是 **"状态在哪个 Composable scope 中被读取"**。只要读操作发生在 LazyColumn item 的 content lambda 外部，item 就没有机会感知变化。

### 解决方案

使用 **`mutableStateMapOf`** 按 adId 独立追踪点赞/收藏状态，**在 LazyColumn `items {}` content lambda 内部**读取 `map[adId]`：

```kotlin
// FeedViewModel
val likedAdIds = mutableStateMapOf<String, Boolean>()
val collectedAdIds = mutableStateMapOf<String, Boolean>()

private fun toggleLike(adId: String) {
    val current = likedAdIds[adId] ?: false
    likedAdIds[adId] = !current   // 只触发这一个 item 的重组
    repository.updateInteraction(adId, isLiked = !current)
}

// FeedScreen — items {} content lambda 内部读取
items(items = uiState.ads, key = { it.id }) { ad ->
    val liked = viewModel.likedAdIds[ad.id] ?: ad.isLiked     // snapshot read
    val collected = viewModel.collectedAdIds[ad.id] ?: ad.isCollected
    LargeImageCard(ad = ad.copy(isLiked = liked, isCollected = collected), ...)
}
```

### 原理

```
toggleLike("feat_001")
    → likedAdIds["feat_001"] = true    // mutableStateMapOf key 级写入
    → Compose snapshot 系统标记读取了 key "feat_001" 的 content lambda 为 invalid
    → 只有 item "feat_001" 的 content lambda 重组
    → 其他 item 完全不受影响
```

`mutableStateMapOf` 提供 key 级粒度——每个 LazyColumn item 的 snapshot 依赖精确到单个 adId，写入只重组那一个 item。与 `mutableStateOf(全列表)` 的"列表引用变化 → LazyColumn key diff → 跳过已有 item"是不同的路径。

### 连带修复

在此过程中还发现并修复了 3 个影响交互体验的问题：

| 问题 | 根因 | 修复 |
|------|------|------|
| 点赞/收藏点击零视觉反馈 | `InteractionButton` 设置了 `indication = null`，Material ripple 被禁用 | 移除 `indication = null`，恢复默认 ripple |
| 按钮触摸区域太小 | 22dp 图标 + 无 padding，手指易点偏，事件被外层 `onCardClick` 吞掉 | 添加 `padding(horizontal=6dp, vertical=4dp)` 扩大触摸目标 |
| 标签 Chip 看不出可点击 | `TagChip` 的 `clickable` 同样设置了 `indication = null` | 移除 `indication = null`，恢复默认 ripple |

### 学到的内容

- `LazyColumn.items(key)` 在 key 匹配时不重新执行 content lambda——状态必须在 lambda **内部**建立 snapshot 读
- `mutableStateMapOf` 的 key 级粒度是实现"精确重组一个 LazyColumn item"的最佳方式
- `mutableStateOf(整个列表)` 对 LazyColumn item 重组不可靠——问题不在 state 类型，在读取 scope
- Material 无障碍建议按钮触摸目标 ≥ 48dp

---

## 记录 #3：互动计数不联动 + AdDataSource 接口不完整（架构分层违规）

> **日期**：2026-06-08 (Day 5)
> **阶段**：详情页模块 — 互动状态同步架构修复
> **影响范围**：AdItem / AdRepository / AdDataSource / MockJsonDataSource / RemoteDataSource / AdApiService / FeedViewModel / DetailViewModel

---

### 问题 1：点赞/收藏后计数不变化

**错误现象**：

点击点赞（❤️）后图标变色正常，但 `likeCount` 数字不变。收藏同理。

**根因**：

`AdRepository.updateInteraction()` 只切换了 `isLiked` / `isCollected` 布尔标记，没有联动更新 `likeCount` / `collectCount`。相当于服务端只执行了：
```sql
UPDATE ads SET is_liked = true WHERE id = ?
```
忘记了：
```sql
UPDATE ads SET like_count = like_count + 1 WHERE id = ?
```

同时 `likeCount` / `collectCount` 在 `AdItem` 中声明为 `abstract val`（不可变），即使想更新也无法赋值。

**解决方法**：

1. `AdItem.kt`：基类+三个子类的 `likeCount` / `collectCount` 从 `abstract val` → `abstract var`
2. `MockJsonDataSource.kt`：实现 `updateInteraction()`，遍历 `channelCache` → 找到 ad → 修改 `isLiked`/`isCollected` + 联动 `likeCount+1`/`collectCount+1`（取消时 `-1`，`coerceAtLeast(0)` 防止负数）

---

### 问题 2：RemoteDataSource.updateInteraction() 是异常桩（架构违规）

**错误现象**：

`RemoteDataSource.updateInteraction()` 实现了 `throw UnsupportedOperationException`。虽然当前使用 Mock 模式不受影响，但如果执行 `gradle.properties → DATA_MODE=remote` 切换后点击点赞，程序会直接崩溃——违背了"改配置即可切换数据源"的 DI 设计目标。

**根因**：

旧版 `AdRepository.updateInteraction()` 是同步 `fun`（非 `suspend`），逻辑直接写在 Repository 层——遍历 `channelItems` Map、直接修改 `ad.isLiked`。**`AdDataSource` 接口里根本没有这个方法**，Mock/Remote 切换对它无效。

```
旧架构（Day 4 及之前）：
  ViewModel → AdRepository.updateInteraction()
                ├─ 直接遍历 channelItems（Repository 内部 Map）
                └─ AdDataSource ← 完全被绕过！接口里无此方法！
```

**解决方法**（四阶段重构）：

| 阶段 | 层 | 变更 |
|------|------|------|
| ① 模型层 | `AdItem.kt` | `likeCount`/`collectCount`: `val` → `var` |
| ② 接口层 | `AdDataSource.kt` | 新增 `suspend fun updateInteraction()` 签名 |
| ② 实现层 | `MockJsonDataSource.kt` | 遍历 channelCache → 修改内存 → 返回 AdItem |
| ③ API 层 | `AdApiService.kt` | 新增 `@POST api/v1/ads/{adId}/interaction` + `InteractionRequest` |
| ③ API 层 | `RemoteDataSource.kt` | 从 `throw UnsupportedOperationException` → `apiService.updateInteraction(...)` 真正的 HTTP 调用 |
| ④ 仓库层 | `AdRepository.kt` | 从同步 `fun` → `suspend fun`，委托 `dataSource.updateInteraction()` + `onSuccess` 同步 channelItems |
| ④ ViewModel层 | `FeedViewModel.kt` / `DetailViewModel.kt` | 包一层 `viewModelScope.launch {}`（乐观更新） |

**重构后架构**：
```
ViewModel ─→ AdRepository.updateInteraction()     ← suspend fun，业务层不变
               │
               ├─① dataSource.updateInteraction()  ← Mock: 内存更新 | Remote: HTTP POST
               │
               └─② onSuccess → 同步 channelItems  ← 跨页面缓存一致性
```

**DI 透明性验证**：
```
gradle.properties → DATA_MODE=mock
  → Koin 注入 MockJsonDataSource
  → updateInteraction() → 遍历 channelCache → 修改内存 → 返回 ✅

gradle.properties → DATA_MODE=remote
  → Koin 注入 RemoteDataSource
  → updateInteraction() → POST /api/v1/ads/{adId}/interaction
  → 有后端：返回 AdItem ✅
  → 无后端：OkHttp IOException → Result.failure（非崩溃）✅
```

### 设计原则提炼

1. **Repository 不应持有业务逻辑**。它只是数据源的调度者——委托 DataSource 获取/修改数据，同步本地缓存。计数的增减逻辑应归属 DataSource 层（Mock 模拟服务端 / Remote 调用服务端）。

2. **接口完整性 = DI 透明性的前提**。只要有一个方法在接口中缺失，Koin 的多态切换就对它无效。它会被 Repo 层直接硬编码实现，未来模式切换时必然出问题。

3. **乐观更新 vs 服务端权威**。当 `isLiked`/`isCollected` 标记为 `@Transient` 时，它们只存在于运行时内存。未来接入真实服务端后，应从服务端拉取最新互动状态初始化这些字段，而非从本地 JSON 反序列化。当前"UI snapshot 立即更新 + 后台异步请求"的乐观更新模式是标准的移动端实践。

### 文档更新

- `docs/troubleshooting.md`：本文

---

## 记录 #4：详情页互动栏数字溢出 + 分享计数全链路缺失 + 分享计数延迟更新

> **日期**：2026-06-08 (Day 6)
> **阶段**：详情页 UI 优化 + 互动系统架构补全
> **影响范围**：DetailInteractions / AdItem / AdDataSource / MockJsonDataSource / RemoteDataSource / AdApiService / AdRepository / FeedViewModel / DetailViewModel / DetailUiState / FeedUiState

---

### 问题 1：底部互动栏计数数字超出屏幕

**错误现象**：

详情页底部点赞、收藏、分享的数字在小屏或大数字时溢出屏幕右侧。

**根因**：

三个按钮使用 `Box(contentAlignment = Alignment.Center)` 包裹图标（28dp），计数文本通过 `Modifier.align(Alignment.BottomCenter).offset(y = 22.dp)` 硬编码偏移放置。

`Box` 的测量尺寸仅由图标决定，`offset` 偏移的文本**不参与 Box 的尺寸计算**。Row 的 `Arrangement.SpaceEvenly` 按 3 个 ~28dp 的 Box 分配空间，完全忽略了文本宽度。当数字较大时（如 "1234"、"999+"），文本超出 Box 边界。

```
修改前（offset 不参与测量）：
┌───────────── Row(Arrangement.SpaceEvenly) ─────────────┐
│  ╔══28dp══╗     ╔══28dp══╗     ╔══28dp══╗            │
│  ║  ❤️    ║     ║  🔖   ║     ║  📤   ║            │
│  ╚════════╝     ╚════════╝     ╚════════╝            │
│     1234 (offset, 不占空间 → 溢出)                       │
└────────────────────────────────────────────────────────┘
```

**解决方法**：

将 `Box` + 硬编码 `offset` 改为 `Column(verticalArrangement)` 垂直堆叠布局，让文本参与布局测量：

| 组件 | 修改前 | 修改后 |
|------|--------|--------|
| LikeButtonWithBurst | Box(center) + offset(y=22) | Box(粒子动画叠加) → Column(图标 + Spacer + 计数) |
| CollectButtonEnhanced | Box(center) + offset(y=22) | Column(图标 + Spacer + 计数) |
| ShareButtonEnhanced | Box(center) + offset(y=22) | Column(图标 + Spacer + 计数) |

```
修改后（Column 自然测量）：
┌────────────── Row(Arrangement.SpaceEvenly) ──────────────┐
│  ┌──────────┐   ┌──────────┐   ┌──────────┐             │
│  │   ❤️     │   │   🔖    │   │   📤    │             │
│  │ 1.2w+    │   │   856    │   │   342    │             │
│  └──────────┘   └──────────┘   └──────────┘             │
│    (自适应宽)     (自适应宽)     (自适应宽)                │
└──────────────────────────────────────────────────────────┘
```

- 点赞按钮保留外层 Box（粒子动画 HeartBurstParticles 需要定位在图标中心），内层改为 Column
- 引入 `formatCount()`：≥10000 → "Xw+"，≥1000 → "Xk+"（与 CardInteractions 保持一致），进一步控制长数字宽度
- 文本添加 `maxLines = 1` 防止意外换行

**改动文件**：`detail/ui/DetailInteractions.kt`

---

### 问题 2：点击分享按钮后计数不变（全链路缺失）

**错误现象**：

在详情页或信息流中点击分享按钮，系统分享面板正常弹出，但 `shareCount` 数字不变。

**根因**：

分享计数更新功能**从未被实现过**——从数据模型到 ViewModel 整条链路都没有分享计数的支持。

| 层级 | 点赞/收藏 | 分享（修改前） |
|------|----------|--------------|
| `AdItem.shareCount` | `likeCount`/`collectCount` 为 `var` | `val`（不可变，无法在运行时修改） |
| `AdDataSource.updateInteraction()` | 接受 `isLiked`, `isCollected` | **无 share 参数** |
| `MockJsonDataSource` | 计数联动 +1/-1 | 不处理 |
| `AdRepository` | 透传 isLiked/isCollected | 不透传 |
| `DetailViewModel.share()` | — | **只 emit(ShowShareSheet)，不调 repository** |
| `FeedViewModel.share()` | — | 同上 |

对比 ViewModel 层：

```kotlin
// ✅ 点赞 — 有计数联动
toggleLike(adId) {
    likedAdIds[adId] = newLiked
    repository.updateInteraction(adId, isLiked = newLiked)  // likeCount +1
}

// ❌ 分享 — 无计数联动（修改前）
share(ad) {
    emit(ShowShareSheet)  // 只弹面板，什么都没改
}
```

**解决方法**（7 个文件链式改动）：

| # | 文件 | 改动 |
|---|------|------|
| 1 | `data/model/AdItem.kt` | `shareCount`: `val` → `var`（基类 + 3 子类） |
| 2 | `data/local/AdDataSource.kt` | `updateInteraction()` 新增 `incrementShare: Boolean = false` |
| 3 | `data/local/MockJsonDataSource.kt` | `if (incrementShare) ad.shareCount += 1` |
| 4 | `data/remote/RemoteDataSource.kt` | 透传 `incrementShare` 到 API |
| 5 | `data/remote/AdApiService.kt` | `InteractionRequest` 新增 `incrementShare` 字段 |
| 6 | `data/repository/AdRepository.kt` | 新增参数 + 透传 + 同步本地缓存 |
| 7 | `detail/viewmodel/DetailViewModel.kt` | `share()` → `repository.updateInteraction(ad.id, incrementShare = true)` |
| 7 | `feed/viewmodel/FeedViewModel.kt` | 同上 |

**参数命名考量**：使用 `incrementShare: Boolean` 而非 `isShared: Boolean`——分享无取消操作，语义明确"累加"。

---

### 问题 3：详情页分享后计数不即时更新（Compose 重组缺失）

**错误现象**：

点击分享按钮后，`shareCount` 在数据层已经 +1（问题 2 修复后确认），但 UI 上数字不变化。只有当用户再进行点赞/收藏操作，或者退出详情页重新进入后，数字才更新。

**根因**：

Compose 没有检测到需要重组的状态变化。

`DetailViewModel` 的 `uiState` 是 `mutableStateOf(DetailUiState(...))`，Compose 的 snapshot 系统使用 `structuralEqualityPolicy`（默认），只有当新值与旧值在结构上**不相等**时才触发重组。

问题 2 修复后，`share()` 方法调用了 `repository.updateInteraction(ad.id, incrementShare = true)`，数据层在内存中的 AdItem 对象上执行了 `shareCount += 1`（原地修改）。但：

1. `MockJsonDataSource.updateInteraction()` 返回的是**同一个对象引用**（`return@runCatching ad`）
2. `DetailViewModel.share()` 没有更新 `uiState` — 没有任何 `mutableStateOf` 被写入
3. `likedAdIds` / `collectedAdIds` 也没有变化（分享不涉及它们）
4. → Compose 认为所有 snapshot state 未变化 → 不重组 → UI 显示旧值

**为什么点赞/收藏能即时更新？**

点赞/收藏触发了 `likedAdIds[adId] = newLiked`（`mutableStateMapOf` 写入），这会触发 Compose 重组。重组时 `DetailScreen` 重新读取 `uiState.ad!!.likeCount`，由于 `ad` 是共享引用且已被原地修改，读到的是新值。分享没有类似的 state map 写入来"搭便车"。

**为什么退出重进后能看到？**

重新进入详情页时，`loadAd()` → `repository.getAdById()` → 从 channelCache 中取出已被修改的 AdItem（shareCount 已经是新值） → `uiState = uiState.copy(ad = newAd)`（注意这里是新的 `DetailUiState` 实例，触发重组）→ UI 正确显示。

**解决方法**（引入 `interactionVersion` 版本计数器）：

**方案选型**：

| 方案 | 可行性 | 问题 |
|------|--------|------|
| `uiState.copy(ad = updatedAd)` | ❌ | 返回的是同一对象引用，`DetailUiState.equals()` 所有字段相等 → Compose 跳过重组 |
| `repository.getAdById()` 重新获取 | ❌ | 返回的仍是同一引用，同上 |
| `mutableStateOf(policy = neverEqualPolicy())` | ⚠️ | 会导致所有 uiState 赋值都触发重组，包括非互动场景 |
| **`interactionVersion` 计数器** | ✅ | 最小侵入，精确控制，语义清晰 |

最终采用方案：在 `DetailUiState` 和 `FeedUiState` 中新增 `interactionVersion: Int = 0` 字段。每次互动操作（点赞/收藏/分享）后，`interactionVersion + 1` + `uiState.copy(ad = updatedAd, interactionVersion = newVersion)`。由于 `interactionVersion` 字段值不同，新旧 `DetailUiState` 结构不相等 → Compose 触发重组。

```kotlin
// DetailViewModel.share() — 修改后
private fun share(ad: AdItem) {
    viewModelScope.launch {
        val result = repository.updateInteraction(ad.id, incrementShare = true)
        result.onSuccess { updatedAd ->
            // interactionVersion +1 → DetailUiState 结构不相等 → Compose 重组
            uiState = uiState.copy(
                ad = updatedAd,
                interactionVersion = uiState.interactionVersion + 1
            )
        }
        _events.emit(DetailOneTimeEvent.ShowShareSheet(shareText))
    }
}
```

**加固：对点赞/收藏方法同步应用**

虽然点赞/收藏当前能工作（依赖 `likedAdIds` 写入的副作用），但架构上不健壮。一并修改 `toggleLike()` / `toggleCollect()` 使用 `interactionVersion` 机制：

```kotlin
private fun toggleLike(adId: String) {
    likedAdIds[adId] = newLiked
    viewModelScope.launch {
        val result = repository.updateInteraction(adId, isLiked = newLiked)
        result.onSuccess { updatedAd ->
            uiState = uiState.copy(ad = updatedAd, interactionVersion = uiState.interactionVersion + 1)
        }
    }
}
```

**FeedViewModel 同样处理**：

`FeedViewModel` 的 `uiState.ads` 是 `List<AdItem>`，通过 `replaceAdInList()` 工具函数替换列表中对应 item：

```kotlin
private fun replaceAdInList(ads: List<AdItem>, updatedAd: AdItem): List<AdItem> {
    val index = ads.indexOfFirst { it.id == updatedAd.id }
    if (index < 0) return ads
    return ads.toMutableList().also { it[index] = updatedAd }
}
```

**完整数据流**（修改后）：

```
用户点击分享
  → ViewModel.share()
    → repository.updateInteraction(adId, incrementShare = true)
      → MockJsonDataSource: ad.shareCount += 1（原地修改）→ 返回同一引用
    → result.onSuccess { updatedAd →
        uiState = uiState.copy(ad = updatedAd, interactionVersion = version + 1)
      }
        → interactionVersion 从 0 → 1
        → DetailUiState(ad=..., interactionVersion=1) ≠ DetailUiState(ad=..., interactionVersion=0)
        → Compose 检测到 uiState 变化 → 重组 DetailScreen
          → 重新读取 uiState.ad!!.shareCount → 新值 ✅
```

**改动文件**：`detail/model/DetailUiState.kt`、`feed/model/FeedUiState.kt`、`detail/viewmodel/DetailViewModel.kt`、`feed/viewmodel/FeedViewModel.kt`

---

### 设计原则提炼

1. **Compose `mutableStateOf` 的 `structuralEqualityPolicy` 是默认行为**。原地修改对象属性后再赋值给同一个 `mutableStateOf` 不会触发重组——因为新旧对象仍是同一引用、结构完全相等。需要确保每次写入都有结构差异。

2. **`interactionVersion` 模式**：当数据层返回"原地修改后的同一引用"时，无法通过对象引用变化驱动 UI 更新。版本计数器是解决这类问题的最小侵入方式。

3. **不要依赖"搭便车"重组**。点赞/收藏能即时更新是因为 `mutableStateMapOf` 触发了重组，重组时顺便读到了新计数。这种隐式依赖脆弱且不可维护——分享没有类似的 map 写入，立刻暴露问题。每个数据变化都应该有自己的重组触发路径。

4. **`offset` 不参与布局测量**。Compose 中 `Modifier.offset()` 是"视觉偏移"——元素在视觉上移动了位置，但在父容器的布局算法中，它仍然占据原始位置。需要"真实"布局空间变化时，应使用 `padding`、`Spacer`、或改为 `Column`/`Row` 结构调整。

### 文档更新

- `docs/troubleshooting.md`：本文

