# Environment Setup

SLAM uses **one Railway MySQL** and **two connection modes**. Do not install a local database.

## How the database is reached

| Where the API runs | Host to use | Why |
|--------------------|-------------|-----|
| Your PC | Railway **public TCP proxy** | `mysql.railway.internal` is not reachable from Windows |
| Railway (`slam-api`) | `${{MySQL.MYSQLHOST}}` → `mysql.railway.internal` | Private network, faster, not exposed |

### Public proxy (local development)

1. Railway → project → **MySQL** → **Connect** → **Public Networking** (enable TCP proxy).
2. Copy the **public host and port** (not `mysql.railway.internal`).
3. Put those values in `api/.env` as `DB_HOST` and `DB_PORT`.
4. User, password, and database name stay the same (`root`, your MySQL password, `railway`).

Keep the internal URL for the deployed service only. Reference variables on `slam-api`:

```
DB_HOST=${{MySQL.MYSQLHOST}}
DB_PORT=${{MySQL.MYSQLPORT}}
DB_USER=${{MySQL.MYSQLUSER}}
DB_PASS=${{MySQL.MYSQLPASSWORD}}
DB_NAME=${{MySQL.MYSQLDATABASE}}
```

Replace `MySQL` with the exact service name if Railway named it differently.

## Local files

Copy examples. Never commit real `.env` files.

```powershell
cd f:\SLAM\api
copy .env.example .env
```

Fill `api/.env` using the table below. Portal local file comes in Phase 6:

```powershell
cd f:\SLAM\web
copy .env.example .env
```

## Variable map

| Variable | Service | Where to get it |
|----------|---------|-----------------|
| `DB_HOST` | `slam-api` / local `api/.env` | Local: MySQL → Connect → **public** host. Railway: `${{MySQL.MYSQLHOST}}` |
| `DB_PORT` | same | Local: public proxy port. Railway: `${{MySQL.MYSQLPORT}}` |
| `DB_USER` | same | MySQL → Variables → `MYSQLUSER` |
| `DB_PASS` | same | MySQL → Variables → `MYSQLPASSWORD` |
| `DB_NAME` | same | MySQL → Variables → `MYSQLDATABASE` |
| `NODE_ENV` | api, web | `development` on PC, `production` on Railway |
| `PORT` | api | Local: `3000`. Railway injects this — do not set it on the service |
| `JWT_SECRET` | api | Random 32+ character string |
| `SESSION_SECRET` | api | Different random 32+ character string |
| `ADMIN_EMAIL` | api | `admin@slam.com` |
| `ADMIN_PASSWORD` | api | `Password123` |
| `EMAIL_USER` | api | Gmail address used for outbound mail |
| `EMAIL_PASS` | api | Google Account → Security → 2-Step Verification → **App passwords** (16 characters, not the Gmail login password) |
| `CLOUDINARY_URL` | api | Cloudinary dashboard → API environment variable |
| `FRONTEND_URL` | api | After deploy: `slam-web` public domain. Local: `http://localhost:5173` |
| `API_PUBLIC_URL` | api | After deploy: `slam-api` public domain (used in admin emails). Local: `http://localhost:3000` |
| `VITE_API_BASE_URL` | web | Local: `http://localhost:3000`. Production: `slam-api` public domain |
| `VITE_GOOGLE_MAPS_API_KEY` | web | Google Cloud → Credentials → Maps JavaScript API key (HTTP referrer restricted) |

## Run locally

```powershell
cd f:\SLAM\api
npm install
npm run dev
```

- API: `http://localhost:3000`
- Health: `http://localhost:3000/health`
- Admin: `http://localhost:3000/admin`

Admin login: `admin@slam.com` / `Password123`

Then follow [api-testing.md](api-testing.md).

## Railway services (deploy later)

| Service | Root directory | Notes |
|---------|----------------|-------|
| MySQL | — | Already provisioned |
| `slam-api` | `api` | Start: `npm start` |
| `slam-web` | `web` | After Phase 6 |

After `slam-web` has a domain, set `FRONTEND_URL` on `slam-api` and redeploy so CORS allows the portal.

## Secrets

- Do not put Gmail **login** passwords in env. Use an **App Password** only.
- Do not commit `.env`.
- If a secret was pasted in chat, rotate it in Railway and Cloudinary when you have a spare moment.
