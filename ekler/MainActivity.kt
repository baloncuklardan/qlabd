package com.abdullahql.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.abdullahql.app.R
import com.abdullahql.app.databinding.ActivityMainBinding
import com.abdullahql.app.service.LocationForegroundService

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refreshState() }

    private val backgroundPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshState() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnGrantPermissions.setOnClickListener { requestCorePermissions() }
        binding.btnBatteryExempt.setOnClickListener { requestBatteryExemption() }
        binding.btnPair.setOnClickListener {
            startActivity(Intent(this, PairingActivity::class.java))
        }
        binding.btnToggleSharing.setOnClickListener { toggleSharing() }
        binding.btnOpenMap.setOnClickListener {
            startActivity(Intent(this, MapActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        refreshState()
    }

    private fun requestCorePermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(permissions.toTypedArray())

        // Arka plan konum izni ayrı ve sonradan istenmeli (Android kuralı)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            backgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }

    private fun requestBatteryExemption() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        }
        openOemAutoStartSettingsIfNeeded()
    }

    /** Xiaomi/Huawei/Samsung gibi agresif pil yönetimi olan cihazlarda
     *  kullanıcıyı üretici ayarlarına yönlendirir. Cihaz üreticisine göre
     *  ayar ekranı adı değişir; bulunamazsa sessizce yoksayılır. */
    private fun openOemAutoStartSettingsIfNeeded() {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val intent = when {
            manufacturer.contains("xiaomi") -> Intent().setComponent(
                android.content.ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
            )
            manufacturer.contains("huawei") -> Intent().setComponent(
                android.content.ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                )
            )
            manufacturer.contains("samsung") -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
            else -> null
        }
        intent?.let { runCatching { startActivity(it) } }
    }

    private fun toggleSharing() {
        val prefs = getSharedPreferences(LocationForegroundService.PREFS, Context.MODE_PRIVATE)
        val currentlySharing = prefs.getBoolean(LocationForegroundService.KEY_SHARING_ENABLED, false)
        if (currentlySharing) {
            LocationForegroundService.stop(this)
        } else {
            LocationForegroundService.start(this)
        }
        refreshState()
    }

    private fun refreshState() {
        val prefs = getSharedPreferences(LocationForegroundService.PREFS, Context.MODE_PRIVATE)
        val sharing = prefs.getBoolean(LocationForegroundService.KEY_SHARING_ENABLED, false)
        binding.btnToggleSharing.text = getString(
            if (sharing) R.string.btn_stop_sharing else R.string.btn_start_sharing
        )
    }
}
