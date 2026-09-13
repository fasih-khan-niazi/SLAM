# API testing (Phase 2)

Run these after `api/.env` is filled and the server is up. PowerShell from any folder.

Base URL:

```powershell
$base = "http://localhost:3000"
```

## 1. Health

```powershell
Invoke-RestMethod "$base/health"
```

Expect `status: ok` and `database: connected`.

## 2. Public plans (no token)

```powershell
Invoke-RestMethod "$base/api/plans"
```

Expect Free, Basic, Premium.

## 2b. Public config (no token)

```powershell
Invoke-RestMethod "$base/api/config"
```

Expect `sms_prefix: SLAM`, `maintenance: false`, `payments_enabled: true`, `login_attempt_cap: 3`, `pin_attempt_cap: 3`, `emergency_enabled: true`, and `emergency_interval_hours: 1`. These come from the `system_config` row (web Admin → Config). Portal login, SMS PIN, and emergency are separate fields.

## 3. Register

```powershell
$register = Invoke-RestMethod -Method Post "$base/api/auth/register" -ContentType "application/json" -Body (@{
  name = "Ayesha Khan"
  email = "ayesha@example.com"
  password = "Password123"
  phone = "03001234567"
} | ConvertTo-Json)

$register.data.subscription.plan_name
$token = $register.data.token
```

Expect `plan_name` = `Free` and `status` = `active`. Save `$token`.

## 4. Profile

```powershell
$headers = @{ Authorization = "Bearer $token" }
Invoke-RestMethod "$base/api/auth/me" -Headers $headers
```

## 5. Subscription

```powershell
Invoke-RestMethod "$base/api/user/subscription" -Headers $headers
```

## 6. Can request location

```powershell
Invoke-RestMethod "$base/api/location/can-request" -Headers $headers
```

Expect `allowed: true`.

## 7. Log a location

```powershell
Invoke-RestMethod -Method Post "$base/api/location/log" -Headers $headers -ContentType "application/json" -Body (@{
  event_id = [guid]::NewGuid().ToString()
  latitude = 33.6844
  longitude = 73.0479
  accuracy = "HIGH"
  accuracy_meters = 8.5
  source = "CURRENT"
  provider = "fused"
  captured_at = (Get-Date).ToUniversalTime().ToString("o")
  requested_by = "03009876543"
} | ConvertTo-Json)
```

`event_id` is required and makes retries idempotent: resending the same event
returns it with `idempotent: true` and does not consume quota again. `source`
must be `CURRENT` or `LAST_KNOWN`. Coordinates and numeric accuracy are
validated before the event and quota increment are committed together.

## 8. History on Free (should be blocked)

```powershell
try {
  Invoke-RestMethod "$base/api/location/history" -Headers $headers
} catch {
  $_.Exception.Response.StatusCode.value__
}
```

Expect `403`.

## 9. Login

```powershell
Invoke-RestMethod -Method Post "$base/api/auth/login" -ContentType "application/json" -Body (@{
  email = "ayesha@example.com"
  password = "Password123"
} | ConvertTo-Json)
```

## 10. Protected route without token

```powershell
try {
  Invoke-RestMethod "$base/api/auth/me"
} catch {
  $_.Exception.Response.StatusCode.value__
}
```

Expect `401`.

## 13. Login rate limit (Phase 12)

Three **failed** portal sign-ins from the same IP (default) return `429`. The count and window come from SystemConfig (`login_attempt_cap`, `login_window_minutes`). A successful sign-in clears the count. Register is still capped at 8 posts per 15 minutes.

Duplicate `transaction_id` on `POST /api/payments/submit` still returns `400`.

## Admin portal (web)

Sign in on the web portal as `admin@slam.com` / `Password123` → `/admin`.

```powershell
# After login as admin, use the JWT:
Invoke-RestMethod "$base/api/admin/stats" -Headers $headers
Invoke-RestMethod "$base/api/admin/payments?status=pending" -Headers $headers
```

Approve from the portal Payments page, or:

```powershell
Invoke-RestMethod "$base/api/admin/payments/<id>/approve" -Method PATCH -Headers $headers
```

## 11. Payment submit (Phase 8)

Needs a JWT and `CLOUDINARY_URL`. Create a paid subscription first (`POST /api/subscribe` with Basic or Premium `plan_id`), then:

```powershell
$form = @{
  subscription_id = "<id>"
  payment_method = "jazzcash"
  transaction_id = "JC-TEST-001"
}
# Attach screenshot as multipart field name "screenshot" (JPG/PNG).
```

Then web `/admin/payments` → Approve. User gets email plus an in-app notification; plan becomes active.

`GET /api/payments/my` lists that user’s history.

## 12. Notifications (Phase 11)

```powershell
Invoke-RestMethod "$base/api/notifications" -Headers $headers
```

Expect `notifications` (may be empty until a payment is submitted).
