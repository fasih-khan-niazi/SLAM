# Web portal

React + Vite. Tokens from [docs/design-system.md](../docs/design-system.md). Talks to the SLAM API.

## Run locally

API first (`f:\SLAM\api` → `npm run dev`). Then:

```powershell
cd f:\SLAM\web
copy .env.example .env
npm install
npm run dev
```

- Portal: http://localhost:5173
- API default: `VITE_API_BASE_URL=http://localhost:3000`

## What is here (Phase A)

- Dark default, light toggle (saved in the browser)
- Login, register, public plans, account dashboard
- Choose Basic/Premium, submit JazzCash/EasyPaisa receipt, payment history
- Custom modal, skeletons, terms page
- `GET /api/config` for maintenance and payment pause (AdminJS SystemConfig)
- Account notifications for payment submit / approve / reject

Maps stay Week 2 P2.

## Production build

```powershell
npm run build
npm run preview
```

On Railway, root directory is `web`. Set `VITE_API_BASE_URL` to the public `slam-api` URL **before** the build. See [docs/deploy.md](../docs/deploy.md).
