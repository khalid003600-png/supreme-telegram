#!/usr/bin/env bash
set -euo pipefail
variant="${1:?variant required}"
case "$variant" in 1|2|3|4|5) ;; *) exit 2 ;; esac
checkout_dir="$PWD"
output_dir="$checkout_dir/output/$variant"
mkdir -p "$output_dir"
python3 ci/unpack-wolfox.py "$variant"
project_dir="$checkout_dir/work/WolFox-Source-$variant"
export ANDROID_PLATFORM_JAR="${ANDROID_HOME:?}/platforms/android-34/android.jar"
export ANDROID_BUILD_TOOLS="$ANDROID_HOME/build-tools/34.0.0"
export WOLFOX_KEYSTORE_PATH="${RUNNER_TEMP:?}/wolfox-release-$variant.jks"
trap 'rm -f "$WOLFOX_KEYSTORE_PATH"' EXIT
mode=unsigned
if [[ -n "${WOLFOX_KEYSTORE_BASE64:-}${WOLFOX_KEY_ALIAS:-}${WOLFOX_KEYSTORE_PASS:-}${WOLFOX_KEY_PASS:-}" ]]; then
  [[ -n "${WOLFOX_KEYSTORE_BASE64:-}" && -n "${WOLFOX_KEY_ALIAS:-}" && -n "${WOLFOX_KEYSTORE_PASS:-}" && -n "${WOLFOX_KEY_PASS:-}" ]] || {
    echo 'Release signing configuration is incomplete; all four WOLFOX secrets are required.' >&2
    exit 2
  }
  python3 - <<'PY'
import base64, os
from pathlib import Path
Path(os.environ['WOLFOX_KEYSTORE_PATH']).write_bytes(base64.b64decode(os.environ['WOLFOX_KEYSTORE_BASE64'], validate=True))
PY
  chmod 600 "$WOLFOX_KEYSTORE_PATH"
  export WOLFOX_UNSIGNED_ONLY=0
  mode=release
else
  unset WOLFOX_KEYSTORE_PATH
  # Keep trap safe under set -u when no signing key was configured.
  trap - EXIT
  export WOLFOX_UNSIGNED_ONLY=1
fi
bash "$project_dir/verification/verify-host-policy.sh"
bash "$project_dir/build-local.sh" 2>&1 | tee "$output_dir/build.log"
if [[ "$mode" == release ]]; then
  built="$project_dir/build/local/apk/WolFox_GPS_Module_v3.1.1_release_SIGNED.apk"
  "$ANDROID_BUILD_TOOLS/apksigner" verify --verbose "$built" > "$output_dir/signature-verification.txt"
else
  built="$project_dir/build/local/apk/WolFox_GPS_Module_v3.1.1_UNSIGNED_ALIGNED.apk"
fi
test -s "$built"
"$ANDROID_BUILD_TOOLS/zipalign" -c 4 "$built"
"$ANDROID_BUILD_TOOLS/aapt2" dump badging "$built" > "$output_dir/apk-badging.txt"
grep -F "package: name='com.wolfox.gps.variant$variant'" "$output_dir/apk-badging.txt"
cp "$built" "$output_dir/WolFox-Variant-$variant-$mode.apk"
(cd "$output_dir" && sha256sum ./*.apk > SHA256SUMS.txt)
printf 'signing=%s\n' "$mode" >> "${GITHUB_OUTPUT:?}"
printf '### WolFox %s\n\n- Package: `com.wolfox.gps.variant%s`\n- Signing: `%s`\n- Source: `%s`\n' "$variant" "$variant" "$mode" "${GITHUB_SHA:?}" >> "${GITHUB_STEP_SUMMARY:?}"
