# Install the updated Luma P6 build

**1.0.4 preview:** the release build, release lint and 47 JVM tests passed. The local image picker, dynamic screensaver updates and universal Keyboard's QR/phone Go path in Stremio were checked on the P6. Individual custom app search interfaces remain unverified. The previous 1.0.3 remains available on the release page for recovery.

[Download Luma-P6-1.0.4.apk](https://github.com/argeojerwin-svg/luma-launcher/releases/download/v1.0.4-p6.1/Luma-P6-1.0.4.apk)

[Release page](https://github.com/argeojerwin-svg/luma-launcher/releases/tag/v1.0.4-p6.1) · [All source changes](https://github.com/smzaki101-lab/luma-launcher/pull/2)

## Install

1. Download **Luma-P6-1.0.4.apk** from the link above, or from **Assets** on the release page. The source-code ZIP is for development.
2. Open the APK on the projector using its file manager or Android TV installer. A USB drive can be used to copy the downloaded APK to the projector. Allow installation from that installer if Android asks, then choose **Install** or **Update**.
3. Open **Luma** from your existing app list. You can try it before choosing it as the default Home app.

This single APK includes the launcher, Luma keyboard, QR phone typing, phone Go, YouTube artwork and search shortcut, App Switcher fixes, and the optional P6 projector-only Menu helper. No separate Luma keyboard or bridge APK is needed.

The home screen contains only your pinned apps. Press **Down** from a pinned tile to slide upward into the full app grid, which includes the **Apps** shortcut. Press **Up** from the grid's top row, or **Back**, to return to your previous home selection. The redundant **All Apps** tile has been removed. **OK** on the grid's Apps shortcut opens app discovery and management; Back returns to the grid. Version 1.0.2 adds this navigation.

## Use your wallpaper as the screensaver

Choose your image in **Wallpaper → Choose local image** using Luma's built-in picker. Saved Luma photos appear immediately; allow local-photo access to browse images in the projector's media library. The selected image is copied into Luma for persistent use. See [local images and motion](LOCAL-IMAGES-AND-MOTION.md).

In **Launcher settings → Screensaver**, choose **Motion · Dynamic** for continuous slow pan and zoom; **Still** turns motion off. This is camera movement over your photo, rather than a generated aerial video.

In **Launcher settings → Screensaver**, choose **Use wallpaper for projector idle screen**. On Android firmware with a screensaver chooser, select **Luma wallpaper** there. On the tested P6, the button selects it directly using the existing local root-capable debugging; enable Projector-only Menu first if that access is not running. The wallpaper screensaver itself uses Android's normal screensaver service.

Idle mode now shows the active Luma profile's selected wallpaper, without app tiles or a separate video. The factory screensaver is replaced as the selected provider; its package remains installed. Any remote key wakes the screensaver. **Start now** previews the wallpaper while Luma is open. Luma's idle timeout applies while the launcher is foreground; Android's system idle timing remains controlled by the projector.

Version 1.0.3 adds this system screensaver. Its selection button, actual Android dream startup and remote wake were verified on the P6.

## Enable the keyboard once

In Android's keyboard settings, enable **Luma Keyboard** and select it as the current keyboard. The exact Android settings location varies by firmware. Android requires the user to approve this selection; installing the APK alone cannot select a new keyboard.

Open a normal app search field, such as Stremio Search. The compact QWERTY keyboard appears at the bottom. Scan the QR code with a phone on the same Wi-Fi, type, then tap **Go** on the phone.

YouTube and TizenTube draw their own search keyboards. While either app is open, use Luma's global **Menu → Keyboard** shortcut for QWERTY/QR search. Their own alphabetic search screen remains part of those apps. See [phone keyboard details](PHONE-KEYBOARD.md).

**Menu → Keyboard** is also available for other apps: open their search field before choosing it. Standard Android fields use Luma's input method; on compatible P6 firmware, custom fields can receive English/ASCII input using existing local debugging. The fallback appends at the cursor, so clear an old query first. Custom app compatibility still needs hardware verification.

## Enable Menu over other apps on a compatible P6

1. In **Launcher settings → Control Centre inside apps**, allow **Display over other apps**.
2. Stop any existing Mac/Windows Menu bridge before enabling the built-in helper.
3. Turn **Projector-only Menu** on. The projector must already have network debugging enabled with the root-capable local debugging access supplied by compatible P6 firmware.
4. Open another app. Press Menu briefly for Control Centre; hold Menu for App Switcher. After a reboot, allow Luma and the network to finish starting.

The helper runs inside the projector and resumes after reboot, so the PC can be off. It still uses privileged debugging; it is not a general root-free solution for every projector. It does not unlock or modify the firmware. If the notification says it is waiting for local debugging, review the [requirements and fallback](PROJECTOR-ONLY-MENU.md). Normal launcher and standard Android keyboard features do not require this helper.

## If installation fails

Use the actual `.apk` file and confirm installation from that installer is permitted. If Android says an existing package conflicts, an older debug build may have a different signing key. Keep the existing installation and its data until the signing-key issue is understood; uninstalling would erase Luma preferences. See [installation and recovery](INSTALL.md).

## What was checked

- APK signature verified against the established Luma release certificate.
- The public download is checked byte-for-byte against the locally signed APK.
- For 1.0.4, release build and release lint passed; all 47 JVM tests passed. The update was installed on the P6, a shared photo was imported and selected, the dynamic screensaver updated over time, and QR/phone Go through the universal overlay opened Stremio search results.
- Full reboot, local listener recovery, on/off setting, short Menu, held Menu and the TizenTube QWERTY/QR shortcut were checked on the AUMI P6 running Android 11.
- The owner confirmed short Menu, held Menu and YouTube keyboard work on the physical remote.

This is a P6 preview release from the contributor fork while the changes are reviewed upstream. Other projector models and firmware have not been validated. The APK identifies itself as Luma 1.0.4, version code 48; the release tag `v1.0.4-p6.1` distinguishes this feature build.
