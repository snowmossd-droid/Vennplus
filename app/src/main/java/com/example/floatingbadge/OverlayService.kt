package com.example.floatingbadge

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.core.app.NotificationCompat
import kotlin.math.abs
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class OverlayService : Service() {

    companion object {
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
    private var isActive = false
    private var lastUpdateTime = 0L
    private val UPDATE_THRESHOLD = 100
    private var isDragging = false
    private val handler = Handler(Looper.getMainLooper())
    private var isLagEnabled = false
    private var lagRunnable: Runnable? = null
    private var networkThreads = mutableListOf<Thread>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        prefs = getSharedPreferences(PrefsKeys.PREFS_NAME, MODE_PRIVATE)
        startForegroundNotification()
        setupBubble()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        applyBubbleSize(prefs.getInt(PrefsKeys.BUBBLE_SIZE_DP, DEFAULT_SIZE_DP))
        return START_STICKY
    }

    private fun startForegroundNotification() {
        val channelId = "floating_badge_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "VennPlus",
                NotificationManager.IMPORTANCE_LOW
            )
            channel.setShowBadge(false)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("VennPlus đang chạy")
            .setContentText("Chạm icon để bật/tắt lag mạng")
            .setSmallIcon(android.R.drawable.presence_online)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
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
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 100
        params.y = 300
        params.width = WindowManager.LayoutParams.WRAP_CONTENT
        params.height = WindowManager.LayoutParams.WRAP_CONTENT

        windowManager.addView(bubbleView, params)
        applyBubbleSize(prefs.getInt(PrefsKeys.BUBBLE_SIZE_DP, DEFAULT_SIZE_DP))

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var moved = false

        bubbleView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    moved = false
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (abs(dx) > 12 || abs(dy) > 12) {
                        moved = true
                        isDragging = true
                    }
                    
                    if (isDragging) {
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastUpdateTime > UPDATE_THRESHOLD) {
                            params.x = initialX + dx
                            params.y = initialY + dy
                            windowManager.updateViewLayout(bubbleView, params)
                            lastUpdateTime = currentTime
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved && !isDragging) {
                        isActive = !isActive
                        badgeText.visibility = if (isActive) View.VISIBLE else View.GONE
                        wifiIcon.setImageResource(
                            if (isActive) R.drawable.ic_wifi_alert else R.drawable.ic_wifi_normal
                        )
                        
                        if (isActive) {
                            startNetworkLag()
                        } else {
                            stopNetworkLag()
                        }
                    }
                    isDragging = false
                    true
                }
                else -> false
            }
        }
    }

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

    private fun startNetworkLag() {
        if (isLagEnabled) return
        isLagEnabled = true
        
        for (i in 0..5) {
            val thread = Thread {
                while (isLagEnabled && isRunning) {
                    try {
                        val url = URL("https://www.google.com")
                        val connection = url.openConnection() as HttpURLConnection
                        connection.connectTimeout = 3000
                        connection.readTimeout = 3000
                        connection.requestMethod = "HEAD"
                        connection.connect()
                        connection.disconnect()
                    } catch (e: IOException) {
                    }
                    
                    try {
                        Thread.sleep(10)
                    } catch (e: InterruptedException) {
                        break
                    }
                }
            }
            thread.start()
            networkThreads.add(thread)
        }
        
        lagRunnable = object : Runnable {
            override fun run() {
                if (!isLagEnabled || !isRunning) return
                handler.postDelayed(this, 100)
            }
        }
        lagRunnable?.let { handler.post(it) }
    }

    private fun stopNetworkLag() {
        isLagEnabled = false
        lagRunnable?.let { handler.removeCallbacks(it) }
        lagRunnable = null
        
        for (thread in networkThreads) {
            try {
                thread.interrupt()
            } catch (e: Exception) {
            }
        }
        networkThreads.clear()
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        stopNetworkLag()
        if (::bubbleView.isInitialized) {
            try {
                windowManager.removeView(bubbleView)
            } catch (e: Exception) {
            }
        }
        handler.removeCallbacksAndMessages(null)
    }
}
