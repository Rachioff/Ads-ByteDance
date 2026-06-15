# 自我介绍

> **项目**: AI广告推荐信息流 (Ads-ByteDance)
> **作者**: 赵诗阳
> **日期**: 2026-06-15

---

## 基本信息

| 项目 | 内容       |
|------|----------|
| **姓名** | 赵诗阳      |
| **学校** | 北京航空航天大学 |
| **专业** | 软件工程     |
| **年级** | 大三       |

---

## 作业技术栈

**选择方向：Android（Kotlin）**

选择 Android 方向的原因：
- 已有 Java/Kotlin 编程基础，可快速上手 Android 开发
- Kotlin 语言现代化特性（协程、Flow、扩展函数、密封类等）适合构建复杂的异步交互场景
- Android Jetpack 生态成熟（Compose、Room、Navigation、ViewModel 等），可专注于业务逻辑实现
- 个人参赛，Android 生态可独立完成从 UI 到后端交互的全链路开发

---

## 入营前技术基础

### 已有基础

- **编程语言**：Java、Kotlin
- **Android 相关**：
  - 了解 Android 四大组件（Activity、Service、BroadcastReceiver、ContentProvider）
  - 了解基本 UI 开发（传统 View 体系）
  - 了解 Gradle 构建系统

---

## AI 工具使用

### 主要工具：Claude Code

使用 Claude Code 作为 AI 编程助手，贯穿整个开发周期：

| 使用场景 | 具体应用 |
|---------|---------|
| **架构设计** | MVVM 架构模式选择、模块划分、数据流设计、方案对比 |
| **代码实现** | 数据模型定义、ViewModel/Repository 编写、Composable UI、DI 配置 |
| **问题排查** | 编译错误诊断、LazyColumn 重组问题、ExoPlayer 黑屏根因分析、Z 轴点击拦截 |
| **技术选型** | Coil vs Glide、Kotlinx Serialization vs Gson、ExoPlayer vs MediaPlayer |
| **文档编写** | 需求文档、技术文档、项目结构文档、开发日报、问题记录 |
| **性能优化** | Compose 稳定性注解、内存管理、ANR 防护、混淆规则编写 |

### 使用心得

1. **方案设计阶段**：通过 AI 进行多方案对比分析，明确各方案优劣后做出技术决策
2. **编码阶段**：AI 辅助编写样板代码（如 Room DAO、Retrofit 接口、Koin 模块），聚焦业务逻辑
3. **Debug 阶段**：AI 辅助分析异常堆栈、推理根因、提供修复方案
4. **文档阶段**：AI 帮助整理技术文档、生成架构图/数据流描述、维护开发日报

### 关键 AI Coding 决策记录

| 日期         | 决策项 | AI 参与方式 |
|------------|--------|------------|
| 2026-06-03 | UI 框架选型：Jetpack Compose 替代 XML ViewBinding | AI 提供 Compose vs ViewBinding 多维对比分析，最终说服采用 Compose |
| 2026-06-05 | LazyColumn 精确重组方案：mutableStateMapOf | AI 经过 3 轮排查定位根因，最终提供 key 级粒度方案 |
| 2026-06-08 | AI 架构升级：Chat Bot 微服务两轨制 | AI 提供 3 种 API 调用方案对比（客户端直连/服务端代理/微服务），选择方案 C |
| 2026-06-08 | 广告匹配策略：客户端本地执行（模式 B） | AI 分析模式 A（服务端匹配）与模式 B 的适用场景，选择客户端匹配 |
| 2026-06-14 | 技术文档 + 答辩材料准备 | AI 协助完善文档体系 |

---

## 项目收获

### 技术成长

1. **Android 现代开发栈**：从传统 View 体系跨越到 Compose 声明式 UI + MVVM + Kotlin 协程/Flow 的现代架构
2. **AI 工程化实践**：从 Prompt Engineering 到 API 集成、缓存策略、降级方案，形成完整的 AI 功能落地链路
3. **性能优化意识**：Compose 重组优化、播放器实例池化、图片精确解码、内存管理、ANR 防护
4. **架构设计能力**：模块化拆分、数据源切换、跨页面状态同步、DI 透明性、降级设计

### 工程素养

1. **文档驱动开发**：需求文档 → 技术文档 → 开发日程 → 日报 → 问题记录，完整闭环
2. **Git 规范**：commit message 明确准确，按功能模块分批提交，保持提交历史清晰
3. **问题复盘**：对关键 bug 做根因分析、解决方案记录、设计原则提炼

