package com.example.tiktokstreamer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.pedro.common.ConnectChecker
import com.pedro.library.rtmp.RtmpDisplay

/**
 * Foreground Service chạy quay màn hình + mic và đẩy luồng RTMP lên TikTok.
 *
 * Vì đây là Foreground Service (loại mediaProjection + microphone) kèm thông báo
 * hiển thị liên tục, Android sẽ KHÔNG kill tiến trình này khi hệ thống thiếu RAM
 * (trừ khi máy quá yếu tới mức OS buộc phải kill toàn bộ ứng dụng nền, trường hợp
 * cực hiếm). Đây là cơ chế chính giúp giải quyết vấn đề "live bị tắt khi mở game nặng".
 *
 * LƯU Ý: Tên phương thức của thư viện RootEncoder (RtmpDisplay) có thể thay đổi nhẹ
 * giữa các phiên bản. Nếu build lỗi do đổi API, tham khảo ví dụ chính thức tại:
 * https://github.com/pedroSG94/RootEncoder/tree/master/app/src/main/java/com/pedro/streamer/screen
 */
class StreamForegroundService : Service(), ConnectChecker {

    private var rtmpDisplay: RtmpDisplay? = null

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_MUTE_MIC = "ACTION_MUTE_MIC"
        const val ACTION_UNMUTE_MIC = "ACTION_UNMUTE_MIC"

        const val EXTRA_RESULT_CODE = "EXTRA_RESULT_CODE"
        const val EXTRA_RESULT_DATA = "EXTRA_RESULT_DATA"
        const val EXTRA_SERVER_URL = "EXTRA_SERVER_URL"
        const val EXTRA_STREAM_KEY = "EXTRA_STREAM_KEY"
        const val EXTRA_CHAT_WS_URL = "EXTRA_CHAT_WS_URL"

        private const val CHANNEL_ID = "live_stream_channel"
        private const val NOTIFICATION_ID = 1001

        // Cấu hình video/audio mặc định - có thể chỉnh theo máy
        private const val VIDEO_WIDTH = 720
        private const val VIDEO_HEIGHT = 1280
        private const val VIDEO_BITRATE = 2_500 * 1024 // 2.5 Mbps
        private const val VIDEO_FPS = 30
        private const val AUDIO_BITRATE = 128 * 1024
        private const val AUDIO_SAMPLE_RATE = 44100
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart(intent)
            ACTION_STOP -> handleStop()
            ACTION_MUTE_MIC -> rtmpDisplay?.disableAudio()
            ACTION_UNMUTE_MIC -> rtmpDisplay?.enableAudio()
        }
        return START_NOT_STICKY
    }

    private fun handleStart(intent: Intent) {
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1)
        val resultData = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
        val serverUrl = intent.getStringExtra(EXTRA_SERVER_URL) ?: ""
        val streamKey = intent.getStringExtra(EXTRA_STREAM_KEY) ?: ""

        if (resultData == null || serverUrl.isEmpty() || streamKey.isEmpty()) {
            stopSelf()
            return
        }

        // Bắt buộc: start foreground TRƯỚC khi bắt đầu MediaProjection (yêu cầu của Android 14+)
        startForegroundNotification()

        rtmpDisplay = RtmpDisplay(this, true, this).apply {
            setIntentResult(resultCode, resultData)
        }

        val prepared = rtmpDisplay?.prepareVideo(
            VIDEO_WIDTH, VIDEO_HEIGHT, VIDEO_FPS, VIDEO_BITRATE, 0, 320
        ) == true && rtmpDisplay?.prepareAudio(AUDIO_BITRATE, AUDIO_SAMPLE_RATE, true) == true

        if (prepared) {
            val fullUrl = buildFullRtmpUrl(serverUrl, streamKey)
            rtmpDisplay?.startStream(fullUrl)
            startChatOverlay(intent.getStringExtra(EXTRA_CHAT_WS_URL))
        } else {
            stopSelf()
        }
    }

    private fun startChatOverlay(wsUrl: String?) {
        val overlayIntent = Intent(this, ChatOverlayService::class.java).apply {
            if (!wsUrl.isNullOrBlank()) putExtra(ChatOverlayService.EXTRA_WS_URL, wsUrl)
        }
        startService(overlayIntent)
    }

    private fun handleStop() {
        if (rtmpDisplay?.isStreaming == true) {
            rtmpDisplay?.stopStream()
        }
        stopService(Intent(this, ChatOverlayService::class.java))
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /**
     * TikTok Live Studio thường cấp Server URL và Stream Key riêng biệt.
     * Một số trường hợp Server URL đã có sẵn dấu "/" cuối, cần ghép đúng để tạo full RTMP URL.
     */
    private fun buildFullRtmpUrl(serverUrl: String, streamKey: String): String {
        val base = if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/"
        return base + streamKey
    }

    private fun startForegroundNotification() {
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Đang live lên TikTok")
            .setContentText("Ứng dụng đang chạy nền để giữ luồng live ổn định")
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Live Stream",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        if (rtmpDisplay?.isStreaming == true) {
            rtmpDisplay?.stopStream()
        }
    }

    // ==== ConnectChecker callbacks (trạng thái kết nối RTMP) ====
    override fun onConnectionStarted(url: String) {}
    override fun onConnectionSuccess() {}
    override fun onConnectionFailed(reason: String) {
        // Thử kết nối lại hoặc dừng service tùy nhu cầu
        handleStop()
    }
    override fun onNewBitrate(bitrate: Long) {}
    override fun onDisconnect() {}
    override fun onAuthError() {
        handleStop()
    }
    override fun onAuthSuccess() {}
}
