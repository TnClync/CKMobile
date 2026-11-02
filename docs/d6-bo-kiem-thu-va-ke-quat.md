# D6. Bộ kiểm thử và kết quả kiểm thử

> Minh chứng deliverable **D6** theo đề bài: *Test cases, kết quả pass/fail, log hoặc ảnh chụp minh chứng.*

## 1. Hai tầng kiểm thử

| Tầng | Công cụ | Chạy ở đâu | Trạng thái |
|---|---|---|---|
| **API (server)** | `backend/test_api.py` — 38 kiểm thử viết tay, không cần phụ thuộc | Trên máy chủ (127.0.0.1:8000) | **38/38 PASS** |
| **Ứng dụng (E2E)** | Kịch bản TC01–TC09 trên **Android Emulator + máy chủ thật**, thao tác qua `adb` (tạo/sửa/xóa, tắt mạng, fault injection, force-stop, wipe data) | Emulator `Medium_Phone_API_37` (API 37) + `app-debug.apk` | **9/9 PASS** |

Cách chạy bộ kiểm thử API (đề bài yêu cầu "bộ kiểm thử" phải tái tạo được):

```bash
cd backend
python -m venv venv && venv\Scripts\activate     # Windows
pip install -r requirements.txt
uvicorn main:app --host 127.0.0.1 --port 8000    # terminal 1
python test_api.py                                 # terminal 2
```

Kết quả lần chạy (môi trường Windows, Python 3.10, ngày 02/11/2026):

```
✓ All tests passed!
(trong đó: TC01: 4/4, TC06: 5/5, TC07: 4/4 + 404 check, TC08: 8/8,
 incremental-sync: 5/5, TC05 fault: 2/2, stats: 4/4 → tổng 38 [PASS], 0 [FAIL])
```

Log đầy đủ được lưu kèm khi nộp: `d6-testrun.log`.

## 2. Kết quả 9 kịch bản bắt buộc (chạy trên emulator + máy chủ thật)

Môi trường: máy chủ FastAPI chạy trên host (`0.0.0.0:8000`), ứng dụng trỏ tới `http://10.0.2.2:8000` (alias loopback của host trên emulator). Bằng chứng là **log HTTP thật** (`HttpLoggingInterceptor` mức BODY) và **trạng thái database thật** (Room trên máy, `tasks.db` trên server).

