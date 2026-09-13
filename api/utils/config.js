const SystemConfig = require('../models/SystemConfig')

const DEFAULTS = {
  sms_prefix: 'SLAM',
  pin_min_length: 4,
  pin_max_length: 6,
  pin_attempt_cap: 3,
  pin_window_minutes: 15,
  login_attempt_cap: 3,
  login_window_minutes: 15,
  maintenance: false,
  payments_enabled: true,
  maps_enabled: false,
  email_enabled: true,
  emergency_enabled: true,
  emergency_interval_hours: 1,
}

function clampInt(value, fallback, min, max) {
  const n = Number(value)
  if (!Number.isFinite(n)) return fallback
  return Math.min(max, Math.max(min, Math.round(n)))
}

function publicFields(row) {
  return {
    sms_prefix: row.sms_prefix || DEFAULTS.sms_prefix,
    pin_min_length: clampInt(row.pin_min_length, 4, 4, 8),
    pin_max_length: clampInt(row.pin_max_length, 6, 4, 8),
    pin_attempt_cap: clampInt(row.pin_attempt_cap, 3, 1, 30),
    pin_window_minutes: clampInt(row.pin_window_minutes, 15, 1, 1440),
    login_attempt_cap: clampInt(row.login_attempt_cap, 3, 1, 30),
    login_window_minutes: clampInt(row.login_window_minutes, 15, 1, 1440),
    maintenance: Boolean(row.maintenance),
    payments_enabled: Boolean(row.payments_enabled),
    maps_enabled: Boolean(row.maps_enabled),
    email_enabled: Boolean(row.email_enabled),
    emergency_enabled: row.emergency_enabled !== false,
    emergency_interval_hours: clampInt(row.emergency_interval_hours, 1, 1, 24),
    easypaisa_account: process.env.EASYPAY_ACCOUNT || process.env.PAYMENT_ACCOUNT || '03300490019',
    jazzcash_account: process.env.JAZZCASH_ACCOUNT || process.env.PAYMENT_ACCOUNT || '03300490019',
  }
}

async function getSystemConfig() {
  const row = await SystemConfig.findOne({ order: [['id', 'ASC']] })
  if (!row) return { ...DEFAULTS }
  return publicFields(row)
}

module.exports = { getSystemConfig, publicFields, DEFAULTS }
