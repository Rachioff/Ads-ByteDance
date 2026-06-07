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

