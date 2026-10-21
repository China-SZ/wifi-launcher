package com.dqmarket.wifilauncher.ui

fun formatBytes(bytes: Long): String {
    if (bytes < 0) return "—"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var i = 0
    while (value >= 1024 && i < units.lastIndex) {
        value /= 1024
        i++
    }
    return if (i == 0) "${bytes} ${units[i]}" else "%.2f %s".format(value, units[i])
}
