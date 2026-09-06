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

Expect `sms_prefix: SLAM`, `maintenance: false`, and `payments_enabled: true`. These come from the `system_config` row (AdminJS → SystemConfigs).

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
  latitude = 33.6844
  longitude = 73.0479
  accuracy = "HIGH"
  requested_by = "03009876543"
} | ConvertTo-Json)
```

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

## Admin panel

Browser: http://localhost:3000/admin  
`admin@slam.com` / `Password123`

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

Then `/admin` → Payments → open the record (Cloudinary URL) → Approve. User gets email plus an in-app notification; plan becomes active.

`GET /api/payments/my` lists that user’s history.

## 12. Notifications (Phase 11)

```powershell
Invoke-RestMethod "$base/api/notifications" -Headers $headers
```

Expect `notifications` (may be empty until a payment is submitted).
