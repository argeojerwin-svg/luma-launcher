# Known limitations of v1.0.0-rc1

- Runtime validation is currently limited to one AUMI P6 running Android 11. Android 6+ is the declared minimum, but other Android TV devices need testing.
- The P6 firmware does not reliably keep Luma's Accessibility service enabled. The physical Menu shortcut over third-party apps may need an optional external bridge after reboot. Home, app launching, ordinary remote navigation and in-launcher Control Centre do not depend on that bridge.
- Watch Next availability, provider title artwork, deep links and exact playback resumption depend on the streaming app. Luma does not fabricate history or progress.
- The RC test APK is debug-signed. A production-signed release requires a secure signing key and a migration path for existing development installs.
- Performance and extended daily-use testing on additional hardware are still pending.

