# Rafed iOS
WKWebView wrapper for https://www.ibtsm.org/rafed/ with persistent website sessions, back, home, reload, and Safari fallback.

The workflow builds on macOS. To produce a signed IPA, configure repository Actions secrets:
- IOS_P12_BASE64: base64 encoded P12 from your uploaded archive
- IOS_P12_PASSWORD: P12 password
- IOS_PROFILE_BASE64: base64 encoded mobileprovision from the same archive

Never commit these files or their encoded contents to this public repository.
The supplied profile permits app.lilac7003.mint3477 on its registered device only. Its expiry and certificate validity are checked by Apple at installation. This is not App Store/TestFlight distribution.

Without signing secrets CI builds and uploads an unsigned .app ZIP only, not an installable IPA.
Run the workflow with a push to this branch affecting rafed-ios or the workflow itself.
Actual website login, Cloudflare compatibility, downloads, camera, and location behavior require device validation. Native camera/location integration is not included.
