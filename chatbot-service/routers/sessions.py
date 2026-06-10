"""
会话管理 API
===========
POST   /api/sessions           — 创建会话
GET    /api/sessions           — 获取会话列表
DELETE /api/sessions/{id}      — 删除会话
"""

from fastapi import APIRouter, Header, HTTPException, Path
from starlette import status

from database import create_session, delete_session, get_session, list_sessions
from models import CreateSessionRequest, SessionListData, ok

router = APIRouter(prefix="/api/sessions", tags=["sessions"])


@router.post("", status_code=status.HTTP_201_CREATED)
async def create_new_session(
    body: CreateSessionRequest | None = None,
    x_user_id: str = Header(..., alias="X-User-Id"),
):
    """创建新会话。"""
    title = body.title if body else None
    session = create_session(user_id=x_user_id, title=title)
    return ok(session)


@router.get("")
async def list_user_sessions(
    x_user_id: str = Header(..., alias="X-User-Id"),
):
    """获取用户的所有活跃会话列表。"""
    sessions = list_sessions(user_id=x_user_id)
    return ok({"sessions": sessions})


@router.delete("/{session_id}")
async def remove_session(
    session_id: str = Path(...),
    x_user_id: str = Header(..., alias="X-User-Id"),
):
    """删除（软删除）指定会话。"""
    session = get_session(session_id, user_id=x_user_id)
    if session is None:
        raise HTTPException(status_code=404, detail="会话不存在")
    delete_session(session_id, user_id=x_user_id)
    return ok(None)