| TC | Kịch bản | Kết quả mong đợi (đề) | Kết quả thực tế | Bằng chứng (trích log thật) |
|:--:|---|---|:--:|---|
| **TC01** | Tạo công việc khi có Internet | Dữ liệu xuất hiện trên thiết bị **và** máy chủ | ✅ PASS | `POST /api/tasks` → `201 Created`; task hiện trong UI + trong `GET /api/tasks` của server |
| **TC02** | Tắt Internet, tạo và sửa công việc | Dữ liệu vẫn lưu và hiển thị từ Room | ✅ PASS | `svc wifi/data disable` → badge UI "Ngoại tuyến · thay đổi sẽ đồng bộ khi có mạng"; chip task mới = "Chưa đồng bộ" (`content-desc="Chưa đồng bộ"`); server `GET /api/tasks` **không** có task mới (chưa push được) |
| **TC03** | Bật lại Internet | Thay đổi ngoại tuyến tự động đồng bộ | ✅ PASS | Hook `networkMonitor` trong ViewModel tự gọi `syncNow()` khi chuyển offline→online: log `Starting sync cycle...` → `GET /api/tasks/changes?since=5` → `200`; task "Offline" (tạo khi TC02) xuất hiện trên server; chip về "Đã đồng bộ", badge "0 chưa đồng bộ" |
| **TC04** | Đóng ứng dụng rồi mở lại | Dữ liệu cục bộ vẫn còn; tác vụ đồng bộ tiếp tục | ✅ PASS | `am force-stop` → mở lại: toàn bộ danh sách hiện nguyên trong Room; WorkManager giữ work "task-sync" (persistent) → chu kỳ sync chạy tiếp, không mất thay đổi |
| **TC05** | Máy chủ tạm thời không phản hồi | Không mất thay đổi; có cơ chế thử lại | ✅ PASS | Bật fault-injection (server trả `500`): `POST /api/tasks → 500 Internal Server Error` → task giữ trạng thái `FAILED` (chip "Thất bại" + nút "Thử lại"); WorkManager `Result.retry()` backoff 30s → 30s sau tự chạy lại; tắt fault → `POST → 201` → `SYNCED` (xem trích log mục 4.4 tài liệu D5) |
| **TC06** | Đồng bộ cùng một thay đổi nhiều lần | Không tạo bản ghi trùng | ✅ PASS | Gửi lại `POST` với cùng `id` (UUID client): server trả `200` bản hiện có (không phải `201`), `version` và `change_seq` không đổi — kiểm chứng bằng API: "exactly one record on server – got 1" (5/5 test TC06 PASS) |
| **TC07** | Hai thiết bị sửa cùng một công việc | Phát hiện + xử lý theo chính sách đã công bố (LWW) | ✅ PASS | Thiết bị B (API) sửa trước (v3) → thiết bị A (app) sửa khi đang ở v3: `PUT baseVersion=3 → 409 Conflict`, body chứa `VERSION_CONFLICT, baseVersion:3, serverVersion:4, current:{...}`; app LWW: ghi `conflict_log` (Room), rebase, vòng push kế tiếp `PUT baseVersion=4 → 200, version=5`; server ghi `conflict_log`: `"LWW: client base=3, server=4"` |
| **TC08** | Xóa công việc khi ngoại tuyến | Bản ghi đã xóa không "sống lại" | ✅ PASS | Xoá khi offline: Room `softDelete` (`is_deleted=TRUE`, UI ẩn); có mạng: `DELETE /api/tasks/{id}` → `200` tombstone; DB server: task có `is_deleted=1, version+1`; pull về thiết bị khác: nhận tombstone qua `/api/tasks/changes` → dòng tombstone được upsert, UI không hiện → bản xoá không sống lại. Kiểm chứng API: "deleted task not in active list" PASS |
| **TC09** | Đồng bộ nhiều bản ghi | Hội tụ toàn bộ (nội dung chi tiết đo tại D7) | ✅ PASS (tính đúng đắn) | `pm clear` (xóa toàn bộ data app) → khởi động: `GET /api/tasks/changes?since=0` → tải toàn bộ → UI hội tụ đúng 3 task sống ("Mua sữa" v6, "Nộp báo cáo", "Gội đầu"), task "Fault" (đã xóa) **không** hiện; `since=10 → 200 (không có gì mới)`; chip toàn "Đã đồng bộ" |

> Ghi chú: số liệu định lượng của TC09 (thời gian, tỷ lệ, N = 10/100/1000) thuộc **D7** (báo cáo thực nghiệm), chưa đo tại đây.

## 3. Phân loại kết quả

| | Số lượng |
|---|---|
| Kiểm thử API (`test_api.py`) | **38/38 PASS** |
| Kịch bản bắt buộc (emulator E2E) | **9/9 PASS** |
| Lỗi phát hiện & sửa trong quá trình kiểm thử | 1 — mutex riêng của 2 instance `SyncManager` (worker + nút sync) cho phép 2 chu kỳ đồng bộ song song; đã sửa bằng `processMutex` dùng chung toàn tiến trình (commit `1d0af92`) |

## 4. Công cụ tái hiện tình huống (dùng trong khi kiểm thử)

| Tình huống | Cách tạo | Dùng cho |
|---|---|---|
| Mất mạng | `adb shell svc wifi disable; svc data disable` (hoặc chế độ máy bay) | TC02, TC03 |
| Máy chủ lỗi 500 | `POST /api/debug/fault?return_500=true` | TC05 |
| Timeout (35s) | `POST /api/debug/fault?return_timeout=true` | TC05 (mất phản hồi) |
| Xung đột 2 thiết bị | 1 app (emulator) + gọi API trực tiếp trên host cùng sửa 1 task | TC07 |
| Thiết bị mới / hội tụ | `adb shell pm clear com.tdtu.ckmobile` | TC09 |
| Đóng app | `adb shell am force-stop` | TC04 |

*(Trạng thái fault được reset bằng `POST /api/debug/fault` không kèm tham số; kết quả "Reset" đã kiểm tra trong `test_api.py` — "TC05 reset restores 200" PASS.)*

## 5. Bằng chứng đính kèm (danh mục nộp)

1. `backend/test_api.py` — mã nguồn bộ kiểm thử (nằm trong repo)
2. `d6-testrun.log` — output lần chạy 38/38 PASS
3. Trích log HTTP trong `docs/d5-mo-dun-dong-bo-xu-ng-dot.md` mục 4 (5 tình huống, nguyên văn từ log thật)
4. `docs/d7-thuc-nghiem-danh-gia.md` — số liệu TC09 (đo riêng, D7)
