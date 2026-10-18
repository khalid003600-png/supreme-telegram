#!/usr/bin/env bash
set -euo pipefail
REPO_URL="${1:-https://github.com/khalid003600-png/supreme-telegram.git}"
BRANCH="wolfox-current-structure-build"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"
test -f app/src/main/java/com/wolfox/gps/manager/FloatingManager.java
test -f app/src/main/java/com/wolfox/gps/manager/GPSMockManager.java
grep -Fq "applicationId 'com.wolfox.gps'" app/build.gradle
bash tests-linux/check.sh
if [ ! -d .git ]; then git init -b "$BRANCH"; else git switch -c "$BRANCH" 2>/dev/null || git switch "$BRANCH"; fi
if ! git remote get-url origin >/dev/null 2>&1; then git remote add origin "$REPO_URL"; fi
git add -A
git commit -m 'Build WolFox current structure on Linux CI' || true
printf '\nReady to publish. The next command requires authenticated GitHub access:\n'
printf 'git push -u origin %s\n' "$BRANCH"
printf 'Then open: https://github.com/khalid003600-png/supreme-telegram/actions/workflows/wolfox-build.yml\n'
