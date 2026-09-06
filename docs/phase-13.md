# Phase 13 — Close Week 2 (plan only)

**Not started.** This is the remaining work after Phase 12. Three items stay **high priority but parked** until you say so.

---

## Parked (later, still P0)

Do not forget these. They are not in the Phase 13 coding slice.

| Parked | Why it still matters | When |
|--------|----------------------|------|
| **Release APK** | Testers/clients need a file they can install. Same debug path you already use: Android Studio → **Build → Build APK(s)** → `android/app/build/outputs/apk/debug/app-debug.apk`. | After Phase 13 code + a rebuild |
| **Deploy slam-web** | Portal today is only `localhost:5173`. Railway needs a `web` service and `VITE_API_BASE_URL` = public slam-api. Then set `FRONTEND_URL` on slam-api. | After you PR `dev` → `main` and want a public portal |
| **Handover notes** (`docs/handover.md`) | How to approve a payment, change SystemConfig, build the APK, which env vars exist. | After the two deploys above |

**Also process (not a feature):** merge this `dev` batch to `main` so Railway slam-api picks up `/admin` CORS, SystemConfig, payments, notifications. Until then, **local** `/admin` works; **Railway** `/admin` can still show `Origin not allowed`.

---

## Phase 13 coding slice (now)

**Goal:** The product looks like SLAM, not like an AdminJS demo, and the last real gaps from the FYP list are closed. Emergency and stealth stay optional until you decide.

### P0 — must do in this slice

| Item | What |
|------|------|
| Replace AdminJS home | Remove the stock “Welcome on Board” rocket, Next.js/AWS cards, **Join the Discord**, and **Found a bug? GitHub**. Those are AdminJS defaults (see below). Put a short SLAM home: pending payments, users, link to SystemConfig. |
| Fix sidebar folder name | “Interchange Proxy Rlwy Net” is AdminJS using the MySQL host (`interchange.proxy.rlwy.net`). Set a navigation name like **SLAM**. |
| Cap trusted numbers by plan | FYP FR-12: Free 1, Basic 5, Premium 20. The phone list is **not** capped today. Cache `max_contacts` from `/api/auth/me` and block add when full. |
| UI pass (product, not academic) | Empty states, clearer errors, haptics where missing. Portal already has dark/light. Android stays dark-default. |

### P1 — if time in this slice

| Item | What |
|------|------|
| Prove one payment on Railway | Submit receipt against the **deployed** API, approve in `/admin` after CORS is on `main`. |
| Admin SystemConfig already works | No extra feature; just use it in the custom home. |

### P2 — decide

| Item | What |
|------|------|
| **Emergency mode (UC-3)** | Owner turns it on; the phone texts location to chosen numbers every **60 minutes** until they turn it off. Needs SMS credit, battery, and a clear stop. FYP marks this as emergency alert. We deferred it as high-risk for two weeks. |
| Premium location **map** on the portal | Still Week 2 Phase 10 P2. History API exists; Maps JavaScript UI does not. |

### P3 — decide (see Stealth below)

| Item | What |
|------|------|
| **Stealth (FR-8)** | Hide the launcher icon. Last-days only unless you require it for grading. |

### Already done (do not rebuild)

| Item | Where |
|------|--------|
| First-run consent (two checkboxes) | Android `ConsentScreen` |
| Silent consent guard | Locate + boot ignore tracking if consent was never accepted |
| No extra popup on **Start listening** | By design |
| Login + SMS PIN limits | SystemConfig, defaults 3 / 15 minutes, separate fields |

---

## Stealth — what it actually does

**FR-8 (Low in the FYP doc).** The app **disappears from the app drawer**. The listener can keep running. A thief (or a child) does not see “SLAM”.

**How it would be built:** disable the launcher activity (`PackageManager.setComponentEnabledSetting`). The process can still run if the foreground service is already up.

**How the owner gets back in:** they cannot tap the icon anymore. You need a back door: a secret dialer code, a persistent notification, or ADB. On Oppo that is easy to get wrong.

**Costs:**

- If they hide it and forget the back door, they cannot change PIN or Stop listening from the UI.
- Android 13 / Oppo can still show the app under Settings → Apps.
- Play Store would hate this; we sideload, so policy is weaker, but supervisors may still ask.
- Boot + hide + service is a common source of “it vanished and never came back” bugs.

**Recommendation:** leave it **parked as P3 / Future Work** unless the supervisor requires FR-8 on the demo. The ethical story is already covered by first-run consent.

---

## Consent in Phase 13

Already shipped in Phase 12. Phase 13 does **not** add a second consent on Start listening unless you ask for that confirm later.

---

## Critical leftovers after a pass over the repo

Nothing else is a blocker for the SMS loop or accounts. Real gaps, in order:

1. AdminJS stock dashboard and Discord/GitHub (looks like someone else’s product).
2. Trusted-number cap not enforced on the phone.
3. Railway `/admin` until `dev` is on `main`.
4. slam-web not public (parked).
5. Emergency and stealth (optional / FYP low–medium).
6. Portal map (P2).
7. Formal API spec and handover (parked / academic).

Not gaps: Helmet, CORS on local admin, JWT 7d, Cloudinary payments, emails, notifications, offline locate cap, Stop listening, terms, PIN lockout.
