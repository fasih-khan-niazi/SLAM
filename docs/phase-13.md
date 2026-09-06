# Phase 13 — Final implementation plan

**Status:** Plan only. Do not start coding until asked.

**This slice:** AdminJS home looks like SLAM, trusted numbers respect the plan cap, emergency locate works and is admin-configurable.

**Parked (later):** stealth, Maps UI, release APK, slam-web deploy, handover notes. Also: merge `dev` → `main` when you want Railway `/admin` CORS.

---

## Do we need cron jobs for emergency?

**No.** A server cron cannot send the location SMS. That SMS has to leave the **tracked phone’s SIM**. Railway has no access to that radio.

Emergency is an **on-device timer**:

- Admin only stores “every N hours” in SystemConfig.
- The phone reads that from `GET /api/config` and caches it (same as PIN cap).
- While emergency is **on**, Android uses `AlarmManager` / `WorkManager` (plus the existing foreground service) to wake, get a fix, and SMS the trusted list.
- Offline still works. Internet is only needed to refresh the interval from admin.

A cron on slam-api would only make sense if the **server** were sending SMS (a paid SMS gateway). That is not this product.

---

## This slice — implementation order

### 1. AdminJS home and navigation (P0)

**Investigate:** Confirm the rocket, Discord, and GitHub footer are the default AdminJS dashboard (`dashboard.component` unset). Confirm the sidebar label is the MySQL host.

**Fix:**

- Custom dashboard component: short SLAM copy, counts or links for pending payments, users, SystemConfig. No AdminJS marketing, no Discord, no GitHub.
- `branding` / resource `navigation`: group name **SLAM**, not `interchange.proxy.rlwy.net`.
- Keep Approve / Reject and SystemConfig as they are.

**Done when:** `/admin` after login is SLAM-only. No “Welcome on Board”, no Discord/GitHub.

### 2. Trusted-number cap (P0)

**Investigate:** Settings can add unlimited numbers. `SubscriptionInfo` from `/api/auth/me` does not include `max_contacts`. Locate already uses the list if it is non-empty.

**Fix:**

- API: include `max_contacts` on the subscription payload (`/api/auth/me` and related).
- Phone: cache it with usage. **Add number** refuses when `count >= cap` (Free 1, Basic 5, Premium 20).
- Clear message: upgrade or remove a number. Empty list still means “anyone with the PIN” (current rule).

**Done when:** a Free account cannot save a second trusted number after a rebuild.

### 3. Emergency locate (P0) — must work

**Admin SystemConfig** (own labeled pair, next to portal login / SMS PIN):

| Field | Meaning | Default | Range |
|-------|---------|---------|-------|
| `emergency_enabled` | Allow the phone to offer Emergency | `true` | on/off |
| `emergency_interval_hours` | Hours between automatic location SMS | `1` | integer **1–24** |

Max **24** hours (once a day). Min **1** hour as you asked. Integer only.

**Phone:**

- Toggle **Emergency** on Home (only if listening is on, consent accepted, PIN set, and `emergency_enabled`).
- Recipients = **trusted numbers**. If the list is empty, do not start — tell them to add at least one.
- Each tick: same locate path (GPS → network → cell), SMS each trusted number (coords + map link). Silent on failure, same as a normal locate.
- Interval from the cached admin value. If they never fetched config, use **1 hour**.
- Survives reboot if emergency was on (same idea as the listener).
- **Stop emergency** is a clear button. Stopping listening also stops emergency.
- Each ping **counts as one locate** against the plan cache (Free 5 / Basic 50 / Premium unlimited), so emergency cannot bypass the cap.

**Done when:** with interval 1, two trusted numbers, and listening on, both numbers get a locate SMS about an hour apart, twice, then Stop ends it. Wrong PIN / no consent / over cap still stay silent.

### 4. Light UI pass (P1)

Empty states and errors on the new Emergency block, trusted-cap modal, and admin home. No redesign of the whole app.

---

## Parked (do not build in this slice)

| Item | Notes |
|------|--------|
| **Stealth** | Hide launcher icon. You will discuss with clients first. |
| **Maps UI** | Premium history map on the portal. API log already exists. |
| **Release APK** | Your usual **Build → Build APK(s)** after this slice is on the phone. |
| **slam-web on Railway** | After `dev` → `main` and `VITE_API_BASE_URL`. |
| **Handover notes** | After those deploys. |
| **Start-listening consent popup** | Not requested. First-run consent + silent guard already shipped. |

**Process (you):** PR `dev` → `main` when you want Railway to get CORS + this slice.

---

## Already shipped — leave it

First-run consent, silent locate/boot guard, portal vs SMS lockouts (3 / 15 min, admin-configurable), payments, Cloudinary, emails, notifications, Stop listening, offline locate cap.

---

## Slice done when

1. Admin home is SLAM, not AdminJS marketing.
2. Free cannot add a second trusted number.
3. Emergency SMS runs on the phone at the admin interval (1–24 hours), no cron.
4. Stealth and Maps stay parked.
