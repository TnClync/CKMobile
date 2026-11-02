# D5. Mô-đun đồng bộ và xử lý xung đột

> Tài liệu minh chứng deliverable **D5** của đề tài 01.
> **Minh chứng theo đề bài:** mã nguồn (phần 2), sơ đồ thuật toán (phần 3), ví dụ minh họa các tình huống kèm nhật ký thật (phần 4).

Mô-đun gồm 3 lớp trong repo `app/`:

| Thành phần | File | Trách nhiệm |
|---|---|---|
| `SyncManager` | `app/.../data/sync/SyncManager.kt` | Thực thi 1 chu kỳ đồng bộ: **push trước, pull sau** (2 pha) |
| `SyncWorker` | `app/.../data/sync/SyncWorker.kt` | Worker chạy nền (WorkManager) — gọi `SyncManager.sync()`, trả `retry()/success()/failure()` |
| `SyncScheduler` | `app/.../data/sync/SyncScheduler.kt` | Đặt lịch: unique work + ràng buộc mạng + backoff |

## 1. Tổng quan chu kỳ đồng bộ

```
sync() = mutex {                        // một chu kỳ đồng bộ tại một thời điểm (mục 4.4.2)
    phase 1: PUSH   ≤ 3 vòng (xử lý 409 + rebase)
    phase 2: PULL   theo con trỏ change_seq (đồng bộ tăng dần)
}
```

- **Push trước** để máy chủ có dữ liệu mới nhất của thiết bị và xung đột được phát hiện *ngay trong vòng push* (409), không phải đợi pull.
- **`mutex` dùng chung toàn tiến trình** (`companion processMutex`): cả WorkManager lẫn nút "Đồng bộ" thủ công (qua `TaskRepository.syncNow()`) đều đi qua cùng một khoá, không có 2 chu kỳ chạy song song.
- Push tối đa 3 vòng vì mỗi lần gặp 409, client **rebase** (nâng `base_version` lên version máy chủ) và vòng kế tiếp sẽ gửi lại đúng phiên bản — nếu 3 vòng vẫn còn xung đột (hai thiết bị đổi thay phiên nhau), chu kỳ dừng ở vòng 3 và để chu kỳ sau (backoff) xử, tránh vòng lặp vô tận.

## 2. Mã nguồn chính (trích)

### 2.1. Phân loại kết quả (cơ sở retry)

```kotlin
sealed class SyncResult {
    data object Success : SyncResult()
    data class TransientError(val message: String) : SyncResult()   // retry (5xx, timeout, mất mạng)
    data class PermanentError(val message: String) : SyncResult()  // không retry (400/422 payload sai)
}
```

`SyncWorker` chuyển đổi sang kết quả WorkManager:

```kotlin
return when (syncManager.sync()) {
    is SyncResult.Success          -> Result.success()
    is SyncResult.TransientError  -> if (runAttemptCount < 5) Result.retry() else Result.failure()
    is SyncResult.PermanentError  -> Result.failure()
}
```

`SyncScheduler` đặt backoff + unique work (chống 10 thao tác sinh 10 worker):

```kotlin
val request = OneTimeWorkRequestBuilder<SyncWorker>()
    .setConstraints(networkConstraint)                    // chỉ chạy khi có mạng
    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
    .build()
WorkManager.getInstance(context).enqueueUniqueWork(
    "task-sync", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
```

### 2.2. Heart của push: 1 task, mọi tình huống

```kotlin
private suspend fun pushOneTask(task: TaskEntity): SyncResult {
    val sentRevision = task.localRevision               // bẫy "sửa trong lúc đang đồng bộ"

    if (task.isDeleted && task.baseVersion == 0)        // tạo & xoá offline, chưa kịp lên server
        return run { taskDao.deleteIfUnchanged(task.id, sentRevision); SyncResult.Success }

    taskDao.setStatus(task.id, SyncStatus.SYNCING)

    val response = when {
        task.isDeleted        -> taskApi.delete(task.id, task.baseVersion)
        task.baseVersion == 0 -> taskApi.create(/* id = task.id ... */)   // POST idempotent (TC06)
        else                  -> taskApi.update(task.id, /* baseVersion */ task.baseVersion)
    }

    when (response.code()) {
        200, 201 -> {
            val serverDto = response.body()
            if (serverDto.sameContentAs(task))
                taskDao.markPushed(task.id, serverDto.version, sentRevision)  // SYNCED nếu chưa sửa lại
            else
                taskDao.rebase(task.id, serverDto.version)                    // server đã khác → vòng push sau sẽ gửi lại
        }
        409 -> { /* LWW — xem mục 4.2 */ }
        404 -> taskDao.rebase(task.id, 0)     // máy chủ không có → vòng sau đổi thành POST
        400, 422 -> { taskDao.setStatus(task.id, FAILED); return PermanentError(...) }
        else    -> { taskDao.setStatus(task.id, FAILED); return TransientError(...) }
    }
}
```

