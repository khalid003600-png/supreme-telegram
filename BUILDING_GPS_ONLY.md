# WolFox GPS-only — build requirements

This branch contains a dedicated GitHub Actions workflow for the GPS-only Android edition. The workflow pins third-party Actions to full commit SHAs to comply with this repository's action policy.

## Toolchain

- Ubuntu 24.04 GitHub Actions runner
- Temurin JDK 17
- Gradle 8.9 downloaded directly, so the build does not depend on the wrapper's distribution bootstrap
- Android SDK platform 34 and platform-tools
- Android Build Tools 34.0.0 (`apksigner`, `zipalign`)
- Gradle wrapper JAR and properties retained in source
- `app/libs/xposed-api-82.jar`, used as a compile-only dependency

## Run the build

1. Open the repository Actions tab.
2. Select **WolFox GPS-only build and verification**.
3. Run it on branch `fix/gps-only-build-prerequisites-20261024`.
4. Confirm the **Verify APK outputs** step passes.
5. Download the artifact named `WolFox-GPS-only-APK`.

## Expected outputs

- `app-debug.apk`: debug APK, verified by `apksigner`.
- `app-release-unsigned.apk`: unsigned release APK, checked for zip alignment.

A production-signed APK requires a real keystore and signing credentials. Do not commit those credentials to the repository. The unsigned release APK is not a production-signed release.

## Evidence standard

Do not claim the build succeeded unless the verification step passes and the Actions artifact contains non-empty APK files. Unit test/lint failures are reported after artifact upload to preserve outputs for diagnosis.
