"""
Chat Bot Microservice 数据模型
===========================
Pydantic 模型，定义请求体、响应体结构，与 chatbot-api.md 完全对齐。
"""

from __future__ import annotations

from typing import Any, Generic, Optional, TypeVar
from pydantic import BaseModel, Field


# ============================================================
# 通用响应包装
# ============================================================

T = TypeVar("T")


class ApiResponse(BaseModel, Generic[T]):
    code: int = 0
    message: str = "ok"
    data: Optional[T] = None


def ok(data: Any = None) -> dict:
    """构造成功响应字典。"""
    return {"code": 0, "message": "ok", "data": data}


def error(code: int, message: str) -> dict:
    """构造错误响应字典。"""
    return {"code": code, "message": message, "data": None}


# ============================================================
# Tag
# ============================================================

class Tag(BaseModel):
    name: str
    category: str  # "category" | "audience" | "style" | "scene"


# ============================================================
# Intent（LLM 解析出的结构化搜索条件）
# ============================================================

class PriceRange(BaseModel):
    min: float = 0
    max: float = 0


class Intent(BaseModel):
    categories: list[str] = Field(default_factory=list)
    audiences: list[str] = Field(default_factory=list)
    styles: list[str] = Field(default_factory=list)
    scenes: list[str] = Field(default_factory=list)
    priceRange: Optional[PriceRange] = None
    keywords: list[str] = Field(default_factory=list)


# ============================================================
# AdItem（微服务关心的广告子集字段，完整返回给客户端）
# ============================================================

class AdItem(BaseModel):
    id: str
    title: str
    adType: str  # "large_image" | "small_image" | "video"
    description: str = ""
    advertiserName: str = ""
    advertiserAvatar: str = ""
    channel: str = ""  # "featured" | "ecommerce" | "local"
    tags: list[Tag] = Field(default_factory=list)
    aiSummary: Optional[str] = None
    thumbnailUrl: Optional[str] = None
    imagePosition: Optional[str] = None  # "left" | "right"
    coverImageUrl: Optional[str] = None
    videoUrl: Optional[str] = None
    likeCount: int = 0
    collectCount: int = 0
    shareCount: int = 0
    exposureCount: int = 0
    clickCount: int = 0


# ============================================================
# Session
# ============================================================

class CreateSessionRequest(BaseModel):
    title: Optional[str] = None


class SessionInfo(BaseModel):
    sessionId: str
    title: str
    createdAt: int
    updatedAt: int = 0


class SessionListItem(BaseModel):
    sessionId: str
    title: str
    messageCount: int = 0
    lastMessage: str = ""
    createdAt: int
    updatedAt: int


class SessionListData(BaseModel):
    sessions: list[SessionListItem]


# ============================================================
# Message — 发消息（核心接口）
# ============================================================

class ContextAd(BaseModel):
    """客户端在请求中携带的当前讨论广告上下文。"""
    adId: str = ""
    title: str = ""
    description: str = ""
    advertiserName: str = ""
    tags: list[str] = Field(default_factory=list)
    aiSummary: Optional[str] = None


class SendMessageRequest(BaseModel):
    content: str
    contextAd: Optional[ContextAd] = None
    previousMatchedAds: Optional[list[ContextAd]] = None  # 上轮搜索匹配到的广告摘要，用于 AI 上下文


class MessageInfo(BaseModel):
    messageId: str
    role: str  # "user" | "assistant" | "system"
    content: str
    ads: Optional[list[AdItem]] = None
    intent: Optional[Intent] = None
    createdAt: int


class SendMessageData(BaseModel):
    """POST /api/sessions/{id}/messages 的 data 载荷。"""
    message: MessageInfo
    ads: list[AdItem] = Field(default_factory=list)
    intent: Optional[Intent] = None
    searchRequested: bool = False  # true=客户端应执行本地广告匹配，false=只展示文字回复


class HistoryMessageData(BaseModel):
    messages: list[MessageInfo]
    hasMore: bool = False


# ============================================================
# OpenAI 兼容 Chat Completions
# ============================================================

class ChatMessage(BaseModel):
    role: str
    content: str


class ChatCompletionRequest(BaseModel):
    model: str = "deepseek-chat"
    messages: list[ChatMessage]
    temperature: float = 0.3
    max_tokens: int = 512


class ChatCompletionChoiceMessage(BaseModel):
    role: str
    content: str


class ChatCompletionChoice(BaseModel):
    index: int
    message: ChatCompletionChoiceMessage
    finish_reason: str = "stop"


class ChatCompletionUsage(BaseModel):
    prompt_tokens: int = 0
    completion_tokens: int = 0
    total_tokens: int = 0


class ChatCompletionResponse(BaseModel):
    id: str
    object: str = "chat.completion"
    created: int
    model: str
    choices: list[ChatCompletionChoice]
    usage: Optional[ChatCompletionUsage] = None
