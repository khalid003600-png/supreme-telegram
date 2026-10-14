#!/usr/bin/env bash
set -euo pipefail
: "${ANDROID_HOME:?يجب تحديد ANDROID_HOME}"
if [[ -x ./gradlew ]]; then
  ./gradlew --no-daemon :app:assembleDebug --stacktrace
else
  gradle --no-daemon :app:assembleDebug --stacktrace
fi
APK=app/build/outputs/apk/debug/app-debug.apk
test -s "$APK"
"${ANDROID_HOME}/build-tools/35.0.0/apksigner" verify --verbose "$APK"
sha256sum "$APK"
