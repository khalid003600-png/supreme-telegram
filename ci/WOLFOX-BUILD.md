# Five independent WolFox builds

`wolfox-five.yml` restores the five source projects delivered in this session,
verifies each source object's SHA-256, and builds each variant separately using
JDK 17 and official Android SDK/Build Tools 34. It uploads one APK artifact per
variant, its hash, package metadata, and build log.

The source object archive deduplicates identical files across the five projects.
Run `python3 ci/unpack-wolfox.py 2` to inspect variant 2 in `work/WolFox-Source-2`.
This archive contains source, resources, Gradle wrapper, and compile-only Xposed
API 82. It does not contain the original K7 APKs or DEX binaries.

Optional release signing requires all four repository secrets:
`WOLFOX_KEYSTORE_BASE64`, `WOLFOX_KEY_ALIAS`, `WOLFOX_KEYSTORE_PASS`, and
`WOLFOX_KEY_PASS`. With none configured, artifacts are explicitly unsigned.
Incomplete configuration fails. No debug or temporary signing key is generated.

Package IDs are `com.wolfox.gps.variant1` through `com.wolfox.gps.variant5`.
Variants 1/4 contain GPS, variant 2 adds the recovered camera component, and
variants 3/5 add the recovered BLE component. Build verification does not verify
device/framework behavior, license-service availability, or equivalence to the
original K7 runtime. Enable only one module in any given host application.
