# SLAM

SMS-based Location Automation for Mobile Phones.

Find a phone’s location with a text message. No mobile data required on the tracked device. Accounts, plans, and payments live on the web; tracking stays on GSM.

## Products

| Product | Path | Role |
|---------|------|------|
| Android app | `android/` | SMS commands, GPS, trusted contacts, offline tracking |
| API + Admin | `api/` | Auth, subscriptions, payments, location logs. AdminJS at `/admin` |
| Web portal | `web/` | Register, plans, payments, dashboard |

## Stack

- Android 13+ · Kotlin
- Node.js 18+ · Express 4 · Sequelize · MySQL
- React (Vite)
- Railway: MySQL + `slam-api` + `slam-web`

## Quick start (API)

```powershell
cd f:\SLAM\api
copy .env.example .env
# Fill DB_* from Railway public MySQL proxy — see docs/env-setup.md
npm install
npm run dev
```

- API: http://localhost:3000
- Admin: http://localhost:3000/admin  
  `admin@slam.com` / `Password123`

## Docs

| File | Contents |
|------|----------|
| [docs/week-1-plan.md](docs/week-1-plan.md) | Phases 1–7 |
| [docs/week-2-plan.md](docs/week-2-plan.md) | Phases 8–13 |
| [docs/env-setup.md](docs/env-setup.md) | Railway MySQL, env vars, local vs deploy |
| [docs/design-system.md](docs/design-system.md) | Shared UI tokens |

## Git

Work on `dev`. Merge to `main` at phase milestones. Secrets stay in Railway and local `.env` files — never in the repo.

## License

Private. All rights reserved.
