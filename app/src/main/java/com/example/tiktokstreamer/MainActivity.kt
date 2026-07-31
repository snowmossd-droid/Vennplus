package com.example.tiktokstreamer

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var editServerUrl: EditText
    private lateinit var editStreamKey: EditText
    private lateinit var editChatWsUrl: EditText
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var textStatus: TextView

    private lateinit var mediaProjectionManager: MediaProjectionManager

    // Launcher xin quyền vẽ đè lên app khác (cần để hiện bubble chat khi mở game)
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)) {
            requestScreenCapture()
        } else {
            Toast.makeText(this, "Cần cấp quyền hiển thị đè để hiện chat/nút điều khiển khi live", Toast.LENGTH_LONG).show()
        }
    }

    // Launcher xin quyền quay màn hình (MediaProjection)
    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            startStreamingService(result.resultCode, result.data!!)
        } else {
            Toast.makeText(this, "Bạn cần cấp quyền quay màn hình để live", Toast.LENGTH_LONG).show()
        }
    }

    // Launcher xin quyền mic + thông báo
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        val allGranted = granted.values.all { it }
        if (allGranted) {
            checkOverlayThenCapture()
        } else {
            Toast.makeText(this, "Cần cấp đủ quyền mic/thông báo để live", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        editServerUrl = findViewById(R.id.editServerUrl)
        editStreamKey = findViewById(R.id.editStreamKey)
        editChatWsUrl = findViewById(R.id.editChatWsUrl)
        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)
        textStatus = findViewById(R.id.textStatus)

        mediaProjectionManager =
            getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        btnStart.setOnClickListener { onStartClicked() }
        btnStop.setOnClickListener { onStopClicked() }
    }

    private fun onStartClicked() {
        val serverUrl = editServerUrl.text.toString().trim()
        val streamKey = editStreamKey.text.toString().trim()

        if (serverUrl.isEmpty() || streamKey.isEmpty()) {
            Toast.makeText(this, "Nhập đủ Server URL và Stream Key", Toast.LENGTH_SHORT).show()
            return
        }

        // Lưu tạm để dùng sau khi có quyền
        StreamConfig.serverUrl = serverUrl
        StreamConfig.streamKey = streamKey
        StreamConfig.chatWsUrl = editChatWsUrl.text.toString().trim()

        val neededPermissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            neededPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missing = neededPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        } else {
            checkOverlayThenCapture()
        }
    }

    private fun checkOverlayThenCapture() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayPermissionLauncher.launch(intent)
        } else {
            requestScreenCapture()
        }
    }

    private fun requestScreenCapture() {
        val captureIntent = mediaProjectionManager.createScreenCaptureIntent()
        screenCaptureLauncher.launch(captureIntent)
    }

    private fun startStreamingService(resultCode: Int, data: Intent) {
        val serviceIntent = Intent(this, StreamForegroundService::class.java).apply {
            action = StreamForegroundService.ACTION_START
            putExtra(StreamForegroundService.EXTRA_RESULT_CODE, resultCode)
            putExtra(StreamForegroundService.EXTRA_RESULT_DATA, data)
            putExtra(StreamForegroundService.EXTRA_SERVER_URL, StreamConfig.serverUrl)
            putExtra(StreamForegroundService.EXTRA_STREAM_KEY, StreamConfig.streamKey)
            putExtra(StreamForegroundService.EXTRA_CHAT_WS_URL, StreamConfig.chatWsUrl)
        }
        ContextCompat.startForegroundService(this, serviceIntent)

        btnStart.isEnabled = false
        btnStop.isEnabled = true
        textStatus.text = "Trạng thái: Đang live..."
    }

    private fun onStopClicked() {
        val serviceIntent = Intent(this, StreamForegroundService::class.java).apply {
            action = StreamForegroundService.ACTION_STOP
        }
        startService(serviceIntent)

        btnStart.isEnabled = true
        btnStop.isEnabled = false
        textStatus.text = "Trạng thái: Đã dừng live"
    }
}

// Lưu tạm cấu hình để Activity <-> Service dùng chung trong 1 phiên chạy
object StreamConfig {
    var serverUrl: String = ""
    var streamKey: String = ""
    var chatWsUrl: String = ""
}
