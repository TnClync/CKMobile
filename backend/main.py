"""
FastAPI server for Offline-First Task Management
Student: 521H0471
"""
from fastapi import FastAPI, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware
from fastapi.encoders import jsonable_encoder
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field
from datetime import datetime
from typing import Optional, List
import sqlite3
import json
from contextlib import contextmanager

app = FastAPI(title="Task Sync API", version="1.0.0")

# CORS for local development
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Database path
DB_PATH = "tasks.db"

# --- Fault injection for testing ---
fault_config = {"return_500": False, "return_timeout": False}

@app.post("/api/debug/fault")
def set_fault(return_500: bool = False, return_timeout: bool = False):
    """Inject faults for testing TC05, TC06"""
    fault_config["return_500"] = return_500
    fault_config["return_timeout"] = return_timeout
    return {"status": "fault configured", "config": fault_config}

def check_fault():
    """Raise error if fault injection is active"""
    if fault_config["return_500"]:
        raise HTTPException(status_code=500, detail="Simulated server error")
    if fault_config["return_timeout"]:
        import time
        time.sleep(35)  # Trigger client timeout

# --- Database setup ---
@contextmanager
def get_db():
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    try:
        yield conn
    finally:
        conn.close()

def init_db():
    """Initialize database tables"""
    with get_db() as conn:
        # Tasks table with change_seq for incremental sync
        conn.execute("""
            CREATE TABLE IF NOT EXISTS tasks (
                id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                description TEXT,
                is_completed INTEGER DEFAULT 0,
                deadline INTEGER,
                version INTEGER DEFAULT 1,
                updated_at INTEGER NOT NULL,
                is_deleted INTEGER DEFAULT 0,
                change_seq INTEGER NOT NULL DEFAULT 0
            )
        """)
        # Add change_seq column if missing (migration)
        try:
            conn.execute("ALTER TABLE tasks ADD COLUMN change_seq INTEGER NOT NULL DEFAULT 0")
        except sqlite3.OperationalError:
            pass  # Column already exists

        # Index for incremental sync
        conn.execute("""
            CREATE INDEX IF NOT EXISTS idx_tasks_change_seq ON tasks (change_seq)
        """)

        # sync_counter for monotonic change_seq
        conn.execute("""
            CREATE TABLE IF NOT EXISTS sync_counter (
                id INTEGER PRIMARY KEY,
                value INTEGER NOT NULL
            )
        """)
        # Initialize counter if empty
        conn.execute("""
            INSERT OR IGNORE INTO sync_counter (id, value) VALUES (1, 0)
        """)

        # Backfill: gán change_seq tăng dần cho các bản ghi cũ (schema trước khi
        # thêm change_seq) để chúng vẫn xuất hiện trong đồng bộ tăng dần.
        # Mỗi bản ghi phải có change_seq khác nhau, nếu không client có thể bị bỏ sót
        # bản ghi khi phân trang.
        conn.execute("""
            UPDATE tasks SET change_seq = (
                SELECT COUNT(*) FROM tasks t2 WHERE t2.rowid <= tasks.rowid
            ) WHERE change_seq = 0
        """)
        conn.execute("""
            UPDATE sync_counter SET value = (
                SELECT COALESCE(MAX(change_seq), 0) FROM tasks
            ) WHERE id = 1
        """)

        conn.execute("""
            CREATE TABLE IF NOT EXISTS conflict_log (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                task_id TEXT NOT NULL,
                lost_data TEXT NOT NULL,
                conflict_time INTEGER NOT NULL,
                reason TEXT
            )
        """)
        conn.commit()

# --- Pydantic models ---
class TaskRequest(BaseModel):
    """Create request. Idempotent: same id twice never creates a duplicate."""
    id: str = Field(..., min_length=36, max_length=36)
    title: str = Field(..., min_length=1, max_length=200)
    description: Optional[str] = None
    isCompleted: bool = False
    deadline: Optional[int] = None
    baseVersion: int = Field(0, ge=0)
    updatedAt: Optional[int] = None

