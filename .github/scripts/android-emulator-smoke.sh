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
adb logcat -c

RUNNER="$(adb shell pm list instrumentation | tr -d '\r' | grep "target=$APP_ID" | sed -n 's/^instrumentation:\([^ ]*\).*/\1/p' | head -n 1)"
if [[ -z "$RUNNER" ]]; then
  echo "ERROR: instrumentation runner for $APP_ID was not registered." >&2
  adb shell pm list instrumentation
  exit 1
fi

INSTRUMENTATION_STATUS=0
adb shell am instrument -w -r "$RUNNER" 2>&1 | tee artifacts/apks/instrumentation.log || INSTRUMENTATION_STATUS=$?

# Preserve the device-side crash and AndroidRuntime trace even when the runner
# process dies before it can report a JUnit failure.
adb logcat -b all -d -v threadtime > artifacts/apks/instrumentation-logcat.txt
if [[ "$INSTRUMENTATION_STATUS" -ne 0 ]] || ! grep -q "OK (" artifacts/apks/instrumentation.log; then
  echo "ERROR: instrumentation failed (adb exit $INSTRUMENTATION_STATUS)." >&2
  grep -E -i "FATAL EXCEPTION|AndroidRuntime|am_crash|am_anr|Process: app\.georgia\.offline|SIGABRT|Fatal signal|OutOfMemoryError" artifacts/apks/instrumentation-logcat.txt | tail -n 160 || true
  exit 1
fi

adb shell am force-stop "$APP_ID"
adb shell monkey -p "$APP_ID" 1
sleep 8
adb shell screencap -p /sdcard/oflayn-map.png
adb pull /sdcard/oflayn-map.png artifacts/apks/oflayn-map.png
