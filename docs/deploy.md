# Deploy on Railway

SLAM uses **one Railway project**. MySQL is already there. Add two web services when you are ready. Do not put secrets in git.

## Services

| Service | Root directory | Start | Public URL |
|---------|----------------|-------|------------|
| **MySQL** | — | Railway image | private + optional TCP proxy |
| **slam-api** | `api` | `npm start` | `https://….up.railway.app` |
| **slam-web** | `web` | `serve` of `dist` | `https://….up.railway.app` |

`api/railway.toml` and `web/railway.toml` set health checks. `VITE_API_BASE_URL` is read **when slam-web builds**, not at runtime.

---

## 1. Create `slam-api`

1. Railway project → **New** → **GitHub repo** → `fasih-khan-niazi/SLAM`.
2. Settings:
   - **Root Directory:** `api`
   - **Watch Paths** (optional): `api`
3. Connect branch **`dev`** while you are still iterating (or `main` after you merge).
4. Generate a public domain: **Settings → Networking → Generate Domain**.
5. Copy that URL. It is `API_PUBLIC_URL` (no trailing slash).

### Variables on `slam-api`

Private MySQL (not the Windows proxy):

```
DB_HOST=${{MySQL.MYSQLHOST}}
DB_PORT=${{MySQL.MYSQLPORT}}
DB_USER=${{MySQL.MYSQLUSER}}
DB_PASS=${{MySQL.MYSQLPASSWORD}}
DB_NAME=${{MySQL.MYSQLDATABASE}}
```

Use the **exact** MySQL service name if it is not `MySQL`.

Also set:

```
NODE_ENV=production
JWT_SECRET=<32+ random characters>
ADMIN_EMAIL=admin@slam.com
ADMIN_PASSWORD=Password123
EMAIL_USER=slam.app.fyp@gmail.com
EMAIL_PASS=<Gmail App Password>
CLOUDINARY_URL=<from Cloudinary, used in Week 2>
FRONTEND_URL=http://localhost:5173
API_PUBLIC_URL=https://<slam-api-domain>
```

Do **not** set `PORT`. Railway injects it.

After `slam-web` has a domain, change `FRONTEND_URL` to that `https://…` URL and **redeploy slam-api** so CORS allows the portal.

### Smoke `slam-api`

```powershell
$base = "https://<slam-api-domain>"
Invoke-RestMethod "$base/health"
Invoke-RestMethod "$base/api/plans"
Invoke-RestMethod "$base/api/config"
```

Expect `database: connected` and Free / Basic / Premium. Then run the register → login steps in [api-testing.md](api-testing.md) against `$base`.

Operator admin: sign in on **slam-web** as `admin@slam.com` / `Password123` → `/admin`.
API admin routes are under `/api/admin/*` (JWT + `role === admin`).

---

## 2. Create `slam-web` (after the portal exists)

1. **New** → same GitHub repo.
2. **Root Directory:** `web`
3. Variables **before the first successful build**:

```
VITE_API_BASE_URL=https://<slam-api-domain>
```

No trailing slash. If you change this later, **redeploy** slam-web so Vite rebuilds.

4. Generate a public domain.
5. Set `FRONTEND_URL` on slam-api to that domain and redeploy the API.

Local production-style check (API must be running, or point at Railway):

```powershell
cd f:\SLAM\web
copy .env.example .env
# optional: set VITE_API_BASE_URL to the Railway API
npm install
npm run build
npm run preview
```

---

## 3. Phone against Railway

The Android app has **no API URL field**. `BuildConfig.API_BASE_URL` is the Railway API. SMS still runs only on the device.

**Do not send `app-debug.apk`.** Play Protect blocks that file for clients. Signed release APK:

Android Studio → **Build → Generate Signed App Bundle or APK → APK**, keystore `android/slam-release.jks` (see [android/README.md](../android/README.md) section 6).  
Output: `android/app/build/outputs/apk/release/app-release.apk` (gitignored). Rename the copy you send to `SLAM.apk`.

---

## Local vs Railway MySQL

| API process | Host |
|-------------|------|
| Your PC | Public TCP proxy in `api/.env` — [env-setup.md](env-setup.md) |
| `slam-api` on Railway | `${{MySQL.MYSQLHOST}}` private network |

Never commit `.env`. Never paste Railway passwords into the repo.