class UpdateTaskRequest(BaseModel):
    """Update request. baseVersion is required for optimistic locking."""
    baseVersion: int = Field(..., ge=0)
    title: str = Field(..., min_length=1, max_length=200)
    description: Optional[str] = None
    isCompleted: bool = False
    deadline: Optional[int] = None
    isDeleted: bool = False

class TaskResponse(BaseModel):
    id: str
    title: str
    description: Optional[str]
    isCompleted: bool
    deadline: Optional[int]
    version: int
    updatedAt: int
    isDeleted: bool
    changeSeq: int = 0

class ChangesResponse(BaseModel):
    tasks: List[TaskResponse]
    nextCursor: int
    hasMore: bool

class SyncResponse(BaseModel):
    tasks: List[TaskResponse]
    serverTime: int

# --- API endpoints ---
@app.on_event("startup")
def startup():
    init_db()
    print(f"[OK] Server started. Database: {DB_PATH}")

@app.get("/")
def root():
    return {"message": "Task Sync API", "version": "1.0.0"}

# 1. GET /api/tasks - list all non-deleted tasks
@app.get("/api/tasks", response_model=List[TaskResponse])
def list_tasks():
    """Get all non-deleted tasks"""
    check_fault()
    with get_db() as conn:
        cursor = conn.execute("""
            SELECT * FROM tasks WHERE is_deleted = 0 ORDER BY updated_at DESC
        """)
        rows = cursor.fetchall()
        return [row_to_task(dict(row)) for row in rows]

# 2. GET /api/tasks/changes - incremental sync with change_seq
@app.get("/api/tasks/changes", response_model=ChangesResponse)
def get_changes(since: int = Query(0, ge=0), limit: int = Query(100, ge=1, le=1000)):
    """Get changed tasks since change_seq cursor (for incremental sync)"""
    check_fault()
    with get_db() as conn:
        cursor = conn.execute("""
            SELECT * FROM tasks WHERE change_seq > ? ORDER BY change_seq ASC LIMIT ?
        """, (since, limit + 1))  # fetch one extra to check hasMore
        rows = cursor.fetchall()
        tasks = [row_to_task(dict(row)) for row in rows]

        has_more = len(tasks) > limit
        if has_more:
            tasks = tasks[:limit]

        next_cursor = tasks[-1].changeSeq if tasks else since

        return ChangesResponse(
            tasks=tasks,
            nextCursor=next_cursor,
            hasMore=has_more
        )

# 3. POST /api/tasks - create only, idempotent (TC06)
@app.post("/api/tasks", response_model=TaskResponse)
def create_task(task: TaskRequest):
    """Create a task. Idempotent by client-generated id.

    - id chưa tồn tại: tạo mới -> 201 + Location header
    - id đã tồn tại (gửi lại do mất phản hồi): trả bản hiện có -> 200, KHÔNG tạo trùng
    """
    check_fault()

    with get_db() as conn:
        cur = conn.cursor()

        # Check if task already exists BEFORE allocating a change_seq
        cur.execute("SELECT * FROM tasks WHERE id = ?", (task.id,))
        existing = cur.fetchone()
        if existing:
            # Idempotent: task đã tồn tại, trả bản hiện có
            return JSONResponse(
                status_code=200,
                content=jsonable_encoder(row_to_task(dict(existing))),
                headers={"Location": f"/api/tasks/{task.id}"}
            )

        # Allocate next change_seq atomically
        cur.execute("UPDATE sync_counter SET value = value + 1 WHERE id = 1 RETURNING value")
        row = cur.fetchone()
        next_seq = row[0] if row else 1

        server_now = int(datetime.now().timestamp() * 1000)
        updated_at = task.updatedAt if task.updatedAt is not None else server_now

        try:
            cur.execute("""
                INSERT INTO tasks (id, title, description, is_completed, deadline,
                                   version, updated_at, is_deleted, change_seq)
                VALUES (?, ?, ?, ?, ?, 1, ?, 0, ?)
            """, (
                task.id, task.title, task.description,
                int(task.isCompleted), task.deadline, updated_at, next_seq
            ))
            conn.commit()
        except sqlite3.IntegrityError:
            # Race: hai request trùng id đến cùng lúc -> trả bản hiện có
            conn.rollback()
            return JSONResponse(
                status_code=200,
                content=jsonable_encoder(get_task_by_id(task.id, conn)),
                headers={"Location": f"/api/tasks/{task.id}"}
            )

        created = get_task_by_id(task.id, conn)
        return JSONResponse(
            status_code=201,
            content=jsonable_encoder(created),
            headers={"Location": f"/api/tasks/{task.id}"}
        )

