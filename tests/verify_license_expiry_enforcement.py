from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
manager = (ROOT / "app/src/main/java/com/wolfox/gps/manager/LicenseManager.java").read_text()
mock_manager = (ROOT / "app/src/main/java/com/wolfox/gps/manager/GPSMockManager.java").read_text()
storage = (ROOT / "app/src/main/java/com/wolfox/gps/util/WFStorage.java").read_text()

assert 'boolean wasMocking = storage.isMocking();' in manager
assert 'boolean licenseActive = storage.isLicenseActive();' in manager
assert 'if (!licenseActive && wasMocking && !licenseCode.isEmpty())' in manager
assert '"Active".equalsIgnoreCase(previousStatus)' in manager
assert '"Expired".equalsIgnoreCase(storage.getLicenseStatus())' in manager
assert 'verify(activity, licenseCode, null);' in manager
assert 'if (mocking && !LicenseManager.getInstance().isActive(appContext))' in mock_manager
assert 'prefs.edit().putString(KEY_LICENSE_STATUS, "Expired").apply();' in storage

print("PASS: local expiration stops mocking and syncs the expired code once")
