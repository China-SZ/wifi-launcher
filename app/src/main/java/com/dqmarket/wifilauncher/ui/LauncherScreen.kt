package com.dqmarket.wifilauncher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NetworkCell
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dqmarket.wifilauncher.LauncherUiState
import com.dqmarket.wifilauncher.network.NetworkSnapshot

enum class LauncherTab(val label: String) {
    Wifi("Wi‑Fi"),
    Network("网络"),
    Traffic("流量"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LauncherScreen(
    state: LauncherUiState,
    onRefresh: () -> Unit,
    onOpenWifiPanel: () -> Unit,
    onOpenTetherSettings: () -> Unit,
    onOpenDataUsage: () -> Unit,
    onToggleHotspot: (Boolean) -> Unit,
    onDismissMessage: () -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            onDismissMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pixel 随身 WiFi") },
                actions = {
                    Button(onClick = onRefresh) { Text("刷新") }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                LauncherTab.entries.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = {
                            Icon(
                                when (item) {
                                    LauncherTab.Wifi -> Icons.Default.Wifi
                                    LauncherTab.Network -> Icons.Default.Router
                                    LauncherTab.Traffic -> Icons.Default.ShowChart
                                },
                                contentDescription = item.label,
                            )
                        },
                        label = { Text(item.label) },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val snap = state.snapshot
            if (snap == null) {
                Text("正在读取网络状态…")
                return@Column
            }
            when (LauncherTab.entries[tab]) {
                LauncherTab.Wifi -> WifiTab(snap, onOpenWifiPanel)
                LauncherTab.Network -> NetworkTab(snap, onOpenTetherSettings, onOpenDataUsage, onToggleHotspot)
                LauncherTab.Traffic -> TrafficTab(snap)
            }
        }
    }
}

@Composable
private fun WifiTab(snap: NetworkSnapshot, onOpenWifiPanel: () -> Unit) {
    StatusCard(title = "Wi‑Fi 状态") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("开关")
            Text(if (snap.wifiEnabled) "已开启" else "已关闭", fontWeight = FontWeight.SemiBold)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("连接")
            Text(
                when {
                    snap.wifiConnected && snap.wifiSsid != null -> snap.wifiSsid
                    snap.wifiConnected -> "已连接"
                    else -> "未连接"
                },
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onOpenWifiPanel, modifier = Modifier.fillMaxWidth()) {
            Text("打开系统 Wi‑Fi 面板")
        }
        Text(
            "Android 10+ 不允许第三方直接改 Wi‑Fi 开关，需通过系统面板或 adb 授权。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NetworkTab(
    snap: NetworkSnapshot,
    onOpenTetherSettings: () -> Unit,
    onOpenDataUsage: () -> Unit,
    onToggleHotspot: (Boolean) -> Unit,
) {
    StatusCard(title = "蜂窝与热点") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("移动数据")
            Text(if (snap.mobileConnected) "已连接" else "未连接/无信号", fontWeight = FontWeight.SemiBold)
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Wi‑Fi 热点")
            when (snap.hotspotEnabled) {
                true -> Text("已开启", fontWeight = FontWeight.SemiBold, color = Color(0xFF188038))
                false -> Text("已关闭", fontWeight = FontWeight.SemiBold)
                null -> Text("未知", fontWeight = FontWeight.SemiBold)
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("尝试切换热点")
            Switch(
                checked = snap.hotspotEnabled == true,
                onCheckedChange = onToggleHotspot,
            )
        }
        Button(onClick = onOpenTetherSettings, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Router, contentDescription = null)
            Text("  热点 / 网络共享设置")
        }
        Button(onClick = onOpenDataUsage, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.NetworkCell, contentDescription = null)
            Text("  系统流量与 SIM 设置")
        }
    }
}

@Composable
private fun TrafficTab(snap: NetworkSnapshot) {
    StatusCard(title = "今日流量") {
        StatRow("下行", formatBytes(snap.rxBytesToday))
        StatRow("上行", formatBytes(snap.txBytesToday))
        StatRow("累计下行", formatBytes(snap.rxBytesTotal))
        StatRow("累计上行", formatBytes(snap.txBytesTotal))
    }
    StatusCard(title = "今日每小时用量 (下行 MB)") {
        TrafficBarChart(snap.hourlyRx)
    }
    StatusCard(title = "今日每小时用量 (上行 MB)") {
        TrafficBarChart(snap.hourlyTx)
    }
    Text(
        "精确分应用/分网卡统计需在首次启动后授予「使用情况访问」权限；未授权时显示为设备总流量近似值。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun StatusCard(title: String, content: @Composable Column.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun TrafficBarChart(hourlyBytes: List<Long>) {
    val maxMb = hourlyBytes.maxOrNull()?.let { it / (1024f * 1024f) }?.coerceAtLeast(1f) ?: 1f
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp),
    ) {
        val barWidth = size.width / 24f * 0.7f
        val gap = size.width / 24f * 0.3f
        hourlyBytes.forEachIndexed { hour, bytes ->
            val mb = bytes / (1024f * 1024f)
            val barHeight = (mb / maxMb) * size.height
            val left = hour * (barWidth + gap)
            drawRect(
                color = Color(0xFF1A73E8),
                topLeft = Offset(left, size.height - barHeight),
                size = Size(barWidth, barHeight),
            )
        }
    }
    Text("0–23 时", style = MaterialTheme.typography.labelSmall)
}