# 4. PUT /api/tasks/{id} - update with optimistic locking (TC07)
@app.put("/api/tasks/{task_id}", response_model=TaskResponse)
def update_task(task_id: str, task: UpdateTaskRequest):
    """Update a task. Requires baseVersion matching the server version.

    - version khớp: cập nhật -> 200, version +1
    - version lệch: 409 + bản server hiện tại để client giải quyết xung đột
    - không có task: 404 (client nên gửi lại bằng POST)
    """
    check_fault()

    with get_db() as conn:
        cur = conn.cursor()

        cur.execute("SELECT * FROM tasks WHERE id = ?", (task_id,))
        existing = cur.fetchone()
        if not existing:
            raise HTTPException(status_code=404, detail="Task not found")

        server_version = existing["version"]

        if task.baseVersion != server_version:
            # Log the losing data for the report / recovery
            conn.execute("""
                INSERT INTO conflict_log (task_id, lost_data, conflict_time, reason)
                VALUES (?, ?, ?, ?)
            """, (
                task_id,
                json.dumps(dict(existing)),
                int(datetime.now().timestamp() * 1000),
                f"LWW: client base={task.baseVersion}, server={server_version}"
            ))
            conn.commit()

            raise HTTPException(
                status_code=409,
                detail={
                    "error": "VERSION_CONFLICT",
                    "baseVersion": task.baseVersion,
                    "serverVersion": server_version,
                    "current": jsonable_encoder(row_to_task(dict(existing)))
                }
            )

        # Allocate next change_seq
        cur.execute("UPDATE sync_counter SET value = value + 1 WHERE id = 1 RETURNING value")
        row = cur.fetchone()
        next_seq = row[0] if row else 1

        new_version = server_version + 1
        server_now = int(datetime.now().timestamp() * 1000)

        # Atomic check-and-write: version check lives inside the UPDATE
        cur.execute("""
            UPDATE tasks
            SET title = ?, description = ?, is_completed = ?,
                deadline = ?, version = ?, updated_at = ?, is_deleted = ?, change_seq = ?
            WHERE id = ? AND version = ?
        """, (
            task.title, task.description, int(task.isCompleted),
            task.deadline, new_version, server_now, int(task.isDeleted), next_seq,
            task_id, task.baseVersion
        ))

        if cur.rowcount == 0:
            raise HTTPException(status_code=409, detail="Version mismatch during update")

        conn.commit()
        return get_task_by_id(task_id, conn)

