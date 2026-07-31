package com.example.floatingbadge

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val overlayPermissionCode = 1234
    private val minSizeDp = 40

    private lateinit var prefs: SharedPreferences
    private lateinit var btnToggle: Button
    private lateinit var sizeLabel: TextView
    private lateinit var sizeSeekBar: SeekBar
    private var lastSaveTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences(PrefsKeys.PREFS_NAME, MODE_PRIVATE)

        btnToggle = findViewById(R.id.btnToggle)
        sizeLabel = findViewById(R.id.sizeLabel)
        sizeSeekBar = findViewById(R.id.sizeSeekBar)

        val savedSizeDp = prefs.getInt(PrefsKeys.BUBBLE_SIZE_DP, 56)
        sizeSeekBar.progress = (savedSizeDp - minSizeDp).coerceIn(0, sizeSeekBar.max)
        sizeLabel.text = getString(R.string.size_label_format, savedSizeDp)

        sizeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val sizeDp = minSizeDp + progress
                sizeLabel.text = getString(R.string.size_label_format, sizeDp)
                
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastSaveTime > 200) {
                    prefs.edit().putInt(PrefsKeys.BUBBLE_SIZE_DP, sizeDp).apply()
                    if (OverlayService.isRunning) {
                        startService(Intent(this@MainActivity, OverlayService::class.java))
                    }
                    lastSaveTime = currentTime
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnToggle.setOnClickListener {
            if (OverlayService.isRunning) {
                stopService(Intent(this, OverlayService::class.java))
                updateToggleButton()
                Toast.makeText(this, "Đã tắt VennPlus", Toast.LENGTH_SHORT).show()
            } else {
                if (!Settings.canDrawOverlays(this)) {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivityForResult(intent, overlayPermissionCode)
                } else {
                    startOverlayService()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateToggleButton()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == overlayPermissionCode) {
            if (Settings.canDrawOverlays(this)) {
                startOverlayService()
            } else {
                Toast.makeText(
                    this,
                    "Cần cấp quyền hiển thị đè lên ứng dụng khác để dùng tính năng này",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun startOverlayService() {
        val intent = Intent(this, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        Toast.makeText(this, "Đã mở VennPlus", Toast.LENGTH_SHORT).show()
        updateToggleButton()
    }

    private fun updateToggleButton() {
        btnToggle.text = if (OverlayService.isRunning) "Tắt VennPlus" else "Mở VennPlus"
    }
}
