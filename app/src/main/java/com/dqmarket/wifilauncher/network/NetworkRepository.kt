package com.dqmarket.wifilauncher.network

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TetheringManager
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import android.net.TrafficStats
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.Executor

data class NetworkSnapshot(
    val wifiEnabled: Boolean,
    val wifiSsid: String?,
    val wifiConnected: Boolean,
    val mobileConnected: Boolean,
    val hotspotEnabled: Boolean?,
    val rxBytesTotal: Long,
    val txBytesTotal: Long,
    val rxBytesToday: Long,
    val txBytesToday: Long,
    val hourlyRx: List<Long>,
    val hourlyTx: List<Long>,
)

class NetworkRepository(private val context: Context) {

    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val connectivity =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val telephony =
        context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    private val networkStats =
        context.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
    private val tetheringManager: TetheringManager? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.getSystemService(Context.TETHERING_SERVICE) as TetheringManager
        } else {
            null
        }

    fun snapshot(): NetworkSnapshot {
        val wifiEnabled = wifiManager.isWifiEnabled
        val wifiInfo = wifiManager.connectionInfo
        val ssid = wifiInfo?.ssid?.trim('"')?.takeIf { it != "<unknown ssid>" }
        val wifiConnected = connectivity.activeNetwork?.let { net ->
            connectivity.getNetworkCapabilities(net)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } == true
        val mobileConnected = connectivity.activeNetwork?.let { net ->
            connectivity.getNetworkCapabilities(net)?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        } == true

        val hotspot = readHotspotEnabled()

        val rxTotal = TrafficStats.getTotalRxBytes().coerceAtLeast(0L)
        val txTotal = TrafficStats.getTotalTxBytes().coerceAtLeast(0L)
        val (rxToday, txToday) = todayMobileWifiBytes()
        val (hourlyRx, hourlyTx) = hourlyBuckets()

        return NetworkSnapshot(
            wifiEnabled = wifiEnabled,
            wifiSsid = ssid,
            wifiConnected = wifiConnected,
            mobileConnected = mobileConnected,
            hotspotEnabled = hotspot,
            rxBytesTotal = rxTotal,
            txBytesTotal = txTotal,
            rxBytesToday = rxToday,
            txBytesToday = txToday,
            hourlyRx = hourlyRx,
            hourlyTx = hourlyTx,
        )
    }

    fun setWifiEnabled(enabled: Boolean): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            false
        } else {
            @Suppress("DEPRECATION")
            wifiManager.setWifiEnabled(enabled)
        }
    }

    fun openWifiPanel() {
        context.startActivity(
            android.content.Intent(Settings.Panel.ACTION_WIFI).addFlags(
                android.content.Intent.FLAG_ACTIVITY_NEW_TASK,
            ),
        )
    }

    fun openTetherSettings() {
        context.startActivity(
            android.content.Intent(Settings.ACTION_WIRELESS_SETTINGS).addFlags(
                android.content.Intent.FLAG_ACTIVITY_NEW_TASK,
            ),
        )
    }

    fun openDataUsageSettings() {
        context.startActivity(
            android.content.Intent(Settings.ACTION_DATA_USAGE_SETTINGS).addFlags(
                android.content.Intent.FLAG_ACTIVITY_NEW_TASK,
            ),
        )
    }

    fun mobileDataEnabled(): Boolean {
        return telephony.isDataEnabled
    }

    fun requestHotspot(onResult: (Boolean, String?) -> Unit) {
        val tm = tetheringManager
        if (tm == null) {
            onResult(false, "需要 Android 11+")
            return
        }
        val executor = Executor { it.run() }
        tm.startTethering(
            TetheringManager.TETHERING_WIFI,
            executor,
            object : TetheringManager.StartTetheringCallback {
                override fun onTetheringStarted() = onResult(true, null)
                override fun onTetheringFailed(error: Int) =
                    onResult(false, "开启热点失败 ($error)，请在系统设置中授权")
            },
        )
    }

    fun stopHotspot(onResult: (Boolean) -> Unit) {
        val tm = tetheringManager ?: return onResult(false)
        val executor = Executor { it.run() }
        tm.stopTethering(TetheringManager.TETHERING_WIFI, executor) {
            onResult(true)
        }
    }

    private fun readHotspotEnabled(): Boolean? {
        return try {
            val method = wifiManager.javaClass.getDeclaredMethod("isWifiApEnabled")
            method.isAccessible = true
            method.invoke(wifiManager) as Boolean
        } catch (_: Exception) {
            null
        }
    }

    private fun todayMobileWifiBytes(): Pair<Long, Long> {
        val zone = ZoneId.systemDefault()
        val startOfDay = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        val now = System.currentTimeMillis()
        var rx = 0L
        var tx = 0L
        for (type in intArrayOf(ConnectivityManager.TYPE_WIFI, ConnectivityManager.TYPE_MOBILE)) {
            try {
                val bucket = networkStats.querySummary(type, null, startOfDay, now)
                rx += sumBucket(bucket, NetworkStats.Bucket::getRxBytes)
                tx += sumBucket(bucket, NetworkStats.Bucket::getTxBytes)
                bucket.close()
            } catch (_: SecurityException) {
                // PACKAGE_USAGE_STATS not granted — fall back to device totals delta is handled elsewhere
            } catch (_: Exception) {
            }
        }
        return rx to tx
    }

    private fun hourlyBuckets(): Pair<List<Long>, List<Long>> {
        val rx = MutableList(24) { 0L }
        val tx = MutableList(24) { 0L }
        val zone = ZoneId.systemDefault()
        val dayStart = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        val now = System.currentTimeMillis()
        val hourMs = 3_600_000L
        for (hour in 0 until 24) {
            val from = dayStart + hour * hourMs
            val to = minOf(from + hourMs, now)
            if (from >= now) break
            for (type in intArrayOf(ConnectivityManager.TYPE_WIFI, ConnectivityManager.TYPE_MOBILE)) {
                try {
                    val bucket = networkStats.querySummary(type, null, from, to)
                    rx[hour] += sumBucket(bucket, NetworkStats.Bucket::getRxBytes)
                    tx[hour] += sumBucket(bucket, NetworkStats.Bucket::getTxBytes)
                    bucket.close()
                } catch (_: Exception) {
                }
            }
        }
        return rx to tx
    }

    private fun sumBucket(
        stats: NetworkStats,
        field: (NetworkStats.Bucket) -> Long,
    ): Long {
        var total = 0L
        val bucket = NetworkStats.Bucket()
        while (stats.hasNextBucket()) {
            stats.getNextBucket(bucket)
            total += field(bucket)
        }
        return total
    }
}
