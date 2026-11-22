#!/usr/bin/env bash
# 第二次录制：用系统计算器代替浏览器作为"其他应用"，避免任何第三方内容
set -u
export MSYS_NO_PATHCONV=1
ADB="C:/android-sdk/platform-tools/adb.exe"
cd "E:/projects/ScreenRotator3/.workbuddy/tmp" || exit 1
log() { echo "[$(date +%M:%S)] $*"; }

$ADB shell settings put system accelerometer_rotation 0
$ADB shell settings put system user_rotation 0
$ADB shell am force-stop com.screenrotator3.app
sleep 1
$ADB shell am start -n com.screenrotator3.app/.MainActivity >/dev/null 2>&1
sleep 2

$ADB shell rm -f /sdcard/demo2.mp4
$ADB shell screenrecord --bit-rate 12000000 --time-limit 120 /sdcard/demo2.mp4 &
log "recording started"
sleep 4.5

python uidriver.py tap-rid cardLandscape
sleep 4
$ADB exec-out screencap -p > j1.png; log "j1 landscape"

python uidriver.py tap-rid btnMenu
sleep 2.5
python uidriver.py tap-desc "Notification bar"
sleep 3
$ADB shell input keyevent KEYCODE_BACK
sleep 2

$ADB shell monkey -p com.miui.calculator -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
sleep 4
$ADB exec-out screencap -p > j2.png; log "j2 calculator landscape"

$ADB shell cmd statusbar expand-notifications
sleep 3.5
$ADB exec-out screencap -p > j3.png; log "j3 shade"

python uidriver.py tap-rid slot_portrait
sleep 3.5
$ADB exec-out screencap -p > j4.png; log "j4 portrait"

$ADB shell cmd statusbar collapse
sleep 1.5
$ADB shell am start -n com.screenrotator3.app/.MainActivity >/dev/null 2>&1
sleep 3
python uidriver.py tap-rid cardAuto
sleep 3.5
$ADB exec-out screencap -p > j5.png; log "j5 auto"

$ADB shell settings put system user_rotation 1
sleep 3.5
$ADB exec-out screencap -p > j6.png; log "j6 system landscape"
$ADB shell settings put system user_rotation 0
sleep 2.5

$ADB shell pkill -INT screenrecord
sleep 2
$ADB pull /sdcard/demo2.mp4 "E:/projects/ScreenRotator3/.workbuddy/tmp/demo2.mp4" >/dev/null 2>&1
ls -la demo2.mp4
log "done"
