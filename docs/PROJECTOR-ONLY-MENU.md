# Experimental projector-only Menu helper

This prototype moves the P6 Menu bridge onto the projector. It is not a general root-free replacement for the bridge. It requires the projector's existing local ADB server on port 5555 to accept loopback connections and support root debugging. It refuses authentication challenges and does not change debugging authentication, unlock the bootloader, or modify firmware.

The option is off by default. In Luma, open Settings → Control Centre in apps → Projector-only Menu. A foreground service bundles and starts the existing P6 IR listener, checks it every 20 seconds, and resumes after boot or an app update. Short Menu opens Control Centre; holding Menu opens App Switcher. The contextual YouTube keyboard remains available through Control Centre.

The scripts run from `/data/local/luma-menu`, with root-only directory permissions. Disable the option to stop its listener. Keep the Windows bridge stopped while testing this option, since two listeners would both react to the same remote button. The existing Windows bridge remains the fallback.

## Validation at publication

The release build, JVM tests (including four local ADB protocol tests), and release lint pass. The protocol tests cover stream acknowledgements, refusal of authentication challenges, malformed checksums, and oversized packets.

Actual projector startup, remote-button behaviour, and recovery after a full reboot are still pending. This is a draft based on PR #1; merge that tested work first. Do not describe this prototype as supported until those device checks pass. Other projector firmware may not provide the required debugging access.
