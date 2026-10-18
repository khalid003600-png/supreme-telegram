#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
F=app/src/main/java/com/wolfox/gps/manager/FloatingManager.java
G=app/src/main/java/com/wolfox/gps/manager/GPSMockManager.java
grep -q 'safeBounds(parent, view)' "$F"
grep -q 'WindowInsets.Type.displayCutout()' "$F"
grep -q 'Valid location required' "$G"
grep -q 'value.getTime() == best.getTime()' "$G"
python3 - <<'PY'
from pathlib import Path
for n in ('FloatingManager.java','GPSMockManager.java'):
 s=(Path('app/src/main/java/com/wolfox/gps/manager')/n).read_text()
 assert s.count('{') == s.count('}'), n
print('PASS: source static assertions (not compilation or Android runtime)')
PY
echo 'PASS: static checks only; Gradle build is handled separately'
