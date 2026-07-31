package com.example.tiktokstreamer

import android.os.Handler
import android.os.Looper
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * App này chỉ quay màn hình + đẩy RTMP, không có đường kết nối riêng tới hệ thống
 * chat live của TikTok (TikTok không cấp API RTMP kèm chat).
 *
 * Cách thực tế để có chat: chạy một "chat bridge" nhỏ trên máy tính/VPS
 * (ví dụ dùng thư viện mã nguồn mở TikTokLive cho Python/Node) để lấy chat từ
 * phiên live, rồi bridge đó bắn từng tin nhắn dạng JSON qua WebSocket cho app này:
 *   {"user": "ten_nguoi_xem", "message": "noi_dung_chat"}
 *
 * ChatBridgeClient chỉ có nhiệm vụ giữ kết nối WebSocket tới bridge và tự
 * kết nối lại khi rớt mạng.
 */
class ChatBridgeClient(
    private val wsUrl: String,
    private val onMessage: (ChatMessage) -> Unit,
    private val onStatusChange: (connected: Boolean) -> Unit
) {
    private val client = OkHttpClient.Builder()
        .pingInterval(15, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var stopped = false

    fun connect() {
        stopped = false
        val request = Request.Builder().url(wsUrl).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                mainHandler.post { onStatusChange(true) }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                parseAndDeliver(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                mainHandler.post { onStatusChange(false) }
                scheduleReconnect()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                mainHandler.post { onStatusChange(false) }
                scheduleReconnect()
            }
        })
    }

    private fun parseAndDeliver(text: String) {
        try {
            val obj = JSONObject(text)
            val user = obj.optString("user", "?")
            val message = obj.optString("message", "")
            if (message.isNotBlank()) {
                mainHandler.post { onMessage(ChatMessage(user, message)) }
            }
        } catch (_: Exception) {
            // Bỏ qua dòng không đúng định dạng JSON
        }
    }

    private fun scheduleReconnect() {
        if (stopped) return
        mainHandler.postDelayed({ if (!stopped) connect() }, 3000)
    }

    fun disconnect() {
        stopped = true
        webSocket?.close(1000, null)
        webSocket = null
    }
}
