# Backend Server - FastAPI

**Student:** 521H0471  
**Project:** Offline-First Task Management System

## Tech Stack
- **FastAPI** 0.115.0
- **SQLite** (built-in with Python)
- **Uvicorn** ASGI server

## Setup

### 1. Install dependencies
```bash
cd backend
pip install -r requirements.txt
```

### 2. Run server
```bash
python main.py
```

Server runs at: `http://localhost:8000`

### 3. Run tests
```bash
python test_api.py
```

### 4. API Documentation
- Swagger UI: `http://localhost:8000/docs`
- ReDoc: `http://localhost:8000/redoc`

## API Endpoints

| Method | Endpoint | Description | Success | Errors |
|--------|----------|-------------|---------|--------|
| GET | `/` | Health check | 200 | - |
| GET | `/api/tasks` | List all non-deleted tasks | 200 | - |
| GET | `/api/tasks/changes?since={cursor}&limit={n}` | Incremental sync (cursor-based) | 200 | - |
| POST | `/api/tasks` | Create task (idempotent by client id) | 201 + `Location` | 200 if id exists |
| GET | `/api/tasks/{id}` | Get single task | 200 | 404 |
| PUT | `/api/tasks/{id}` | Update task (optimistic locking) | 200 | 404, 409 |
| DELETE | `/api/tasks/{id}?baseVersion={v}` | Soft delete, returns tombstone | 200 | 404, 409 |
| GET | `/api/tasks/sync?since={ts}` | Legacy sync by timestamp | 200 | - |
| GET | `/api/stats` | Database statistics | 200 | - |
| POST | `/api/debug/fault` | Inject faults for testing | 200 | - |

> **Note:** `/api/tasks/changes` is the recommended endpoint for sync. It uses a
> server-issued monotonic cursor (`changeSeq`), so no record is ever missed even
> if a device's clock is wrong. The older `/api/tasks/sync` compares the client's
> `updatedAt` and is kept only for backward compatibility.

## Data Model

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "title": "Buy milk",
  "description": "From supermarket",
  "isCompleted": false,
  "deadline": null,
  "version": 1,
  "updatedAt": 1729670400000,
  "isDeleted": false,
  "changeSeq": 1
}
```

| Field | Meaning |
|-------|---------|
| `id` | UUID generated **by the client**; primary key on both device and server |
| `version` | Server-managed; incremented on every successful write. Used for conflict detection |
| `updatedAt` | Server timestamp of the last write (milliseconds) |
| `isDeleted` | Soft-delete tombstone flag. Records are never removed from the server |
| `changeSeq` | Monotonic server sequence number; the sync cursor |

## Sync Flow

1. **Push** — client sends each local change:
   - Create: `POST /api/tasks` with client-generated `id`
   - Update: `PUT /api/tasks/{id}` with `baseVersion`
   - Delete: `DELETE /api/tasks/{id}?baseVersion={v}`
2. **Pull** — client calls `GET /api/tasks/changes?since={lastCursor}` and applies
   each returned task, comparing `version` to decide whether to overwrite.
3. The client advances its cursor to `nextCursor` after applying a page.

## Features Implemented

### ✅ Idempotent Create (TC06)
- Uses UUID from client as PRIMARY KEY
- Duplicate POST returns `200` + the existing task; no duplicate is created
- The `Location` header points to the task

### ✅ Conflict Detection (TC07)
- Version-based optimistic locking
- Returns `409` with `VERSION_CONFLICT`, `serverVersion` and the full `current` task
- The losing (server) data is written to the `conflict_log` table for recovery
- Client policy: **Last-Write-Wins by server arrival order** (see design doc §8)

### ✅ Soft Delete (TC08)
- Sets `is_deleted = 1` instead of removing the row
- `DELETE` returns `200` + the tombstone so the client learns the new version
- Repeating a delete is idempotent: no extra version bump, no extra change record
- Tombstones are included in `/api/tasks/changes` so other devices learn about the delete

### ✅ Incremental Sync
- `GET /api/tasks/changes?since={cursor}&limit={n}`
- Cursor is a server-issued `changeSeq`, so it is immune to device clock skew
- Paginated: returns `nextCursor` and `hasMore`
- `changeSeq` is allocated inside the same transaction as the write, so ordering is guaranteed

### ✅ Fault Injection (TC05, TC06)
```bash
# Simulate server error
curl -X POST "http://localhost:8000/api/debug/fault?return_500=true"

