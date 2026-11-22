#!/usr/bin/env bash
# 第四次录制：每次从 pm clear 的干净状态开始，避免上一轮的偏好残留
set -u
export MSYS_NO_PATHCONV=1
ADB="C:/android-sdk/platform-tools/adb.exe"
PKG=com.screenrotator3.app
cd "E:/projects/ScreenRotator3/.workbuddy/tmp" || exit 1
log() { echo "[$(date +%M:%S)] $*"; }

# ---------- 1. 干净状态 ----------
$ADB shell svc power stayon true
$ADB shell settings put system show_touches 1
$ADB shell input keyevent KEYCODE_WAKEUP
$ADB shell settings put system accelerometer_rotation 0
$ADB shell settings put system user_rotation 0
$ADB shell pm clear $PKG >/dev/null
$ADB shell appops set $PKG SYSTEM_ALERT_WINDOW allow
$ADB shell pm grant $PKG android.permission.POST_NOTIFICATIONS
$ADB shell am start -n $PKG/.MainActivity >/dev/null 2>&1
sleep 3
python uidriver.py tap-rid btnMenu
sleep 2
python uidriver.py tap-rid segLangEn
sleep 2.5
$ADB shell am force-stop $PKG
sleep 1
$ADB shell am start -n $PKG/.MainActivity >/dev/null 2>&1
sleep 2.5
log "state reset, orient=$($ADB shell dumpsys display | grep -m1 -o 'mCurrentOrientation=[0-9]*')"

# ---------- 2. 清空通知栏里其他应用的通知 ----------
$ADB shell cmd statusbar expand-notifications
sleep 2
python uidriver.py tap-desc "清除所有通知。" || true
sleep 1.5
$ADB shell cmd statusbar collapse
sleep 1

# ---------- 3. 录制 ----------
$ADB shell rm -f /sdcard/demo5.mp4
$ADB shell screenrecord --bit-rate 12000000 --time-limit 120 /sdcard/demo5.mp4 &
log "recording started"
sleep 4.5

python uidriver.py tap-rid cardLandscape || log "FAIL landscape"
sleep 4
$ADB exec-out screencap -p > g1.png; log "f1 landscape"

python uidriver.py tap-rid btnMenu || log "FAIL menu"
sleep 2.5
python uidriver.py tap-desc "Notification bar" || log "FAIL bar"
sleep 3
$ADB shell input keyevent KEYCODE_BACK
sleep 2
log "bar state: $($ADB shell dumpsys notification --noredact | grep -c 'contentView=com.screenrotator3.app')"

$ADB shell monkey -p com.miui.calculator -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
sleep 4
$ADB exec-out screencap -p > g2.png; log "f2 calculator"

$ADB shell cmd statusbar expand-notifications
sleep 4
for i in 1 2 3; do
  python uidriver.py tap-rid slot_portrait && break
  sleep 1.5
done
sleep 3
$ADB exec-out screencap -p > g3.png; log "f3 after portrait tap"

$ADB shell cmd statusbar collapse
sleep 1.5
$ADB shell am start -n $PKG/.MainActivity >/dev/null 2>&1
sleep 3
$ADB shell settings put system accelerometer_rotation 0
$ADB shell settings put system user_rotation 0
sleep 1.5
log "before auto: orient=$($ADB shell dumpsys display | grep -m1 -o 'mCurrentOrientation=[0-9]*')"
python uidriver.py tap-rid cardAuto || log "FAIL auto"
sleep 3.5
$ADB exec-out screencap -p > g4.png; log "f4 auto"

$ADB shell settings put system user_rotation 1
sleep 3.5
$ADB exec-out screencap -p > g5.png; log "f5 system landscape"
$ADB shell settings put system user_rotation 0
sleep 2.5
$ADB exec-out screencap -p > g6.png; log "f6 back to portrait"

$ADB shell settings put system show_touches 0
$ADB shell pkill -INT screenrecord
sleep 2
$ADB pull /sdcard/demo5.mp4 "E:/projects/ScreenRotator3/.workbuddy/tmp/demo5.mp4" >/dev/null 2>&1
ls -la demo5.mp4
log "done"
