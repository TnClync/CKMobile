# D7. Báo cáo thực nghiệm và đánh giá

> Minh chứng deliverable **D7** theo đề bài: *Mô tả môi trường thử nghiệm, dữ liệu kiểm thử, thời gian đồng bộ, tỷ lệ thành công, tính nhất quán và hạn chế.*
> Toàn bộ số liệu được đo tự động bằng `backend/benchmark.py` (mã nguồn trong repo), kết quả thô: `docs/d7-benchmark-results.json`.

## 1. Môi trường thử nghiệm

| Hạng mục | Giá trị |
|---|---|
| Máy chủ | Laptop Windows, **FastAPI** (uvicorn) + Python 3.10.3, `http://127.0.0.1:8000` |
| CSDL máy chủ | SQLite file `backend/tasks.db` (WAL) |
| Thiết bị | Android Emulator AVD `Medium_Phone_API_37` (API 37, x86_64) |
| Ứng dụng | `app-debug.apk` (build `:app:assembleDebug`, đã gồm bản sửa mutex dùng chung) |
| Mạng app → máy chủ | `http://10.0.2.2:8000` (alias loopback của host trên emulator) |
| Đồng bộ phía app | WorkManager, unique work `task-sync`, backoff exponential 30 s, pull theo trang 100 |
| Ngôn ngữ đo | Python 3.10.3, `urllib` (HTTP/1.1) |

## 2. Dữ liệu kiểm thử

- **Ba lô dữ liệu** N = **10, 100, 1000** bản ghi. Mỗi lô `BENCH{N}` được **tạo mới** bằng `POST /api/tasks` (mỗi task một dòng `change_seq` riêng), đo xong thì **soft-delete** (giữ tombstone — đúng cơ chế TC08) nên không làm bẩn dữ liệu demo.
- **Pull** lặp **3 lần** mỗi lô, lấy **trung bình** (đề yêu cầu "đo thời gian đồng bộ").
- Pull **tăng dần** theo con trỏ `change_seq` (D5 §2.3), không tải lại toàn bộ mỗi vòng.

## 3. Kết quả đo

### 3.1. Thời gian đồng bộ

**PULL — `GET /api/tasks/changes?since=…&limit=100`**

| N (lô mới) | Tạo dữ liệu (seed) | Pull trung bình | Số trang | ms/bản ghi* |
|---:|---:|---:|---:|---:|
| 10 | 0,11 s | **0,011 s** | 1 | 1,05 |
| 100 | 1,27 s | **0,020 s** | 2 | 0,20 |
| 1000 | 13,60 s | **0,151 s** | 12 | 0,15 |

\* Vì pull kéo **toàn bộ lịch sử** (không chỉ lô mới), mẫu số tăng dần nên ms/bản ghi *giảm* khi dữ liệu tích luỹ — đúng hiệu quả của cơ chế tăng dần: càng nhiều dữ liệu, mỗi bản ghi kéo về càng nhanh.

**PUSH — N lệnh `PUT /api/tasks/{id}` (mỗi task +1 version)**

| N | Tổng thời gian | ms/lệnh | Thành công | Tỷ lệ |
|---:|---:|---:|---:|---:|
| 10 | 0,155 s | 15,54 | 10/10 | **100 %** |
| 100 | 1,498 s | 14,98 | 100/100 | **100 %** |
| 1000 | 13,915 s | 13,91 | 1000/1000 | **100 %** |

**Nhận xét:**
- **Pull rất nhanh, tăng theo số trang**: 12 trang cho 1120 bản ghi lịch sử vẫn chỉ 0,151 s. Nhờ con trỏ `change_seq`, một chu kỳ đồng bộ thực tế chỉ tải phần *thay đổi*.
- **Push tuyến tính, ~14–15 ms/lệnh**: chi phí bị chi phối bởi **độ trễ round-trip mỗi request** (1 chu kỳ TCP+HTTP), không phải xử lý nội dung. ms/lệnh *giảm nhẹ* khi N tăng (15,54→13,91) vì chi phí cố định bị chia dần. Với dữ liệu task cá nhân (vài chục–vài trăm), cả lô đồng bộ xong dưới 1,5 s.
- **Tỷ lệ thành công 100 %** cả 3 lô (máy chủ ổn định). Máy chủ lỗi đo riêng ở D6 (TC05): task → `FAILED`, không mất dữ liệu, WorkManager **tự retry** (backoff) tới khi thành công.

