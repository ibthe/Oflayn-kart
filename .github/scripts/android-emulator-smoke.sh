#!/usr/bin/env bash
set -euo pipefail

mkdir -p artifacts/apks
APP_APK="$(find artifacts/apks -type f -name '*.apk' ! -name '*androidTest*.apk' | head -n 1)"
TEST_APK="$(find artifacts/apks -type f -name '*androidTest*.apk' | head -n 1)"

if [[ -z "$APP_APK" || -z "$TEST_APK" ]]; then
  echo "ERROR: app or instrumentation APK is missing." >&2
  find artifacts/apks -maxdepth 5 -type f -print
  exit 1
fi

APP_ID="app.georgia.offline.debug"
adb install -r "$APP_APK"
adb install -r "$TEST_APK"
adb shell pm clear "$APP_ID"

RUNNER="$(adb shell pm list instrumentation | tr -d '\r' | grep "target=$APP_ID" | sed -n 's/^instrumentation:\\([^ ]*\\).*/\\1/p' | head -n 1)"
if [[ -z "$RUNNER" ]]; then
  echo "ERROR: instrumentation runner for $APP_ID was not registered." >&2
  adb shell pm list instrumentation
  exit 1
fi

adb shell am instrument -w -r "$RUNNER" | tee artifacts/apks/instrumentation.log
grep -q "OK (" artifacts/apks/instrumentation.log

adb shell am force-stop "$APP_ID"
adb shell monkey -p "$APP_ID" 1
sleep 8
adb shell screencap -p /sdcard/oflayn-map.png
adb pull /sdcard/oflayn-map.png artifacts/apks/oflayn-map.png
