"""
Chat Bot Microservice — DeepSeek LLM 客户端
==========================================
基于 OpenAI Python SDK，调用 DeepSeek API（OpenAI 兼容）。
"""

import json
import logging
from typing import Any, Optional

from openai import OpenAI

import config
from prompts import (
    CHAT_SYSTEM_PROMPT,
    CHAT_WITH_AD_CONTEXT_TEMPLATE,
    TITLE_GENERATION_PROMPT,
)

logger = logging.getLogger("chatbot.llm")

# 延迟初始化，API key 可在运行时设置
_client: Optional[OpenAI] = None


def _get_client() -> OpenAI:
    """获取或创建 OpenAI 客户端（指向 DeepSeek）。"""
    global _client
    if _client is None:
        if not config.DEEPSEEK_API_KEY:
            raise RuntimeError("DEEPSEEK_API_KEY 未配置，请在 config.py 或环境变量中设置")
        _client = OpenAI(
            api_key=config.DEEPSEEK_API_KEY,
            base_url=config.DEEPSEEK_BASE_URL,
            timeout=config.LLM_TIMEOUT_SECONDS,
        )
    return _client


def _call_llm(
    messages: list[dict],
    temperature: float = config.LLM_TEMPERATURE,
    max_tokens: int = config.LLM_MAX_TOKENS,
) -> str:
    """
    底层调用 DeepSeek Chat API，返回 assistant 文本内容。

    Raises:
        RuntimeError: API key 未配置
        ConnectionError: 网络不通
        TimeoutError: 超时
        Exception: 其他 API 错误
    """
    client = _get_client()
    try:
        response = client.chat.completions.create(
            model=config.DEEPSEEK_MODEL,
            messages=messages,
            temperature=temperature,
            max_tokens=max_tokens,
        )
    except Exception as e:
        logger.error("LLM API 调用失败: %s", e)
        raise

    content = response.choices[0].message.content or ""
    return content


def _format_previous_ads(ads: list[dict]) -> str:
    """将上轮匹配广告列表格式化为 LLM 可读的文本。"""
    lines = []
    for i, ad in enumerate(ads, 1):
        title = ad.get("title", "")
        desc = ad.get("description", "")[:60]
        advertiser = ad.get("advertiserName", "")
        tags = ", ".join(ad.get("tags", []))
        lines.append(f"{i}. {title}（{advertiser}）— {desc} — 标签：{tags}")
    return "\n".join(lines)


def _empty_intent() -> dict:
    """返回空的 intent 结构。"""
    return {
        "categories": [],
        "audiences": [],
        "styles": [],
        "scenes": [],
        "priceRange": None,
        "keywords": [],
    }


def _parse_json_response(raw: str) -> dict:
    """
    从 LLM 返回的原始文本中解析 JSON。
    处理 LLM 可能包裹 ```json ... ``` 或带额外文本的情况。

    如果 JSON 解析彻底失败，返回降级结果：
    {"reply": raw, "intent": empty} —— 把原文当聊天回复，不触发搜索。
    """
    raw = raw.strip()
    # 尝试移除 markdown 代码块
    if raw.startswith("```"):
        lines = raw.split("\n")
        if lines[0].startswith("```"):
            lines = lines[1:]
        if lines and lines[-1].strip() == "```":
            lines = lines[:-1]
        raw = "\n".join(lines).strip()

    try:
        return json.loads(raw)
    except json.JSONDecodeError:
        # 尝试提取第一个 JSON 对象
        start = raw.find("{")
        end = raw.rfind("}") + 1
        if start >= 0 and end > start:
            try:
                return json.loads(raw[start:end])
            except json.JSONDecodeError:
                pass

        # 彻底失败 → 降级为纯文本对话，不抛异常
        logger.warning("LLM 未返回有效 JSON，降级为纯文本回复: %s", raw[:100])
        return {"reply": raw, "intent": _empty_intent()}


# ============================================================
# 高层接口
# ============================================================

def chat_completion(
    messages: list[dict],
    temperature: float = config.LLM_TEMPERATURE,
    max_tokens: int = config.LLM_MAX_TOKENS,
) -> str:
    """
    OpenAI 兼容的 Chat Completions 代理。
    直接转发请求到 DeepSeek，返回原始文本。
    用于 /v1/chat/completions 端点。
    """
    return _call_llm(messages, temperature=temperature, max_tokens=max_tokens)


def chat_search(
    user_content: str,
    history: Optional[list[dict]] = None,
    context_ad: Optional[dict] = None,
    previous_matched_ads: Optional[list[dict]] = None,
) -> dict:
    """
    对话搜索核心逻辑。

    Args:
        user_content: 用户输入的自然语言查询
        history: 最近 N 条历史消息
        context_ad: 可选的广告上下文（用户从详情页带入）
        previous_matched_ads: 上轮搜索匹配到的广告摘要列表，注入 prompt 让 AI 知道展示了哪些广告

    Returns:
        {"reply": "...", "intent": {...}}
    """
    # 选择 System Prompt
    if context_ad:
        tags_str = ", ".join(context_ad.get("tags", []))
        ai_summary = context_ad.get("aiSummary") or "无"
        system_content = CHAT_WITH_AD_CONTEXT_TEMPLATE.format(
            title=context_ad.get("title", ""),
            description=context_ad.get("description", ""),
            advertiserName=context_ad.get("advertiserName", ""),
            tags=tags_str if tags_str else "无",
            aiSummary=ai_summary,
        )
    else:
        system_content = CHAT_SYSTEM_PROMPT

    # 注入上轮搜索结果上下文
    if previous_matched_ads:
        ads_info = _format_previous_ads(previous_matched_ads)
        system_content += f"\n\n## 上轮搜索展示给用户的广告\n{ads_info}\n当用户提及'这个'、'那个'、'它'等指代词时，请优先关联这些广告。"

    # 构造消息列表
    llm_messages = [{"role": "system", "content": system_content}]

    # 拼入历史消息
    if history:
        for msg in history:
            role = msg.get("role", "user")
            content = msg.get("content", "")
            if role in ("user", "assistant"):
                llm_messages.append({"role": role, "content": content})

    # 当前用户消息
    llm_messages.append({"role": "user", "content": user_content})

    # 调用 LLM
    raw = _call_llm(llm_messages)
    result = _parse_json_response(raw)

    # 确保必要字段存在
    if "reply" not in result:
        result["reply"] = "好的，让我来帮您找找！"
    if "intent" not in result:
        result["intent"] = {
            "categories": [],
            "audiences": [],
            "styles": [],
            "scenes": [],
            "priceRange": None,
            "keywords": [],
        }

    return result


def generate_title(user_content: str) -> str:
    """
    根据用户首条消息自动生成会话标题。

    Args:
        user_content: 用户的第一条消息内容

    Returns:
        标题字符串（不超过15字）
    """
    try:
        prompt = TITLE_GENERATION_PROMPT.format(content=user_content)
        title = _call_llm(
            messages=[{"role": "user", "content": prompt}],
            temperature=0.3,
            max_tokens=32,
        )
        title = title.strip().strip('"').strip("'").strip("。").strip("！")
        if len(title) > 15:
            title = title[:15]
        return title or "新对话"
    except Exception:
        # 降级：截取用户消息前15字
        if len(user_content) > 15:
            return user_content[:15] + "..."
        return user_content or "新对话"
