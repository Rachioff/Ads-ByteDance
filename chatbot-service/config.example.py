"""
Chat Bot Microservice 配置模板
============================
复制此文件为 config.py 并填入你的 API Key。
config.py 已在 .gitignore 中，不会被提交到版本控制。

使用方法:
    cp config.example.py config.py
    # 编辑 config.py，填入 DEEPSEEK_API_KEY
"""

import os

# --- LLM (DeepSeek) ---
DEEPSEEK_API_KEY = os.getenv("DEEPSEEK_API_KEY", "")  # <-- 在这里填入你的 DeepSeek API Key
DEEPSEEK_BASE_URL = os.getenv("DEEPSEEK_BASE_URL", "https://api.deepseek.com")
DEEPSEEK_MODEL = os.getenv("DEEPSEEK_MODEL", "deepseek-reasoner")  # DeepSeek R1 推理模型

# --- LLM 请求参数 ---
LLM_TEMPERATURE = float(os.getenv("LLM_TEMPERATURE", "0.3"))
LLM_MAX_TOKENS = int(os.getenv("LLM_MAX_TOKENS", "512"))
LLM_TIMEOUT_SECONDS = int(os.getenv("LLM_TIMEOUT_SECONDS", "30"))

# --- 数据库 ---
DB_PATH = os.getenv("CHATBOT_DB_PATH", "chatbot.db")

# --- 会话 ---
SESSION_HISTORY_LIMIT = int(os.getenv("SESSION_HISTORY_LIMIT", "10"))
SESSION_EXPIRE_DAYS = int(os.getenv("SESSION_EXPIRE_DAYS", "7"))

# --- 服务 ---
SERVICE_HOST = os.getenv("SERVICE_HOST", "0.0.0.0")
SERVICE_PORT = int(os.getenv("SERVICE_PORT", "8080"))
