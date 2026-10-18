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
unsigned_only="${WOLFOX_UNSIGNED_ONLY:-0}"
xposed_api_jar="${XPOSED_API_JAR:-}"

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
require_file "$build_tools/lib/d8.jar"
if [[ -n "$xposed_api_jar" ]]; then require_file "$xposed_api_jar"; fi
for tool in aapt2 d8 zipalign apksigner; do
  require_executable "$build_tools/$tool"
done
command -v java >/dev/null 2>&1 || fail 'Java runtime is required.'
java_feature="$(java -version 2>&1 | sed -n '1s/.*version \"\([0-9][0-9]*\).*/\1/p')"
[[ "$java_feature" == '17' ]] || fail "Java 17 is required for this reproducible build; found: ${java_feature:-unknown}."
command -v zip >/dev/null 2>&1 || fail 'zip is required.'
[[ "$unsigned_only" == '0' || "$unsigned_only" == '1' ]] || fail 'WOLFOX_UNSIGNED_ONLY must be 0 or 1.'
if [[ "$unsigned_only" != '1' ]]; then
  command -v keytool >/dev/null 2>&1 || fail 'keytool is required for APK signing.'
fi
[[ "$version_name" =~ ^[0-9]+(\.[0-9]+){1,3}([.-][A-Za-z0-9]+)?$ ]] || fail 'WOLFOX_VERSION_NAME is invalid.'
[[ "$version_code" =~ ^[1-9][0-9]*$ ]] || fail 'WOLFOX_VERSION_CODE must be a positive integer.'
require_file "$project_dir/app/src/main/res/drawable/wolfox_module_icon.xml"
require_file "$project_dir/app/src/main/res/xml/ui_audit_data_extraction_rules.xml"

compiler=''
if command -v javac >/dev/null 2>&1; then
  compiler='javac'
else
  require_file "$ecj_jar"
  compiler='ecj'
fi

signing_mode='unsigned'
if [[ "$unsigned_only" != '1' ]]; then
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
fi

rm -rf "$output_dir"
mkdir -p "$output_dir/stub-classes" "$output_dir/classes" "$output_dir/dex" "$output_dir/apk" \
  "$output_dir/compiled-res"

xposed_library=''
if [[ -n "$xposed_api_jar" ]]; then
  xposed_library="$xposed_api_jar"
  printf 'xposed_api_mode=official-api-jar\n'
