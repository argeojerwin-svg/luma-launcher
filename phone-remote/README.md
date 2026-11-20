# Luma Remote for Android phones

An installable Android shell for Luma's existing local phone remote. Open it from
the phone's app list rather than Chrome or Samsung Internet. Android 8 or later.

Install the signed preview APK on your phone, open Luma Remote and paste the
complete private URL shown by the projector's Phone remote QR code. Include
`http://`, port `19767`, and the complete fragment after `#`. The app remembers
this link in its private preferences; Android backup is disabled. No projector
address or real token is embedded in the APK or source.

The projector must have Phone remote enabled and both devices must be on the same
trusted Wi-Fi. This app uses Android WebView to display the projector's existing
controls; it needs no browser selection, cloud account or PC. It cannot bypass
Wi-Fi isolation, phone VPN restrictions or a stopped projector service. It is
not an offline remote. If the projector IP changes, use **Projector** to update
the saved URL. **Reconnect** reloads the controls. **Forget projector** clears
the saved link. It does not disable Phone remote on the projector.

Build from the repository root using JDK 17 and Android SDK 35:

```sh
./gradlew -p phone-remote assembleRelease testDebugUnitTest lintRelease
```

The four `LUMA_RELEASE_*` environment variables sign release builds, as for the
launcher. Without them the release APK is unsigned. Package: `dev.luma.remote`,
separate from the TV launcher. The app allows plain HTTP for the local remote;
pairing accepts only private IPv4 endpoints at the expected port with a complete
48-character token. Page navigation stays on the paired endpoint. There is no
JavaScript/native interface, file access, content access or external URL handler.
