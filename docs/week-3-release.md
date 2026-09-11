# Week 3 Android Release Candidate

**Version:** 1.1.0 (`versionCode` 2)
**APK:** `android/app/build/outputs/apk/release/app-release.apk`
**Size:** 46,993,735 bytes
**SHA-256:** `AC784C9A12B98CA3524E9B5032E0E7492686D5B80F9776DF0D2FA7B35BDC6B3F`

## Automated verification

- Android debug compilation: passed.
- Android release compilation and packaging: passed.
- Release signing verification: passed using APK Signature Scheme v2.
- Signer certificate SHA-256:
  `00e7b8a4502d35113d9c84ed29b3be4365c49621f035c74db1c34527fda16cff`.
- Android unit tests: passed.
- Android instrumented-test sources: compiled.
- Android debug and release lint: passed with no errors.
- API tests: 6 passed.
- API/Android IDE diagnostics: no errors.
- Git whitespace/conflict check: passed.

## Physical verification required before client distribution

No Android device or emulator was connected during the automated release run.
Run [week-3-qa.md](week-3-qa.md) on:

1. Oppo F19 / Android 13.
2. One non-Oppo Android 13+ phone.

Do not replace the client APK until the account-switch, permission recreation,
offline emergency cap, Location-off fallback, reboot, and upgrade rows pass.
