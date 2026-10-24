#!/usr/bin/env bash
set -euo pipefail

fail() { printf 'CHECK FAILED: %s\n' "$*" >&2; exit 1; }
pass() { printf 'PASS: %s\n' "$*"; }
require_file() { [[ -s "$1" ]] || fail "Missing or empty file: $1"; pass "$1 exists"; }

require_file settings.gradle
require_file build.gradle
require_file gradle/wrapper/gradle-wrapper.jar
require_file gradle/wrapper/gradle-wrapper.properties
require_file app/build.gradle
require_file app/src/main/AndroidManifest.xml
require_file app/libs/xposed-api-82.jar
require_file app/src/main/java/com/wolfox/gps/manager/FloatingManager.java
require_file app/src/main/java/com/wolfox/gps/manager/GPSMockManager.java

grep -Eq 'compileSdk[[:space:]]+34' app/build.gradle || fail 'compileSdk must be 34'
grep -Eq 'versionCode[[:space:]]+313' app/build.gradle || fail 'versionCode must be 313'
grep -Eq 'versionName[[:space:]]+"3\.1\.1"' app/build.gradle || fail 'versionName must be 3.1.1'
grep -Fq "applicationId 'com.wolfox.gps.edition.gps'" app/build.gradle || fail 'GPS-only applicationId mismatch'
grep -Fq 'android.permission.ACCESS_FINE_LOCATION' app/src/main/AndroidManifest.xml || fail 'Fine location permission missing'
grep -Fq 'xposedmodule' app/src/main/AndroidManifest.xml || fail 'Xposed module metadata missing'

if grep -Eqi 'BLUETOOTH|CAMERA|READ_MEDIA_IMAGES|WRITE_EXTERNAL_STORAGE' app/src/main/AndroidManifest.xml; then
  fail 'GPS-only manifest includes unrelated Bluetooth/camera/image permissions'
fi

if command -v bash >/dev/null 2>&1; then
  bash -n build-local.sh
  pass 'build-local.sh shell syntax'
fi
pass 'GPS-only source preflight completed'
