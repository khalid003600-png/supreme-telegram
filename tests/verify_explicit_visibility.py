#!/usr/bin/env python3
"""Source guards only; Java Robolectric tests exercise local hide/show state separately."""
from pathlib import Path
import re
root=Path(__file__).resolve().parents[1]
java=root/'app/src/main/java/com/wolfox/gps'
texts={str(p.relative_to(java)):p.read_text(encoding='utf-8') for p in java.rglob('*.java')}
storage=texts['util/WFStorage.java']
floating=texts['manager/FloatingManager.java']
bootstrap=texts['DirectBootstrap.java']
activity=texts['hook/ActivityHook.java']
activation=texts['ui/ActivationDialog.java']
license=texts['manager/LicenseManager.java']
assert 'prefs.getBoolean(KEY_BUBBLE_VISIBLE, true)' in storage
visibility = storage.split('public boolean isBubbleVisible()', 1)[1].split('\n    }', 1)[0]
assert 'Process.myPid()' not in visibility and 'processStartIdentity' not in visibility
assert 'public static void showControls(Activity activity)' in bootstrap
assert 'public static void hideControls(Activity activity)' in bootstrap
assert 'restoreRequestedVisibility(activity);' in floating
assert 'if (token == attachToken && !visible)' in floating
assert 'FloatingManager.getInstance().show(act)' not in activity
assert 'FloatingManager.getInstance().show(activity)' not in activation
assert 'setBubbleVisible(true)' not in activation
assert 'FloatingManager.getInstance().show(activity)' not in license
for path,source in texts.items():
    if 'setBubbleVisible(true)' in source:
        assert path=='manager/FloatingManager.java', f'implicit show write in {path}'
    if re.search(r'FloatingManager\.getInstance\(\)\.show\(',source):
        assert path=='DirectBootstrap.java', f'implicit show call in {path}'
assert 'MAIN.postDelayed(pending, 850L)' in texts['hook/CenterLongPressHook.java']
assert 'REVEAL_HOLD_MS = 850L' in bootstrap
assert 'CenterLongPressHook.cancel();' in activity
assert 'cancelReveal();' in bootstrap
assert 'cancelRenders();' in bootstrap
assert 'WeakReference<Activity>' in floating and 'WeakReference<View>' in floating
print('PASS: first-open visible; explicit hide/show persists independently of process identity')
print('PASS: explicit show/hide bridge, 850ms reveal and cancellation guards (source-level only)')
