# Stack

| Layer | Tool | Role |
|-------|------|------|
| Android app | Android Studio, Kotlin, Jetpack Compose | UI, SMS, location |
| Android min / target | SDK 33 / 35 (Android 13+) | Oppo F19 and newer |
| Local data | Account-scoped Room, DataStore, Android Keystore | History, event outbox, PIN verifier, preferences |
| Location | Google Play services Fused Location + SLAM-owned last fix | Current fix → clearly timestamped last known |
| SMS | `SmsReceiver`, `SmsManager` | Command in, location out |
| Background | Foreground service + WorkManager | SMS listener, emergency schedule, offline event sync |
| API | Node.js 18+, Express 4.21 | REST + AdminJS at `/admin` |
| API data | MySQL, Sequelize, JWT, bcrypt | Accounts, plans, logs |
| Email | Nodemailer + Gmail App Password | Payment notices |
| Files (later) | Cloudinary | Payment screenshots |
| Web portal | React + Vite | Plans and payments |
| Admin | AdminJS on the API | Users, payments, plans |
| Hosting | Railway | MySQL + `slam-api` + `slam-web` |
| Local DB access | Railway public TCP proxy | `*.proxy.rlwy.net` + proxy port |
| Maps | Later | SMS map links and web map UI when we reach that work |
| Git | GitHub `main` / `dev` | Source of truth |
