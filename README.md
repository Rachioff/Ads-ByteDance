# Ads-ByteDance · AI 广告推荐信息流

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-blue)](https://kotlinlang.org/)
[![Compose](https://img.shields.io/badge/Compose-BOM%202024.06-4285F4)](https://developer.android.com/compose)
[![API](https://img.shields.io/badge/API-26%2B-green)](https://developer.android.com/about/versions/oreo)

基于 **Jetpack Compose + MVVM** 的 Android 广告推荐信息流 App，集成 **AI 大模型**生成广告摘要与智能标签，提供**对话式搜索**与**个性化推荐**能力。

---

## 功能概览

| 模块 | 功能 |
|------|------|
| **信息流** | 单列 LazyColumn，3 种广告卡片（大图 / 小图 / 视频），3 频道切换（精选 / 电商 / 本地），下拉刷新 + 上拉加载 |
| **详情页** | 图文 / 视频详情，点赞爱心粒子动画，收藏弹簧缩放，分享系统面板，跨页面状态同步 |
| **视频播放** | ExoPlayer 实例池（PlayerPool），外流封面 ↔ 播放 Crossfade 切换，进度拖动，静音切换 |
| **AI 摘要/标签** | 大模型生成摘要 + 智能标签，三级缓存（内存 LRU → Room → API），标签点击过滤 |
| **对话式搜索** | 自然语言搜索 + 多轮对话，聊天气泡 UI，微服务不可用时降级本地关键词匹配 |
| **埋点统计** | 曝光追踪（≥50% 可见 + ≥1s 停留 + 去重），点击率排序，用户偏好可视化 |

---

## Quick Start — 客户端

### 前置要求

| 工具 | 版本 | 说明 |
|------|------|------|
| **Android Studio** | Ladybug (2024.2+) | Compose 编译需要 |
| **JDK** | 17 | 随 Android Studio 安装 |
| **Android SDK** | API 36 + Build Tools 36 | SDK Manager 中安装 |

### 1. 克隆仓库

```bash
git clone https://github.com/rachioff/Ads-ByteDance.git
cd Ads-ByteDance
```

### 2. 创建 local.properties

在项目根目录创建 `local.properties`（已 gitignore，不会提交）：

```properties
# Android SDK 路径（Android Studio 通常自动生成，如已存在则跳过）
sdk.dir=C\:\\Users\\<你的用户名>\\AppData\\Local\\Android\\Sdk

# ── 以下为 AI 功能配置（可选，仅在使用 AI 功能时需要）──

# AI API Key（走微服务代理时可留空）
ai.api.key=
ai.api.base.url=http://localhost:8080

# Chat Bot 微服务地址
chatbot.service.url=http://localhost:8080
```

> AI 配置留空不影响 App 运行——AI 功能会自动降级为静态内容。

### 3. 编译运行

```bash
# macOS / Linux
./gradlew assembleDebug

# Windows
gradlew.bat assembleDebug
```

或在 Android Studio 中直接 **Run ▶**。首次编译约 2-3 分钟（下载依赖）。

---

## Mock / Remote 数据源切换

项目数据来源通过 **`gradle.properties`** 中的一行配置切换，无需改代码。

### 切换方式

编辑 `gradle.properties`：

```properties
# 默认：Mock 本地 JSON（无需后端，开箱即用）
DATA_MODE=mock

# 切换为远程 API（需后端就绪）
# DATA_MODE=remote
```

### 原理

```
gradle.properties  →  BuildConfig.DATA_MODE
                          ↓
                   Koin DI（AppModule.kt）
                   ┌──────────┴──────────┐
                   ▼                     ▼
          DATA_MODE=mock          DATA_MODE=remote
                   │                     │
                   ▼                     ▼
        MockJsonDataSource       RemoteDataSource
        (assets/mock/*.json)     (Retrofit API)
                   │                     │
                   └──────────┬──────────┘
                              ▼
                        AdRepository
                  (对上层 ViewModel 透明)
```

编译时通过 `buildConfigField` 写入 `BuildConfig.DATA_MODE`，Koin 在运行时根据该值决定注入 `MockJsonDataSource` 还是 `RemoteDataSource`。**改完 `gradle.properties` 后需重新编译**（Clean → Rebuild）。

### Mock 数据

| 文件 | 内容 |
|------|------|
| `app/src/main/assets/mock/ads_featured.json` | 精选频道：大图 3 + 小图 4 + 视频 3，含分页 |
| `app/src/main/assets/mock/ads_ecommerce.json` | 电商频道：大图 3 + 小图 4 + 视频 3，含分页 |
| `app/src/main/assets/mock/ads_local.json` | 本地频道：大图 3 + 小图 3 + 视频 4，含分页 |

每条 JSON 包含 `page` / `totalPages` / `items[]` 分页结构，`MockJsonDataSource` 在内存中做 `subList()` 切片模拟翻页。

### 切换到 Remote

1. 实现 `AdApiService` 中定义的 API 端点（见 `data/remote/AdApiService.kt`）
2. `gradle.properties` → `DATA_MODE=remote`
3. `local.properties` → 配置远程 API 的 base URL
4. Clean → Rebuild

---

## Quick Start — AI 微服务

Chat Bot 微服务（Python FastAPI）为客户端代理 LLM 调用并管理对话 session。

### 前置要求

- **Conda**（Miniconda 或 Anaconda）
- Python 3.12

### 1. 创建环境

```bash
cd chatbot-service
conda env create -f environment.yml
```

### 2. 配置 API Key

```bash
cp config.example.py config.py
```

编辑 `config.py`，填入 DeepSeek API Key：

```python
DEEPSEEK_API_KEY = "sk-your-deepseek-api-key"
```

> `config.py` 和 `chatbot.db` 已在 `chatbot-service/.gitignore` 中，不会被提交。

### 3. 启动服务

```bash
conda activate chatbot-service
python main.py
```

服务启动后：

| 地址 | 说明 |
|------|------|
| `http://localhost:8080/docs` | Swagger UI，在线调试所有 API |
| `POST /v1/chat/completions` | OpenAI 兼容端点（AI 摘要 / 标签） |
| `POST /api/sessions/{id}/messages` | 对话搜索核心接口 |

### 4. 完整启动顺序

```
1. conda activate chatbot-service && python main.py   ← 先启动微服务
2. Android Studio → Run ▶                              ← 再启动 App
```

微服务不可用时，App 自动降级：AI 摘要 → 静态内容，对话搜索 → 本地关键词匹配。

---

## 项目结构

```
Ads-ByteDance/
├── app/
│   ├── build.gradle.kts                  # 模块构建（BuildConfig + 11 组依赖）
│   ├── proguard-rules.pro                # R8 混淆规则
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/mock/                  # Mock JSON（3 频道 × 2 页）
│       │   ├── ads_featured.json
│       │   ├── ads_ecommerce.json
│       │   └── ads_local.json
│       ├── java/com/bytedance/ads_bytedance/
│       │   ├── MainActivity.kt           # 单 Activity + NavHost 路由
│       │   ├── AdsApplication.kt         # Koin 初始化 + 启动缓存清理
│       │   ├── di/                       # DI 模块（AppModule + ViewModelModule）
│       │   ├── feed/                     # 信息流（HomeScreen + FeedScreen + 3 种卡片）
│       │   │   ├── ui/card/              #   LargeImageCard / SmallImageCard / VideoCard
│       │   │   └── viewmodel/            #   FeedViewModel
│       │   ├── detail/                   # 详情页（DetailScreen + 增强互动动画）
│       │   ├── data/                     # 数据层（AdItem 密封类 + Repository + Mock/Remote）
│       │   │   ├── model/                #   数据模型
│       │   │   ├── repository/           #   数据仓库
│       │   │   ├── local/                #   本地数据源（Mock JSON + Room + DAO）
│       │   │   └── remote/               #   远程数据源（Retrofit API 骨架）
│       │   ├── ai/                       # AI 模块（摘要/标签生成 + Chat Bot 对接 + 对话缓存）
│       │   │   ├── api/                  #   AI API 接口 + Prompt 构造 + 响应解析
│       │   │   ├── cache/               #   三级缓存管理
│       │   │   └── chat/                 #   对话搜索（ChatScreen + ChatViewModel + 预加载）
│       │   ├── search/                   # 常规搜索（关键词搜索 + 搜索历史）
│       │   ├── player/                   # 视频播放器（PlayerPool + VideoPlayer）
│       │   ├── analytics/                # 埋点统计（曝光追踪 + 统计页面）
│       │   ├── behavior/                 # 用户行为（采集 + 画像 + 推荐排序）
│       │   └── common/                   # 公共组件（网络/图片/匹配引擎/工具类）
│       └── res/                          # 资源文件
├── chatbot-service/                      # Chat Bot 微服务（Python FastAPI + SQLite）
│   ├── environment.yml                   #   Conda 环境定义
│   ├── config.example.py                 #   配置模板（复制为 config.py 填 Key）
│   ├── main.py                           #   FastAPI 入口
│   ├── database.py                       #   SQLite CRUD
│   ├── llm_client.py                     #   DeepSeek API 客户端
│   ├── models.py                         #   Pydantic 模型
│   ├── prompts.py                        #   System Prompt 模板
│   └── routers/                          #   API 路由（sessions / messages / completions）
├── docs/                                 # 开发文档（提交版）
│   ├── req.md                            #   需求文档
│   ├── tech.md                           #   技术文档（架构 / 模块 / AI / 缓存 / 埋点 / 性能）
│   ├── tech-design.md                    #   技术设计文档（方案对比 + 难点 + 效果评估）
│   ├── troubleshoot.md                   #   问题解决记录
│   ├── daily-report.md                   #   开发日报（Day 1-14）
│   └── self-intro.md                     #   自我介绍
├── gradle/
│   └── libs.versions.toml                # Version Catalog（35 个库 + 6 个插件）
├── chatbot-api.md                        # 微服务 API 接口文档
├── build.gradle.kts                      # 顶层构建脚本
├── settings.gradle.kts                   # 项目设置
├── gradle.properties                     # DATA_MODE 开关
└── README.md                             # 本文档
```

---

## 技术栈

| 类别 | 技术 | 选型理由 |
|------|------|---------|
| **语言** | Kotlin 2.0 | 协程 + Flow + 密封类原生支持 |
| **UI** | Jetpack Compose + Material3 | 声明式 UI + StateFlow 自动驱动 + 多类型列表简洁 |
| **架构** | MVVM + Repository | UDF 单向数据流 + 数据源透明切换 |
| **DI** | Koin 3.5 | Kotlin DSL + 无注解处理 + ViewModel 参数注入 |
| **网络** | OkHttp 4.12 + Retrofit 2.11 | 拦截器链 + 多实例独立配置 |
| **JSON** | Kotlinx Serialization | 编译期安全 + 密封类多态序列化 |
| **图片** | Coil 3.x | 协程原生 + 内存/磁盘二级缓存 + 体积小 (~200KB) |
| **视频** | Media3 ExoPlayer 1.3 | 组件可插拔 + 自定义 PlayerPool |
| **数据库** | Room 2.7 | DAO + Flow 响应式 + 编译期 SQL 校验 |
| **AI** | DeepSeek + Chat Bot 微服务 | API Key 安全 + Session 服务端管理 |
| **微服务** | Python FastAPI + SQLite | 异步支持 + OpenAI SDK 官方支持 |

---

## 文档索引

| 文档 | 说明 |
|------|------|
| [`docs/req.md`](docs/req.md) | 项目需求文档 |
| [`docs/tech.md`](docs/tech.md) | 技术文档（架构 / 模块设计 / 数据层 / AI / 缓存 / 埋点 / 推荐 / 性能） |
| [`docs/tech-design.md`](docs/tech-design.md) | 技术设计文档（方案对比 ≥2 种 + 难点思考 + 决策记录 + 效果评估） |
| [`chatbot-api.md`](chatbot-api.md) | Chat Bot 微服务 API 接口文档 |
| [`docs/daily-report.md`](docs/daily-report.md) | 开发日报（Day 1-14） |
| [`docs/troubleshooting.md`](docs/troubleshooting.md) | 问题解决记录（根因分析 + 设计原则提炼） |
| [`docs/self-intro.md`](docs/self-intro.md) | 自我介绍 |

---

## AI 辅助开发声明

本项目使用 **Claude Code** 作为 AI 编程助手，应用范围：架构设计、代码实现、问题排查、技术选型分析、文档编写。所有 AI 生成代码均经人工审查确认。详细记录见 [`docs/self-intro.md`](docs/self-intro.md)。
