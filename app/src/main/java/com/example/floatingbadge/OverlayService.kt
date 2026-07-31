package com.example.floatingbadge

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.core.app.NotificationCompat
import kotlin.math.abs

class OverlayService : Service() {

    companion object {
        // Cho MainActivity biết icon nổi đang chạy hay không, để đổi chữ nút
        var isRunning: Boolean = false
            private set

        private const val DEFAULT_SIZE_DP = 56
    }

    private lateinit var windowManager: WindowManager
    private lateinit var bubbleView: View
    private lateinit var wifiIcon: ImageView
    private lateinit var badgeText: TextView
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var prefs: SharedPreferences

    // trạng thái: false = bình thường, true = đang hiện 999+
    private var isActive = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        prefs = getSharedPreferences(PrefsKeys.PREFS_NAME, MODE_PRIVATE)
        startForegroundNotification()
        setupBubble()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Mỗi lần activity gọi lại startService (vd: khi kéo thanh chỉnh size),
        // áp dụng kích thước mới nhất từ SharedPreferences mà không tạo lại bubble.
        applyBubbleSize(prefs.getInt(PrefsKeys.BUBBLE_SIZE_DP, DEFAULT_SIZE_DP))
        return START_STICKY
    }

    private fun startForegroundNotification() {
        val channelId = "floating_badge_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Icon nổi",
                NotificationManager.IMPORTANCE_MIN
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("VennPlus đang chạy")
            .setContentText("Chấm vào icon để bật/tắt số 999+")
            .setSmallIcon(android.R.drawable.presence_online)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        startForeground(1, notification)
    }

    private fun setupBubble() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        bubbleView = LayoutInflater.from(this).inflate(R.layout.overlay_bubble, null)
        wifiIcon = bubbleView.findViewById(R.id.wifiIcon)
        badgeText = bubbleView.findViewById(R.id.badgeText)

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 100
        params.y = 300

        windowManager.addView(bubbleView, params)

        // Áp dụng kích thước đã lưu (hoặc mặc định) ngay khi tạo bubble
        applyBubbleSize(prefs.getInt(PrefsKeys.BUBBLE_SIZE_DP, DEFAULT_SIZE_DP))

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var moved = false

        // Kéo thả icon đi chỗ khác được, chấm (không kéo) thì bật/tắt badge 999+
        bubbleView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (abs(dx) > 12 || abs(dy) > 12) moved = true
                    params.x = initialX + dx
                    params.y = initialY + dy
                    windowManager.updateViewLayout(bubbleView, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        isActive = !isActive
                        badgeText.visibility = if (isActive) View.VISIBLE else View.GONE
                        wifiIcon.setImageResource(
                            if (isActive) R.drawable.ic_wifi_alert else R.drawable.ic_wifi_normal
                        )
                    }
                    true
                }
                else -> false
            }
        }
    }

    /** Đổi kích thước icon wifi nổi (đơn vị dp) và cập nhật ngay trên màn hình. */
    private fun applyBubbleSize(sizeDp: Int) {
        if (!::wifiIcon.isInitialized) return
        val sizePx = (sizeDp * resources.displayMetrics.density).toInt()
        val lp = wifiIcon.layoutParams
        lp.width = sizePx
        lp.height = sizePx
        wifiIcon.layoutParams = lp
        if (::windowManager.isInitialized && ::bubbleView.isInitialized) {
            windowManager.updateViewLayout(bubbleView, params)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        if (::bubbleView.isInitialized) {
            windowManager.removeView(bubbleView)
        }
    }
}
