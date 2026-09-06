# Week 2 Plan — Full Platform

**Dates:** 6–10 September  
**Outcome:** Subscriptions, payments, notifications, security, and a release APK.

Priorities: **P0** must ship · **P1** required for complete product · **P2** polish · **P3** only if ahead

---

## Phase 8 — Payments and admin approval

**Goal:** Manual JazzCash / EasyPaisa flow that an admin can complete.

| Item | Priority |
|------|----------|
| Submit transaction ID + screenshot | P0 |
| Store screenshots on Cloudinary (Railway has no persistent disk) | P0 |
| AdminJS Approve / Reject | P0 |
| Email on submit (admin) and on decision (user) | P0 |
| Payment history for the logged-in user | P1 |

**Status:** API stores screenshots on Cloudinary. Submit + history + AdminJS approve/reject + emails. Portal pay UI is Phase 10.

**Done when:** A pending payment becomes an active 30-day subscription after admin approval (prove once against Railway).

---

## Phase 9 — App accounts and limits

**Goal:** The phone is linked to an account; SMS still works without internet.

| Item | Priority |
|------|----------|
| Android login, JWT stored securely | P0 |
| Sync subscription on launch when online | P0 |
| Cache monthly remaining requests for offline use | P0 |
| Block further SMS replies when cached limit is reached | P0 |
| Stop listening (clear button; stops the foreground service) | P0 |
| POST location log when online | P1 |

**Status:** Code on `dev` — usage cache, silent block at the cap, Stop listening, location log when online.

**Done when:** Free plan allows 5 requests; the 6th is blocked even if the network is off (using cache). Rebuild the app to pick this up.

---

## Phase 10 — Portal dashboard

**Goal:** Users manage the product from the web.

| Item | Priority |
|------|----------|
| Dashboard: plan, usage, dates | P0 |
| Subscribe + payment submit UI | P0 |
| Payment history | P1 |
| Premium location history map (Maps JavaScript API) | P2 |

**Status:** Portal dashboard, plan picker, receipt upload, and payment history are in `web/`. Maps stay P2.

**Done when:** Register → pick plan → submit payment → see status without touching AdminJS as a user.

---

## Phase 11 — Admin configuration and notifications

**Goal:** Operators control the product from the panel; users see in-app alerts.

| Item | Priority |
|------|----------|
| `SystemConfig` table + AdminJS config page | P0 |
| Public config endpoint (safe fields only) — `GET /api/config` reads `SystemConfig` | P1 |
| In-app notification list API | P0 |
| Admin notification on new payment | P1 |
| Settings: SMS prefix, limits, maintenance, email toggle, PIN attempt cap | P0 |

**Status:** `SystemConfig` + AdminJS (no new/delete), `GET /api/config` reads the table, notification list API, payment submit/approve/reject create in-app alerts. Maintenance and `payments_enabled` are honored on subscribe/pay and the portal banner.

**Done when:** Changing a config value in admin is reflected by the API without a code deploy. Redeploy slam-api so `/admin` CORS allows the API’s own origin.

---

## Phase 12 — Security and consent

**Goal:** The product looks and behaves like something you would ship.

| Item | Priority |
|------|----------|
| Rate limit failed PINs (local) and login (API) | P0 |
| Helmet, CORS allowlist, JWT expiry | P0 |
| First-run consent screen on Android | P0 |
| Terms page on the portal | P1 |
| Duplicate transaction ID rejected | P0 |

**Done when:** Unauthenticated API calls return 401; consent must be accepted before tracking is enabled.

---

## Phase 13 — Release

**Goal:** Hourly emergency updates, optional stealth, production deploy, handover.

| Item | Priority |
|------|----------|
| Emergency mode: location SMS every 60 minutes while active | P2 |
| Stealth: hide launcher icon (last days only) | P3 |
| UI pass: empty states, errors, haptics, dark/light | P1 |
| Release APK | P0 |
| Deploy `slam-api` + `slam-web` on Railway | P0 |
| Handover notes in `docs/handover.md` | P1 |

**Done when:** Testers can install the APK, use the portal, and admins can operate `/admin` on the public API URL.
