# v1.0.0-rc1 — public test candidate

Luma's first public release candidate brings the P6-tested launcher source together with the cinematic Home, App Switcher, Control Centre, weather, and genuine Continue Watching sources. This is a test build, not the stable v1.0 release.

The APK uses package ID `dev.luma.launcher` and versionCode 43. It is debug-signed to preserve in-place upgrades for existing development testers. SHA-256: `e41946d119829a1de092a68208868aa80cbaf0114118b0c4238dff75e915cc37`.

Validated on the AUMI P6 (Android 11, 1920 × 1080, 240 dpi, 32-bit ARM). The code declares Android 6.0 as its minimum version, but other devices have not been validated. The P6's physical Menu shortcut over third-party apps can require the optional external bridge after reboot; normal Home use does not.

Install and recovery: [docs/INSTALL.md](docs/INSTALL.md). Known limits: [docs/KNOWN-LIMITATIONS.md](docs/KNOWN-LIMITATIONS.md).

