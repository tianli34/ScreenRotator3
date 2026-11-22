#!/usr/bin/env bash
# 一次性录制 specialUse 演示视频：全程一条连续录屏，期间只用 adb 驱动界面。
set -u
export MSYS_NO_PATHCONV=1
ADB="C:/android-sdk/platform-tools/adb.exe"
cd "E:/projects/ScreenRotator3/.workbuddy/tmp" || exit 1

log() { echo "[$(date +%M:%S)] $*"; }

# 起始状态：竖屏、AUTO、无服务
$ADB shell settings put system user_rotation 0
$ADB shell am force-stop com.screenrotator3.app
sleep 1
$ADB shell am start -n com.screenrotator3.app/.MainActivity >/dev/null 2>&1
sleep 2

$ADB shell rm -f /sdcard/demo.mp4
$ADB shell screenrecord --bit-rate 12000000 --time-limit 120 /sdcard/demo.mp4 &
REC_PID=$!
log "recording started"
sleep 5

python uidriver.py tap-rid cardLandscape
sleep 3.5
$ADB exec-out screencap -p > k1.png; log "k1 landscape"

python uidriver.py tap-rid btnMenu
sleep 2
python uidriver.py tap-desc "Notification bar"
sleep 3
$ADB shell input keyevent KEYCODE_BACK
sleep 1.5

$ADB shell am start -n com.android.chrome/com.google.android.apps.chrome.Main \
  -a android.intent.action.VIEW -d "https://www.baidu.com" >/dev/null 2>&1
sleep 5
$ADB exec-out screencap -p > k2.png; log "k2 chrome"

$ADB shell cmd statusbar expand-notifications
sleep 3
$ADB exec-out screencap -p > k3.png; log "k3 shade"

python uidriver.py tap-rid slot_portrait
sleep 3
$ADB exec-out screencap -p > k4.png; log "k4 portrait in chrome"

$ADB shell cmd statusbar collapse
sleep 1
$ADB shell am start -n com.screenrotator3.app/.MainActivity >/dev/null 2>&1
sleep 3
python uidriver.py tap-rid cardAuto
sleep 3.5
$ADB exec-out screencap -p > k5.png; log "k5 auto"

$ADB shell settings put system user_rotation 1
sleep 3.5
$ADB exec-out screencap -p > k6.png; log "k6 system landscape"
$ADB shell settings put system user_rotation 0
sleep 2.5

$ADB shell pkill -INT screenrecord
sleep 2
$ADB pull /sdcard/demo.mp4 "E:/projects/ScreenRotator3/.workbuddy/tmp/demo.mp4" >/dev/null 2>&1
ls -la demo.mp4
log "done"