### 3.2. Tính nhất quán (TC09)

| Chỉ số | Giá trị | Mong đợi |
|---|---:|---:|
| Task sống trên máy chủ | 8 | — |
| Thiết bị mới pull (`since=0`) thấy (bản ghi sống) | 8 | = 8 |
| Tổng bản ghi pull về (kể cả tombstone) | 1120 | — |
| **Thiếu** | **0** | 0 |
| **Extra** (bản "ma") | **0** | 0 |
| **Trùng id** trong cùng pull | **0** | 0 |
| **Kết luận hội tụ** | **✓ HỘI TỤ** | ✓ |

Đọc: sau khi tạo + xoá hơn 1100 bản ghi, **một thiết bị mới (cursor = 0) nhận đúng 8 task đang sống** — không thiếu, không thừa, không trùng. Bằng chứng định lượng cho **TC09** và tính **lặp an toàn (idempotency)**.

### 3.3. Tỷ lệ đồng bộ thành công (kể cả khi máy chủ lỗi)

| Tình huống | Kết quả | Nguồn |
|---|---|---|
| Máy chủ ổn định (3 lô N=10/100/1000) | **100 %** (1110/1110 PUT → HTTP 200) | §3.1 |
| Máy chủ trả 500 (fault-injection) | Task `FAILED`, không mất dữ liệu; WorkManager `Result.retry()` + backoff 30 s → tự gửi lại → `201` → `SYNCED` | D6 §2, TC05 |
| Timeout 35 s (server ngủ > timeout client 15 s) | Client chủ động bỏ, retry sau, không treo worker | D6 §2, D5 §4.4 |

## 4. Phân tích

- **Hội tụ** — đạt: thiết bị mới về đúng 1 bản với máy chủ (§3.2). Tiêu chí quan trọng nhất của đề.
- **Không trùng (idempotency)** — đạt: 0 trùng sau push lặp 3 lô + pull lặp 3 lần; cơ chế là UUID do client sinh + `POST` idempotent (D5 §4.1).
- **Xung đột đúng chính sách** — đạt: `conflict_log` ghi đủ bản thắng/bản thua khi gặp 409 (D5 §4.2); không có bản "ma".
- **Chi phí mở rộng** — PULL ~O(tổng thay đổi) nhờ con trỏ; PUSH ~O(N). Với task cá nhân, cả hai dưới 15 s cho 1000 bản — không phải rào cản.
- **Lưu ý công bằng của phép đo**: `ms/bản ghi` ở PULL tính trên *toàn bộ* lịch sử. Lô đầu (N=10) lịch sử ít → 1,05; các lô sau lịch sử lớn → 0,15. Đo "chi phí biên" (mỗi bản ghi mới) thì lấy **hiệu hai lô liên tiếp**: (13,915 − 1,498) ≈ 12,4 s cho 900 bản → ~13,8 ms/bản mới, khớp ms/lệnh của PUSH. Cả hai đều cho thấy **chi phí bị chi phối bởi round-trip HTTP, không phải xử lý dữ liệu**.

## 5. Hạn chế

