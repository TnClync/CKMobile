# D8 — Hướng dẫn sử dụng và trình diễn

> Minh chứng deliverable **D8**: *APK cài đặt được · video demo các kịch bản chính · hướng dẫn cài đặt server, chạy ứng dụng và tái hiện tình huống mất mạng.*

| Thành phần | Vị trí |
|---|---|
| APK cài đặt được (đã gồm bản sửa mutex) | [`app-debug.apk`](../app-debug.apk) (17 MB, Android 7.0+) |
| Video demo 9 kịch bản TC01–TC09 | *đặt tại `BAOCAO-GIUA-KY/08-D8-San-pham-trinh-diem/video-demo-TC01-TC09.mp4`* |
| Máy chủ + tài liệu API | [`../backend/`](../backend/) (xem `backend/README.md`) |
| Mã nguồn app + hướng dẫn build | gốc repo (xem [`README.md`](../README.md)) |
| Công cụ hỗ trợ demo phía máy chủ | [`../tools/demo-tc.bat`](../tools/demo-tc.bat) (menu 1–9, dùng khi quay/chạy lại demo) |

---

## 1. Cài đặt và chạy

### 1.1. Máy chủ (FastAPI)

```powershell
cd CKMobile\backend
pip install -r requirements.txt
python -m uvicorn main:app --host 0.0.0.0 --port 8000
```

Kiểm tra: `http://localhost:8000/` → `{"message":"Task Sync API","version":"1.0.0"}`.
Tài liệu API tương tác (Swagger): `http://localhost:8000/docs`.

> Trên máy dùng `py` (không có lệnh `python`): thay bằng `py -m uvicorn ...` hoặc chạy file sẵn `backend\start-server.bat`.

### 1.2. Ứng dụng Android — 1 trong 2 cách

**Cách A (khuyến nghị): cài APK có sẵn**

1. Cài [`app-debug.apk`](../app-debug.apk) lên điện thoại/AVD (cần bật "Cài từ nguồn không xác định" nếu máy thật).
2. Mở app. Trên **emulator** app đã trỏ sẵn tới `http://10.0.2.2:8000` (= máy chủ trên laptop, xem mục 1.4).
3. Trên **điện thoại thật** (cùng mạng Wi-Fi): mở **Sửa thông tin** trong app (hoặc build lại và đổi `baseUrl` trong `app/src/main/java/com/tdtu/ckmobile/data/remote/RetrofitClient.kt` thành IP LAN của máy chạy server, VD `http://192.168.1.100:8000/`).

**Cách B: build từ mã nguồn**

```powershell
# Java 17 + Android SDK; mở bằng Android Studio hoặc:
cd CKMobile
$env:JAVA_HOME="D:\android\jdk-17"
.\gradlew.bat assembleDebug
# APK: app\build\outputs\apk\debug\app-debug.apk
```

### 1.3. Emulator

```powershell
& "D:\android\Sdk\emulator\emulator.exe" -avd Medium_Phone_API_37.0
```

### 1.4. Mạng app → máy chủ

| Môi trường | Địa chỉ API | Giải thích |
|---|---|---|
| Emulator (Android) | `http://10.0.2.2:8000` | `10.0.2.2` là alias của máy chủ host theo tài liệu chính thức của Android |
| Máy thật (cùng Wi-Fi) | `http://<IP_LAN_máy_chủ>:8000` | server phải chạy `--host 0.0.0.0` và mở port 8000 trên tường lửa |

---

## 2. Tái hiện tình huống mất mạng (Yêu cầu bắt buộc của D8)

Có 3 cách, dùng **cách 1 hoặc 2** (cách 3 dùng cho TC04):

### Cách 1 — Emulator: tắt "Cellular" (nhanh nhất, dùng khi quay video)

1. Emulator chạy, mở app.
2. Kéo thanh trạng thái xuống (chạm vùng giờ PIN) → mục **Network** → gõ *airplane* hoặc kéo công tắc **Airplane mode** (hoặc Extended Controls ⚠️ → **Cellular → No Service**).
3. Trên app: banner hổ phách **"Ngoại tuyến"** hiện ra; các thao tác vẫn được lưu vào Room với chip **"Chưa đồng bộ"**.
4. **Bật lại mạng** → ~8 giây sau app **tự** đồng bộ (không cần bấm), chip về "Đã đồng bộ", badge "0 chưa đồng bộ".

### Cách 2 — Lệnh adb (chạy từ máy chủ)

```powershell
D:\android\Sdk\platform-tools\adb.exe shell svc wifi disable     # tắt
D:\android\Sdk\platform-tools\adb.exe shell svc data disable      # (tắt cả dữ liệu)
# ... thao tác trên app ...
D:\android\Sdk\platform-tools\adb.exe shell svc wifi enable      # bật lại
D:\android\Sdk\platform-tools\adb.exe shell svc data enable
```

### Cách 3 — Máy thật

Tắt Wi-Fi + 3G/4G trong Cài đặt của điện thoại; bật lại để đồng bộ.

### Kiểm chứng phía máy chủ (bằng chứng)

```powershell
# Đếm task đang sống trước và sau khi bật lại mạng:
curl.exe -s http://127.0.0.1:8000/api/tasks | ConvertFrom-Json | Where-Object { -not $_.isDeleted }.Count
# Khi offline: số KHÔNG đổi; sau khi online + vài giây: số TĂNG đúng số task vừa tạo/sửa.
```
Hoặc dùng menu tương ứng trong `tools\demo-tc.bat` (mục **2**).

---

## 3. Kịch bản trình diễn (tham khảo video demo)

| Kịch bản | Thao tác | Bằng chứng |
|---|---|---|
| TC01 Tạo khi online | `+` → Lưu | task có trên app lẫn `GET /api/tasks` |
| TC02 Offline tạo/sửa | tắt mạng → tạo/sửa | badge "Ngoại tuyến", chip "Chưa đồng bộ" |
| TC03 Phục hồi mạng | bật mạng, đợi 8s | chip tự "Đã đồng bộ" |
| TC04 Đóng/mở app | `adb shell am force-stop` rồi mở lại | danh sách nguyên vẹn |
| TC05 Server lỗi | `POST /api/debug/fault?return_500=true` rồi tạo task | chip "Thất bại" + "Thử lại"; tắt fault → retry thành công |
| TC06 Đồng bộ lặp | gửi lại `POST /api/tasks` với cùng id | server trả 200, không trùng |
| TC07 Xung đột | sửa cùng task bằng API rồi bằng app | 409 → LWW + banner + `conflict_log` |
| TC08 Xóa offline | xoá khi offline | tombstone `is_deleted=1`, không "sống lại" |
| TC09 Hội tụ | `adb shell pm clear` rồi mở app | danh sách đúng bản server |

Toàn bộ bước phía máy chủ được gói sẵn trong menu của [`tools/demo-tc.bat`](../tools/demo-tc.bat).

---

## 4. Kiểm thử nhanh (không cần app)

```powershell
cd CKMobile\backend
python test_api.py        # 38/38 PASS (bộ test API, xem docs/d6-bo-kiem-thu-va-ke-quat.md)
python benchmark.py       # đo TC09: thời gian, tỷ lệ thành công, nhất quán (kết quả: docs/d7-benchmark-results.json)
```
