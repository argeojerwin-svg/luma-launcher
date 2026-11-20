# Installable Luma web remote

Open https://argeojerwin-svg.github.io/luma-launcher/ in Chrome on Android. Use
Chrome's Install app action (or the page's Install Luma Remote button when
offered). Paste the complete private projector QR link and select Connect.
Allow local-network access when Chrome requests it. Use the same Wi-Fi as the
projector, with Phone remote enabled in Luma settings.

This static HTTPS frontend is hosted on GitHub Pages. It saves the private link
only in this browser's local storage; no real device address, token or account
credentials are embedded in the published files. Commands and text go directly
to the local projector, never through a cloud proxy. GitHub serves the public
interface; no analytics or third-party scripts are included.

The service worker caches only same-origin interface files. LAN API traffic is
excluded, so control requests, typed text and pairing tokens are never cached by
it. Forget removes the local saved link. Turning Phone remote off on the
projector revokes its token. Updating the IP requires pasting the new QR link.

The projector update permits CORS only for the exact HTTPS Pages origin, and
only on the permanent remote endpoint. Preflight does not authorize commands:
every actual API request still needs the private token. Other origins and
temporary keyboard editors remain blocked. No router port forwarding is needed.

Chrome must support Local Network Access permissions and the corresponding
mixed-content exemption for local HTTP devices. Older browsers may block this
connection. If prompted, grant Local network access for this website. Installing
the web interface cannot override an Android policy that blocks local networking
or app installation; physical-phone confirmation is still required.

Hosting: Pages source is branch `feature/installable-web-remote`, folder `/docs`.
The web app manifest uses standalone mode, stable app ID/start URL and 192/512px
icons. Updating the hosted files updates the interface without an APK install.