### 2.3. Pull: đồng bộ tăng dần theo con trỏ máy chủ

```kotlin
while (hasMore) {
    val changes = taskApi.getChanges(since = cursor, limit = 100)   // GET /api/tasks/changes?since=...
    for (serverTask in changes.tasks) {
        val localTask = taskDao.getById(serverTask.id)
        when {
            localTask == null && !serverTask.isDeleted -> taskDao.upsert(serverTask.toEntity(SYNCED))
            localTask?.syncStatus == SYNCED &&
            serverTask.version > localTask.baseVersion   -> taskDao.upsert(serverTask.toEntity(SYNCED))
            // localTask còn thay đổi chưa gửi (PENDING/FAILED): giữ nguyên,
            // push phase (vòng tới) sẽ xử 409 theo LWW
        }
    }
    cursor = changes.nextCursor; lastCursor = cursor
}
taskDao.purgeSyncedTombstones()   // dọn tombstone đã đồng bộ lâu
```

Con trỏ là **`change_seq` do máy chủ cấp** (không dùng giờ điện thoại) — mục 7.4 tài liệu D1.

## 3. Sơ đồ thuật toán

```mermaid
flowchart TD
    A[[Bắt đầu chu kỳ (worker hoặc nút sync)]] --> M{mutex: đang có chu kỳ khác?}
    M -->|đang có| B[Chờ / bỏ qua]
    M -->|rảnh| P1[PHASE 1 — PUSH (tối đa 3 vòng)]
    P1 --> Q{Còn task syncStatus ≠ SYNCED?}
    Q -->|không| P2[PHASE 2 — PULL]
    Q -->|có| R1{Task loại gì?}
    R1 -->|base_version = 0, chưa xoá| C1[POST /api/tasks (idempotent, TC06)]
    R1 -->|base_version = 0, đã xoá| C0[Xoá khỏi Room, bỏ qua (chưa từng lên server)]
    R1 -->|đã xoá| C2[DELETE /api/tasks/id]
    R1 -->|còn sống| C3[PUT /api/tasks/id + baseVersion]
    C1 --> H{Phản hồi}
    C2 --> H
    C3 --> H
    H -->|200/201 + nội dung trùng| OK1[markPushed → SYNCED nếu revision chưa đổi]
    H -->|200/201 + nội dung khác| OK2[rebase lên version server → vòng push sau gửi lại]
    H -->|409| K[LWW: đọc bản server, ghi conflict_log, thông báo UI, rebase]
    H -->|404| F[rebase về 0 → vòng sau chuyển thành POST]
    H -->|400/422| E1[FAILED, lỗi cố định — không retry]
    H -->|5xx / lỗi mạng| E2[FAILED, lỗi tạm thời → WorkManager retry (backoff 30s ×2ⁿ)]
    OK1 --> Q
    OK2 --> Q
    K --> Q
    F --> Q
    P2 --> L1["GET /api/tasks/changes?since=cursor (trang 100)"]
    L1 --> L2{Lấy bản server, so với bản local}
    L2 -->|không có local & chưa xoá| L3[upsert SYNCED]
    L2 -->|SYNCED & version server > base| L3
    L2 -->|local còn PENDING/FAILED| L4[giữ local — để push xử 409]
    L3 --> L5[cursor = nextCursor; lặp nếu hasMore]
    L4 --> L5
    L5 --> DONE[[Chu kỳ kết thúc → syncStatus UI tự cập nhật qua Flow]]
    E1 --> DONE
    E2 --> DONE
```

