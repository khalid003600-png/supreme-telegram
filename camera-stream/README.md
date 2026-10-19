# WolFox Camera Stream (Android 16, no root)
Independent Android app: choose an image, start local Wi-Fi MJPEG stream, replace the image without restarting, and view snapshots from another device. No virtual system camera is installed.
Run from the camera-stream directory: gradle :app:assembleDebug. Install app/build/outputs/apk/debug/app-debug.apk.
Sender: http://DEVICE_IP:8080/stream.mjpg (MJPEG) and /snapshot.jpg (JPEG). Both devices must be on the same trusted Wi-Fi. The server has no authentication. The receiver polls snapshots.
GitHub Actions builds a signed debug APK and an unsigned release APK. Do not claim a production-signed release.
