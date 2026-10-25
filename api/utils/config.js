const SystemConfig = require('../models/SystemConfig')

const DEFAULT_MERCHANT = '03300490019'

const PIN_ATTEMPT_CAP_MIN = 1
const PIN_ATTEMPT_CAP_MAX = 5
const EMERGENCY_MINUTES_MIN = 5
const EMERGENCY_MINUTES_MAX = 1440
// WorkManager's periodic jobs cannot repeat faster than 15 minutes, so this is a hard floor.
const QUIET_LOCATION_MINUTES_MIN = 15
const QUIET_LOCATION_MINUTES_MAX = 1440

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
  quiet_location_interval_minutes: 15,
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
    pin_attempt_cap: clampInt(row.pin_attempt_cap, 3, PIN_ATTEMPT_CAP_MIN, PIN_ATTEMPT_CAP_MAX),
    pin_window_minutes: clampInt(row.pin_window_minutes, 15, 1, 1440),
    login_attempt_cap: clampInt(row.login_attempt_cap, 3, 1, 30),
    login_window_minutes: clampInt(row.login_window_minutes, 15, 1, 1440),
    maintenance: Boolean(row.maintenance),
    payments_enabled: Boolean(row.payments_enabled),
    maps_enabled: Boolean(row.maps_enabled),
    email_enabled: Boolean(row.email_enabled),
    emergency_enabled: row.emergency_enabled !== false,
    emergency_interval_minutes: resolveEmergencyMinutes(row),
    quiet_location_interval_minutes: clampInt(
      row.quiet_location_interval_minutes,
      15,
      QUIET_LOCATION_MINUTES_MIN,
      QUIET_LOCATION_MINUTES_MAX,
    ),
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

// Allowed whole-number ranges for admin-editable settings.
const INTEGER_RULES = {
  pin_min_length: { min: 4, max: 8, label: 'pin_min_length' },
  pin_max_length: { min: 4, max: 8, label: 'pin_max_length' },
  pin_attempt_cap: { min: PIN_ATTEMPT_CAP_MIN, max: PIN_ATTEMPT_CAP_MAX, label: 'pin_attempt_cap' },
  pin_window_minutes: { min: 1, max: 1440, label: 'pin_window_minutes' },
  login_attempt_cap: { min: 1, max: 30, label: 'login_attempt_cap' },
  login_window_minutes: { min: 1, max: 1440, label: 'login_window_minutes' },
  emergency_interval_minutes: {
    min: EMERGENCY_MINUTES_MIN,
    max: EMERGENCY_MINUTES_MAX,
    label: 'emergency_interval_minutes',
  },
  quiet_location_interval_minutes: {
    min: QUIET_LOCATION_MINUTES_MIN,
    max: QUIET_LOCATION_MINUTES_MAX,
    label: 'quiet_location_interval_minutes',
  },
}

function isIntegerInRange(value, min, max) {
  if (value === '' || value === null || typeof value === 'boolean') return false
  const n = Number(value)
  return Number.isInteger(n) && n >= min && n <= max
}

/** Returns human-readable problems; empty when the admin patch is acceptable. */
function validateConfigPatch(body) {
  const errors = []
  for (const [key, rule] of Object.entries(INTEGER_RULES)) {
    if (body[key] != null && !isIntegerInRange(body[key], rule.min, rule.max)) {
      errors.push(`${rule.label} must be a whole number from ${rule.min} to ${rule.max}`)
    }
  }
  if (body.sms_prefix != null && !/^[A-Za-z0-9]{2,12}$/.test(String(body.sms_prefix).trim())) {
    errors.push('sms_prefix must be 2 to 12 letters or digits')
  }
  return errors
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
  validateConfigPatch,
  DEFAULTS,
  DEFAULT_MERCHANT,
  normalizeMerchant,
  envMerchant,
}
