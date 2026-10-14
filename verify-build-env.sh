#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$ROOT/toolchain/android-sdk}}"
BT="$SDK/build-tools/34.0.0"
fail(){ echo "[FAIL] $*" >&2; exit 2; }
command -v java >/dev/null || fail "Java 17 is missing"
JAVA_MAJOR="$(java -version 2>&1 | sed -n '1s/.*version "\([0-9][0-9]*\).*/\1/p')"
[[ "$JAVA_MAJOR" == 17 ]] || fail "Java 17 required; found ${JAVA_MAJOR:-unknown}"
for x in zip keytool; do command -v "$x" >/dev/null || fail "$x is missing"; done
[[ -f "$SDK/platforms/android-34/android.jar" ]] || fail "Android SDK platform 34 missing under $SDK"
for x in aapt2 d8 zipalign apksigner; do [[ -x "$BT/$x" ]] || fail "Build Tools 34.0.0 missing: $x"; done
echo "[OK] Linux WolFox build environment is ready"
