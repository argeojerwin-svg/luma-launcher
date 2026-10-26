# Optional P6 Menu bridge on Windows

This is the Windows equivalent of the [Mac-assisted P6 bridge](P6-MENU-BRIDGE.md). It reuses the same projector-side listener: a short press of the original IR Menu button toggles Luma Control Centre over the current app; holding for at least 450 ms and releasing opens App Switcher. Luma handles Menu itself while its Home screen is active.

The bridge requires root-capable wireless ADB and the P6 `sunxi-ir-uinput` input device. It is not a universal driver for other Android TVs or Bluetooth/USB remotes. It observes the original Menu signal; it does not consume the button from the foreground app, so an app may also react to its own Menu event. It does not change firmware or key maps, and does not require changing the Home app or enabling Accessibility.

## Start or install

With the PC and projector on the same trusted network, open PowerShell in the repository folder. Substitute your projector address and ADB path:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tools\p6-menu-bridge\p6-menu-bridge.ps1 -Action Start -Serial 192.168.1.50:5555 -AdbPath C:\platform-tools\adb.exe
```

`Start` checks the connection, enables the projector's existing root ADB mode, normalizes the shared shell scripts to Android-compatible line endings, and starts the listener if it is missing. It does not rebuild or reinstall Luma.

For automatic recovery, run the same command with `-Action Install`. This also installs a hidden Windows Task Scheduler task for the current user, starts it immediately, and starts it again at Windows login. It checks every 20 seconds, reconnects stale ADB transports and restarts the listener when needed. The task runs at ordinary user privilege and does not store a Windows password. Its ADB commands have timeouts, and duplicate watcher instances are prevented.

Installation saves the address and ADB location in the Git-ignored `.local/menu-bridge/config.json`. Subsequent commands can omit those two parameters:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tools\p6-menu-bridge\p6-menu-bridge.ps1 -Action Status
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tools\p6-menu-bridge\p6-menu-bridge.ps1 -Action Stop
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tools\p6-menu-bridge\p6-menu-bridge.ps1 -Action Start
```

`Stop` stops the projector listener and Windows watcher. It remains stopped after login until `Start` or `Install` is run. `Uninstall` stops the bridge and removes the login task, leaving Luma installed. Stop/uninstall the bridge before removing Luma.

For a foreground watcher without installing login recovery, use `-Action Watch` with the address and ADB path and stop it with Ctrl-C. It also exits if another instance is already watching the same address. `Stop` signals foreground watchers to exit at their next check.

## What stays running

The Menu listener runs on the projector and usually continues if the PC goes to sleep. A projector reboot clears that process. Recovery then requires this PC to be awake, logged in and connected to the same network. An IP change requires running `Install` again with the new address. Sleep/wake and physical remote behavior should be checked on each device.

The Windows task name begins with `Luma P6 Menu Bridge` and includes a suffix derived from the address. The watcher log is `.local/menu-bridge/watch.log`; the projector log is `/data/local/tmp/luma-menu-bridge.log`. Only recovery state changes are written to the Windows log. Do not move the repository or remove its scripts while the login task is installed; uninstall and reinstall the task if moving it.
