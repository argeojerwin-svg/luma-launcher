# Windows development

Open PowerShell in the repository folder. Install JDK 17 and the Android SDK with platform 35 and build-tools 34.0.0 (the default used by Android Gradle Plugin 8.7). Android Studio can manage these tools, or you can use Google's command-line tools.

Set `JAVA_HOME` to the JDK folder and `ANDROID_HOME` to the SDK folder for your terminal session. Add the JDK `bin` folder and your existing Android platform-tools folder to that session's `Path`. Alternatively, put those settings in `.local/environment.ps1`; `tools/dev.ps1` loads this optional file automatically. The `.local` folder is ignored by Git. Do not put machine-specific paths in tracked files.

## Build and check

```powershell
.\tools\dev.ps1 -Task Check
```

This builds the debug APK and runs the existing unit tests and Android lint checks, matching the repository's GitHub checks. For a faster rebuild after the initial checks:

```powershell
.\tools\dev.ps1 -Task Build
```

The APK is `app/build/outputs/apk/debug/app-debug.apk`. The script does not install it or change the projector's Home app.

If PowerShell blocks a local script, use a one-process policy override from PowerShell:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tools\dev.ps1 -Task Check
```

## Connect the projector

Enable the projector's ADB/network debugging and connect it to the same network as this PC. Use the address and port shown by the projector. For example, if its address is `192.168.1.50` and its ADB port is `5555`:

```powershell
.\tools\dev.ps1 -Task Connect -DeviceAddress 192.168.1.50:5555
.\tools\dev.ps1 -Task Devices
```

Accept any debugging authorization prompt on the projector. A successful connection appears as `device` in the device list. `unauthorized` requires approval on the projector; `offline` is not ready for testing. Some devices need wireless pairing first, using `adb pair IP:PAIRING_PORT` and the on-screen code; the connection port can differ from the pairing port. See [Android's ADB guide](https://developer.android.com/tools/adb#wireless).

Read [installation and recovery](INSTALL.md) before installing a development APK over an existing Luma installation. Installing, uninstalling and changing the default Home app are separate steps from connecting ADB.
