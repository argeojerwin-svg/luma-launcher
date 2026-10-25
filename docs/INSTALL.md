# Installation and recovery

Luma's package ID is `dev.luma.launcher`. The v1.0.0-rc1 test APK is debug-signed so it can update earlier Luma development builds with the same signature. It is not a production-signed stable APK.

Download the APK from the repository's release page, then install it with a compatible Android TV installer or ADB:

```sh
adb install -r Luma-v1.0.0-rc1-P6-test.apk
```

Launch Luma from your existing app list first. You do not need to select it as the default Home app to try it. If you later choose Luma as Home, use Luma's **Launcher settings → Home app selection** to return to Android's Home App chooser.

To uninstall only Luma:

```sh
adb uninstall dev.luma.launcher
```

If ADB has more than one connected device, add `-s DEVICE_ID` immediately after `adb`. Uninstalling Luma removes its local preferences and cached data. It does not uninstall or disable the original AUMI TV launcher. Keep a copy of your current working APK before replacing it; a future production-signed APK cannot be installed over a debug-signed copy without a migration step.

