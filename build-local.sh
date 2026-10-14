#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
sdk_root="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$project_dir/../toolchain/android-sdk}}"
platform_jar="${ANDROID_PLATFORM_JAR:-$sdk_root/platforms/android-34/android.jar}"
build_tools="${ANDROID_BUILD_TOOLS:-$sdk_root/build-tools/34.0.0}"
output_dir="$project_dir/build/local"
version_name="${WOLFOX_VERSION_NAME:-3.1.1}"
version_code="${WOLFOX_VERSION_CODE:-313}"
ecj_jar="${ECJ_JAR:-$project_dir/../toolchain/downloads/ecj-3.38.0.jar}"

fail() {
  printf 'Build preflight failed: %s\n' "$*" >&2
  exit 2
}

require_file() {
  [[ -f "$1" ]] || fail "Required file is missing: $1"
}

require_executable() {
  [[ -x "$1" ]] || fail "Required Android build tool is missing or not executable: $1"
}

# Run all preflight checks before touching build/local.
require_file "$platform_jar"
for tool in aapt2 d8 zipalign apksigner; do
  require_executable "$build_tools/$tool"
done
command -v java >/dev/null 2>&1 || fail 'Java runtime is required.'
java_feature="$(java -version 2>&1 | sed -n '1s/.*version "\([0-9][0-9]*\).*/\1/p')"
[[ "$java_feature" == '17' ]] || fail "Java 17 is required for this reproducible build; found: ${java_feature:-unknown}."
command -v zip >/dev/null 2>&1 || fail 'zip is required.'
command -v keytool >/dev/null 2>&1 || fail 'keytool is required for APK signing.'
[[ "$version_name" =~ ^[0-9]+(\.[0-9]+){1,3}([.-][A-Za-z0-9]+)?$ ]] || fail 'WOLFOX_VERSION_NAME is invalid.'
[[ "$version_code" =~ ^[1-9][0-9]*$ ]] || fail 'WOLFOX_VERSION_CODE must be a positive integer.'

compiler=''
if command -v javac >/dev/null 2>&1; then
  compiler='javac'
else
  require_file "$ecj_jar"
  compiler='ecj'
fi

key_store="${WOLFOX_KEYSTORE_PATH:-}"
key_alias="${WOLFOX_KEY_ALIAS:-}"
key_store_pass="${WOLFOX_KEYSTORE_PASS:-}"
key_pass="${WOLFOX_KEY_PASS:-}"
if [[ -z "$key_store$key_alias$key_store_pass$key_pass" ]]; then
  if [[ "${WOLFOX_ALLOW_TEST_KEYSTORE:-0}" != '1' ]]; then
    fail 'Release signing requires WOLFOX_KEYSTORE_PATH, WOLFOX_KEY_ALIAS, WOLFOX_KEYSTORE_PASS, and WOLFOX_KEY_PASS. Set WOLFOX_ALLOW_TEST_KEYSTORE=1 only for an isolated test build.'
  fi
  key_store="$output_dir/test-signing/wolfox-module-test.jks"
  key_alias='wolfox-module-test'
  key_store_pass="$(od -An -N24 -tx1 /dev/urandom | tr -d ' \n')"
  key_pass="$key_store_pass"
  signing_mode='test'
else
  [[ -n "$key_store" && -n "$key_alias" && -n "$key_store_pass" && -n "$key_pass" ]] || fail 'All release-signing variables must be provided together.'
  require_file "$key_store"
  signing_mode='release'
fi

rm -rf "$output_dir"
mkdir -p "$output_dir/stub-classes" "$output_dir/classes" "$output_dir/dex" "$output_dir/apk"

mapfile -d '' stub_sources < <(find "$project_dir/stubs" -name '*.java' -print0 | sort -z)
((${#stub_sources[@]} > 0)) || fail 'No Xposed stub sources were found.'
if [[ "$compiler" == 'javac' ]]; then
  javac -encoding UTF-8 -source 8 -target 8 -classpath "$platform_jar" \
    -d "$output_dir/stub-classes" "${stub_sources[@]}"
else
  java -jar "$ecj_jar" -proc:none -encoding UTF-8 -source 1.8 -target 1.8 \
    -classpath "$platform_jar" -d "$output_dir/stub-classes" "${stub_sources[@]}"
fi

(cd "$output_dir/stub-classes" && zip -q -r "$output_dir/xposed-stubs.jar" .)

mapfile -d '' main_sources < <(find "$project_dir/app/src/main/java" -name '*.java' -print0 | sort -z)
((${#main_sources[@]} > 0)) || fail 'No module Java sources were found.'
if [[ "$compiler" == 'javac' ]]; then
  javac -encoding UTF-8 -source 8 -target 8 \
    -classpath "$platform_jar:$output_dir/xposed-stubs.jar" \
    -d "$output_dir/classes" "${main_sources[@]}"
else
  java -jar "$ecj_jar" -proc:none -encoding UTF-8 -source 1.8 -target 1.8 \
    -classpath "$platform_jar:$output_dir/xposed-stubs.jar" \
    -d "$output_dir/classes" "${main_sources[@]}"
fi

mapfile -d '' class_files < <(find "$output_dir/classes" -name '*.class' -print0 | sort -z)
((${#class_files[@]} > 0)) || fail 'Java compilation produced no classes.'
"$build_tools/d8" --release --min-api 21 \
  --lib "$platform_jar" --lib "$output_dir/xposed-stubs.jar" \
  --output "$output_dir/dex" "${class_files[@]}"

"$build_tools/aapt2" link \
  -I "$platform_jar" \
  --manifest "$project_dir/app/src/main/AndroidManifest.xml" \
  --min-sdk-version 21 --target-sdk-version 34 \
  --version-code "$version_code" --version-name "$version_name" \
  -o "$output_dir/apk/base.apk"

unsigned="$output_dir/apk/WolFox_GPS_Module_v${version_name}_unsigned.apk"
aligned="$output_dir/apk/WolFox_GPS_Module_v${version_name}_aligned.apk"
signed="$output_dir/apk/WolFox_GPS_Module_v${version_name}_${signing_mode}_SIGNED.apk"
cp "$output_dir/apk/base.apk" "$unsigned"
cp "$output_dir/dex/classes.dex" "$output_dir/apk/classes.dex"
(cd "$project_dir/app/src/main" && zip -q -r "$unsigned" assets)
(cd "$output_dir/apk" && zip -q -u "$(basename "$unsigned")" classes.dex)

"$build_tools/zipalign" -f 4 "$unsigned" "$aligned"
if [[ "$signing_mode" == 'test' ]]; then
  mkdir -p "$(dirname "$key_store")"
  keytool -genkeypair -noprompt -keystore "$key_store" \
    -storepass "$key_store_pass" -keypass "$key_pass" -alias "$key_alias" \
    -keyalg RSA -keysize 3072 -validity 30 \
    -dname 'CN=WolFox Test Module,OU=Testing,O=WolFox,C=SA' >/dev/null 2>&1
fi

"$build_tools/apksigner" sign \
  --ks "$key_store" --ks-key-alias "$key_alias" \
  --ks-pass "pass:$key_store_pass" --key-pass "pass:$key_pass" \
  --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true \
  --out "$signed" "$aligned"

"$build_tools/apksigner" verify --verbose --print-certs "$signed"
printf 'build_mode=%s\n' "$signing_mode"
printf 'apk=%s\n' "$signed"
sha256sum "$signed"
