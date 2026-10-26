# Privacy

Updated 2026-10-26 for the local keyboard build.

Luma stores launcher layout, preferences, weather location, caches and manually managed Continue Watching entries on the device. It reads installed applications to display and launch tiles. It has no analytics or advertising SDK.

Luma's optional **phone typing** opens a temporary HTTP listener on the projector's active IPv4 network address while a Luma text editor or its selected Android keyboard is visible. The QR code contains a random session token; the phone browser sends text directly over the local network. The session expires after 10 minutes and closes when the dialog is dismissed, its activity stops, the keyboard hides or the active input field changes. New QR code invalidates the old connection. Text and session tokens are not logged, sent to a cloud service or stored by this feature; a normal Luma setting is saved only when Save is selected on the projector or Go is pressed on the phone. Local HTTP is not encrypted, so this feature is intended for a trusted local network, not sensitive passwords. QR encoding is performed on the projector using ZXing, without an external QR service. The phone page loads no third-party resources or analytics.

Optional **Read TV listings** access lets Luma read Watch Next and channel entries published through Android TV by participating apps. These entries may reveal viewing activity. You can disable Continue Watching or revoke the permission in Android settings. Luma does not access other apps' private databases.

Weather requests use Open-Meteo with the selected location's coordinates. Continue Watching artwork may be fetched from public Apple artwork and Stremio/Metahub image endpoints when a title identifier is available. Those services may receive network request metadata such as your IP address.

Optional Trakt integration uses the official device authorization flow. Tokens and its cached private history are encrypted with Android Keystore per Luma profile. Disconnecting clears the token. No Trakt client secret is bundled.

The optional global Menu bridge used during P6 testing is separate from normal Luma operation and may require a computer on that specific firmware. The public source package does not contain the P6 owner's connection details.


The bundled **Luma Keyboard** is optional and must be enabled/selected through Android. It edits only the current app field using Android InputConnection. Typed text is sent to that app; the app may store or send it according to its own behavior. Luma records no typing history and sends no keyboard data to cloud services. Phone QR typing is disabled for password input types and fields without a complete bounded text snapshot. You can switch to another installed keyboard through Android settings.
