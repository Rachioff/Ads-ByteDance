"""
AI 摘要/标签 API（OpenAI 兼容）
==============================
POST /v1/chat/completions  — 兼容 OpenAI Chat Completions API
客户端 AiContentGenerator 直接调用此端点，生成广告摘要和智能标签。
"""

import logging
import time
import uuid

from fastapi import APIRouter, HTTPException
from fastapi.responses import JSONResponse

from llm_client import chat_completion
from models import ChatCompletionRequest

logger = logging.getLogger("chatbot.completions")

router = APIRouter(prefix="/v1", tags=["completions"])


@router.post("/chat/completions")
async def create_chat_completion(body: ChatCompletionRequest):
    """
    OpenAI 兼容的 Chat Completions 端点。

    接收标准 OpenAI 格式请求，转发到 DeepSeek API，返回标准响应。
    客户端 AiContentGenerator 零改动对接。
    """
    # 构造 OpenAI SDK 格式的消息列表
    messages = [{"role": m.role, "content": m.content} for m in body.messages]

    try:
        content = chat_completion(
            messages=messages,
            temperature=body.temperature,
            max_tokens=body.max_tokens,
        )
    except RuntimeError as e:
        # API key 未配置
        logger.warning("LLM 不可用（API key 未配置）: %s", e)
        raise HTTPException(
            status_code=503,
            detail="LLM 服务未配置，请先设置 DEEPSEEK_API_KEY",
        )
    except (ConnectionError, TimeoutError) as e:
        # 网络超时 → 503 触发客户端降级
        logger.error("LLM 网络请求失败: %s", e)
        raise HTTPException(
            status_code=503,
            detail="LLM API 暂时不可用，请稍后重试",
        )
    except Exception as e:
        logger.error("LLM 调用异常: %s", e)
        raise HTTPException(
            status_code=500,
            detail=f"内部错误: {str(e)}",
        )

    # 构造 OpenAI 标准响应
    now = int(time.time())
    return JSONResponse(
        status_code=200,
        content={
            "id": f"chatcmpl-{uuid.uuid4().hex[:12]}",
            "object": "chat.completion",
            "created": now,
            "model": body.model,
            "choices": [
                {
                    "index": 0,
                    "message": {
                        "role": "assistant",
                        "content": content,
                    },
                    "finish_reason": "stop",
                }
            ],
            "usage": {
                "prompt_tokens": 0,
                "completion_tokens": 0,
                "total_tokens": 0,
            },
        },
    )
