#!/usr/bin/env bash
set -euo pipefail
command -v gh >/dev/null || { echo 'Install GitHub CLI (gh)'; exit 1; }
command -v git >/dev/null || { echo 'Install git'; exit 1; }
gh auth status >/dev/null
ROOT="$(cd "$(dirname "$0")" && pwd)"
REPO='khalid003600-png/supreme-telegram'
BRANCH='wolfox-current-structure-build'
grep -Fq "applicationId 'com.wolfox.gps'" "$ROOT/app/build.gradle"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
gh repo clone "$REPO" "$TMP/repo" -- --quiet
cd "$TMP/repo"
git fetch origin "$BRANCH"
git checkout -B "$BRANCH" "origin/$BRANCH"
# Remove tracked files in this dedicated build branch, then import the matching source.
git rm -rq --ignore-unmatch .
cp -a "$ROOT"/. .
rm -f deploy-and-build-linux.sh
# No local generated build files or sensitive signing material should be committed.
git add -A
git diff --cached --quiet && { echo 'No changes to upload'; } || {
  git -c user.name='WolFox Build' -c user.email='wolfox-build@users.noreply.github.com' commit -m 'Import matching WolFox source and Android build workflow'
  git push origin "$BRANCH"
}
gh workflow run wolfox-build.yml --repo "$REPO" --ref "$BRANCH"
echo "View runs: https://github.com/$REPO/actions/workflows/wolfox-build.yml"
