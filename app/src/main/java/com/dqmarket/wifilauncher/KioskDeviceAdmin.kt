package com.dqmarket.wifilauncher

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.app.admin.DevicePolicyManager

class KioskDeviceAdmin : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        dpm.setLockTaskPackages(adminComponent(context), arrayOf(context.packageName))
    }

    companion object {
        fun adminComponent(context: Context) =
            android.content.ComponentName(context, KioskDeviceAdmin::class.java)
    }
}
