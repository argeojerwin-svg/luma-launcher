# Experimental projector-only Menu helper

This prototype moves the P6 Menu bridge onto the projector. It is not a general root-free replacement for the bridge. It requires the projector's existing local ADB server on port 5555 to accept loopback connections and support root debugging. It refuses authentication challenges and does not change debugging authentication, unlock the bootloader, or modify firmware.

The option is off by default. In Luma, open Settings → Control Centre in apps → Projector-only Menu. A foreground service bundles and starts the existing P6 IR listener, checks it every 20 seconds, and resumes after boot or an app update. Short Menu opens Control Centre; holding Menu opens App Switcher. The contextual YouTube keyboard remains available through Control Centre.

The scripts run from `/data/local/luma-menu`, with root-only directory permissions. Disable the option to stop its listener. Keep the Windows bridge stopped while testing this option, since two listeners would both react to the same remote button. The existing Windows bridge remains the fallback.

## Validation at publication

The release build, all 38 JVM tests (including five local ADB protocol tests), and release lint pass. The protocol tests cover stream acknowledgements, explicit setup completion when a child keeps the connection open, refusal of authentication challenges, malformed checksums, and oversized packets.

On an AUMI P6 running Android 11, a full reboot restored Luma's foreground service and IR listener automatically, with the Windows watcher stopped and Accessibility disabled. The app requested root through local debugging itself. Kernel-level short and held Menu events then opened Control Centre and App Switcher, and Control Centre opened the contextual TizenTube QWERTY/QR keyboard. These checks do not establish support on other firmware; physical-remote confirmation and longer-term use remain useful.

The settings toggle stopped and restarted the service and listener cleanly. After deliberately stopping only the listener, Luma recreated it during its next recovery check without the Windows watcher.

This remains a draft based on PR #1; merge that tested work first. The tested P6 already exposes root-capable, unauthenticated debugging. The helper does not enable that access on other devices. Network debugging must remain available after boot. A projector without this access still needs an external bridge or another privileged integration.
