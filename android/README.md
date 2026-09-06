# Android app

Kotlin + Jetpack Compose. Min SDK 33 (Android 13+). Dark theme by default.

Open **this folder** in Android Studio: `F:\SLAM\android`  
Do **not** open `F:\SLAM` as the Gradle project. The app module lives under `android/`.

**Status (Week 1 / Phase A):** On Oppo F19 via wireless debug. Consent, admin login, PIN, permissions, listener notification work. Locate produced an accurate SMS body (coords + Maps link). Outbound **delivery** needs SMS credit on SIM 1. Settings, trusted numbers, failed-PIN log, last-10 history, and battery preference are in the app (rebuild to pick them up).

---

## 1. One-time Android Studio setup

1. Install [Android Studio](https://developer.android.com/studio) (Koala / Ladybug or newer is fine).
2. First launch: **Standard** install. Let it download the SDK.
3. **More Actions → SDK Manager** (or **File → Settings → Languages & Frameworks → Android SDK**).
4. **SDK Platforms** — check:
   - **Android 13.0 (Tiramisu)** API 33
   - **Android 15.0** API 35 (or the newest installed 34/35)
5. **SDK Tools** — check:
   - Android SDK Build-Tools
   - Android SDK Platform-Tools
   - Android SDK Command-line Tools
   - Google Play services
6. Apply / OK. Wait until downloads finish.

**JDK:** Android Studio’s embedded JBR 17 is enough.  
**File → Settings → Build → Build Tools → Gradle → Gradle JDK** → select **jbr-17** (not an old Java 8).

You do **not** need the emulator. We use the Oppo F19.

---

## 2. Open this project

1. Android Studio → **File → Open**.
2. Select `F:\SLAM\android` → **OK**.
3. If asked **Trust Project**, trust it.
4. Wait for **Gradle Sync**. Bottom status should become **Gradle sync finished**.
   - First sync downloads Gradle 8.9 and dependencies. That can take 5–15 minutes.
5. If sync fails:
   - **File → Sync Project with Gradle Files**
   - Or **File → Invalidate Caches → Invalidate and Restart**
   - Confirm internet is on (wrapper downloads `gradle-8.9-bin.zip`).

When sync is good, the left **Project** tree shows `app` with `java/com/slam/app`.

---

## 3. Phone: USB debugging

On the **Oppo F19**:

1. **Settings → About phone** → tap **Build number** 7 times.
2. **Settings → System → Developer options** (sometimes under Additional settings).
3. Turn **on**:
   - **USB debugging**
   - **Install via USB** (Oppo/ColorOS often has this; turn it on)
   - **Disable permission monitoring** if present (helps SMS/location)
4. Unlock the phone. Plug in USB.
5. Choose **File transfer / MTP** if a USB mode popup appears.
6. On the phone, tap **Allow USB debugging** for this PC. Check **Always allow**.

In Android Studio, the device dropdown (top toolbar, next to the green Run button) should show **OPPO** / **CPH2219** / similar — not “No devices”.

If it does not:

```powershell
cd "$env:LOCALAPPDATA\Android\Sdk\platform-tools"
.\adb devices
```

You want `device`, not `unauthorized`. If unauthorized, unplug, replug, accept the prompt again.

---

## 4. Run the app

1. Toolbar: module = **app**, device = your Oppo.
2. Green **Run** (Shift+F10).
3. First build compiles Kotlin and Compose. Wait.
4. The app installs and opens: splash → consent → login.

If install is blocked: phone → **Install anyway** / allow the computer.

---

## 5. What to do in the app (in order)

1. **Consent** — tick both boxes → Continue. Tracking stays off until you accept.
2. **Login**
   - Email and password only. The app talks to the deployed API (no URL field).
   - Login needs internet on the phone. SMS tracking does not.
   - Register a user (name, email, password 8+, phone) or sign in.
3. **Home**
   - **Tracking PIN** — enter 4–6 digits → **Save PIN**. Remember it.
   - **Allow SMS and location** — Allow all (SMS, location, notifications).
   - **Start listening** — a persistent notification “Listening for location requests” should appear.
   - **Stop listening** — notification goes away; SLAM texts are ignored until you start again.
4. **SMS test (two SIMs on this phone)**
   - SIM 1 = the SLAM number (the one the app is on).
   - SIM 2 = the tracker.
   - From SIM 2, SMS **to SIM 1’s number**:

     ```
     SLAM 1234 LOCATE
     ```

     Replace `1234` with the PIN you saved. A wrong PIN gets no reply. After 8 wrong PINs in 15 minutes (or the admin `pin_attempt_cap`), locates stay silent until the window ends or you update the PIN in Settings.
   - Wait up to ~30 seconds. SIM 2 should get a reply with coordinates. Wrong PIN = **no** reply (logged on device).
5. **Settings** (from Home)
   - Change PIN (current PIN required).
   - Trusted numbers: empty list = anyone with the PIN; once you add a number, only those numbers get a reply.
   - Prefer battery: skip GPS-first (also automatic below 15% battery).
   - Recent locations: last 10 replies stored on this phone.

Oppo may kill background apps. If SMS never wakes SLAM:

- **Settings → Apps → SLAM → Battery** → **Don’t optimize** / allow background.
- **Autostart** on for SLAM if ColorOS shows it.

---

## 6. Generate an APK to send later

**Build → Build Bundle(s) / APK(s) → Build APK(s).**  
Output: `android/app/build/outputs/apk/debug/app-debug.apk`  
(That folder is gitignored; you send the file yourself.)

---

## Design

Tokens: [docs/design-system.md](../docs/design-system.md)
