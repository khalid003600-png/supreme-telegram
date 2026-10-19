#!/usr/bin/env python3
"""Source/metadata contract only; loading into a real host is outside this local test."""
from pathlib import Path
import json

root = Path(__file__).resolve().parents[1]
java = root / 'app/src/main/java/com/wolfox/gps'
hook = (java / 'hook/WolFoxHook.java').read_text(encoding='utf-8')
policy = (java / 'util/WFHostPolicy.java').read_text(encoding='utf-8')
bridge = (java / 'DirectBootstrap.java').read_text(encoding='utf-8')
floating = (java / 'manager/FloatingManager.java').read_text(encoding='utf-8')
storage = (java / 'util/WFStorage.java').read_text(encoding='utf-8')
manifest = (root / 'app/src/main/AndroidManifest.xml').read_text(encoding='utf-8')
metadata = json.loads((root / 'app/src/main/assets/lspatch_module_info.json').read_text(encoding='utf-8'))

assert 'AUTHORIZED_HOST_PACKAGE' not in hook
assert 'com.t2.AvailoHader' not in hook
assert 'WFHostPolicy.permits(lpparam.packageName)' in hook
assert 'com.android.systemui' in policy and 'com.wolfox.gps' in policy and 'android.' in policy
assert metadata['scope'] == []
assert metadata['id'] == 'com.wolfox.gps.variant5' and metadata['versionCode'] == 313
for api in ('attach', 'showControls', 'hideControls'):
    assert f'public static void {api}(Activity activity)' in bridge
assert 'public static void observeTouch(Activity activity, MotionEvent event)' in bridge
assert 'prefs.getBoolean(KEY_BUBBLE_VISIBLE, true)' in storage
assert 'activity.findViewById(android.R.id.content)' in floating
assert 'parent.addView(button, new FrameLayout.LayoutParams' in floating
assert 'if (LicenseManager.getInstance().isActive(activity))' in floating
for forbidden in ('SYSTEM_ALERT_WINDOW', 'WRITE_SECURE_SETTINGS', 'ACCESS_MOCK_LOCATION', 'RECEIVE_BOOT_COMPLETED'):
    assert forbidden not in manifest
assert '<service' not in manifest and '<receiver' not in manifest
print('PASS: no fixed application allowlist; selected embedded hosts retain core-process exclusions')
print('PASS: icon uses host Activity only; first-open visible and persistent hide; no overlay/service/boot expansion')
print('PASS: direct attach/show/hide/touch APIs and activation gate remain unchanged')
