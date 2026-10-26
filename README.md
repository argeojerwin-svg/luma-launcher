# Luma Launcher

Luma is an open-source, remote-first Android TV launcher with a cinematic home screen, glass-inspired controls, an App Switcher, and Continue Watching where real provider data is available. It was developed and tested primarily on the AUMI P6 projector (Android 11, 1920 × 1080, 240 dpi, 32-bit ARM, approximately 1.73 GiB RAM).

**Current status: v1.0.0 stable release for the tested P6.** Other Android TV models remain unverified. See [known limitations](docs/KNOWN-LIMITATIONS.md) before installing.

## Screenshots

| Home | Control Centre |
| --- | --- |
| ![Luma home on P6](screenshots/home.png) | ![Luma Control Centre on P6](screenshots/control-centre.png) |

| App Switcher | Continue Watching |
| --- | --- |
| ![Luma App Switcher](screenshots/app-switcher.png) | ![Luma Continue Watching with Stremio](screenshots/continue-watching.png) |

Screenshots were captured on the P6 during development. The App Switcher image is from an earlier beta with the same feature; appearance may vary with installed apps, wallpaper, weather and connected services. Third-party logos and title artwork belong to their respective owners.

## What it does

- D-pad navigation, editable app dock, All Apps library, custom tile rendering, and a recent-app App Switcher.
- Home Control Centre and an optional over-app Control Centre. On compatible Android TVs, Luma's built-in Accessibility shortcut can handle the remote Menu button without a computer; see [setup and compatibility](docs/GLOBAL-MENU.md). The tested P6 needs an [optional Mac-assisted bridge](docs/P6-MENU-BRIDGE.md) because its firmware does not keep that service active.
- Clock, Wi-Fi status, live weather with an offline cache, wallpapers and screensaver.
- Continue Watching from supported Android TV Watch Next entries, Stremio data where available, optional Trakt, and user-managed entries. Luma only displays real progress when a source supplies it. Provider deep links and resume behavior vary.
- Recovery options to return to Android's Home App selection. The original system launcher is never removed by Luma.

## Install and build

See [installation and recovery](docs/INSTALL.md). To build from source, install JDK 17 and Android SDK 35, then run `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`. The build uses Gradle 8.9. Android Studio can also open this directory.

The application ID is `dev.luma.launcher`. No Trakt secret or account token is included in this repository. See [privacy](docs/PRIVACY.md) and [assets](docs/ASSETS.md).

## Project status

The source is published under [Apache License 2.0](LICENSE). See [production release preparation](docs/RELEASE-PREPARATION.md) for the signing, migration, policy, and device-validation work remaining before a stable APK is released. Contributions and device reports are welcome.

Luma is an independent project. It is not affiliated with or endorsed by Apple, Google, Stremio or any streaming provider.

