"""
Chat Bot Microservice 入口
=========================
为 Ads-ByteDance Android 客户端提供 AI 聊天服务的独立微服务。

启动方式:
    conda activate chatbot-service
    uvicorn main:app --host 0.0.0.0 --port 8080

    # 或者直接：
    python main.py

API 文档:
    http://localhost:8080/docs    — Swagger UI
    http://localhost:8080/redoc   — ReDoc
"""

import asyncio
import logging
import threading
import time
from contextlib import asynccontextmanager

from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

import config
from database import expire_old_sessions, init_db
from routers import completions, messages, sessions

# ============================================================
# 日志配置
# ============================================================

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
)
logger = logging.getLogger("chatbot")


# ============================================================
# Session 清理后台任务
# ============================================================

def _cleanup_loop():
    """后台线程：每天凌晨执行一次过期 session 清理。"""
    while True:
        # 计算到下一个凌晨的秒数
        now = time.time()
        # 明天 00:00:00
        tomorrow = (int(now // 86400) + 1) * 86400
        sleep_seconds = tomorrow - now
        # 至少等 60 秒，避免频繁重试
        if sleep_seconds < 60:
            sleep_seconds = 60

        time.sleep(sleep_seconds)

        try:
            count = expire_old_sessions()
            if count > 0:
                logger.info("Session 清理完成: %d 个过期 session 被标记为非活跃", count)
        except Exception as e:
            logger.error("Session 清理异常: %s", e)


# ============================================================
# App 生命周期
# ============================================================

@asynccontextmanager
async def lifespan(app: FastAPI):
    """应用启动/关闭时的生命周期管理。"""
    # 启动时
    logger.info("Chat Bot Microservice 正在启动...")
    init_db()
    logger.info("数据库已初始化: %s", config.DB_PATH)

    if config.DEEPSEEK_API_KEY:
        logger.info("DeepSeek API Key 已配置，模型: %s", config.DEEPSEEK_MODEL)
    else:
        logger.warning(
            "DEEPSEEK_API_KEY 未配置 — LLM 功能将不可用。"
            "请在环境变量或 config.py 中填写 API Key 后重启服务。"
        )

    # 启动清理线程（守护线程，随主进程退出）
    cleanup_thread = threading.Thread(target=_cleanup_loop, daemon=True)
    cleanup_thread.start()
    logger.info("Session 过期清理任务已启动（清理周期: %d 天）", config.SESSION_EXPIRE_DAYS)

    yield  # 服务运行中

    # 关闭时
    logger.info("Chat Bot Microservice 已停止")


# ============================================================
# 创建 FastAPI 应用
# ============================================================

app = FastAPI(
    title="Chat Bot Microservice",
    description="Ads-ByteDance 广告推荐应用 — AI 聊天微服务",
    version="1.0.0",
    lifespan=lifespan,
)

# CORS — 允许 Android 客户端及本地开发调试
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# 注册路由
app.include_router(sessions.router)
app.include_router(messages.router)
app.include_router(completions.router)


# ============================================================
# 全局异常处理
# ============================================================

@app.exception_handler(Exception)
async def global_exception_handler(request: Request, exc: Exception):
    """捕获未处理的异常，返回统一格式。"""
    logger.exception("未处理的异常: %s", exc)
    return JSONResponse(
        status_code=500,
        content={
            "code": 500,
            "message": f"服务端内部错误: {str(exc)}",
            "data": None,
        },
    )


# ============================================================
# 健康检查
# ============================================================

@app.get("/health", tags=["health"])
async def health_check():
    """健康检查端点。"""
    return {
        "status": "ok",
        "service": "chatbot-microservice",
        "version": "1.0.0",
        "deepseek_configured": bool(config.DEEPSEEK_API_KEY),
    }


# ============================================================
# 直接运行入口
# ============================================================

if __name__ == "__main__":
    import uvicorn

    uvicorn.run(
        "main:app",
        host=config.SERVICE_HOST,
        port=config.SERVICE_PORT,
        reload=True,
        log_level="info",
    )
