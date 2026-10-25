# Optional P6 global Menu bridge

Luma works without this bridge. On the tested AUMI P6, the projector firmware does not reliably keep Luma's Accessibility service active. While the optional bridge is running, a short press of the original physical ☰ Menu button toggles Luma Control Centre over another app; holding it for about half a second opens the existing App Switcher. Menu on Luma Home remains handled by Luma itself.

This is a **P6-specific, Mac-assisted test feature**, not a built-in universal remote feature for all Android TVs. It uses root-capable wireless ADB to read the P6's `sunxi-ir-uinput` Menu signal. It does not alter firmware, key maps, the default Home app, or the original AUMI launcher. It cannot start itself after a projector reboot when the Mac is off.

## Start manually

On a Mac with Android platform-tools installed, connect the Mac and P6 to the same trusted network. Enable wireless ADB on the P6 and find its current IP address. From the repository root, run:

```sh
export P6_SERIAL="YOUR_P6_IP:5555"
export ADB_BIN="/full/path/to/adb"  # omit this line if adb is on PATH
adb connect "$P6_SERIAL"
sh tools/p6-menu-bridge/p6-menu-bridge.sh start
sh tools/p6-menu-bridge/p6-menu-bridge.sh status
```

The script calls `adb root`. If the device refuses that command, this bridge cannot read its remote input and should not be used. Do not expose wireless ADB to the internet or an untrusted network.

To stop it:

```sh
sh tools/p6-menu-bridge/p6-menu-bridge.sh stop
```

## Recover after reboot or wake

The bridge runs as a temporary process on the projector, so a reboot clears it. With the Mac awake and connected, run `start` again. For a one-time check that starts it only if missing:

```sh
sh tools/p6-menu-bridge/p6-menu-watch.sh --once
```

Running `sh tools/p6-menu-bridge/p6-menu-watch.sh` without `--once` keeps a **foreground** watcher running on the Mac. It checks every 20 seconds and reconnects a stale wireless ADB transport when needed; stop it with Ctrl-C. To make that watcher run automatically at Mac login, configure a user LaunchAgent for this script with your own absolute repository path, `ADB_BIN`, and `P6_SERIAL`. The project does not install a background watcher automatically. The original P6 development setup used this Mac-side recovery successfully after a real projector reboot, but sleep/wake behavior may still vary.

Stop the watcher before uninstalling Luma, then stop the bridge. The launcher package ID is `dev.luma.launcher`; `adb uninstall dev.luma.launcher` removes Luma only. The AUMI launcher remains intact.
