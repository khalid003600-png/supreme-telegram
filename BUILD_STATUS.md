# WolFox GPS build status
- GitHub Actions is blocked by an account billing lock; source code changes cannot fix billing.
- GitLab CI configuration added for an independent Android build, but no GitLab repository is connected and no GitLab pipeline has run.
- The current Android app is a **minimal build verification scaffold**, not a complete LSPatch module.
- APK build success and Android 16 behavior have not yet been verified.
- To run GitLab CI, import the branch into a GitLab project with available runners.
- To build locally on Linux, install JDK 17, Android SDK platform 35, build-tools 35.0.0 and Gradle 8.9; run bash scripts/build-linux.sh.
- Do not publish a release APK without testing the signing key and the actual module implementation.
