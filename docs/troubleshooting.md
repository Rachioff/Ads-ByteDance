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
