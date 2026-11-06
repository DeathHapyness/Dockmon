#!/bin/sh
ADB=${1:-adb}
"$ADB" shell dpm remove-active-admin dev.rique.guardiao/.kiosk.AdminReceiver
