# Week 1 Plan — Operational Core

**Dates:** 1–5 September  
**Outcome:** A working SMS location loop on Android 13, a live API, and a polished portal shell.

Priorities: **P0** ship now · **P1** required this week · **P2** if time · **P3** later

---

## Phase 1 — Foundation

**Goal:** A professional monorepo, documented plan, and a cleaned API that starts locally.

| Item | Priority |
|------|----------|
| Root structure: `api/`, `web/`, `android/`, `docs/` | P0 |
| Week plans and environment setup docs | P0 |
| Shared design system (theme tokens) | P0 |
| Migrate previous API into `api/` (no secrets, no `node_modules`) | P0 |
| Git: `main` + `dev`, `.gitignore`, `.env.example` | P0 |

**Status:** Complete.

**Done when:** `api` starts, AdminJS loads, plans seed, no secrets in git.

---

## Phase 2 — API hardening

**Goal:** Auth, plans, subscriptions, and location routes are consistent and ready for the app.

| Item | Priority |
|------|----------|
| Register / login / profile (`/api/auth`) | P0 |
| Plans and subscribe (`/api/plans`, `/api/subscribe`) | P0 |
| Free plan auto-activates (no payment) | P0 |
| Location can-request, log, history | P0 |
| CORS for local portal + production origin | P1 |
| Admin role guard on payment approve/reject | P1 |
| Health check `/health` | P1 |

**Status:** Complete.

**Done when:** Auth + plans + location can be called against the local API. See [api-testing.md](api-testing.md).

---

## Phase 3 — Android shell

**Goal:** Installable app with a strong first impression before SMS logic.

| Item | Priority |
|------|----------|
| Kotlin project, min SDK 33 (Android 13+) | P0 |
| Shared theme: dark mode default, teal accent, typography | P0 |
| Navigation: splash → onboarding/consent → login → home | P0 |
| SMS, location, and notification permissions | P0 |
| Skeleton loaders, custom modals, haptics on primary actions | P1 |
| Polished login screen | P0 |

**Status:** Complete.

**Done when:** APK installs on Oppo F19 and the UI shell feels finished, not placeholder.

---

## Phase 4 — Core SMS loop

**Goal:** The product actually works: command in, location out.

| Item | Priority |
|------|----------|
| Receive `SLAM [PIN] LOCATE` | P0 |
| PIN stored in Android Keystore + Room | P0 |
| GPS → network → cell-tower fallback | P0 |
| SMS reply: coordinates + Maps link | P0 |
| Foreground service so Android 13 does not kill the listener | P0 |
| Battery: location only while fulfilling a request | P1 |

**Done when:** SIM 2 sends the command to SIM 1 and a location SMS comes back.

---

## Phase 5 — Device security and settings

**Goal:** Owner controls who can track, and failures are handled cleanly.

| Item | Priority |
|------|----------|
| Trusted numbers list | P0 |
| Settings: PIN, contacts, accuracy preference | P0 |
| Wrong PIN: no reply, failed attempt logged | P0 |
| Error SMS when location unavailable (no secrets leaked) | P1 |
| Last 10 locations stored on device | P2 |

**Done when:** Trusted number works, unknown number is ignored, settings persist.

---

## Phase 6 — Web portal shell

**Goal:** User-facing React app that matches the Android look.

| Item | Priority |
|------|----------|
| Vite + React, design tokens from `docs/design-system.md` | P0 |
| Dark mode, skeleton loaders, custom modals | P0 |
| Login / register screens | P0 |
| Public plans page | P0 |
| Auth against local API | P1 |

**Done when:** A user can register, log in, and view plans in the browser.

---

## Phase 7 — Week 1 integration

**Goal:** API on Railway; app and portal talk to it.

| Item | Priority |
|------|----------|
| Deploy `slam-api` on Railway (root `api/`) | P0 |
| Public domain → `API_BASE_URL` | P0 |
| Android debug build against Railway API (login only; SMS stays local) | P1 |
| Smoke test: register → login → list plans | P0 |
| Signed debug APK for testers | P1 |

**Done when:** Portal and API work on Railway; SMS loop still proven on the phone.

**Not this week:** payments, Cloudinary, Maps UI, config page, stealth, emergency mode.
