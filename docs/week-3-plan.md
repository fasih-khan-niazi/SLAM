# Week 3 — Android Reliability and Product Overhaul

**Status:** Implementation and automated verification complete on `dev`.
Physical-device release QA remains required; see
[week-3-release.md](week-3-release.md) and [week-3-qa.md](week-3-qa.md).

**Scope:** Android app plus only the API contracts required by it. The user web
portal and AdminJS UI remain deferred.

## Fixed product decisions

- Account sign-out or account switching stops tracking and clears all
  account-owned local data. Consent, theme, and haptic preferences remain on
  the device.
- One emergency location event consumes one locate, regardless of how many
  trusted contacts receive it.
- Cell-tower lookup is dropped. If a current location is unavailable, SLAM may
  use only its own last successfully stored fix and must label its age clearly.
- Reliability, migration safety, and regression tests come before visual
  redesign.

## Delivery sequence

### Phase 0 — Baseline and regression harness

- Reproduce the five client reports in tests.
- Add unit, Room, WorkManager, Compose UI, and API contract test foundations.
- Keep diagnostics free of JWTs, PINs, phone numbers, and coordinates.

### Phase 1 — Reactive architecture

- Split authenticated and unauthenticated navigation.
- Move business logic from composables into lifecycle-aware ViewModels,
  repositories, immutable state, and one application container.
- Use one Retrofit/OkHttp client, typed failures, cached-first rendering, and
  debug-only HTTP logging.

### Phase 2 — Account isolation and security

- Persist the authenticated user ID and centralize login, logout, token expiry,
  and account switching.
- Stop listener/emergency work and wipe account-owned PIN, contacts, attempts,
  history, last fix, quota/outbox, token, and cache.
- Scope Room records to their owner and use explicit migrations.
- Protect the JWT with Android Keystore-backed storage and store a salted PIN
  verifier rather than the PIN value.

### Phase 3 — Auth validation and onboarding

- Match server rules for name, email, phone, password, and confirmation.
- Add inline errors, keyboard actions, autofill, rotation-safe form state,
  request debouncing, branded loading/error states, and legal links.
- Honor configured PIN bounds and SMS prefix.

### Phase 4 — Permission and listener truth

- Derive permission, Location-service, notification, and battery state from
  Android on launch and every resume.
- Use the required staged permission flow, remove `READ_SMS` if tests confirm it
  is unnecessary, and deep-link to the relevant Settings screen.
- Gate listener, boot, emergency, and location work on actual prerequisites.

### Phase 5 — Atomic quota and emergency accounting

- Use subscription start/end dates, never calendar-month rollover.
- Record UUID-backed locate events in a local atomic ledger/outbox.
- Count once only after a location exists and at least one SMS is queued.
- Make the API idempotent and transactional, sync offline events with backoff,
  update usage live, prevent overlaps, and stop emergency at the cap.

### Phase 6 — Current and last-known location

- Return typed current, stored-last, disabled, denied, timeout, and unavailable
  outcomes.
- Store only SLAM's latest successful fix with provider, timestamp, and
  accuracy in metres.
- If live acquisition fails, clearly send `LAST KNOWN`, its recorded time/age,
  accuracy, coordinates, and map link. Clear it during account wipe.
- Remove all cell-ID fallback code.

### Phase 7 — Product-grade UI and UX

- Add four authenticated tabs: **Home**, **Tracking**, **Activity**, and
  **Settings**.
- Build a proper status dashboard, dedicated tracking controls, structured
  history/notifications, and coherent account/preferences screens.
- Expand reusable themed controls: button variants, fields, chips, banners,
  cards, rows, empty/error/offline states, sheets, dialogs, and snackbars.
- Add press/ripple/loading feedback, optional haptics, persisted
  System/Light/Dark modes, purposeful short animations, and reduced-motion
  support.

### Phase 8 — Hardening

- Remove forced splash delay and unnecessary recomposition/client creation.
- Add offline, retry, maintenance, expiry, and OEM battery states.
- Add accessibility semantics, 48dp targets, scalable type, contrast checks,
  previews, localized strings, and UI tests.
- Deduplicate SMS events, avoid global PIN-lockout denial of service, reconcile
  plan downgrades, require a trusted contact before tracking, and define local
  privacy retention.

### Phase 9 — Release verification

- Run unit, migration, WorkManager, Compose UI, API, lint, and signed release
  checks.
- Test fresh install and upgrade on Oppo plus another Android 13+ device,
  including account switching, recreation, reboot, permission changes,
  Location off, offline cap exhaustion, multi-recipient emergency, calendar
  rollover, token expiry, and poor connectivity.
- Ship only when every P0 regression passes; increment version, generate a
  checksum, preserve signing material, and publish client update notes.

## Definition of done

- Account B cannot see or use account A's tracking data.
- Permission/listener/emergency UI remains truthful through process death,
  settings changes, and reboot.
- Manual and emergency tracking share one idempotent 30-day quota pipeline.
- Location-off fallback is a clearly timestamped SLAM-owned last fix; there is
  no cell-tower feature.
- The signed app has four coherent tabs, live state, working appearance and
  haptic settings, consistent feedback/animations, accessibility coverage, and
  no P0 release blocker.
