# WolFox source variant 5

Complete independent Android module project based on the supplied WolFox source.
Application ID: `com.wolfox.gps.variant5`. Namespace: `com.wolfox.gps`.
See `README_AR.md` and `VALIDATION.json` for scope, provenance and validation.

## Build

Use JDK 17, Android SDK platform 34, Build Tools 34.0.0 and network access for Gradle dependencies.

```sh
bash gradlew :app:assembleRelease
```

The Xposed API 82 jar in `app/libs/` is compile-only and is excluded from the APK.
The release output is unsigned until signed with your own release key.

The alternative tested Linux path is `bash build-local.sh` with
`ANDROID_PLATFORM_JAR`, `ANDROID_BUILD_TOOLS`, `ECJ_JAR` (if javac is absent),
and `WOLFOX_UNSIGNED_ONLY=1`. It uses the official Android build tools directly.

Runtime needs the original supported framework and valid license services.
No device/runtime test or bit-for-bit equivalence with K7 APKs is claimed.
