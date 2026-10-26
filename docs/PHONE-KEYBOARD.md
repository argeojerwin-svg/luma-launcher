# Luma keyboard and QR phone typing

Luma includes a bundled Android keyboard for other apps, plus its shared launcher text editor. No separate keyboard APK, phone app or PC bridge is needed. Both use a compact panel at the bottom so the app stays visible above it.

## Use it

1. Enable **Luma Keyboard** in Android keyboard settings and select it as the current keyboard. It is already enabled and selected on the test projector.
2. Open a standard Android text field, for example Stremio Search. Use the remote's arrows and OK on QWERTY, or scan the QR beside it.
3. Keep your phone and projector on the same local network. Phone text appears directly in the current app field.
4. Tap **Go** on the phone, or its keyboard Go/Enter key. Luma performs the field's Android action, such as Search, Go, Next or Send. The projector button does the same. Close or Back hides the keyboard; text already entered in another app remains in that app.

Luma's own fields still use their existing Save/Cancel behavior. Their QWERTY editor is also a smaller bottom panel. Cancel there discards the draft.

The remote keyboard includes digits, Shift, symbols, Space, Delete, cursor arrows and Clear. Client apps retain their own input filters and limits. Apps that draw their own keyboard rather than requesting Android input may keep that custom interface.

## Phone sessions

Each visible editor has a fresh random connection token and local port. Codes expire after 10 minutes. New QR code replaces the link. Hiding the keyboard, changing fields or apps, closing a Luma editor, or stopping its activity closes the old session.

Phone typing is unavailable for password fields, text over 2048 characters, and editors that do not expose a complete text snapshot through Android. Remote input remains available. Phone input replaces the current field text; it is not a phone-wide remote control.

Phone typing uses temporary local HTTP and no cloud service. Guest Wi-Fi isolation or VPN routing can block it. Use a trusted local network. No text, pairing token or typing history is logged or saved by the keyboard. See [privacy](PRIVACY.md).

## Changing keyboards

Select the original System Keyboard or Android Keyboard in Android keyboard settings to switch back. The original keyboards remain installed and enabled. For development, use `adb shell ime list -a -s` to list them, then `adb shell ime set <id>` to choose one. The previous projector keyboard was `com.android.inputmethod.pinyin/.PinyinIME`.

## Development

- `LumaInputMethodService.kt`: bundled IME, real app InputConnection editing, non-fullscreen bottom panel, D-pad routing, phone-session and password isolation. Content insets preserve the application layout behind the panel.
- `GlassNameDialog.kt`: launcher editor, QR display, Save/Cancel and dialog-owned cleanup.
- `QwertyKeyboardView.kt`: reusable remote keys with direct field or InputConnection actions.
- `PhoneTextSession.kt`: bounded token-protected local server, exact Host/Origin checks, strict UTF-8, expiry, single-use submit and cleanup.
- `assets/phone-keyboard.html`: serialized live typing followed by Go; repeated submit is blocked.
- `res/xml/luma_input_method.xml` and the manifest register the IME with Android. Only Android may bind it using BIND_INPUT_METHOD.
- ZXing Core 3.5.3 generates QR codes locally; its Apache 2.0 license is bundled under assets/licenses.

Verification: 31 JVM tests and 5 targeted projector tests passed. The integration test exercises Android input, D-pad navigation, QR text delivery, phone Go invoking Search, previous-session closure and password classification. End-to-end testing in Stremio confirmed the bottom overlay, QR scan and phone Go returning search results. Release build and lint passed with 0 errors and 52 existing warnings.

Android instrumentation force-stops its target app at completion. Launch Luma once after running device tests before checking the IME from another app; ordinary launcher usage does not involve this test-runner stop.