# 5. DELETE /api/tasks/{id} - soft delete, return tombstone (200 + body)
@app.delete("/api/tasks/{task_id}", response_model=TaskResponse)
def delete_task(
    task_id: str,
    base_version: Optional[int] = Query(None, alias="baseVersion"),
    base_version_legacy: Optional[int] = Query(None, alias="base_version"),
):
    """Soft delete with conflict detection. Returns the tombstone (200 + body).

    - version khớp: đánh dấu is_deleted=1 -> 200 + tombstone
    - version lệch: 409 + bản server
    - đã là tombstone: trả 200 + tombstone hiện có, KHÔNG ghi thêm (idempotent)
    """
    check_fault()

    bv = base_version if base_version is not None else base_version_legacy
    if bv is None:
        raise HTTPException(status_code=422, detail="Query param 'baseVersion' is required")

    with get_db() as conn:
        cur = conn.cursor()

        cur.execute("SELECT * FROM tasks WHERE id = ?", (task_id,))
        existing = cur.fetchone()

        if not existing:
            raise HTTPException(status_code=404, detail="Task not found")

        server_version = existing["version"]
        already_deleted = bool(existing["is_deleted"])

        if bv != server_version:
            raise HTTPException(
                status_code=409,
                detail={
                    "error": "VERSION_CONFLICT",
                    "baseVersion": bv,
                    "serverVersion": server_version,
                    "current": jsonable_encoder(row_to_task(dict(existing)))
                }
            )

        # Đã là tombstone: trả bản hiện có, không ghi thêm (tránh tạo thay đổi giả)
        if already_deleted:
            return get_task_by_id(task_id, conn)

        # Allocate next change_seq
        cur.execute("UPDATE sync_counter SET value = value + 1 WHERE id = 1 RETURNING value")
        row = cur.fetchone()
        next_seq = row[0] if row else 1

        new_version = server_version + 1
        cur.execute("""
            UPDATE tasks
            SET is_deleted = 1, version = ?, updated_at = ?, change_seq = ?
            WHERE id = ? AND version = ?
        """, (new_version, int(datetime.now().timestamp() * 1000), next_seq, task_id, bv))

        if cur.rowcount == 0:
            raise HTTPException(status_code=409, detail="Version mismatch")

        conn.commit()
        return get_task_by_id(task_id, conn)

# 6. GET /api/tasks/sync - legacy sync (kept for compatibility)
@app.get("/api/tasks/sync", response_model=SyncResponse)
def sync_tasks(since: Optional[int] = Query(None)):
    """Get changed tasks since timestamp (legacy - use /api/tasks/changes for incremental)"""
    check_fault()

    with get_db() as conn:
        if since:
            cursor = conn.execute("""
                SELECT * FROM tasks WHERE updated_at > ? ORDER BY updated_at ASC
            """, (since,))
        else:
            cursor = conn.execute("""
                SELECT * FROM tasks ORDER BY updated_at ASC
            """)

        rows = cursor.fetchall()
        tasks = [row_to_task(dict(row)) for row in rows]

        return SyncResponse(
            tasks=tasks,
            serverTime=int(datetime.now().timestamp() * 1000)
        )

@app.get("/api/tasks/{task_id}", response_model=TaskResponse)
def get_task(task_id: str):
    """Get single task by ID"""
    with get_db() as conn:
        return get_task_by_id(task_id, conn)

@app.get("/api/stats")
def get_stats():
    """Get database statistics for testing"""
    with get_db() as conn:
        cursor = conn.cursor()
        cursor.execute("SELECT COUNT(*) FROM tasks WHERE is_deleted = 0")
        active_count = cursor.fetchone()[0]

        cursor.execute("SELECT COUNT(*) FROM tasks WHERE is_deleted = 1")
        deleted_count = cursor.fetchone()[0]

        cursor.execute("SELECT COUNT(*) FROM conflict_log")
        conflict_count = cursor.fetchone()[0]

        cursor.execute("SELECT value FROM sync_counter WHERE id = 1")
        change_seq = cursor.fetchone()[0]

        return {
            "activeTasks": active_count,
            "deletedTasks": deleted_count,
            "conflicts": conflict_count,
            "changeSeq": change_seq
        }

# --- Helper functions ---
def get_task_by_id(task_id: str, conn) -> TaskResponse:
    cursor = conn.cursor()
    cursor.execute("SELECT * FROM tasks WHERE id = ?", (task_id,))
    row = cursor.fetchone()
    if not row:
        raise HTTPException(status_code=404, detail="Task not found")
    return row_to_task(dict(row))

def row_to_task(row: dict) -> TaskResponse:
    return TaskResponse(
        id=row["id"],
        title=row["title"],
        description=row["description"],
        isCompleted=bool(row["is_completed"]),
        deadline=row["deadline"],
        version=row["version"],
        updatedAt=row["updated_at"],
        isDeleted=bool(row["is_deleted"]),
        changeSeq=row.get("change_seq", 0)
    )

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)