*(Render bằng [mermaid.live](https://mermaid.live) hoặc `mmdc`; có thể xuất PNG/SVG dán vào báo cáo.)*

## 4. Ví dụ minh hoạ các tình huống (từ nhật ký kiểm thử thật)

Các trích dẫn HTTP dưới đây lấy nguyên văn từ log `HttpLoggingInterceptor` (level BODY) của ứng dụng khi chạy kiểm thử D6 — không phải mô phỏng.

### 4.1. Tình huống 1 — Đồng bộ lặp, không trùng (TC06, idempotency)

Máy gửi `POST` task có `id` do client sinh (UUID). Lỗi mất phản hồi → worker **gửi lại nguyên y POST**:

```
--> POST http://10.0.2.2:8000/api/tasks
    {"id":"35aad939-…","title":"Fault","baseVersion":0,"updatedAt":1793107650082}
<-- 201 Created            (lần đầu)

--> POST http://10.0.2.2:8000/api/tasks        (gửi lại cùng payload)
<-- 200 OK                  (máy chủ trả bản HIỆN CÓ, KHÔNG tạo task thứ hai)
```

Server (trích `backend/main.py`):

```python
cur.execute("SELECT * FROM tasks WHERE id = ?", (task.id,))
if existing:
    return JSONResponse(status_code=200, content=...,   # trả bản hiện có
                        headers={"Location": f"/api/tasks/{task.id}"})
```

Cả hai phía cộng lại: **client luôn mang cùng một `id` (UUID) cho cùng một task; server luôn trả `200` thay vì tạo mới khi `id` đã tồn tại; truy cập song song bị chặn bởi ràng buộc UNIQUE + bẫy `IntegrityError`** → không có bản ghi trùng, không có thao tác ngoài ý muốn (đúng yêu cầu D5 "tránh gửi trùng").

### 4.2. Tình huống 2 — Xung đột hai thiết bị (TC07, LWW theo thứ tự máy chủ nhận)

Thiết bị A (ứng dụng) và thiết bị B (gọi API trực tiếp) cùng sửa task `550e8400-…` khi cả hai đang ở version 2:

```
B  PUT baseVersion=2  → 200, version thành 3
A  PUT baseVersion=2  → 409 Conflict, body kèm bản server (v3)

A (SyncManager, xử 409):
   • so nội dung: khác (bản B "Milk edited by B")
   • ghi conflict_log:  { type:"EDIT_EDIT", resolution:"LOCAL_WINS",
                           localJson:"…", serverJson:"…" }
   • rebase base_version → 3
A (vòng push kế tiếp):
   PUT baseVersion=3, title="Milk|A-EDIT-local"  → 200, version thành 4  ← bản A thắng (LWW: đến máy chủ sau cùng)
```

Phía máy chủ cũng ghi `conflict_log` (SQLite):

```
('550e8400-…', 'LWW: client base=2, server=3', <thời điểm>)
```

Kết quả cuối: trên mọi thiết bị (kể cả thiết bị chưa từng tham gia) nội dung task đều về đúng một bản — nhờ vòng pull, các thiết bị còn lại sẽ nhận được thay đổi có `change_seq` lớn qua con trỏ tăng dần. Đây là tính **hội tụ (convergence)**: sau khi mọi thiết bị có mạng và không ai sửa gì thêm, dữ liệu ở mọi nơi giống nhau (đo tại D7/TC09).

### 4.3. Tình huống 3 — Xóa logic / tombstone (TC08)

Xoá một task khi offline (base_version chưa kịp lên server = 0):

```
Room: softDelete(id)  →  is_deleted=TRUE, sync_status=PENDING
     (dòng KHÔNG bị xoá vật lý; UI ẩn đi nhờ query WHERE is_deleted = 0)

Sync (vòng push):  task.isDeleted && baseVersion == 0
     →  machine đã tạo rồi xoá khi offline, máy chủ chưa từng biết
     →  xoá vật lý khỏi Room (deleteIfUnchanged), bỏ qua — không gửi gì cả
```

Xoá một task **đã có trên server** (base_version > 0):

```
DELETE /api/tasks/{id}?baseVersion=n
   → 200 + bản tombstone (isDeleted=true, version+1, change_seq+1)
   → Room: markPushed → SYNCED (vẫn giữ dòng, không xoá vật lý)
```

Vòng pull sau này, thiết bị khác nhận tombstone qua `changes`:

```
GET /api/tasks/changes?since=lastCursor
   → tasks: [ {id, isDeleted:true, changeSeq:10} ]
   → local: nếu chưa biết → upsert dòng tombstone; UI ẩn; bản xoá không "sống lại"
```

`GET /api/tasks` (danh sách sống) luôn lọc `is_deleted = 0`, nên bản xóa logic không bao giờ hiện trong danh sách — bằng chứng tại D6 (TC08: "deleted task not in active list").

### 4.4. Tình huống 4 — Máy chủ lỗi (TC05) + retry tự động

```
POST /api/tasks  →  500 Internal Server Error   (fault-injection bật sẵn)
→ SyncManager: markPushed không được gọi; task để FAILED (chip "Thất bại" + nút "Thử lại")
→ WorkManager: Result.retry() → backoff EXPONENTIAL 30s → lần chạy sau tự gửi lại
→ tắt fault: POST → 201; task SYNCED
```

Nhật ký D6 cho thấy 3 vòng: lần 1 (worker do mở app) thất bại vì fault; lần 2 (lệnh sync thủ công ngay sau khi tắt fault) thành công 201; vòng 3 không còn gì để push. Không mất dữ liệu — task được giữ nguyên trạng thái `PENDING/FAILED` và tự khôi phục (yêu cầu 5: "Tự động thử lại khi gặp lỗi tạm thời").

### 4.5. Tình huống 5 — "Sửa trong lúc đang đồng bộ" (bẫy §4.5 tài liệu D1)

1. Worker đọc task `T` (local_revision = r₁), gửi PUT, chờ phản hồi.
2. Trong lúc chờ, người dùng sửa `T` → Room `local_revision = r₂` (r₂ > r₁), `sync_status = PENDING` lại.
3. Nhận `200/201`: `markPushed(id, serverVersion, sentRevision = r₁)` — câu SQL:

   ```sql
   UPDATE tasks SET base_version = :serverVersion,
       sync_status = CASE WHEN local_revision = :sentRevision   -- r₂ ≠ r₁
                          THEN 'SYNCED' ELSE 'PENDING' END        -- → về PENDING
   WHERE id = :id
   ```

   → bản sửa thứ hai tự động trở lại `PENDING` và được gửi ở vòng push tiếp theo (đúng §2.2 "chỉ đánh dấu đã đồng bộ sau khi xác nhận kết quả phù hợp"). Nếu nội dung server trả về *khác* bản địa (409 LWW), thay vào đó là `rebase` (nâng base_version) và vòng sau gửi lại nội dung local (mục 4.2).

## 5. Chính sách xung đột đã chọn và phạm vi mất dữ liệu

Chọn **Last-Write-Wins (LWW) tính theo thứ tự máy chủ nhận** (mục 8.5 tài liệu D1). Trường hợp mất dữ liệu: bản sửa được gửi *đến máy chủ trước* bị ghi đè bởi bản gửi *đến sau*; không phụ thuộc đồng hồ thiết bị (tránh "thiết bị chỉnh giờ sai thắng vô lý"). Toàn bộ dữ liệu bị ghi đè được lưu trong `conflict_log` (cả máy chủ lẫn Room) với các trường `localJson / serverJson / resolution`, cho phép khôi phục thủ công hoặc dùng làm bằng chứng khi đánh giá (D7).

Chính sách này đơn giản, hội tụ nhanh (một vòng push + một vòng pull là đủ để toàn mạng nhất quán), và phù hợp đề tài: xung đột hiếm (số lượng task cá nhân nhỏ), hậu quả nhẹ (ghi chú của task, không phải tiền). Trường hợp hai thiết bị sửa **hai trường khác nhau** (ví dụ A đánh dấu "hoàn thành", B đổi "deadline") LWW vẫn ghi đè một trường — đây là hạn chế đã nêu tại D7 (hướng cải tiến: merge theo trường, hoặc giao diện cho người dùng chọn).

## 6. Đối chiếu các yêu cầu bắt buộc của đề bài (D5)

| Yêu cầu D5 | Đã triển khai ở đâu |
|---|---|
| Đồng bộ hai chiều | `SyncManager.sync()` (push + pull) |
| Theo dõi thay đổi cục bộ | Cờ `sync_status` + `local_revision` (Room) |
| Xử lý xóa logic | `softDelete` + `deleteIfUnchanged` + tombstone trên máy chủ (mục 4.3) |
| Retry | `SyncWorker` → `Result.retry()` + `BackoffPolicy.EXPONENTIAL` (mục 4.4) |
| Tránh gửi trùng | UUID client-side + idempotent `POST` trên server + `markPushed` bảo vệ revision (mục 4.1, 4.5) |
| Chính sách xung đột được triển khai | LWW theo thứ tự nhận + `conflict_log` (mục 4.2, 5) |
| Minh chứng: sơ đồ thuật toán | Mục 3 |
| Minh chứng: ví dụ tình huống | Mục 4 (niếp ảnh từ kiểm thử D6) |