| Hạn chế | Ảnh hưởng | Cách khắc phục / hướng cải tiến |
|---|---|---|
| LWW **ghi đè trường** (A sửa tiêu đề, B sửa deadline → 1 trường mất) | Mất dữ liệu tại trường bị ghi đè (đã lưu `conflict_log` hai phía) | Merge theo trường (field-level) hoặc giao diện cho người dùng chọn (D5 §5) |
| WorkManager **không chạy đúng giây** (Doze, OEM diệt task nền) | Độ trễ đồng bộ không xác định trên một số máy thật | Trong "phạm vi bảo đảm của hệ điều hành"; Periodic 15 phút làm lưới an toàn |
| Đo trên **máy chủ local** | Chưa phản ánh RTT Internet thật (20–150 ms) | Khi triển khai thật, ms/lệnh của PUSH tăng; nên gom nhiều thay đổi trong 1 request (batch) |
| Đo qua **emulator** (10.0.2.2) | Có thêm tầng NAT so với điện thoại thật | Đo lại trên điện thoại thật |
| **N > 1000** chưa đo | PUSH tuyến tính có thể lên hàng phút | Gom theo lô (batch PUT) / song song; phân trang phía máy chủ |
| Chưa stress **2 thiết bị thật song song** | Chưa đo tải xung đột đồng thời cao | Mở 2 AVD hoặc app + điện thoại thật cùng trỏ một máy chủ |
| Tombstone giữ **vĩnh viễn** trong DB | DB phình dần | Đã có `purgeSyncedTombstones()` (D5 §2.3) — thêm lịch dọn định kỳ |

## 6. Kết luận

Ứng dụng đạt các tiêu chí đề bài:

1. **Đồng bộ hai chiều đúng**: 1110 push thành công (100 %), pull tăng dần kéo 1120 bản trong 0,151 s.
2. **Tính nhất quán**: thiết bị mới hội tụ về đúng 8 task sống — thiếu 0, thừa 0, trùng 0 (TC09 ✓).
3. **Lặp an toàn**: 0 trùng dù push/pull lặp nhiều lần (TC06 ✓).
4. **Chịu lỗi**: 500 → `FAILED` + retry backoff, không mất dữ liệu (TC05 ✓).
5. **Xung đột**: phát hiện bằng version (409), giải quyết theo LWW, `conflict_log` đầy đủ (TC07 ✓).

Về hiệu năng, chi phí đồng bộ cho dữ liệu ứng dụng thực tế rất nhỏ: < 1,5 s cho 100 bản và 14 s cho 1000 bản, phần lớn là round-trip HTTP chứ không phải xử lý dữ liệu. Hạn chế lớn nhất không nằm ở hiệu năng mà ở **chính sách LWW** (ghi đè trường) và **tính không xác định của WorkManager** — đã nêu ở mục 5 kèm hướng cải tiến.

---

## Phụ lục A. Cách tái hiện đo

```bash
cd D:\CKMobile\backend
python -m uvicorn main:app --port 8000      # terminal 1: máy chủ
python benchmark.py                          # terminal 2: đo + ghi d7-benchmark-results.json
```

Kết quả lần chạy đã dùng trong báo cáo:

```
N=   10 seed=0.11s | pull: 0.011s (1 trang)  |  1.05 ms/bản | push 0.155s, 15.54 ms/lệnh, 10/10
N=  100 seed=1.27s | pull: 0.020s (2 trang)  |  0.20 ms/bản | push 1.498s, 14.98 ms/lệnh, 100/100
N= 1000 seed=13.60s| pull: 0.151s (12 trang) |  0.15 ms/bản | push 13.915s, 13.91 ms/lệnh, 1000/1000
Nhất quán: server=8, pulled=8, thiếu=0, extra=0, trùng=0 -> HỘI TỤ ✓
```

## Phụ lục B. Số liệu thô (`d7-benchmark-results.json`)

| Nhóm | N=10 | N=100 | N=1000 |
|---|---:|---:|---:|
| Seed (s) | 0,110 | 1,269 | 13,597 |
| Pull trung bình (s) | 0,0105 | 0,0199 | 0,1508 |
| Pull trang | 1 | 2 | 12 |
| Push tổng (s) | 0,155 | 1,498 | 13,915 |
| Push ms/lệnh | 15,54 | 14,98 | 13,91 |
| Push thành công | 10/10 | 100/100 | 1000/1000 |

Nhất quán: `server_active=8`, `pulled_records=1120`, `pulled_live=8`, `missing=0`, `extra=0`, `duplicates=0`, `converged=true`.
