# Week 3 Android QA Matrix

Run this matrix against the signed release candidate. Record phone model,
Android version, APK version, result, and notes for every row.

## Installation and migration

- Fresh install: consent → register → permissions → PIN → trusted contact.
- Upgrade over the last signed client APK without clearing app data.
- Verify Room migrations preserve only data belonging to the active owner.
- Verify the release APK is signed and its checksum is recorded.

## Account boundary

- Account A configures tracking, signs out, then Account B signs in.
- Account B must have no Account A PIN, contacts, history, quota, last fix,
  failed attempts, listener state, emergency state, or pending work.
- Sign-out must stop the foreground notification and scheduled emergency work.
- Theme, haptics, and consent must remain as device preferences.
- Expired/deleted account must enter the same safe cleanup path.

## Permissions and lifecycle

- Grant all permissions, close/reopen the app, and verify they remain shown as
  granted.
- Revoke each permission in Settings and return to SLAM.
- Disable/enable device Location and return to SLAM.
- Kill the process, reboot, and verify listener/emergency truth.
- Test notification denial and battery optimization on Oppo.

## Manual location

- Valid trusted sender and PIN receives one current fix and consumes one locate.
- Wrong PIN, untrusted sender, duplicate broadcast, and exhausted quota do not
  consume or send.
- SMS queue failure does not consume.
- Offline locates decrement locally and reconcile once online without double
  counting.

## Emergency

- Immediate ping and periodic ping each consume exactly one locate.
- Multiple trusted recipients still consume one locate.
- Immediate and periodic workers cannot overlap.
- Stop, logout, cap reached, permissions revoked, and empty contacts cancel all
  pending emergency work.
- UI shows last run, next run, failure reason, and live remaining quota.

## Last-known fallback

- Acquire a current fix, disable Location, then request again.
- SMS must say `LAST KNOWN`, include captured time, age, accuracy, coordinates,
  and map link.
- With no stored fix, send a clear unavailable response.
- Disabling the fallback prevents use of the stored fix.
- Account wipe removes the stored fix.

## UI and accessibility

- Verify Home, Tracking, Activity, and Settings tab/back-state behavior.
- Verify System, Light, and Dark appearance after restart.
- Verify haptics off/on, press states, loading/disabled buttons, custom
  confirmations, sheets, banners, and snackbars.
- Test TalkBack labels, 48dp targets, large font, keyboard traversal, contrast,
  offline/maintenance/error states, and reduced motion.

## Devices and release gate

- Oppo F19 / Android 13.
- At least one non-Oppo Android 13+ device.
- Unit, API, migration, WorkManager, Compose UI, lint, debug build, and signed
  release build all pass.
- No P0 regression may remain open before distribution.
