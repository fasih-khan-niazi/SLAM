# Phase A — Finish Week 1

**What it is:** Phases 5 + 6 + 7, done as **one tagged Week 1 close**, in order.  
**When:** After Phase 4 code (SMS loop) — delivery on a topped-up SIM is your test later, not a blocker for this coding.  
**Not in Phase A:** Payments, Cloudinary, Maps UI, admin config page, stealth, emergency mode (Week 2).

**Status:** A1–A3 **code and docs are in the repo**. You still click Railway to create `slam-api` / `slam-web` (see [deploy.md](deploy.md)). Tag Week 1 when you ask.

---

## A1 — Device security and settings (old Phase 5)

**Goal:** The owner controls who can ask for location. Settings persist. Failures stay quiet and safe.

| Step | Work | Priority |
|------|------|----------|
| A1.1 | Settings screen from Home (PIN change, requires current PIN) | P0 |
| A1.2 | Trusted numbers: add / remove, stored on device (Room) | P0 |
| A1.3 | SMS handler: if list is not empty, ignore senders not on it (even with correct PIN) | P0 |
| A1.4 | Empty trusted list = current behaviour (any sender + correct PIN) until they add one | P0 |
| A1.5 | Wrong PIN: no SMS reply; increment local failed-attempt log | P0 |
| A1.6 | Location fail: existing `SLAM location unavailable` only — no internals | P1 |
| A1.7 | Settings: last 10 on-device locations (Room already writes them) | P2 |
| A1.8 | Optional: accuracy preference (GPS first vs battery) if time | P2 |

**Code:** Settings screen, Room v2 (trusted numbers + failed PIN, migration keeps last-10 history), SMS gate, battery preference.

**Done when:** Rebuild the app. A number on the list gets a reply; an unknown number does not; PIN change works.

**App doc:** `android/README.md` (settings, trusted list, command format).

---

## A2 — Web portal shell (old Phase 6)

**Goal:** Browser UI that matches the app theme and talks to the **local** API.

| Step | Work | Priority |
|------|------|----------|
| A2.1 | Vite + React in `web/`, tokens from `docs/design-system.md` | P0 |
| A2.2 | Dark default, skeletons, custom modal (no stock alerts) | P0 |
| A2.3 | Login + register (API URL default `http://localhost:3000`) | P0 |
| A2.4 | Public plans page (`GET /api/plans`) | P0 |
| A2.5 | After login: show plan name / remaining from `GET /api/user/subscription` | P1 |

**Code:** Vite + React in `web/` (theme tokens, login, register, plans, account home).

**Done when:** Register → login → see Free/Basic/Premium in the browser with API running locally.

**App/web doc:** `web/README.md` run steps; `docs/env-setup.md` `VITE_API_BASE_URL`.

---

## A3 — Week 1 integration (old Phase 7)

**Goal:** API reachable on Railway; portal and phone can use that URL. SMS still on-device.

| Step | Work | Priority |
|------|------|----------|
| A3.1 | Railway web service `slam-api`, root `api/`, env refs to MySQL (private host) | P0 |
| A3.2 | Public URL → document as `API_BASE_URL` / `VITE_API_BASE_URL` | P0 |
| A3.3 | Portal production build pointed at Railway API | P0 |
| A3.4 | Smoke: register → login → list plans against Railway | P0 |
| A3.5 | Debug APK (`Build → APK`); testers set API URL to Railway in the login field | P1 |
| A3.6 | Phone login against Railway (SMS loop unchanged) | P1 |

**Code:** `api/railway.toml`, `GET /api/config`, Helmet (CSP off for AdminJS), CORS extras, [deploy.md](deploy.md). You create the Railway service and paste env refs — nothing secret is in git.

**Done when:** Someone can use the portal on the Railway API URL and install a debug APK. SMS proof stays “when SIM 1 has credit.”

---

## Order for today

1. A1 (Android) → commit `android/` + app docs  
2. A2 (React) → commit `web/` + portal docs  
3. A3 (Railway + APK) → commit deploy notes only; no secrets  

Then we can **tag Week 1** on `dev`/`main` (later, when you ask).

## Your tests later (not required to write A1–A3)

- SIM 1 topped up → friend/SIM 2 receives the locate SMS  
- Create-account on the phone with `http://192.168.100.7:3000` (or Railway URL after A3)
