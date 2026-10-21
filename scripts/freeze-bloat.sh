#!/usr/bin/env bash
# 禁用常见非必要应用（不删系统，可 adb shell pm enable 恢复）
# 不会禁用：电话/短信/设置/网络栈/本启动器
set -euo pipefail

KEEP=(
  com.dqmarket.wifilauncher
  com.android.settings
  com.android.phone
  com.android.stk
  com.google.android.networkstack
  com.google.android.networkstack.tethering
  com.android.server.telecom
)

should_keep() {
  local pkg="$1"
  for k in "${KEEP[@]}"; do
    [[ "$pkg" == "$k" ]] && return 0
  done
  return 1
}

# 用户可见 launcher / 商店 / 娱乐 / 社交（按需增删）
DISABLE=(
  com.google.android.apps.nexuslauncher
  com.android.vending
  com.google.android.gm
  com.google.android.youtube
  com.google.android.apps.maps
  com.google.android.apps.photos
  com.google.android.apps.messaging
  com.android.chrome
  com.spotify.music
  com.instagram.android
)

for pkg in "${DISABLE[@]}"; do
  if should_keep "$pkg"; then continue; fi
  adb shell pm disable-user --user 0 "$pkg" 2>/dev/null || true
  echo "disabled $pkg"
done

echo "完成。热点/WiFi 相关包请勿随意 disable。"
