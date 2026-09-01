# Android app

Kotlin + Jetpack Compose. Min SDK 33 (Android 13+). Dark theme by default.

## Open in Android Studio

1. Install Android Studio and the Android SDK (API 35) if you have not already.
2. **File → Open** → `F:\SLAM\android`
3. Let Gradle sync. Use JDK 17 (Android Studio bundled JDK is fine).
4. Connect the Oppo F19 with USB debugging on.
5. Run the `app` configuration.

First install is UI-only for SMS: splash → consent → login → home + permission prompt. The SMS location loop is Phase 4.

## API URL on a physical phone

`10.0.2.2` is the emulator alias for your PC. On the Oppo, set **API URL** on the login screen to:

```
http://YOUR_PC_LAN_IP:3000
```

Example: `http://192.168.1.24:3000`

PC and phone must be on the same Wi‑Fi. The API must be running (`npm run dev` in `api/`).

## Design

Tokens: [docs/design-system.md](../docs/design-system.md)