else
  mapfile -d '' stub_sources < <(find "$project_dir/stubs" -name '*.java' -print0 | sort -z)
  ((${#stub_sources[@]} > 0)) || fail 'No Xposed stub sources were found.'
  if [[ "$compiler" == 'javac' ]]; then
    javac -g:none -encoding UTF-8 -source 8 -target 8 -classpath "$platform_jar" \
      -d "$output_dir/stub-classes" "${stub_sources[@]}"
  else
    java -jar "$ecj_jar" -proc:none -encoding UTF-8 -source 1.8 -target 1.8 \
      -classpath "$platform_jar" -d "$output_dir/stub-classes" "${stub_sources[@]}"
  fi
  (cd "$output_dir/stub-classes" && zip -q -r "$output_dir/xposed-stubs.jar" .)
  xposed_library="$output_dir/xposed-stubs.jar"
  printf 'xposed_api_mode=compile-only-stubs\n'
fi

mapfile -d '' main_sources < <(find "$project_dir/app/src/main/java" -name '*.java' -print0 | sort -z)
((${#main_sources[@]} > 0)) || fail 'No module Java sources were found.'
if [[ "$compiler" == 'javac' ]]; then
  javac -g:none -encoding UTF-8 --release 8 \
    -classpath "$platform_jar:$xposed_library" \
    -d "$output_dir/classes" "${main_sources[@]}"
else
  java -jar "$ecj_jar" -proc:none -encoding UTF-8 -source 1.8 -target 1.8 \
    -classpath "$platform_jar:$xposed_library" \
    -d "$output_dir/classes" "${main_sources[@]}"
fi

mapfile -d '' class_files < <(find "$output_dir/classes" -name '*.class' -print0 | sort -z)
((${#class_files[@]} > 0)) || fail 'Java compilation produced no classes.'
# R8 preserves runtime entry points while obfuscating module internals.
(cd "$output_dir/classes" && zip -q -r "$output_dir/program.jar" .)
mkdir -p "$output_dir/private"
java -cp "$build_tools/lib/d8.jar" com.android.tools.r8.R8 \
  --release --min-api 21 --lib "$platform_jar" \
  --lib "$xposed_library" \
  --pg-conf "$project_dir/app/proguard-rules.pro" \
  --pg-map-output "$output_dir/private/mapping.txt" \
  --output "$output_dir/dex" "$output_dir/program.jar"

"$build_tools/aapt2" compile --dir "$project_dir/app/src/main/res" \
  -o "$output_dir/compiled-res"
mapfile -d '' compiled_resources < <(find "$output_dir/compiled-res" -type f -name '*.flat' -print0 | sort -z)
((${#compiled_resources[@]} > 0)) || fail 'Resource compilation produced no .flat files.'
resource_link_args=()
for resource in "${compiled_resources[@]}"; do
  resource_link_args+=( -R "$resource" )
done

python3 - "$project_dir/app/src/main/AndroidManifest.xml" "$output_dir/AndroidManifest.xml" <<'PY'
import sys
import xml.etree.ElementTree as ET
android = 'http://schemas.android.com/apk/res/android'
ET.register_namespace('android', android)
ET.register_namespace('tools', 'http://schemas.android.com/tools')
tree = ET.parse(sys.argv[1])
tree.getroot().set('package', 'com.wolfox.gps')
application = tree.getroot().find('application')
application.set('{' + android + '}debuggable', 'false')
application.set('{' + android + '}extractNativeLibs', 'false')
tree.write(sys.argv[2], encoding='utf-8', xml_declaration=True)
PY

"$build_tools/aapt2" link \
  -I "$platform_jar" \
  --manifest "$output_dir/AndroidManifest.xml" \
  --min-sdk-version 21 --target-sdk-version 34 \
  --version-code "$version_code" --version-name "$version_name" \
  "${resource_link_args[@]}" \
  -o "$output_dir/apk/base.apk"

unsigned="$output_dir/apk/WolFox_GPS_Module_v${version_name}_unsigned.apk"
aligned="$output_dir/apk/WolFox_GPS_Module_v${version_name}_aligned.apk"
signed="$output_dir/apk/WolFox_GPS_Module_v${version_name}_${signing_mode}_SIGNED.apk"
cp "$output_dir/apk/base.apk" "$unsigned"
cp "$output_dir/dex/classes.dex" "$output_dir/apk/classes.dex"
(cd "$project_dir/app/src/main" && zip -q -r "$unsigned" assets)
(cd "$output_dir/apk" && zip -q -u "$(basename "$unsigned")" classes.dex)

"$build_tools/zipalign" -f 4 "$unsigned" "$aligned"
if [[ "$unsigned_only" == '1' ]]; then
  unsigned_aligned="$output_dir/apk/WolFox_GPS_Module_v${version_name}_UNSIGNED_ALIGNED.apk"
  cp "$aligned" "$unsigned_aligned"
  "$build_tools/zipalign" -c 4 "$unsigned_aligned"
  printf 'build_mode=unsigned\n'
  printf 'apk=%s\n' "$unsigned_aligned"
  sha256sum "$unsigned_aligned"
  exit 0
fi
if [[ "$signing_mode" == 'test' ]]; then
  mkdir -p "$(dirname "$key_store")"
  keytool -genkeypair -noprompt -keystore "$key_store" \
    -storepass "$key_store_pass" -keypass "$key_pass" -alias "$key_alias" \
    -keyalg RSA -keysize 3072 -validity 30 \
    -dname 'CN=WolFox Test Module,OU=Testing,O=WolFox,C=SA' >/dev/null 2>&1
fi

export WOLFOX_SIGN_STORE_PASS="$key_store_pass" WOLFOX_SIGN_KEY_PASS="$key_pass"
"$build_tools/apksigner" sign \
  --ks "$key_store" --ks-key-alias "$key_alias" \
  --ks-pass env:WOLFOX_SIGN_STORE_PASS --key-pass env:WOLFOX_SIGN_KEY_PASS \
  --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true \
  --out "$signed" "$aligned"

unset WOLFOX_SIGN_STORE_PASS WOLFOX_SIGN_KEY_PASS
"$build_tools/apksigner" verify --verbose --print-certs "$signed"
printf 'build_mode=%s\n' "$signing_mode"
printf 'apk=%s\n' "$signed"
sha256sum "$signed"
