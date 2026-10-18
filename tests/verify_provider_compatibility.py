from pathlib import Path

root = Path(__file__).resolve().parents[1]
hook = (root / "app/src/main/java/com/wolfox/gps/hook/WolFoxHook.java").read_text()
compat = (root / "app/src/main/java/com/wolfox/gps/hook/ProviderCompatibilityHooks.java").read_text()
mock = (root / "app/src/main/java/com/wolfox/gps/manager/GPSMockManager.java").read_text()

required = [
    "ProviderCompatibilityHooks.install",
    "com.google.android.gms.location.LocationResult",
    "com.huawei.hms.location.LocationResult",
    "com.huawei.hms.location.FusedLocationProviderClient",
    "com.mapbox.android.core.location.LocationEngineResult",
]

combined = hook + compat
missing = [value for value in required if value not in combined]
if missing:
    raise SystemExit("FAIL: missing compatibility markers: " + ", ".join(missing))

if "activeModel()" not in compat or "isMocking()" not in compat:
    raise SystemExit("FAIL: compatibility hooks are not gated by active mocking")

if "setMock(true)" not in mock or "setIsFromMockProvider" not in mock:
    raise SystemExit("FAIL: generated Location objects are not explicitly marked as mock")

for forbidden in ("hookFrameworkAvailability", "isProviderEnabled", "isLocationEnabled",
                  "AMapLocation", "BDLocation"):
    if forbidden in compat:
        raise SystemExit("FAIL: provider status or unmarked vendor location hook found: " + forbidden)

for forbidden in ("hookWebView", "navigator.geolocation", "isLocationAvailable"):
    if forbidden in hook:
        raise SystemExit("FAIL: non-Location response or availability override found: " + forbidden)

print("PASS: supported location response hooks are active only for marked mock Locations")
