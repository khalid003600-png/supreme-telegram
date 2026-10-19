from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
FEATURES = (ROOT / "app/src/main/java/com/wolfox/gps/compat/FeatureIntegration.java").read_text(encoding="utf-8")
PANEL = (ROOT / "app/src/main/java/com/wolfox/gps/ui/WolFoxPanel.java").read_text(encoding="utf-8")
MANIFEST = (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
errors = []

for token, description in (
    ("MediaStore.ACTION_IMAGE_CAPTURE", "system camera launch"),
    ("Intent.ACTION_GET_CONTENT", "system image picker"),
    ("refreshCameraPreview", "local image preview"),
    ("BLE_SCAN_DURATION_MS = 10_000L", "bounded BLE scan"),
    ("scanner.startScan(callback)", "BLE discovery start"),
    ("currentScanner.stopScan(currentCallback)", "BLE discovery stop"),
    ("hostDeclares(Activity activity, String permission)", "host manifest permission check"),
    ("PackageManager.GET_PERMISSIONS", "declared host permission inspection"),
    ("FeatureIntegration.showBluetooth(activity)", "Bluetooth panel button"),
    ("FeatureIntegration.showCamera(activity)", "camera panel button"),
):
    if token not in FEATURES + PANEL:
        errors.append(f"missing {description}")

if "resolveActivity(" in FEATURES:
    errors.append("system intents must not depend on package-visibility resolution")
if 'android:name="android.permission.BLUETOOTH_SCAN" android:usesPermissionFlags="neverForLocation"' not in MANIFEST:
    errors.append("BLE scan must be declared neverForLocation")
if 'android:name="android.permission.BLUETOOTH_CONNECT"' not in MANIFEST:
    errors.append("Bluetooth connect permission declaration missing")
if 'android:name="android.permission.CAMERA"' in MANIFEST:
    errors.append("camera permission must not be requested; use the explicit system camera intent")
if "المعاينة محلية داخل WolFox فقط" not in FEATURES:
    errors.append("camera preview must be explicitly local-only")
if "CameraManager" in FEATURES or "setRepeatingRequest" in FEATURES:
    errors.append("direct camera pipeline replacement is out of scope")
if ".append(internalKey)" in FEATURES or "getAddress() +" in FEATURES:
    errors.append("BLE device addresses must not be displayed")

if errors:
    print("FAIL: " + "; ".join(errors))
    sys.exit(1)
print("PASS: local-only camera selection/preview and permission-gated, bounded BLE discovery")
