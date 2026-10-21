package com.dqmarket.wifilauncher

import android.app.ActivityManager
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private val viewModel: LauncherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        requestUsageAccessIfNeeded()
        ContextCompat.startForegroundService(this, Intent(this, TrafficMonitorService::class.java))
        tryEnterKiosk()

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                val state by viewModel.uiState.collectAsState()
                com.dqmarket.wifilauncher.ui.LauncherScreen(
                    state = state,
                    onRefresh = { viewModel.refresh() },
                    onOpenWifiPanel = { viewModel.openWifiPanel() },
                    onOpenTetherSettings = { viewModel.openTetherSettings() },
                    onOpenDataUsage = { viewModel.openDataUsage() },
                    onToggleHotspot = { viewModel.toggleHotspot(it) },
                    onDismissMessage = { viewModel.clearMessage() },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        tryEnterKiosk()
        viewModel.refresh(silent = true)
    }

    @Deprecated("Launcher should stay in foreground")
    override fun onBackPressed() {
        // Keep user inside kiosk launcher; double-back could exit if not in lock task.
        if (!isInLockTaskMode()) {
            moveTaskToBack(true)
        }
    }

    private fun tryEnterKiosk() {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && am.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_NONE) {
            try {
                startLockTask()
            } catch (_: SecurityException) {
                // Requires Device Owner — see scripts/setup-kiosk.sh
            }
        }
    }

    private fun isInLockTaskMode(): Boolean {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
        } else {
            false
        }
    }

    private fun requestUsageAccessIfNeeded() {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        @Suppress("DEPRECATION")
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName,
        )
        if (mode != AppOpsManager.MODE_ALLOWED) {
            startActivity(
                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    },
                )
            }
        }
    }
}
