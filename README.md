# TikTok RTMP Streamer

App Android đơn giản: quay **toàn bộ màn hình + mic**, đẩy trực tiếp lên TikTok qua **RTMP**,
chạy dưới dạng **Foreground Service** nên không bị hệ điều hành kill khi bạn mở game nặng
(Free Fire...) song song.

Không sửa/mod app TikTok gốc — đây là app độc lập dùng cổng RTMP chính thức mà TikTok cung cấp
cho streamer (giống cách OBS Studio hay Streamlabs kết nối).

---

## 1. Lấy Server URL + Stream Key từ TikTok

1. Vào TikTok (ứng dụng hoặc web) → mục **LIVE Studio** (hoặc "Go Live" > "Sử dụng phần mềm khác/RTMP").
   - Một số tài khoản cần đủ điều kiện (follower tối thiểu) mới thấy tùy chọn RTMP/Live Studio.
   - Nếu không thấy, tìm trên trình duyệt: `https://livecenter.tiktok.com` (đăng nhập cùng tài khoản).
2. TikTok sẽ cấp cho bạn 2 thông tin:
   - **Server URL**: dạng `rtmp://push.tiktok.com/live/...` hoặc tương tự.
   - **Stream Key**: một chuỗi ký tự dài.
3. Copy 2 giá trị này để dán vào app.

⚠️ Stream Key giống mật khẩu — không chia sẻ cho người khác, ai có key đó có thể live thay bạn.

---

## 2. Build APK bằng Android Studio (khuyên dùng)

1. Cài **Android Studio** (bản mới nhất): https://developer.android.com/studio
2. Mở Android Studio → **Open** → chọn thư mục project này (`TikTokRtmpStreamer`).
3. Đợi Gradle Sync tự tải thư viện (cần internet, lần đầu hơi lâu).
4. Vào menu **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
5. Sau khi build xong, bấm **locate** trong thông báo góc dưới màn hình để lấy file APK
   (thường nằm ở `app/build/outputs/apk/debug/app-debug.apk`).
6. Copy file APK này vào điện thoại và cài đặt (cần bật "Cài đặt từ nguồn không xác định").

### Build bằng dòng lệnh (nếu không muốn dùng Android Studio)
Cần đã cài Android SDK + biến môi trường `ANDROID_HOME`:

```bash
cd TikTokRtmpStreamer
./gradlew assembleDebug
```
File APK sẽ nằm ở `app/build/outputs/apk/debug/app-debug.apk`.

*(Lưu ý: project chưa có file `gradlew`/`gradle wrapper` đi kèm — khi mở bằng Android Studio,
nó sẽ tự tạo wrapper cho bạn. Nếu build dòng lệnh, chạy `gradle wrapper` trước để sinh file này.)*

---

## 3. Cách dùng app

1. Mở app → dán **Server URL** và **Stream Key** đã lấy ở bước 1.
2. Bấm **"Bắt đầu Live"**.
3. Cấp quyền:
   - Ghi âm (mic)
   - Thông báo (Android 13+)
   - Quay màn hình (hộp thoại hệ thống của Android, bấm "Bắt đầu ngay bây giờ")
4. Sau khi cấp quyền, thanh thông báo sẽ hiện dòng "Đang live lên TikTok" —
   đây là dấu hiệu Foreground Service đang chạy và sẽ không bị kill.
5. Mở app Free Fire để chơi bình thường — màn hình + tiếng vẫn được đẩy lên TikTok qua luồng RTMP,
   độc lập với việc app TikTok gốc có mở hay không.
6. Muốn dừng, quay lại app, bấm **"Dừng Live"**.

---

## 4. Một vài lưu ý thực tế

- **Chất lượng mạng quan trọng hơn cả RAM** khi stream RTMP — cần Wi-Fi hoặc 4G/5G ổn định,
  tối thiểu ~3-4 Mbps upload để hình không giật.
- Bitrate/video mặc định trong code (`StreamForegroundService.kt`) đang để 720x1280 @ 2.5Mbps —
  nếu máy yếu hoặc mạng yếu, có thể giảm `VIDEO_BITRATE`, `VIDEO_WIDTH/HEIGHT` xuống thấp hơn.
- Thư viện dùng: [RootEncoder](https://github.com/pedroSG94/RootEncoder) (mã nguồn mở, MIT-like license) —
  nếu Android Studio báo lỗi thiếu API do thư viện cập nhật phiên bản mới, tham khảo ví dụ chính thức:
  https://github.com/pedroSG94/RootEncoder/tree/master/app/src/main/java/com/pedro/streamer/screen
- Việc dùng RTMP để live lên TikTok là tính năng TikTok hỗ trợ chính thức, không vi phạm điều khoản
  sử dụng (khác với việc mod/patch app TikTok gốc).

---

## 5. Chat overlay khi live (bubble nổi, mic, tạm dừng)

Khi bấm "Bắt đầu Live", app sẽ xin thêm quyền **"Hiển thị đè lên ứng dụng khác"**
(Settings > Apps > Special access > Display over other apps) để hiện một **bubble avatar
nổi** trên toàn màn hình — kể cả khi đang mở Free Fire. Vì đây là quay TOÀN BỘ màn hình,
bubble và mọi thứ vẽ ra đều nằm trong hình đang live, giống hệt cách overlay của
Streamlabs/OBS mobile hoạt động.

- **Bấm vào avatar**: thu gọn ⇄ mở rộng, giống chat head TikTok.
- **Kéo avatar**: di chuyển tự do quanh màn hình.
- **Nút Mic**: bật/tắt mic ngay trong lúc live, không cần dừng stream.
- **Nút Đọc chat**: bật/tắt đọc chat bằng giọng nói (Text-to-Speech tiếng Việt).
- **Nút Tạm dừng**: tắt mic + phủ banner "ĐÃ TẠM DỪNG LIVE" che toàn màn hình
  (RTMP không hỗ trợ pause thật sự nên đây là cách thực tế nhất để "tạm dừng"
  mà không phải ngắt kết nối stream).

### Chat bridge — vì sao cần và cách chạy

App RTMP này **không tự nói chuyện được với hệ thống chat live của TikTok**
(TikTok không cấp API RTMP kèm chat). Ô "Chat bridge WS URL" trong app chỉ là nơi
app **nhận** tin nhắn chat, còn việc lấy chat thật từ phiên live phải chạy ở
một chương trình riêng (máy tính/VPS), dùng thư viện mã nguồn mở phổ biến để đọc
chat live TikTok, rồi bắn từng dòng qua WebSocket dạng:

```json
{"user": "ten_nguoi_xem", "message": "noi_dung_chat"}
```

Nếu để trống ô này, app vẫn live/mic/tạm dừng bình thường — chỉ là không có tin
nhắn nào đổ vào panel chat để đọc.
