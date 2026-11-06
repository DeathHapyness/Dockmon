#!/bin/sh
set -e
ADB=${1:-adb}
APK=${2:-app/build/outputs/apk/debug/app-debug.apk}
"$ADB" install -r -t "$APK"
"$ADB" shell dpm set-device-owner dev.rique.guardiao/.kiosk.AdminReceiver
"$ADB" shell am start -n dev.rique.guardiao/.MainActivity
