#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$project_dir"
command -v python3 >/dev/null || { echo 'python3 is required' >&2; exit 2; }
command -v java >/dev/null || { echo 'Java 17 is required' >&2; exit 2; }
bash -n build-local.sh
bash -n scripts/verify-qa.sh
./gradlew :app:testDebugUnitTest :app:lintDebug --no-daemon
for check in \
  verify_build_consistency.py \
  verify_icon_lifecycle_fix.py \
  verify_license_expiry_enforcement.py \
  verify_no_root_dependency.py \
  verify_provider_compatibility.py \
  verify_safe_favorites_map.py \
  verify_merged_runtime.py \
  verify_explicit_visibility.py \
  verify_embedded_hosts.py; do
  python3 "tests/$check"
done
# Explicit test signing or authorized production-signing environment is required.
# Recommended: export XPOSED_API_JAR=/path/to/official/xposed-api-82.jar
bash build-local.sh
python3 tests/verify_protected_artifact.py
python3 tests/verify_packaging_contract.py
printf '\nPASS: local tests, Lint, source contracts, APK signature and packaging contracts.\n'
printf 'This does not prove live Xposed/LSPatch execution in the host on a physical device.\n'
