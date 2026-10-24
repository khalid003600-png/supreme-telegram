# WolFox GPS-only clean branch

This branch is isolated from the prior build branch. It removes Bluetooth permissions from the Android manifest and labels the module as GPS-only. Repository test scripts are retained as verification tooling; they are not copied into the APK by the local build script.

## Build environment
- GitHub Actions runner: Ubuntu 24.04
- Java: Temurin 17
- Android SDK platform 34
- Android Build Tools 34.0.0
- Build mode: unsigned APK; no test signing key or release credentials are included

## Run
1. Open the repository Actions tab.
2. Choose **WolFox GPS-only clean build and verification**.
3. Run it on branch `gps-only-clean-no-test-data-20261024`, or push a commit to that branch.
4. Verify the workflow passes and inspect the artifact before installation.

## Output and limitations
The current workflow builds `build/local/apk/WolFox_GPS_Module_v3.1.1_UNSIGNED_ALIGNED.apk` and uploads it with a SHA-256 file. It is unsigned and is not a production-signed release.

Source-level checks and APK packaging checks do not prove that hooks work inside a target app on a physical device. The manifest no longer requests Bluetooth permissions, but a full source-level removal of all Bluetooth implementation/UI code and device-level runtime verification are still separate tasks. Do not describe this branch as a fully runtime-validated release until those checks are completed.