# Simulate timeout
curl -X POST "http://localhost:8000/api/debug/fault?return_timeout=true"

# Reset
curl -X POST "http://localhost:8000/api/debug/fault"
```

## Database Schema

### `tasks` table
```sql
CREATE TABLE tasks (
    id TEXT PRIMARY KEY,              -- UUID from client
    title TEXT NOT NULL,
    description TEXT,
    is_completed INTEGER DEFAULT 0,
    deadline INTEGER,                 -- Unix timestamp (ms)
    version INTEGER DEFAULT 1,        -- Server-managed version
    updated_at INTEGER NOT NULL,      -- Server timestamp (ms)
    is_deleted INTEGER DEFAULT 0,     -- Soft delete flag (tombstone)
    change_seq INTEGER NOT NULL       -- Monotonic sync cursor
);
CREATE INDEX idx_tasks_change_seq ON tasks (change_seq);
```

### `sync_counter` table
```sql
CREATE TABLE sync_counter (
    id    INTEGER PRIMARY KEY,
    value INTEGER NOT NULL
);
-- Single row (id = 1) holding the last allocated change_seq.
-- Updated in the same transaction as each write.
```

### `conflict_log` table
```sql
CREATE TABLE conflict_log (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    task_id TEXT NOT NULL,
    lost_data TEXT NOT NULL,          -- JSON of the server version that was overwritten
    conflict_time INTEGER NOT NULL,
    reason TEXT                       -- Conflict description
);
```

## Testing with cURL

### Create task
```bash
curl -X POST "http://localhost:8000/api/tasks" \
  -H "Content-Type: application/json" \
  -d '{
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "title": "Buy milk",
    "description": "From supermarket",
    "isCompleted": false,
    "deadline": null,
    "baseVersion": 0,
    "updatedAt": 1729670400000
  }'
```

### Update task (conflict example)
```bash
curl -X PUT "http://localhost:8000/api/tasks/550e8400-e29b-41d4-a716-446655440000" \
  -H "Content-Type: application/json" \
  -d '{
    "baseVersion": 1,
    "title": "Buy fresh milk",
    "description": "From supermarket",
    "isCompleted": false,
    "deadline": null,
    "isDeleted": false
  }'
```

### Delete task (soft delete)
```bash
curl -X DELETE "http://localhost:8000/api/tasks/550e8400-e29b-41d4-a716-446655440000?baseVersion=2"
```

### List active tasks
```bash
curl "http://localhost:8000/api/tasks"
```

### Incremental sync (recommended)
```bash
# First sync: everything
curl "http://localhost:8000/api/tasks/changes?since=0&limit=100"

# Subsequent syncs: only what changed since the last cursor
curl "http://localhost:8000/api/tasks/changes?since=42&limit=100"
```

### Legacy sync by timestamp
```bash
curl "http://localhost:8000/api/tasks/sync?since=1729670400000"
```

### Get statistics
```bash
curl "http://localhost:8000/api/stats"
```

## File Structure
```
backend/
├── main.py              # FastAPI application
├── test_api.py          # Automated test suite (TC01, TC03, TC05-TC09)
├── requirements.txt     # Python dependencies
├── start-server.bat     # Windows launcher
├── tasks.db             # SQLite database (auto-created)
└── README.md            # This file
```

## Notes
- **No authentication**: For local testing only. Do NOT expose to public internet.
- **LAN access**: Phone can connect via `http://<laptop-ip>:8000` when on same Wi-Fi
- **CORS enabled**: Allows requests from any origin for development

## Finding Your IP (for Android to connect)
```bash
# Windows
ipconfig | grep "IPv4"

# Result example: 192.168.1.100
# Android should use: http://192.168.1.100:8000
```
