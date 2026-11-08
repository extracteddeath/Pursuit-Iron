#!/usr/bin/env bash
set -euo pipefail
# This is installation/lifecycle smoke only, NOT an acceptance verdict on
# onboarding, progression quality, accessibility or device-wide UX.
package="io.github.extracteddeath.pursuitiron.kotlinlabtrial8"
component="$package/io.github.extracteddeath.pursuitiron.KotlinEnginePreviewActivity"
apk="$(find trial -type f -name '*.apk' -print -quit)"
mkdir -p smoke-out
test -s "$apk"
adb wait-for-device
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
adb install -r "$apk" | tee smoke-out/install.txt
adb shell pm path "$package" | tee smoke-out/installed-package.txt
adb logcat -c
adb shell am start -W -n "$component" | tee smoke-out/first-launch.txt
sleep 8
if ! adb shell pidof "$package" | grep -Eq '[0-9]'; then
  adb logcat -d -v brief -s AndroidRuntime:E > smoke-out/android-runtime-errors.txt || true
  echo "FAIL: Kotlin Lab process not running after first launch" | tee smoke-out/result.txt
  exit 1
fi
adb exec-out screencap -p > smoke-out/first-launch.png
# Background/resume should retain the app process and not land in legacy JS.
adb shell input keyevent KEYCODE_HOME
sleep 2
adb shell am start -W -n "$component" | tee smoke-out/resume.txt
sleep 4
if ! adb shell pidof "$package" | grep -Eq '[0-9]'; then
  echo "FAIL: Kotlin Lab process missing after resume" | tee smoke-out/result.txt
  exit 1
fi
# Fresh Activity after process shutdown, with empty local test history.
adb shell am force-stop "$package"
adb shell am start -W -n "$component" | tee smoke-out/cold-launch.txt
sleep 8
if ! adb shell pidof "$package" | grep -Eq '[0-9]'; then
  echo "FAIL: Kotlin Lab process missing after cold restart" | tee smoke-out/result.txt
  exit 1
fi
adb exec-out screencap -p > smoke-out/cold-launch.png
adb shell uiautomator dump /sdcard/kotlin-lab-ui.xml > smoke-out/uiautomator-log.txt 2>&1 || true
adb pull /sdcard/kotlin-lab-ui.xml smoke-out/ui-tree.xml > /dev/null 2>&1 || true
test -s smoke-out/ui-tree.xml
grep -F 'Your training' smoke-out/ui-tree.xml > /dev/null
if grep -F 'Native equipment could not be verified' smoke-out/ui-tree.xml; then
  echo "FAIL: fresh onboarding showed a false equipment error" | tee smoke-out/result.txt
  exit 1
fi
adb logcat -d -v brief -s AndroidRuntime:E > smoke-out/android-runtime-errors.txt || true
if grep -F "Process: $package" smoke-out/android-runtime-errors.txt; then
  echo "FAIL: AndroidRuntime reported Kotlin Lab fatal exception" | tee smoke-out/result.txt
  exit 1
fi
echo "PASS: trial8 APK installed, launched, resumed and cold-restarted on emulator API 35 without a false equipment error" | tee smoke-out/result.txt
echo "LIMIT: does not certify program creation, complete workout, backup, accessibility or a real phone" >> smoke-out/result.txt
