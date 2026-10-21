#!/usr/bin/env bash
# Pixel 7 专用机：设为 Device Owner + 默认桌面 + 锁定任务模式
# 前提：出厂重置或无任何 Google/工作资料账号；已开启 USB 调试；已安装 APK
set -euo pipefail

PKG="com.dqmarket.wifilauncher"
APK="${1:-app/build/outputs/apk/debug/app-debug.apk}"

adb wait-for-device
if [[ -f "$APK" ]]; then
  adb install -r "$APK"
fi

adb shell dpm set-device-owner "${PKG}/.KioskDeviceAdmin" 2>/dev/null || {
  echo "Device Owner 设置失败：请恢复出厂设置，跳过 Google 账号，仅通过 adb 安装本应用后再运行。"
  exit 1
}

adb shell cmd package set-home-activity "${PKG}/.MainActivity"
adb shell am start -n "${PKG}/.MainActivity"
echo "完成。重启后应直接进入随身 WiFi 控制台。"
