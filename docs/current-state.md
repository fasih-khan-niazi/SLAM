# SLAM: current state

This is the up-to-date description of how SLAM behaves today. The older phase and week docs are kept as history and are not edited; where they disagree with this file, this file wins.

SLAM is SMS-based location automation. A trusted contact texts the tracked phone and gets its location back, with no mobile data needed on the tracked phone. Accounts, plans and payments live on the web.

## Parts

| Part | Path | Stack |
|------|------|-------|
| Android app | `android/` | Kotlin, Jetpack Compose, Room, WorkManager, fused location |
| API | `api/` | Node, Express, MySQL (Sequelize), JWT, Cloudinary, Nodemailer |
| Web portal | `web/` | React, Vite. User pages plus operator admin at `/admin` |

Hosting is Railway: MySQL, `slam-api`, `slam-web`. See [deploy.md](deploy.md) and [env-setup.md](env-setup.md).

## How a locate works

1. The listener service (foreground, location type) keeps the app able to receive SMS.
2. An SMS shaped `<PREFIX> <PIN> LOCATE` is parsed. Anything else is ignored.
3. The sender must be on the trusted list, and the PIN must match. A wrong PIN gets no reply.
4. Wrong PINs are counted per sender. At the admin-set cap, that sender is locked out for the admin-set window. Other trusted numbers keep working.
5. The phone reserves one locate from the plan quota, gets a fix, and replies with coordinates, accuracy in meters and a Maps link.
6. If live location is unavailable, it replies with the saved last-known location, labelled `LAST KNOWN` with the capture time and age.
7. The event is stored in local history and uploaded to the server when online (offline events wait in an outbox).

Accuracy is reported in meters on purpose, not as High/Medium/Low.

## Last-known location (quiet refresh)

- While listening is on, a background worker saves a fresh location roughly every 15 minutes. One extra capture runs as soon as listening starts.
- Android decides the exact moment. 15 minutes is the minimum, and battery savers or Doze can delay a run. The timing is approximate.
- A newer saved location is never replaced by an older fused one.
- Settings shows the saved location's age and what the last background check did, and offers the unrestricted-battery request, which makes runs more reliable.
- The saved location is used only when live location fails. Turn it off with "Use last known location" in Settings.

## Quota and listening

- Plans: Free 5 locates and 1 contact; Basic 50 locates and 5 contacts (PKR 299); Premium unlimited locates and 20 contacts with history (PKR 699). Limits come from the database and can be edited by an operator.
- Every locate counts, including each emergency send.
- At zero locates the app stops listening, and Start listening is disabled ("No locates left") until the period renews or the plan changes.
- The cached quota is enforced offline.

## Emergency mode

- While on, the phone texts its location to all trusted contacts on a fixed interval.
- The interval is set for all users in admin Config: 5 to 1440 minutes.
- Running phones follow a changed interval after their next config sync (app open or resume). A dead chain is restarted on app start.

## Notifications

- Local system notifications: location sent, emergency sent, locate limit reached, turn Location on, payment update.
- Each payment notification is shown once per phone, then marked read on the server.

## Admin config

Edited at web `/admin` → Config and served to clients by `GET /api/config`. Out-of-range values are rejected with a 400.

| Setting | Allowed | Default |
|---------|---------|---------|
| SMS prefix | text | `SLAM` |
| PIN length | 4 to 8 | 4 to 6 |
| Wrong-PIN attempts before lockout | 1 to 5 | 3 |
| Wrong-PIN window (minutes) | 1 to 1440 | 15 |
| Failed web/app logins before lockout | 1 to 30 | 3 |
| Login window (minutes) | 1 to 1440 | 15 |
| Emergency available | on/off | on |
| Emergency interval (minutes) | 5 to 1440 | 60 |
| Maintenance, payments, email | on/off | off, on, on |
| JazzCash and EasyPaisa numbers | 10 to 15 digits | set in admin |

## Payments and email

A user picks a plan, pays by JazzCash or EasyPaisa, and submits the transaction ID and a screenshot (stored on Cloudinary). Every admin account is emailed at its registered address on submit, and the user is emailed on approve or reject. Email needs `EMAIL_USER` and `EMAIL_PASS` set on the API and `email_enabled` on.

## Not built, on purpose

Stealth mode, cell-tower location, cell-only mode at low battery, the premium history map, and stealth and accuracy-label settings were dropped. The "last ~10 history" limit is about 40 on the device.

## Running and building

```powershell
# API (needs api/.env)
cd api; npm install; npm test; npm run dev

# Web
cd web; npm install; npm run build

# Android (open the android/ folder in Android Studio, or:)
cd android; .\gradlew.bat testDebugUnitTest assembleDebug
```

The app's API address is set in `android/app/build.gradle.kts` (`API_BASE_URL`). Moving the API to a new host means rebuilding the APK with the new address. A custom domain pointed at the live deployment avoids that in future.
