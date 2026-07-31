# VennPlus (Floating Wifi Bubble với số 999+)

Đây là source code project Android (Kotlin) hoàn chỉnh cho app **VennPlus**. Mình không build được file `.apk` trực tiếp ở đây vì môi trường này không có Android SDK/Gradle, nhưng bạn có thể tự build ra APK rất nhanh bằng Android Studio.

## Ứng dụng làm gì
- **Icon app (launcher)**: ảnh con mèo bạn gửi, đã được cắt vuông/tròn tự động cho các độ phân giải màn hình khác nhau.
- Giao diện chính có:
  - Nút **"Mở VennPlus"** (đổi thành **"Tắt VennPlus"** khi đang bật) → xin quyền "hiển thị đè lên ứng dụng khác" (chỉ hỏi 1 lần) rồi bật/tắt icon nổi.
  - Thanh kéo (**SeekBar**) bên dưới để **chỉnh kích thước icon nổi** (từ 40dp đến 120dp), có nhãn hiển thị số dp hiện tại. Kéo tới đâu, icon đang nổi trên màn hình đổi size ngay tới đó, kể cả khi đang bật sẵn.
- Sau khi bật, một **icon wifi nổi** xuất hiện đè lên mọi màn hình/app khác (kiểu bong bóng Messenger).
  - **Chưa bật**: hiện icon wifi bình thường (màu xám).
  - **Chấm vào icon để bật**: đổi sang icon wifi có dấu chấm than đỏ (!) kèm badge đỏ **999+**.
  - **Chấm lại lần nữa**: quay về icon wifi bình thường, badge biến mất.
- Có thể **kéo icon** đi chỗ khác trên màn hình (giữ và kéo, không tính là chấm).

Cả 2 icon wifi (bình thường / có dấu !) và icon app (con mèo) đã được mình tự động xóa nền, cắt gọn và resize sẵn, để thẳng vào đúng thư mục resource trong project — bạn không cần chỉnh sửa gì thêm.

## Cách build ra APK

### Cách 1: Dùng Android Studio (khuyên dùng, dễ nhất)
1. Cài [Android Studio](https://developer.android.com/studio) (miễn phí).
2. Mở Android Studio → **Open** → chọn thư mục `FloatingBadge` (thư mục chứa file `settings.gradle`).
3. Đợi Android Studio tự tải Gradle + đồng bộ project (lần đầu có thể mất vài phút, cần mạng).
4. Vào menu **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
5. Khi build xong, bấm vào thông báo **"locate"** để mở thư mục chứa file APK
   (thường ở `app/build/outputs/apk/debug/app-debug.apk`).
6. Copy file APK đó vào điện thoại Android và cài đặt (nhớ bật "Cho phép cài từ nguồn không xác định" nếu máy hỏi).

### Cách 2: Build bằng dòng lệnh (nếu đã có Android SDK cài sẵn)
```bash
cd FloatingBadge
./gradlew assembleDebug
```
(Nếu chưa có file `gradlew`, mở project bằng Android Studio một lần, nó sẽ tự tạo gradle wrapper, sau đó lệnh trên mới chạy được.)

File APK debug sẽ nằm ở: `app/build/outputs/apk/debug/app-debug.apk`

## Ghi chú
- App cần quyền **"Hiển thị đè lên ứng dụng khác" (Draw over other apps / Overlay)** — đây là quyền bắt buộc để icon nổi lên trên mọi màn hình, y hệt cách Messenger làm chat-head.
- Một số điện thoại (Xiaomi, Oppo, Samsung...) có thể có thêm cài đặt riêng để cho phép overlay, chặn ở phần "Ứng dụng chạy nền / Hiển thị popup khi chạy nền" trong Cài đặt máy.
- Đây là project mẫu đơn giản, có thể tùy biến thêm: đổi màu, icon, animation khi bấm, v.v. Cứ nói mình chỉnh giúp.
