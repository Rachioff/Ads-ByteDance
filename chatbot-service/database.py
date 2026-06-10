"""
Chat Bot Microservice 数据库层
===========================
基于 sqlite3 的轻量存储，管理 sessions 和 messages 表。
"""

import json
import os
import secrets
import sqlite3
import threading
import time
from typing import Any, Optional

import config

_conn: Optional[sqlite3.Connection] = None
_lock = threading.Lock()


def _now_ms() -> int:
    """返回当前时间的毫秒时间戳。"""
    return int(time.time() * 1000)


def _gen_id(prefix: str) -> str:
    """生成带前缀的唯一 ID，如 sess_a1b2c3d4。"""
    return f"{prefix}_{secrets.token_hex(4)}"


# ============================================================
# 初始化
# ============================================================

def get_conn() -> sqlite3.Connection:
    """获取数据库连接。若未初始化则自动初始化。"""
    global _conn
    if _conn is None:
        init_db()
    assert _conn is not None
    return _conn


def init_db() -> None:
    """初始化数据库连接并创建表。"""
    global _conn
    db_path = config.DB_PATH
    # 确保数据库文件所在目录存在
    db_dir = os.path.dirname(os.path.abspath(db_path))
    if db_dir and not os.path.exists(db_dir):
        os.makedirs(db_dir, exist_ok=True)

    _conn = sqlite3.connect(db_path, check_same_thread=False)
    _conn.row_factory = sqlite3.Row
    _conn.execute("PRAGMA journal_mode=WAL")
    _conn.execute("PRAGMA foreign_keys=ON")

    _conn.executescript("""
        CREATE TABLE IF NOT EXISTS sessions (
            session_id  TEXT PRIMARY KEY,
            user_id     TEXT NOT NULL,
            title       TEXT DEFAULT '新对话',
            created_at  INTEGER NOT NULL,
            updated_at  INTEGER NOT NULL,
            is_active   INTEGER DEFAULT 1
        );
        CREATE INDEX IF NOT EXISTS idx_sessions_user
            ON sessions(user_id, updated_at DESC);

        CREATE TABLE IF NOT EXISTS messages (
            message_id  TEXT PRIMARY KEY,
            session_id  TEXT NOT NULL,
            role        TEXT NOT NULL CHECK(role IN ('user', 'assistant', 'system')),
            content     TEXT NOT NULL,
            ads_json    TEXT,
            intent_json TEXT,
            created_at  INTEGER NOT NULL,
            FOREIGN KEY (session_id) REFERENCES sessions(session_id)
        );
        CREATE INDEX IF NOT EXISTS idx_messages_session
            ON messages(session_id, created_at);
    """)
    _conn.commit()


# ============================================================
# Session CRUD
# ============================================================

def create_session(user_id: str, title: Optional[str] = None) -> dict:
    """创建新会话，返回 session 字典。"""
    conn = get_conn()
    now = _now_ms()
    session_id = _gen_id("sess")
    session_title = title or "新对话"

    with _lock:
        conn.execute(
            "INSERT INTO sessions (session_id, user_id, title, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
            (session_id, user_id, session_title, now, now),
        )
        conn.commit()

    return {
        "sessionId": session_id,
        "title": session_title,
        "createdAt": now,
        "updatedAt": now,
    }


def get_session(session_id: str, user_id: str) -> Optional[dict]:
    """获取指定会话，验证 user_id 归属。"""
    conn = get_conn()
    row = conn.execute(
        "SELECT * FROM sessions WHERE session_id = ? AND user_id = ? AND is_active = 1",
        (session_id, user_id),
    ).fetchone()
    if row is None:
        return None
    return _row_to_session(row)


def list_sessions(user_id: str) -> list[dict]:
    """获取用户的所有活跃会话列表，含消息数和最后消息摘要。"""
    conn = get_conn()
    rows = conn.execute(
        """
        SELECT s.*,
               (SELECT COUNT(*) FROM messages m WHERE m.session_id = s.session_id) AS msg_count,
               (SELECT m2.content FROM messages m2 WHERE m2.session_id = s.session_id
                ORDER BY m2.created_at DESC LIMIT 1) AS last_msg
        FROM sessions s
        WHERE s.user_id = ? AND s.is_active = 1
        ORDER BY s.updated_at DESC
        """,
        (user_id,),
    ).fetchall()

    result = []
    for row in rows:
        d = _row_to_session(row)
        msg_count = row["msg_count"]
        last_msg = row["last_msg"] or ""
        result.append({
            "sessionId": d["sessionId"],
            "title": d["title"],
            "messageCount": msg_count,
            "lastMessage": _truncate(last_msg, 30),
            "createdAt": d["createdAt"],
            "updatedAt": d["updatedAt"],
        })
    return result


