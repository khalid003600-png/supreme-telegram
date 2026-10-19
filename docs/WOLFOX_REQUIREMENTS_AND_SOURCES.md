# WolFox build and source requirements

## Verified Android build dependencies (full edition, 2026-10-19)
- JDK 17 (Temurin in GitHub Actions)
- Android Gradle Plugin 8.2.2; Gradle Wrapper 8.9
- Android SDK Platform 34; Build Tools 34.0.0; platform-tools (adb)
- Build executables: aapt/aapt2, d8, zipalign, apksigner
- Python 3 for source verification scripts
- Local compile-only Xposed API 82: app/libs/xposed-api-82.jar
- JUnit 4.13.2 and Robolectric 4.14.1 (test dependencies)
- Repositories: Google Maven, Maven Central, Gradle Plugin Portal; legacy Xposed Maven scoped to de.robv.android.xposed

## External upstream references (NOT vendored or automatically merged)
- LSPosed: https://github.com/LSPosed/LSPosed (module runtime/API reference)
- LSPatch: https://github.com/LSPosed/LSPatch (archived loader reference; review license and security before reuse)
- Android camera samples: https://github.com/android/camera-samples
- Android BLE samples: https://github.com/android/connectivity-samples
- Location Joystick: https://github.com/shortcuts/locationjoystick
- GPS Locator: https://github.com/0xfnzero/gps-locator

External source is not automatically safe or compatible. Review its license, exact API, maintenance status, and tests before importing code. Do not copy APK/DEX internals as source code.

## Independent Android edition branches
- edition/android-gps-20261019
- edition/android-gps-bluetooth-20261019
- edition/android-gps-upload-identifier-20261019
- edition/android-full-20261019

## Verification and deliverables
1. Run tests/verify_four_editions.py, tests/verify_design_palette.py, tests/verify_safe_device_tools.py.
2. Run ./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleRelease.
3. Verify APK output and its SHA-256, then upload build artifacts.
4. An unsigned APK is not a signed production installable release. Configure signing using authorized GitHub Secrets, never commit a private keystore.
5. Test GPS, BLE permissions/advertising and camera on physical Android 16 devices. CI success does not prove device behavior.
6. Keep app-generated UUID, Bluetooth service UUID and device identifiers conceptually separate.
