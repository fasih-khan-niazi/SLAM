const SystemConfig = require('../models/SystemConfig')

const DEFAULT_MERCHANT = '03300490019'

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
  emergency_interval_minutes: 60,
  easypaisa_account: DEFAULT_MERCHANT,
  jazzcash_account: DEFAULT_MERCHANT,
}

function clampInt(value, fallback, min, max) {
  const n = Number(value)
  if (!Number.isFinite(n)) return fallback
  return Math.min(max, Math.max(min, Math.round(n)))
}

function normalizeMerchant(value, fallback) {
  const raw = String(value || '').trim()
  if (!raw) return fallback
  const digits = raw.replace(/\D/g, '')
  if (digits.length < 10 || digits.length > 15) return fallback
  return digits
}

function envMerchant(primaryKey, fallbackKey) {
  return process.env[primaryKey] || process.env[fallbackKey] || DEFAULT_MERCHANT
}

function publicFields(row) {
  let pinMin = clampInt(row.pin_min_length, 4, 4, 8)
  let pinMax = clampInt(row.pin_max_length, 6, 4, 8)
  if (pinMax < pinMin) pinMax = pinMin

  const easyFallback = envMerchant('EASYPAY_ACCOUNT', 'PAYMENT_ACCOUNT')
  const jazzFallback = envMerchant('JAZZCASH_ACCOUNT', 'PAYMENT_ACCOUNT')

  return {
    sms_prefix: row.sms_prefix || DEFAULTS.sms_prefix,
    pin_min_length: pinMin,
    pin_max_length: pinMax,
    pin_attempt_cap: clampInt(row.pin_attempt_cap, 3, 1, 30),
    pin_window_minutes: clampInt(row.pin_window_minutes, 15, 1, 1440),
    login_attempt_cap: clampInt(row.login_attempt_cap, 3, 1, 30),
    login_window_minutes: clampInt(row.login_window_minutes, 15, 1, 1440),
    maintenance: Boolean(row.maintenance),
    payments_enabled: Boolean(row.payments_enabled),
    maps_enabled: Boolean(row.maps_enabled),
    email_enabled: Boolean(row.email_enabled),
    emergency_enabled: row.emergency_enabled !== false,
    emergency_interval_minutes: resolveEmergencyMinutes(row),
    easypaisa_account: normalizeMerchant(row.easypaisa_account, easyFallback),
    jazzcash_account: normalizeMerchant(row.jazzcash_account, jazzFallback),
  }
}

function resolveEmergencyMinutes(row) {
  if (row.emergency_interval_minutes != null && row.emergency_interval_minutes !== '') {
    return clampInt(row.emergency_interval_minutes, 60, 5, 1440)
  }
  // Legacy hours column → minutes
  if (row.emergency_interval_hours != null && row.emergency_interval_hours !== '') {
    return clampInt(Number(row.emergency_interval_hours) * 60, 60, 5, 1440)
  }
  return 60
}

async function getSystemConfig() {
  const row = await SystemConfig.findOne({ order: [['id', 'ASC']] })
  if (!row) {
    return publicFields({
      ...DEFAULTS,
      easypaisa_account: envMerchant('EASYPAY_ACCOUNT', 'PAYMENT_ACCOUNT'),
      jazzcash_account: envMerchant('JAZZCASH_ACCOUNT', 'PAYMENT_ACCOUNT'),
    })
  }
  return publicFields(row)
}

module.exports = {
  getSystemConfig,
  publicFields,
  DEFAULTS,
  DEFAULT_MERCHANT,
  normalizeMerchant,
  envMerchant,
}
