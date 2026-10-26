# Global Menu on Android TV

Luma includes an optional Android Accessibility service for its physical remote shortcut. On a compatible TV, a short press of **Menu** opens or closes Luma Control Centre over another app; a hold opens App Switcher. This does **not** require the P6 Mac bridge.

1. Install and open Luma.
2. In Luma, open **Launcher settings → Control Centre inside apps** and allow **Display over other apps**.
3. Choose **Open Android Accessibility settings**, enable **Luma Control Centre in apps**, and accept Android's confirmation.
4. Open another app and try a short Menu press and a hold. You can turn the service off again in Android Accessibility settings.

The shortcut depends on the TV exposing that remote button as Android `KEYCODE_MENU`, allowing key-event filtering, and keeping the Accessibility service enabled. Android may give key filtering to only one service at a time. Some remotes have no Menu button or map it to another code. On the tested AUMI P6, the service is repeatedly disabled by firmware, so this route is not reliable there. The [P6 bridge](P6-MENU-BRIDGE.md) is a device-specific workaround, not a requirement for other TVs.

Compatibility with other TV models has **not yet been verified**. Normal Luma Home, app launching, and in-launcher Control Centre do not need this optional service.