def update_session(session_id: str, title: Optional[str] = None) -> None:
    """更新会话标题或时间戳。"""
    conn = get_conn()
    now = _now_ms()
    with _lock:
        if title:
            conn.execute(
                "UPDATE sessions SET title = ?, updated_at = ? WHERE session_id = ?",
                (title, now, session_id),
            )
        else:
            conn.execute(
                "UPDATE sessions SET updated_at = ? WHERE session_id = ?",
                (now, session_id),
            )
        conn.commit()


def delete_session(session_id: str, user_id: str) -> bool:
    """软删除会话（标记 is_active=0）。返回是否成功。"""
    conn = get_conn()
    with _lock:
        cur = conn.execute(
            "UPDATE sessions SET is_active = 0 WHERE session_id = ? AND user_id = ?",
            (session_id, user_id),
        )
        conn.commit()
        return cur.rowcount > 0


def expire_old_sessions() -> int:
    """清理超过 EXPIRE_DAYS 天未活动的 session。返回清理数量。"""
    conn = get_conn()
    threshold = _now_ms() - config.SESSION_EXPIRE_DAYS * 24 * 3600 * 1000
    with _lock:
        cur = conn.execute(
            "UPDATE sessions SET is_active = 0 WHERE is_active = 1 AND updated_at < ?",
            (threshold,),
        )
        conn.commit()
        return cur.rowcount


# ============================================================
# Message CRUD
# ============================================================

def create_message(
    session_id: str,
    role: str,
    content: str,
    ads: Optional[list[dict]] = None,
    intent: Optional[dict] = None,
) -> dict:
    """创建新消息，返回 message 字典。"""
    conn = get_conn()
    now = _now_ms()
    message_id = _gen_id("msg")

    ads_json = json.dumps(ads, ensure_ascii=False) if ads else None
    intent_json = json.dumps(intent, ensure_ascii=False) if intent else None

    with _lock:
        conn.execute(
            "INSERT INTO messages (message_id, session_id, role, content, ads_json, intent_json, created_at) "
            "VALUES (?, ?, ?, ?, ?, ?, ?)",
            (message_id, session_id, role, content, ads_json, intent_json, now),
        )
        # 同时更新 session 的 updated_at
        conn.execute(
            "UPDATE sessions SET updated_at = ? WHERE session_id = ?",
            (now, session_id),
        )
        conn.commit()

    return {
        "messageId": message_id,
        "role": role,
        "content": content,
        "ads": ads,
        "intent": intent,
        "createdAt": now,
    }


def get_history(
    session_id: str,
    user_id: str,
    limit: int = 20,
    before: Optional[int] = None,
) -> tuple[list[dict], bool]:
    """获取会话历史消息，返回 (messages, hasMore)。"""
    conn = get_conn()
    session = get_session(session_id, user_id)
    if session is None:
        return [], False

    if before is None:
        before = _now_ms()

    rows = conn.execute(
        "SELECT * FROM messages WHERE session_id = ? AND created_at < ? "
        "ORDER BY created_at DESC LIMIT ?",
        (session_id, before, limit + 1),
    ).fetchall()

    has_more = len(rows) > limit
    if has_more:
        rows = rows[:limit]

    messages = []
    for row in reversed(rows):  # 按时间升序返回
        messages.append(_row_to_message(row))

    return messages, has_more


def get_recent_messages(session_id: str, limit: int = 10) -> list[dict]:
    """获取最近 N 条消息（用于构造 LLM 上下文），不校验 user_id。"""
    conn = get_conn()
    rows = conn.execute(
        "SELECT * FROM messages WHERE session_id = ? ORDER BY created_at DESC LIMIT ?",
        (session_id, limit),
    ).fetchall()

    messages = []
    for row in reversed(rows):
        messages.append(_row_to_message(row))
    return messages


# ============================================================
# 辅助函数
# ============================================================

def _row_to_session(row: sqlite3.Row) -> dict:
    return {
        "sessionId": row["session_id"],
        "title": row["title"],
        "createdAt": row["created_at"],
        "updatedAt": row["updated_at"],
    }


def _row_to_message(row: sqlite3.Row) -> dict:
    ads = None
    if row["ads_json"]:
        try:
            ads = json.loads(row["ads_json"])
        except json.JSONDecodeError:
            pass

    intent = None
    if row["intent_json"]:
        try:
            intent = json.loads(row["intent_json"])
        except json.JSONDecodeError:
            pass

    return {
        "messageId": row["message_id"],
        "role": row["role"],
        "content": row["content"],
        "ads": ads,
        "intent": intent,
        "createdAt": row["created_at"],
    }


def _truncate(text: str, max_len: int) -> str:
    """截断文本到指定长度。"""
    if len(text) <= max_len:
        return text
    return text[:max_len] + "..."
