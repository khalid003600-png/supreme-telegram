# WolFox GPS integration — Android 16
Source integration includes Android WebView activation UI, OpenStreetMap iframe map, coordinate selection, persistent favorites with rename/delete/clear, floating overlay, and Android LocationManager test provider with updates when coordinates change.
Activation URL: https://gps.p3nd.fun/api/v2/android/activate.php (server response contract unverified).
Mock location requires developer-options mock location app selection.
Xposed/LSPatch entry remains a logging-only stub; no external app hooks.
Tests required: build app APK on Linux CI; install on Android 16; test activation, permission, map network, favorites persistence, overlay, GPS mock toggles.
Android 17 is not device-tested. No APK build is confirmed.
