# Ứng dụng Quản lý Công việc Offline-First (Android & FastAPI)

**Đề tài:** Xây dựng ứng dụng Android hỗ trợ hoạt động ngoại tuyến và đồng bộ dữ liệu tự động  
**Sinh viên:** MSSV 521H0471  
**Công nghệ:** Kotlin (Jetpack Compose, Room, MVVM, Retrofit, WorkManager, Coroutines/Flow) + FastAPI (Python, SQLite)

---

## 📱 Cấu trúc Giao diện Android (Jetpack Compose)

Giao diện được thiết kế bám sát 100% tài liệu thiết kế ([`design-document.md`](design-document.md)) và các ảnh mockup tại ([`docs/mockups/`](docs/mockups/)):

| Thành phần UI | File Kotlin | Mô tả |
|---|---|---|
| **Màn hình chính** | [`TaskScreen.kt`](app/src/main/java/com/tdtu/ckmobile/ui/screens/TaskScreen.kt) | Hiển thị tiêu đề "Công việc", số lượng công việc & số task chưa đồng bộ, trạng thái mạng (Online/Ngoại tuyến), nút đồng bộ thủ công, danh sách task và nút FAB (+) |
| **Chip trạng thái đồng bộ** | [`SyncStatusChip.kt`](app/src/main/java/com/tdtu/ckmobile/ui/components/SyncStatusChip.kt) | 4 biến thể (`PENDING`, `SYNCING`, `SYNCED`, `FAILED`) theo mockup `05-SyncStatus-4variants.png`. Task `FAILED` có thêm nút **"Thử lại"** |
| **Banner Ngoại tuyến** | [`OfflineBanner.kt`](app/src/main/java/com/tdtu/ckmobile/ui/components/OfflineBanner.kt) | Thanh cảnh báo màu hổ phách khi mất kết nối Internet theo mockup `02-list-offline.png` |
| **Banner Xung đột (LWW)** | [`ConflictBanner.kt`](app/src/main/java/com/tdtu/ckmobile/ui/components/ConflictBanner.kt) | Thông báo khi hệ thống tự động giải quyết xung đột theo quy tắc Last-Write-Wins (cuối-gọi-thắng) theo mockup `04-form-edit.png` |
| **Thẻ công việc (Card)** | [`TaskCard.kt`](app/src/main/java/com/tdtu/ckmobile/ui/components/TaskCard.kt) | Checkbox bo góc, gạch ngang khi hoàn thành, mã task (T1, T2...), tiêu đề, mô tả, hạn chót (kèm icon lịch), chip đồng bộ |
| **Form Thêm công việc** | [`AddTaskBottomSheet.kt`](app/src/main/java/com/tdtu/ckmobile/ui/components/AddTaskBottomSheet.kt) | ModalBottomSheet nhập tiêu đề, mô tả, chọn hạn chót theo mockup `03-form-add.png` |
| **Form Sửa công việc** | [`EditTaskBottomSheet.kt`](app/src/main/java/com/tdtu/ckmobile/ui/components/EditTaskBottomSheet.kt) | ModalBottomSheet chỉnh sửa, cảnh báo khi đồng bộ thất bại, nút **"Xóa công việc"** (soft delete) theo mockup `04-form-edit.png` |
| **Theme & Màu sắc** | [`Color.kt`](app/src/main/java/com/tdtu/ckmobile/ui/theme/Color.kt), [`Theme.kt`](app/src/main/java/com/tdtu/ckmobile/ui/theme/Theme.kt) | Bảng màu chuẩn: Primary `#4E65CD`, Pending `#FFF3E0`, Syncing `#E3F2FD`, Synced `#E8F5E9`, Failed `#FFEBEE` |

---

## 🏗 Kiến trúc Hệ thống

- **Presentation:** Jetpack Compose + `TaskViewModel` (sử dụng `StateFlow<TaskUiState>`).
- **Data (Offline-First):** 
  - Room Database là **Single Source of Truth** cho giao diện.
  - `TaskDao`: Cung cấp `Flow<List<TaskEntity>>`, xử lý soft delete (`isDeleted = true`), theo dõi phiên bản (`baseVersion`, `localRevision`).
  - `SyncManager`: Động cơ đồng bộ 2 chiều (Push -> Pull), xử lý xung đột HTTP 409 bằng Last-Write-Wins (LWW) theo thứ tự server nhận, lưu nhật ký `conflict_log`.
  - `SyncScheduler` & `SyncWorker`: WorkManager tự động kích hoạt đồng bộ khi có mạng, cơ chế exponential backoff retry.
  - `NetworkMonitor`: Theo dõi trạng thái mạng theo thời gian thực bằng `ConnectivityManager.NetworkCallback`.

---

## 🚀 Hướng dẫn Chạy Thử nghiệm

### 1. Khởi động Backend Server
```bash
cd backend
pip install -r requirements.txt
python main.py
```
- Server chạy tại: `http://localhost:8000`
- Swagger API Docs: `http://localhost:8000/docs`

### 2. Mở Ứng dụng Android trong Android Studio
1. Mở **Android Studio** -> Chọn **Open** -> Chọn thư mục `CKMobile`.
2. Chờ Gradle sync xong.
3. Chạy trên Android Emulator hoặc thiết bị thật:
   - Trên **Android Emulator**: Địa chỉ API mặc định trỏ về máy tính chủ qua `http://10.0.2.2:8000/`.
   - Trên **Thiết bị thật**: Đổi `baseUrl` trong [`RetrofitClient.kt`](app/src/main/java/com/tdtu/ckmobile/data/remote/RetrofitClient.kt) thành IP mạng LAN máy tính của bạn (VD: `http://192.168.1.100:8000/`).

---

## 📋 Kiểm thử các Kịch bản bắt buộc (TC01 - TC08)

1. **TC01 (Tạo khi có Internet):** Nhấn `+`, nhập thông tin và bấm Lưu. Task lập tức xuất hiện trong Room và chuyển thành `Đã đồng bộ`.
2. **TC02 (Offline tạo/sửa):** Tắt Wi-Fi/dữ liệu trên máy ảo. Banner "Ngoại tuyến" xuất hiện. Thêm/sửa công việc vẫn hoạt động bình thường, chip trạng thái hiển thị `Chưa đồng bộ`.
3. **TC03 (Bật lại Internet):** Bật lại Wi-Fi/dữ liệu. Ứng dụng tự động phát hiện mạng và kích hoạt WorkManager để đồng bộ các thay đổi lên server.
4. **TC04 (Đóng app rồi mở lại):** Tắt hẳn app (force stop) và mở lại. Dữ liệu từ Room vẫn bảo toàn nguyên vẹn.
5. **TC05 (Mô phỏng lỗi Server):** Gọi `POST /api/debug/fault` với `return_500: true`. Thao tác sẽ hiển thị `Thất bại` kèm nút `Thử lại`.
6. **TC07 (Xử lý xung đột):** Mô phỏng sửa cùng task từ thiết bị khác. App phát hiện `409 Conflict`, áp dụng LWW, ghi vào `conflict_log` và hiển thị banner thông báo.
