# Stack

| Layer | Tool | Role |
|-------|------|------|
| Android app | Android Studio, Kotlin, Jetpack Compose | UI, SMS, location |
| Android min / target | SDK 33 / 35 (Android 13+) | Oppo F19 and newer |
| Local data | Room, EncryptedSharedPreferences (Android Keystore) | History, PIN |
| Location | Google Play services Fused Location, LocationManager | GPS → network → last known |
| SMS | `SmsReceiver`, `SmsManager` | Command in, location out |
| Background | Foreground service (`location`) | Keeps listener alive |
| API | Node.js 18+, Express 4.21 | REST + AdminJS at `/admin` |
| API data | MySQL, Sequelize, JWT, bcrypt | Accounts, plans, logs |
| Email | Nodemailer + Gmail App Password | Payment notices |
| Files (later) | Cloudinary | Payment screenshots |
| Web portal | React + Vite (Phase 6) | Plans and payments |
| Admin | AdminJS on the API | Users, payments, plans |
| Hosting | Railway | MySQL + `slam-api` + `slam-web` |
| Local DB access | Railway public TCP proxy | `*.proxy.rlwy.net` + proxy port |
| Maps in SMS | Google Maps query URL | No API key |
| Maps on web (later) | Maps JavaScript API | Premium history |
| Git | GitHub `main` / `dev` | Source of truth |
