# SLAM API

Express service for authentication, subscriptions, payments, location logs, and admin REST (`/api/admin`).

```powershell
cd f:\SLAM\api
copy .env.example .env
npm install
npm run dev
```

Operator UI lives on the web portal at `/admin` (JWT `role === admin`).

See [docs/env-setup.md](../docs/env-setup.md) for Railway MySQL (public proxy locally, private host on Railway).

After the server starts, run the checks in [docs/api-testing.md](../docs/api-testing.md).
