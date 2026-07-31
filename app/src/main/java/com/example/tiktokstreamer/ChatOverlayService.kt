package com.example.tiktokstreamer

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale

/**
 * Overlay nổi trên toàn màn hình (kể cả trong game). Vì luồng live là quay
 * TOÀN BỘ màn hình, mọi thứ overlay này vẽ ra cũng nằm trong hình đang live.
 *
 * - Bubble avatar: kéo thả tự do, bấm vào để thu gọn/mở rộng giống chat head TikTok.
 * - Panel mở rộng: danh sách chat gần nhất + nút Mic / Đọc chat / Tạm dừng.
 * - Banner tạm dừng: che toàn màn hình khi bấm "Tạm dừng", đồng thời tắt mic.
 */
class ChatOverlayService : Service() {

    companion object {
        const val EXTRA_WS_URL = "EXTRA_WS_URL"
        var isPaused: Boolean = false
            private set
    }

    private lateinit var windowManager: WindowManager
    private var bubbleView: View? = null
    private var panelView: View? = null
    private var pauseBannerView: View? = null

    private var expanded = false
    private var micOn = true
    private var ttsOn = false

    private lateinit var chatList: LinearLayout
    private lateinit var chatScroll: ScrollView
    private var tts: TextToSpeech? = null
    private var chatBridge: ChatBridgeClient? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("vi", "VN")
            }
        }
        addBubble()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val wsUrl = intent?.getStringExtra(EXTRA_WS_URL)
        if (!wsUrl.isNullOrBlank() && chatBridge == null) {
            chatBridge = ChatBridgeClient(
                wsUrl = wsUrl,
                onMessage = { msg -> onChatReceived(msg) },
                onStatusChange = { }
            )
            chatBridge?.connect()
        }
        return START_STICKY
    }

    // ---------- Bubble avatar (thu gọn) ----------

    private fun addBubble() {
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.overlay_bubble, null)
        bubbleView = view

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 40
        params.y = 200

        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false

        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = params.x
                    startY = params.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downX).toInt()
                    val dy = (event.rawY - downY).toInt()
                    if (kotlin.math.abs(dx) > 8 || kotlin.math.abs(dy) > 8) moved = true
                    params.x = startX + dx
                    params.y = startY + dy
                    windowManager.updateViewLayout(view, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) toggleExpanded()
                    true
                }
                else -> false
            }
        }

        windowManager.addView(view, params)
    }

    // ---------- Panel mở rộng ----------

    private fun toggleExpanded() {
        expanded = !expanded
        if (expanded) showPanel() else hidePanel()
    }

    private fun showPanel() {
        bubbleView?.visibility = View.GONE

        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.overlay_panel, null)
        panelView = view

        chatList = view.findViewById(R.id.chatList)
        chatScroll = view.findViewById(R.id.chatScroll)
        val panelAvatar = view.findViewById<TextView>(R.id.panelAvatar)
        val btnMic = view.findViewById<TextView>(R.id.btnMic)
        val btnTts = view.findViewById<TextView>(R.id.btnTts)
        val btnPause = view.findViewById<TextView>(R.id.btnPause)

        updateMicButton(btnMic)
        updateTtsButton(btnTts)
        btnPause.text = if (isPaused) "Live tiếp" else "Tạm dừng"

        panelAvatar.setOnClickListener { toggleExpanded() }

        btnMic.setOnClickListener {
            micOn = !micOn
            sendServiceAction(if (micOn) StreamForegroundService.ACTION_UNMUTE_MIC else StreamForegroundService.ACTION_MUTE_MIC)
            updateMicButton(btnMic)
        }

        btnTts.setOnClickListener {
            ttsOn = !ttsOn
            updateTtsButton(btnTts)
        }

        btnPause.setOnClickListener {
            setPaused(!isPaused)
            btnPause.text = if (isPaused) "Live tiếp" else "Tạm dừng"
        }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 40
        params.y = 160

        windowManager.addView(view, params)
    }

    private fun hidePanel() {
        panelView?.let { windowManager.removeView(it) }
        panelView = null
        bubbleView?.visibility = View.VISIBLE
    }

    private fun updateMicButton(btn: TextView) {
        btn.text = if (micOn) "Mic: Bật" else "Mic: Tắt"
    }

    private fun updateTtsButton(btn: TextView) {
        btn.text = if (ttsOn) "Đọc chat: Bật" else "Đọc chat: Tắt"
    }

    // ---------- Chat ----------

    private fun onChatReceived(msg: ChatMessage) {
        if (ttsOn) {
            tts?.speak("${msg.username} nói: ${msg.text}", TextToSpeech.QUEUE_ADD, null, null)
        }

        val panel = panelView ?: return
        val line = TextView(this).apply {
            text = "${msg.username}: ${msg.text}"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 12f
            setPadding(0, 4, 0, 4)
        }
        chatList.addView(line)
        while (chatList.childCount > 30) {
            chatList.removeViewAt(0)
        }
        chatScroll.post { chatScroll.fullScroll(View.FOCUS_DOWN) }
        panel.let { }
    }

    // ---------- Pause ----------

    private fun setPaused(paused: Boolean) {
        isPaused = paused
        if (paused) {
            micOn = false
            sendServiceAction(StreamForegroundService.ACTION_MUTE_MIC)
            showPauseBanner()
        } else {
            micOn = true
            sendServiceAction(StreamForegroundService.ACTION_UNMUTE_MIC)
            hidePauseBanner()
        }
    }

    private fun showPauseBanner() {
        if (pauseBannerView != null) return
        val view = TextView(this).apply {
            text = "ĐÃ TẠM DỪNG LIVE"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 22f
            gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.bg_pause_banner)
        }
        pauseBannerView = view

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        )
        windowManager.addView(view, params)
    }

    private fun hidePauseBanner() {
        pauseBannerView?.let { windowManager.removeView(it) }
        pauseBannerView = null
    }

    private fun sendServiceAction(action: String) {
        val intent = Intent(this, StreamForegroundService::class.java).apply {
            this.action = action
        }
        startService(intent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        chatBridge?.disconnect()
        tts?.stop()
        tts?.shutdown()
        bubbleView?.let { runCatching { windowManager.removeView(it) } }
        panelView?.let { runCatching { windowManager.removeView(it) } }
        pauseBannerView?.let { runCatching { windowManager.removeView(it) } }
    }
}
