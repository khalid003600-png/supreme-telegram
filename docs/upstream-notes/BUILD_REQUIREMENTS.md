# WolFox GPS v3.1.1 — Build Requirements

Canonical build baseline:
- Linux / Ubuntu 24.04 CI
- Java 17
- Android SDK Platform 34
- Android Build Tools 34.0.0
- minSdk 21 / targetSdk 34
- package: com.wolfox.gps
- versionName 3.1.1 / versionCode 313
- output: signed module APK + classes.dex + SHA256SUMS.txt

Source architecture:
Xposed/LSPatch -> WolFoxHook -> DirectBootstrap/ActivityHook -> FloatingManager -> WolFoxPanel.

Do not add to the build tree unless explicitly required:
- server/backend deployment files
- root helpers or shell privilege escalation
- SYSTEM_ALERT_WINDOW based FloatingService
- prebuilt/old APK duplicates
- private signing keys or passwords

Any future source addition must pass the three verification scripts and a full compile/sign/verify run.

Canonical command:
```bash
WOLFOX_ALLOW_TEST_KEYSTORE=1 ./build-local.sh
```

Gradle files are retained for IDE/source metadata; the reproducible Linux artifact is produced by `build-local.sh`.
