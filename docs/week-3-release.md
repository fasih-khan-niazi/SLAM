# Week 3 Android Release Candidate

**Version:** 1.2.0 (`versionCode` 3)
**APK:** `android/app/build/outputs/apk/release/app-release.apk`

## What changed in the UI revamp continuation

- Bottom tabs fixed (Home no longer jumps to Tracking).
- Tracking owns PIN and trusted numbers; Settings is preferences/account only.
- Themed toasts with a 3s reverse progress bar.
- Darker true-dark palette.
- Login device lockout: 3 fails → 15 minutes, toast with tries left.
- Create-account phone must be 11–12 digits.
- Quiet last-known refresh every ~20 minutes while Listening is on.
- Clearer CURRENT / LAST KNOWN SMS templates with age warnings.
- Consent checkbox text no longer overlaps.

## Notes for friend QA

- Uninstall any older SLAM build first if signatures differ.
- Do not merge to `main` / redeploy Railway for UI-only testing.
- Physical phone install is not done from this machine unless you ask.
