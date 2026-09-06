const SystemConfig = require('../models/SystemConfig')

const DEFAULTS = {
  sms_prefix: 'SLAM',
  pin_min_length: 4,
  pin_max_length: 6,
  pin_attempt_cap: 8,
  maintenance: false,
  payments_enabled: true,
  maps_enabled: false,
  email_enabled: true,
}

function publicFields(row) {
  return {
    sms_prefix: row.sms_prefix,
    pin_min_length: row.pin_min_length,
    pin_max_length: row.pin_max_length,
    pin_attempt_cap: row.pin_attempt_cap,
    maintenance: Boolean(row.maintenance),
    payments_enabled: Boolean(row.payments_enabled),
    maps_enabled: Boolean(row.maps_enabled),
    email_enabled: Boolean(row.email_enabled),
  }
}

async function getSystemConfig() {
  const row = await SystemConfig.findOne({ order: [['id', 'ASC']] })
  if (!row) return { ...DEFAULTS }
  return publicFields(row)
}

async function seedSystemConfig() {
  const count = await SystemConfig.count()
  if (count > 0) return
  await SystemConfig.create(DEFAULTS)
}

module.exports = { getSystemConfig, seedSystemConfig, publicFields }
