"""
消息与对话 API
=============
POST /api/sessions/{id}/messages  — 发送消息（核心接口）
GET  /api/sessions/{id}/messages  — 获取历史消息
"""

import logging
from typing import Optional

from fastapi import APIRouter, Header, HTTPException, Path, Query
from fastapi.responses import JSONResponse

from database import (
    create_message,
    get_history,
    get_recent_messages,
    get_session,
    update_session,
)
from llm_client import chat_search, generate_title
from models import SendMessageRequest, ok
from config import SESSION_HISTORY_LIMIT

logger = logging.getLogger("chatbot.messages")

router = APIRouter(prefix="/api/sessions", tags=["messages"])


def _has_search_intent(intent: Optional[dict]) -> bool:
    """判断 intent 是否包含实质搜索条件（非空）。"""
    if intent is None:
        return False
    # 检查所有数组字段是否有内容
    for key in ("categories", "audiences", "styles", "scenes", "keywords"):
        if intent.get(key):
            return True
    # 检查 priceRange 是否有有效值
    pr = intent.get("priceRange")
    if pr and (pr.get("min", 0) > 0 or pr.get("max", 0) > 0):
        return True
    return False


@router.post("/{session_id}/messages")
async def send_message(
    body: SendMessageRequest,
    session_id: str = Path(...),
    x_user_id: str = Header(..., alias="X-User-Id"),
):
    """
    发送消息（核心接口）。

    流程：
    1. 校验 session 归属
    2. 保存用户消息
    3. 加载历史 → 调用 LLM → 获取 reply + intent
    4. 保存 assistant 消息
    5. 返回结果
    """
    # 1. 校验 session
    session = get_session(session_id, user_id=x_user_id)
    if session is None:
        raise HTTPException(status_code=404, detail="会话不存在")

    # 2. 保存用户消息
    user_msg = create_message(
        session_id=session_id,
        role="user",
        content=body.content,
        ads=None,
        intent=None,
    )

    # 3. 构造 context_ad (如果有)
    context_ad = None
    if body.contextAd:
        context_ad = {
            "title": body.contextAd.title,
            "description": body.contextAd.description,
            "advertiserName": body.contextAd.advertiserName,
            "tags": body.contextAd.tags,
            "aiSummary": body.contextAd.aiSummary,
        }

    # 3b. 提取上轮搜索结果
    previous_matched_ads = None
    if body.previousMatchedAds:
        previous_matched_ads = [ad.model_dump() for ad in body.previousMatchedAds]

    # 4. 加载历史消息
    history = get_recent_messages(session_id, limit=SESSION_HISTORY_LIMIT)
    history_for_llm = [
        {"role": m["role"], "content": m["content"]}
        for m in history
    ]

    # 5. 调用 LLM
    llm_error = None
    try:
        result = chat_search(
            user_content=body.content,
            history=history_for_llm,
            context_ad=context_ad,
            previous_matched_ads=previous_matched_ads,
        )
        reply = result.get("reply", "好的，我来帮您搜索！")
        intent = result.get("intent") or None
    except RuntimeError as e:
        # API key 未配置 — 返回提示
        logger.warning("LLM 不可用（API key 未配置）: %s", e)
        llm_error = str(e)
        reply = "AI 服务暂未配置，请先设置 DeepSeek API Key。您可以稍后在 chatbot-service/config.py 中填写。"
        intent = None
    except Exception as e:
        # 网络超时、503 等 — 触发客户端降级
        logger.error("LLM 调用失败: %s", e)
        llm_error = str(e)
        reply = "搜索服务暂不可用，以下是在线匹配结果。"
        intent = None

    # 6. 保存 assistant 消息
    stored_ads = previous_matched_ads if previous_matched_ads else []
    assistant_msg = create_message(
        session_id=session_id,
        role="assistant",
        content=reply,
        ads=stored_ads,
        intent=intent,
    )

    # 7. 如果是该 session 的第一条消息，自动生成标题
    if len(history) == 0:
        try:
            title = generate_title(body.content)
        except Exception:
            title = None
        update_session(session_id, title=title)

    # 8. 构造响应
    search_requested = _has_search_intent(intent)
    response_data = {
        "message": {
            "messageId": assistant_msg["messageId"],
            "role": assistant_msg["role"],
            "content": assistant_msg["content"],
            "createdAt": assistant_msg["createdAt"],
        },
        "ads": [],  # 模式 B
        "intent": assistant_msg.get("intent"),
        "searchRequested": search_requested,
    }

    if llm_error:
        return JSONResponse(
            status_code=200,  # 仍然返回 200，让客户端正常处理
            content={
                "code": 0,
                "message": "ok",
                "data": response_data,
                "_llm_error": llm_error,
            },
        )

    return ok(response_data)


@router.get("/{session_id}/messages")
async def get_messages(
    session_id: str = Path(...),
    x_user_id: str = Header(..., alias="X-User-Id"),
    limit: int = Query(20, ge=1, le=100),
    before: Optional[int] = Query(None),
):
    """获取会话历史消息（分页）。"""
    session = get_session(session_id, user_id=x_user_id)
    if session is None:
        raise HTTPException(status_code=404, detail="会话不存在")

    messages, has_more = get_history(
        session_id=session_id,
        user_id=x_user_id,
        limit=limit,
        before=before,
    )

    return ok({
        "messages": messages,
        "hasMore": has_more,
    })
