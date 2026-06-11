# Chat Bot 微服务 API 接口文档

> **版本**: v1.0
> **制定日期**: 2026-06-08
> **服务端语言**: 任选（Python FastAPI / Go Gin / Node.js Express / Kotlin Ktor）
> **通信协议**: HTTP + JSON (RESTful)

---

## 目录

1. [概述](#1-概述)
2. [通用规范](#2-通用规范)
3. [会话管理 API](#3-会话管理-api)
4. [消息与对话 API](#4-消息与对话-api)
5. [AI 摘要/标签 API（OpenAI 兼容）](#5-ai-摘要标签-apiopenai-兼容)
6. [数据模型](#6-数据模型)
7. [客户端对接指南](#7-客户端对接指南)
8. [服务端实现建议](#8-服务端实现建议)

---

## 1. 概述

### 1.1 架构定位

```
┌──────────────────────┐     ┌─────────────────────────┐     ┌──────────────┐
│   Android 客户端       │────▶│   Chat Bot 微服务        │────▶│   LLM API    │
│   (Ads-ByteDance)     │◀────│   (localhost:8080)       │◀────│   (DeepSeek)  │
└──────────────────────┘     └─────────────────────────┘     └──────────────┘
                                       │
                                       ▼
                              ┌─────────────────────────┐
                              │   本地存储（SQLite/JSON）  │
                              │   - session 表           │
                              │   - message 表           │
                              └─────────────────────────┘
```

微服务承担两个职责：

| 职责 | 端点 | 说明 |
|------|------|------|
| **对话搜索** | `/api/*` 自定义 REST API | session 管理、多轮对话、意图解析、广告匹配结果返回 |
| **AI 摘要/标签** | `/v1/chat/completions`（OpenAI 兼容） | 无状态调用，客户端传入 prompt，返回 JSON |

### 1.2 用户身份

客户端通过请求头 `X-User-Id` 标识用户。值由客户端首次启动时生成的 UUID 填充（存于 SharedPreferences）。

**注意**：本项目不做登录注册，`X-User-Id` 仅用于微服务端隔离不同设备的 session 和对话历史，不做权限校验。

---

## 2. 通用规范

### 2.1 Base URL

```
http://<host>:<port>
```

默认配置（`local.properties`）：

```properties
chatbot.service.url=http://localhost:8080
```

### 2.2 通用请求头

| Header | 值 | 必填 | 说明 |
|--------|-----|------|------|
| `Content-Type` | `application/json` | 是 | 请求体类型 |
| `X-User-Id` | `<UUID>` | 是 | 用户/设备标识，由客户端生成并持久化 |
| `Authorization` | `Bearer <key>` | 否 | 预留，当前版本不校验 |

### 2.3 通用响应格式

**成功响应**：

```json
{
  "code": 0,
  "message": "ok",
  "data": { ... }
}
```

**错误响应**：

```json
{
  "code": <错误码>,
  "message": "<错误描述>",
  "data": null
}
```

### 2.4 错误码

| code | 含义 |
|------|------|
| 0 | 成功 |
| 400 | 请求参数错误 |
| 404 | session/资源不存在 |
| 500 | 服务端内部错误 |
| 503 | LLM API 不可用（触发客户端降级） |

### 2.5 HTTP 状态码映射

| HTTP Status | 场景 |
|-------------|------|
| 200 | 成功 |
| 201 | 创建成功（如新建 session） |
| 400 | 参数校验失败 |
| 404 | 资源不存在 |
| 500 | 服务端异常 |

---

## 3. 会话管理 API

### 3.1 创建会话

客户端进入搜索页面时调用，创建一个新的对话 session。

```
POST /api/sessions
```

**请求头**：
```
X-User-Id: <UUID>
```

**请求体**：无（或可选 `title`）

```json
{
  "title": "可选：初始标题，不传则默认'新对话'"
}
```

**响应** `201 Created`：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "sessionId": "sess_a1b2c3d4",
    "title": "新对话",
    "createdAt": 1717843200000
  }
}
```

**客户端行为**：
- 进入 `SearchScreen` 时调用
- 保存 `sessionId` 用于后续发消息
- 若创建失败（网络异常等），回落本地模式（纯关键词搜索）

---

### 3.2 获取用户会话列表

```
GET /api/sessions
```

**请求头**：
```
X-User-Id: <UUID>
```

**响应** `200 OK`：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "sessions": [
      {
        "sessionId": "sess_a1b2c3d4",
        "title": "学生党数码推荐",
        "messageCount": 6,
        "lastMessage": "为您找到3条符合的广告",
        "createdAt": 1717843200000,
        "updatedAt": 1717843500000
      }
    ]
  }
}
```

**字段说明**：

| 字段 | 类型 | 说明 |
|------|------|------|
| `sessionId` | String | 会话唯一 ID |
| `title` | String | 会话标题（可自动从首条消息截取或由 LLM 生成） |
| `messageCount` | Int | 消息条数 |
| `lastMessage` | String | 最后一条消息的摘要（截取前 30 字） |
| `createdAt` | Long | 创建时间戳（毫秒） |
| `updatedAt` | Long | 最后更新时间戳（毫秒） |

**客户端行为**：
- 用于"历史对话"列表 UI（可选功能，Day 7 可先不实现，预留接口）

---

### 3.3 删除会话

```
DELETE /api/sessions/{sessionId}
```

**请求头**：
```
X-User-Id: <UUID>
```

**响应** `200 OK`：

```json
{
  "code": 0,
  "message": "ok",
  "data": null
}
```

**客户端行为**：
- "清空对话"按钮 → 调用此接口 → 再调用 3.1 创建新 session

---

## 4. 消息与对话 API

### 4.1 发送消息（核心接口）

用户输入自然语言查询，服务端解析意图 → 匹配广告 → 返回 AI 回复 + 广告卡片。

```
POST /api/sessions/{sessionId}/messages
```

**请求头**：
```
X-User-Id: <UUID>
Content-Type: application/json
```

**请求体**：

```json
{
  "content": "推荐一些适合学生党的平价数码产品",
  "contextAd": {
    "adId": "ad_001",
    "title": "高性价比蓝牙耳机",
    "description": "学生党必备的蓝牙耳机...",
    "advertiserName": "数码优选",
    "tags": ["数码", "学生党", "性价比"],
    "aiSummary": "适合预算有限的学生党的蓝牙耳机"
  }
}
```

**请求体字段说明**：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `content` | String | 是 | 用户输入的自然语言查询 |
| `contextAd.adId` | String | 否 | 广告唯一 ID |
| `contextAd.title` | String | 否 | 广告标题 |
| `contextAd.description` | String | 否 | 广告描述 |
| `contextAd.advertiserName` | String | 否 | 广告主名称 |
| `contextAd.tags[]` | Array\<String\> | 否 | 广告标签名称列表 |
| `contextAd.aiSummary` | String? | 否 | AI 摘要（可为 null） |

> **`contextAd` 是可选字段。** 当用户从搜索结果或详情页点击"和AI讨论"进入 ChatBot 时，客户端会在每次消息请求中携带当前讨论的广告上下文。微服务检测到 `contextAd` 不为 null 时，应将其拼入 LLM 的 System Prompt 中，使 AI 回复围绕该广告展开。

**响应** `200 OK`：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "message": {
      "messageId": "msg_x1y2z3",
      "role": "assistant",
      "content": "为您找到了3条适合学生党的平价数码产品，以下是推荐：",
      "createdAt": 1717843600000
    },
    "ads": [
      {
        "id": "ad_001",
        "title": "高性价比蓝牙耳机",
        "adType": "small_image",
        "description": "学生党必备...",
        "advertiserName": "数码优选",
        "advertiserAvatar": "https://picsum.photos/200",
        "channel": "ecommerce",
        "tags": [
          { "name": "数码", "category": "category" },
          { "name": "学生党", "category": "audience" },
          { "name": "性价比", "category": "style" }
        ],
        "aiSummary": "适合预算有限的学生党的蓝牙耳机，音质出色价格亲民",
        "thumbnailUrl": "https://picsum.photos/400",
        "imagePosition": "left",
        "coverImageUrl": "https://picsum.photos/800",
        "videoUrl": null,
        "likeCount": 128,
        "collectCount": 45,
        "shareCount": 12,
        "exposureCount": 1500,
        "clickCount": 230
      }
    ],
    "intent": {
      "categories": ["数码"],
      "audiences": ["学生党"],
      "styles": ["性价比"],
      "scenes": [],
      "priceRange": { "min": 0, "max": 500 },
      "keywords": ["平价", "数码产品"]
    }
  }
}
```

**响应字段说明**：

| 字段 | 类型 | 说明 |
|------|------|------|
| `message.messageId` | String | 消息唯一 ID |
| `message.role` | String | 固定 `"assistant"` |
| `message.content` | String | AI 回复的自然语言文本（展示在聊天气泡中） |
| `message.createdAt` | Long | 消息时间戳（毫秒） |
| `ads[]` | Array | 匹配的广告卡片列表（**复用客户端 `AdItem` 数据结构**） |
| `ads[].id` | String | 广告 ID（客户端已有数据，可本地匹配或直接渲染） |
| `ads[].adType` | String | `large_image` / `small_image` / `video` |
| `ads[].*` | * | 其他字段完整返回，客户端可直接渲染卡片 |
| `intent` | Object | 解析出的结构化搜索条件（调试用，客户端可不消费） |

**客户端行为**：

1. 用户输入 `"推荐学生党数码"` → 发送到微服务
2. 收到响应后：
   - 在聊天气泡中展示 `message.content`
   - 在气泡下方以广告卡片形式渲染 `ads[]`
   - 点击卡片 → 导航到 `DetailScreen(adId)`
3. 微服务不可用（503 / 网络超时）→ 客户端降级为本地关键词搜索：
   - 对本地已加载广告做 `title.contains(keyword)` + `description.contains(keyword)` 匹配
   - 聊天气泡显示"搜索暂不可用，以下是本地结果"

### 4.2 获取对话历史

```
GET /api/sessions/{sessionId}/messages
```

**请求头**：
```
X-User-Id: <UUID>
```

**查询参数**：

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `limit` | Int | 20 | 返回条数 |
| `before` | Long | 当前时间 | 时间戳，获取此时间之前的消息（分页） |

**响应** `200 OK`：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "messages": [
      {
        "messageId": "msg_a1",
        "role": "user",
        "content": "推荐一些适合学生党的平价数码产品",
        "ads": null,
        "createdAt": 1717843590000
      },
      {
        "messageId": "msg_x1y2z3",
        "role": "assistant",
        "content": "为您找到了3条适合学生党的平价数码产品，以下是推荐：",
        "ads": [ ... ],
        "intent": { ... },
        "createdAt": 1717843600000
      }
    ],
    "hasMore": false
  }
}
```

**客户端行为**：
- 进入搜索页面时加载最近 N 条历史消息
- 上拉加载更多历史消息（通过 `before` 参数翻页）

---

## 5. AI 摘要/标签 API（OpenAI 兼容）

### 5.1 Chat Completions

兼容 OpenAI Chat Completions API 规范。客户端 `AiContentGenerator` 直接复用此端点。

```
POST /v1/chat/completions
```

**请求头**：
```
Content-Type: application/json
Authorization: Bearer <api-key>   （可选，微服务内部校验或不校验）
```

**请求体**（OpenAI 标准格式）：

```json
{
  "model": "deepseek-v4-flash",
  "messages": [
    {
      "role": "system",
      "content": "你是一个广告内容分析助手。给定广告信息，请生成：\n1. 一个简洁的摘要（1-2句话，不超过80字）\n2. 3-5个智能标签...\n你必须严格按照以下 JSON 格式返回..."
    },
    {
      "role": "user",
      "content": "广告标题：xxx\n广告描述：xxx\n广告主：xxx\n广告类型：大图广告\n\n请为以上广告生成摘要和智能标签。"
    }
  ],
  "temperature": 0.3,
  "max_tokens": 512
}
```

**响应** `200 OK`（OpenAI 标准格式）：

```json
{
  "id": "chatcmpl-xxx",
  "object": "chat.completion",
  "created": 1717843200,
  "model": "deepseek-v4-flash",
  "choices": [
    {
      "index": 0,
      "message": {
        "role": "assistant",
        "content": "{\n  \"summary\": \"高品质蓝牙耳机，音质出色价格亲民，学生党的入门首选\",\n  \"tags\": [\n    {\"name\": \"数码\", \"category\": \"category\"},\n    {\"name\": \"学生党\", \"category\": \"audience\"},\n    {\"name\": \"性价比\", \"category\": \"style\"}\n  ]\n}"
      },
      "finish_reason": "stop"
    }
  ],
  "usage": {
    "prompt_tokens": 180,
    "completion_tokens": 95,
    "total_tokens": 275
  }
}
```

**客户端对接**：
- 此端点与当前 `AiApiService.chatCompletions()` 完全兼容
- 只需修改 `NetworkConfig.getAiBaseUrl()` 从 `https://api.openai.com` → `http://localhost:8080` 即可
- `AiContentGenerator` 零改动

---

## 6. 数据模型

### 6.1 Session（会话）

```json
{
  "sessionId": "sess_a1b2c3d4",
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "title": "学生党数码推荐",
  "createdAt": 1717843200000,
  "updatedAt": 1717843500000,
  "isActive": true
}
```

**服务端存储建议**：

```sql
CREATE TABLE sessions (
    session_id  TEXT PRIMARY KEY,
    user_id     TEXT NOT NULL,
    title       TEXT DEFAULT '新对话',
    created_at  INTEGER NOT NULL,
    updated_at  INTEGER NOT NULL,
    is_active   INTEGER DEFAULT 1
);
CREATE INDEX idx_sessions_user ON sessions(user_id, updated_at DESC);
```

### 6.2 Message（消息）

```json
{
  "messageId": "msg_x1y2z3",
  "sessionId": "sess_a1b2c3d4",
  "role": "assistant",
  "content": "为您找到了3条符合的广告",
  "ads": [ ... ],
  "intent": { ... },
  "createdAt": 1717843600000
}
```

**服务端存储建议**：

```sql
CREATE TABLE messages (
    message_id  TEXT PRIMARY KEY,
    session_id  TEXT NOT NULL,
    role        TEXT NOT NULL CHECK(role IN ('user', 'assistant', 'system')),
    content     TEXT NOT NULL,
    ads_json    TEXT,           -- JSON string, 可为 null
    intent_json TEXT,           -- JSON string, 可为 null
    created_at  INTEGER NOT NULL,
    FOREIGN KEY (session_id) REFERENCES sessions(session_id)
);
CREATE INDEX idx_messages_session ON messages(session_id, created_at);
```

### 6.3 AdItem（广告卡片）

广告数据模型完整字段定义见 `app/.../data/model/AdItem.kt`。微服务在对话搜索响应中返回的 `ads[]` 数组复用此结构。

**核心字段**（微服务需要关心的）：

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | String | 广告唯一 ID |
| `title` | String | 广告标题 |
| `adType` | Enum | `large_image` / `small_image` / `video` |
| `description` | String | 广告描述 |
| `advertiserName` | String | 广告主名称 |
| `advertiserAvatar` | String | 广告主头像 URL |
| `channel` | Enum | `featured` / `ecommerce` / `local` |
| `tags[]` | Array\<Tag\> | 标签列表（含 name + category） |
| `aiSummary` | String? | AI 摘要（可为 null） |
| `coverImageUrl` | String? | 大图/视频封面 URL |
| `thumbnailUrl` | String? | 小图缩略图 URL |
| `videoUrl` | String? | 视频 URL |
| `imagePosition` | Enum? | `left` / `right`（小图卡片） |
| `likeCount` | Int | 点赞数 |
| `collectCount` | Int | 收藏数 |
| `shareCount` | Int | 分享数 |

---

## 7. 客户端对接指南

### 7.1 实际客户端文件结构

> **更新于 2026-06-09**: 聊天机器人功能已从 `search/` 模块迁移至 `ai/chat/`，
> 常规搜索功能独立为 `search/` 模块。API 契约不变。

| 文件 | 说明 |
|------|------|
| `ai/api/ChatBotService.kt` | Retrofit 接口定义（4 个端点） |
| `ai/chat/model/ChatModels.kt` | API DTO：`ChatApiResponse<T>`, `CreateSessionRequest`, `SendMessageRequest`, `SendMessageData`, `SessionInfo` 等 |
| `ai/chat/model/ChatUiState.kt` | 对话页面 UI 状态 |
| `ai/chat/viewmodel/ChatViewModel.kt` | 对话逻辑：session 管理 + 微服务调用 + 降级匹配 |
| `ai/chat/ui/ChatScreen.kt` | 聊天气泡式 UI Composable |
| `common/engine/AdMatchingEngine.kt` | 本地广告匹配引擎（标签/关键词/意图多维度评分） |
| `common/util/SessionManager.kt` | 设备 UUID 生成/持久化 |
| `common/network/NetworkConfig.kt` | Retrofit 工厂（含 `createChatBotRetrofit()`） |
| `di/AppModule.kt` | Koin 注册 ChatBotService + AdMatchingEngine |
| `di/ViewModelModule.kt` | Koin 注册 ChatViewModel |
| `local.properties` | `chatbot.service.url=http://localhost:8080` | |

### 7.2 `local.properties` 配置

```properties
# 原有
ai.api.key=sk-xxx
ai.api.base.url=https://api.deepseek.com

# 新增
chatbot.service.url=http://localhost:8080
```

### 7.3 `AiContentGenerator` 对接

**完全不需改动**。因为微服务暴露的 `/v1/chat/completions` 与当前 `AiApiService` 接口兼容，只需修改 `local.properties`：

```properties
# 指向微服务（微服务内部转发到 DeepSeek）
ai.api.base.url=http://localhost:8080
```

### 7.4 降级策略

```
对话搜索微服务不可用（连接失败 / 503 / 超时）
    → ChatViewModel 不抛异常
    → 聊天气泡显示 AI 回复："搜索服务暂不可用，以下是在线匹配结果"
    → 调用 repository.searchAds(query)（通过 AdDataSource → Mock 遍历全量 JSON / Remote 调用搜索 API）
    → 以广告卡片形式展示匹配结果
```

数据层搜索接口对 Mock/Remote 透明，降级不再依赖 ViewModel 层手动聚合缓存数据。

---

## 8. 服务端实现建议

### 8.1 技术选型建议

| 方案 | 适合场景 |
|------|---------|
| **Python FastAPI** | 快速开发，LLM 生态好（langchain/openai SDK） |
| **Go Gin** | 高性能，部署简单（单二进制） |
| **Node.js Express** | 前端技术栈统一 |

推荐 **Python FastAPI**：异步支持好，OpenAI SDK 官方支持，开发效率高。

### 8.2 核心接口实现要点

#### 8.2.1 `/v1/chat/completions`（OpenAI 兼容）

```
实现非常简单：
1. 接收 OpenAI 格式请求
2. 转发到 DeepSeek / Qwen（附加 API Key）
3. 流式或非流式返回
```

约 30 行 Python 代码即可。

#### 8.2.2 `/api/sessions/{id}/messages`（对话搜索）

**服务端处理流程**：

```
1. 接收 POST { content: "...", contextAd?: {...} }
2. 从 DB 加载该 session 的历史消息（最近 10 条）
3. 构造 LLM 请求：
   System: "你是广告搜索助手。解析用户意图并匹配广告。"
   + 如果 contextAd 不为 null：
     System += "\n用户正在查看以下广告，请围绕它进行讨论：\n" + contextAd 序列化
   History: [...最近10条消息...]
   User: "推荐学生党数码"
4. 调用 LLM → 获取意图解析结果
5. 在本地广告库中匹配（或返回意图给客户端，客户端本地匹配）
6. 返回 { message: "为您找到3条...", ads: [...], intent: {...} }
```

**关于广告匹配的两种模式**：

| 模式 | 数据位置 | 优点 | 缺点 |
|------|---------|------|------|
| **模式 A：服务端匹配** | 广告数据同步到微服务 | 响应完整，客户端直接渲染 | 需要数据同步机制 |
| **模式 B：客户端匹配（推荐）** | 微服务返回意图 + LLM 回复，客户端本地匹配 | 无数据同步成本 | 匹配逻辑简单 |

**推荐模式 B**——本项目数据量小（30条 × 3频道），客户端本地匹配足够。微服务只做 LLM 调用和 session 管理。

简化实现流程：

```
1. 客户端发消息 "推荐学生党平价数码"
2. 微服务调用 LLM 解析意图 → { categories:["数码"], audiences:["学生党"], keywords:["平价"] }
3. 微服务调用 LLM 生成友好的回复文本 → "为您找到了3条学生党平价数码产品！"
4. 微服务存储消息 + 返回 { message: "...", intent: {...} }（不返回 ads）
5. 客户端收到 intent → 本地 AdMatchingEngine.match(localAds, intent) → 渲染卡片
```

### 8.3 最小可行实现（MVP）

如果时间有限，可以先实现最小版本：

| 优先级 | 端点 | 说明 |
|--------|------|------|
| P0 | `POST /v1/chat/completions` | 转发 LLM，让 AI 摘要/标签先跑通 |
| P0 | `POST /api/sessions/{id}/messages` | 对话搜索核心接口 |
| P1 | `POST /api/sessions` | 创建 session |
| P1 | `GET /api/sessions/{id}/messages` | 历史消息 |
| P2 | `GET /api/sessions` | 会话列表 |
| P2 | `DELETE /api/sessions/{id}` | 删除会话 |

P0 两天内可完成（含 LLM 调试），P1 一天，P2 半天。

### 8.4 Session 清理策略

- 超过 7 天未活动的 session → 标记 `is_active = false`（软删除）
- 定时任务（如每天凌晨）清理

---

> **文档维护说明**：本文档随微服务开发和客户端对接持续更新。接口如有变更，请同步更新本文档。
