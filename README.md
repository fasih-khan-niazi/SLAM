# SLAM

SMS-based Location Automation for Mobile Phones.

Find a phone’s location with a text message. No mobile data required on the tracked device. Accounts, plans, and payments live on the web; tracking stays on GSM.

## Products

| Product | Path | Role |
|---------|------|------|
| Android app | `android/` | SMS commands, GPS, trusted contacts, offline tracking |
| API + Admin | `api/` | Auth, subscriptions, payments, location logs. AdminJS at `/admin` |
| Web portal | `web/` | Register, plans, payments, dashboard |

## Tools and technologies

| Layer | Tool | Role |
|-------|------|------|
| Android app | Android Studio, Kotlin, Jetpack Compose | UI, SMS, location |
| Android min / target | SDK 33 / 35 (Android 13+) | Oppo F19 and newer |
| Local data | Room, EncryptedSharedPreferences (Android Keystore) | History, PIN |
| Location | Google Play services Fused Location, LocationManager | GPS → network → last known |
| SMS | `SmsReceiver`, `SmsManager` | Command in, location out |
| Background | Foreground service (`location`) | Keeps the listener alive |
| API | Node.js 18+, Express 4.21 | REST + AdminJS at `/admin` |
| API data | MySQL, Sequelize, JWT, bcrypt | Accounts, plans, logs |
| Email | Nodemailer + Gmail App Password | Payment notices |
| Files | Cloudinary | Payment screenshots |
| Web portal | React + Vite | Plans and payments |
| Admin | AdminJS on the API | Users, payments, plans |
| Hosting | Railway | MySQL + `slam-api` + `slam-web` |
| Local DB access | Railway public TCP proxy | `*.proxy.rlwy.net` + proxy port |
| Maps | Later | SMS map links and web map UI when we reach that work |
| Git | GitHub `main` / `dev` | Source of truth |

## Quick start (API)

```powershell
cd f:\SLAM\api
copy .env.example .env
# Fill DB_* from Railway public MySQL proxy — see docs/env-setup.md
npm install
npm run dev
```

- API: http://localhost:3000
- Health: http://localhost:3000/health
- Admin: http://localhost:3000/admin  
  `admin@slam.com` / `Password123`

## Android

Open **`F:\SLAM\android`** (not the repo root) in Android Studio. Full steps: [android/README.md](android/README.md).

## Docs

| File | Contents |
|------|----------|
| [docs/week-1-plan.md](docs/week-1-plan.md) | Phases 1–7 and Phase A |
| [docs/phase-a.md](docs/phase-a.md) | A1–A3 to close Week 1 |
| [docs/week-2-plan.md](docs/week-2-plan.md) | Phases 8–13 |
| [docs/env-setup.md](docs/env-setup.md) | Railway MySQL, env vars, local vs deploy |
| [docs/design-system.md](docs/design-system.md) | Shared UI tokens |
| [docs/api-testing.md](docs/api-testing.md) | Local API checks |
| [docs/stack.md](docs/stack.md) | Tools and technologies |

## Git

Work on `dev`. Merge to `main` at phase milestones. Secrets stay in Railway and local `.env` files — never in the repo.

## License

Private. All rights reserved.
