# Hướng dẫn chụp ảnh minh chứng cho D6 (mỗi TC 1–2 shot)

> Mục đích: đề bài D6 yêu cầu *"ảnh chụp minh chứng"*. File này chỉ **chính xác từng bước bấm ở đâu trên emulator** để bắt được trạng thái cần chứng minh, kèm lệnh `adb` chụp + lưu ảnh. Chụp xong đặt ảnh vào `docs/mockups/evidence/` (hoặc `D:\…\shots\`) rồi chèn vào `d6-bo-kiem-thu-va-ke-quat.md` §2.

## Chuẩn bị (làm 1 lần)

```bash
# 1. Bật server (terminal 1)
cd D:\CKMobile\backend
start-server.bat                 # hoặc: python -m uvicorn main:app --port 8000

# 2. Bật emulator (terminal 2) — nếu chưa chạy
"D:\android\Sdk\emulator\emulator" -avd Medium_Phone_API_37.0

# 3. Chờ emulator boot xong, cài + mở app
"D:\android\Sdk\platform-tools\adb.exe" install -r D:\CKMobile\app\build\outputs\apk\debug\app-debug.apk
"D:\android\Sdk\platform-tools\adb.exe" shell am start -n com.tdtu.ckmobile/.MainActivity
```

Tạo biến để lười gõ (Git Bash / PowerShell):

```bash
ADB="D:\android\Sdk\platform-tools\adb.exe"
SHOT(){ mkdir -p docs/mockups/evidence && $ADB exec-out screencap -p > "docs/mockups/evidence/$1.png" && echo "đã lưu $1.png"; }
```

> Ghi chú toạ độ: dưới đây là **khoảng chừng** (màn 1080×2400). Nếu ảnh lệch, mở `adb shell uiautomator dump` để xem lại toạ độ chính xác. Nút FAB "+": `(949,2190)`. Nút "Đồng bộ thủ công": `(977,236)`. Ô tiêu đề: `(540,1387)`. Ô mô tả: `(540,1688)`. Nút "Lưu công việc": `(791,2200)`.

---

## TC01 — Tạo task khi online (2 shot)

1. Ensure app ở màn danh sách, badge "Online" (góc phải). `SHOT tc01-before`
2. Bấm FAB → gõ tiêu đề `TC01 Mua sữa` → (mô tả tuỳ) → "Lưu công việc" → đợi ~3s chip chuyển "Đã đồng bộ". `SHOT tc01-after`
   - *Bằng chứng cần thấy:* task mới hiện, badge "0 chưa đồng bộ", chip "Đã đồng bộ".

## TC02 — Offline tạo/sửa (2 shot)

1. Tắt mạng: `adb shell svc wifi disable; adb shell svc data disable` (hoặc nhấn chế độ máy bay trong Extended Controls).
2. Tạo task `TC02 Offline task`. Badge chuyển **"Ngoại tuyến"**, chip task mới = **"Chưa đồng bộ"**. `SHOT tc02-offline`
3. (Tuỳ chọn) Sửa một task có sẵn khi offline → chip "Chưa đồng bộ". `SHOT tc02-edit`

## TC03 — Bật lại mạng, tự sync (1 shot)

1. Bật mạng: `adb shell svc wifi enable; adb shell svc data enable`. Đợi ~8s (hook network auto-sync).
2. Task "TC02 Offline task" chip về **"Đã đồng bộ"**, badge "0 chưa đồng bộ". `SHOT tc03-synced`

## TC04 — Force-stop rồi mở lại (1 shot)

1. `adb shell am force-stop com.tdtu.ckmobile`
2. Mở lại: `adb shell am start -n com.tdtu.ckmobile/.MainActivity`
3. Toàn bộ task vẫn hiện (dữ liệu Room giữ), các task đã sync vẫn "Đã đồng bộ". `SHOT tc04-persist`

## TC05 — Máy chủ lỗi, retry (2 shot)

1. Bật fault: `curl -X POST "http://127.0.0.1:8000/api/debug/fault?return_500=true"`
2. Tạo task `TC05 Fault task` → sync → task chip **"Thất bại" + nút "Thử lại"**. `SHOT tc05-failed`
3. Tắt fault: `curl -X POST "http://127.0.0.1:8000/api/debug/fault?return_500=false"` → bấm "Thử lại" (hoặc đợi backoff ~30s) → chip "Đã đồng bộ". `SHOT tc05-recovered`

## TC06 — Không trùng khi đồng bộ lặp (1 shot)

Bằng chứng nằm **ở server**, chụp bằng log:

```bash
# Trên app, tạo task "TC06 Dup" 1 lần (đã sync). Rồi:
# Mò số task trùng có trên server (mong đợi = 1):
curl -s http://127.0.0.1:8000/api/tasks | python -c "import sys,json;print([t['title'] for t in json.load(sys.stdin) if 'Dup' in t['title']])"
# → ['TC06 Dup']  (chỉ 1 bản)
```
Chụp **output terminal** này + UI không hiện task trùng. `SHOT tc06-single` (có thể screenshot màn terminal).

## TC07 — Xung đột 2 thiết bị, LWW (2 shot)

1. Tạo task `TC07 Milk` (đã sync, giả sử server ở version v).
2. Sửa task đó bằng **API** (đại diện "thiết bị B"):
   ```bash
   curl -X PUT "http://127.0.0.1:8000/api/tasks/<ID>?baseVersion=v" \
        -H "Content-Type: application/json" \
        -d '{"baseVersion":v,"title":"Milk B-EDIT","isCompleted":false,"isDeleted":false}'
   ```
   Lấy `<ID>` và `v` từ `curl http://127.0.0.1:8000/api/tasks`.
3. Trong **app** (thiết bị A), mở cùng task, sửa tiêu đề thành "Milk A-EDIT", "Lưu" → app PUT base cũ → **409 → LWW → conflict banner**.
4. Chụp banner "Đã có thay đổi từ thiết bị khác…". `SHOT tc07-banner`
5. Chụp `conflict_log` phía server:
   ```bash
   python -c "import sqlite3,sys;sys.stdout.reconfigure(encoding='utf-8');[print(r) for r in sqlite3.connect('tasks.db').execute('SELECT task_id,reason FROM conflict_log ORDER BY conflict_time DESC LIMIT 5')]"
   ```
   `SHOT tc07-conflictlog` (screenshot terminal)

## TC08 — Xóa logic, không sống lại (2 shot)

1. Tạo task `TC08 Temp` (đã sync). Bật offline (svc disable), **xoá** nó → chip "Chưa đồng bộ" trên dòng tombstone (nếu thấy) rồi tắt offline. `SHOT tc08-tombstone`
2. Xác nhận server giữ tombstone + UI đã ẩn task:
   ```bash
   python -c "import sqlite3,sys;sys.stdout.reconfigure(encoding='utf-8');[print(r) for r in sqlite3.connect('tasks.db').execute(\"SELECT title,is_deleted FROM tasks WHERE title LIKE 'TC08%'\")]"
   # → ('TC08 Temp', 1)
   ```
   `SHOT tc08-deleted` (list UI không có TC08 + output DB)

## TC09 — Hội tụ thiết bị mới (1 shot)

1. `adb shell pm clear com.tdtu.ckmobile` (xóa toàn bộ data app)
2. Mở lại app → pull `since=0` → list **hội tụ đúng** các task sống trên server (không có task đã xóa). `SHOT tc09-converge`

---

## Sau khi chụp

- Đặt tất cả `.png` vào `docs/mockups/evidence/` (đã tạo sẵn ở lệnh `SHOT`).
- Trong `d6-bo-kiem-thu-va-ke-quat.md` §2, chèn ảnh đúng cột "Bằng chứng":
  ```
  ![TC05 – chip Thất bại](mockups/evidence/tc05-failed.png)
  ```
- Chạy `git add docs/mockups/evidence && git commit` kèm khi nộp (ảnh bằng chứng).

> Gợi ý: nếu chỉ kịp chụp **ít nhất** để đủ "minh chứng", ưu tiên 5 shot: `tc02-offline`, `tc03-synced`, `tc05-failed`, `tc07-banner`, `tc08-deleted` — bao quát cả 4 trạng thái chip + xung đột + xóa logic.
