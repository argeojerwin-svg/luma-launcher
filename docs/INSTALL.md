# Installation and recovery

**Updated P6 build with QWERTY/QR typing and projector-only Menu:** download [Luma-P6-1.0.0.apk](https://github.com/argeojerwin-svg/luma-launcher/releases/download/v1.0.0-p6.1/Luma-P6-1.0.0.apk) and follow the [quick installation guide](P6-QUICK-INSTALL.md). The older general release below does not contain these latest additions.

Download the [signed v1.0.0 APK](https://github.com/smzaki101-lab/luma-launcher/releases/download/v1.0.0/Luma-v1.0.0-signed.apk) and install it using a compatible Android TV installer or ADB:

```sh
adb install Luma-v1.0.0-signed.apk
```

Luma's package ID is `dev.luma.launcher`. Open Luma from your current app list first. You do not need to make it the default Home app to try it. If you later choose Luma as Home, use **Launcher settings → Home app selection** to return to Android's Home App chooser.

**Upgrading from the earlier `v1.0.0-rc1` or another debug-signed build:** those builds use a different signing key, so Android cannot install v1.0.0 over them. Uninstall the debug build before installing the signed release. Uninstalling clears Luma's local preferences and caches, so save anything important first.

To uninstall only Luma:

```sh
adb uninstall dev.luma.launcher
```

If ADB has more than one connected device, add `-s DEVICE_ID` immediately after `adb`. Uninstalling Luma does not remove or disable your device's existing system launcher. Keep a copy of a known-working APK before changing versions.
