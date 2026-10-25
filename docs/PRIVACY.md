# Privacy

Updated 2026-10-25 for v1.0.0-rc1.

Luma stores launcher layout, preferences, weather location, caches and manually managed Continue Watching entries on the device. It reads installed applications to display and launch tiles. It has no analytics or advertising SDK.

Optional **Read TV listings** access lets Luma read Watch Next and channel entries published through Android TV by participating apps. These entries may reveal viewing activity. You can disable Continue Watching or revoke the permission in Android settings. Luma does not access other apps' private databases.

Weather requests use Open-Meteo with the selected location's coordinates. Continue Watching artwork may be fetched from public Apple artwork and Stremio/Metahub image endpoints when a title identifier is available. Those services may receive network request metadata such as your IP address.

Optional Trakt integration uses the official device authorization flow. Tokens and its cached private history are encrypted with Android Keystore per Luma profile. Disconnecting clears the token. No Trakt client secret is bundled.

The optional global Menu bridge used during P6 testing is separate from normal Luma operation and may require a computer on that specific firmware. The public source package does not contain the P6 owner's connection details.

