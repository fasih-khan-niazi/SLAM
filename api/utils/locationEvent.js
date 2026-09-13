const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i
const LOCATION_SOURCES = new Set(['CURRENT', 'LAST_KNOWN'])
const ACCURACY_LABELS = new Set(['HIGH', 'MEDIUM', 'LOW'])

function parseFiniteNumber(value) {
  if (value === '' || value === null || value === undefined) return null
  const number = Number(value)
  return Number.isFinite(number) ? number : null
}

function sanitizeRequester(value) {
  const raw = String(value || '').trim()
  if (raw.toLowerCase() === 'emergency') return 'emergency'
  const digits = raw.replace(/\D/g, '')
  return digits ? `sms:***${digits.slice(-4)}` : 'sms'
}

function validateLocationEvent(body = {}) {
  const event_id = String(body.event_id || '').trim().toLowerCase()
  if (!UUID_RE.test(event_id)) {
    return { error: 'event_id must be a valid UUID' }
  }

  const latitude = parseFiniteNumber(body.latitude)
  const longitude = parseFiniteNumber(body.longitude)
  if (latitude === null || longitude === null) {
    return { error: 'latitude and longitude must be finite numbers' }
  }
  if (latitude < -90 || latitude > 90) {
    return { error: 'latitude must be between -90 and 90' }
  }
  if (longitude < -180 || longitude > 180) {
    return { error: 'longitude must be between -180 and 180' }
  }

  const requestedByRaw = String(body.requested_by || '').trim()
  if (!requestedByRaw) {
    return { error: 'requested_by is required' }
  }
  const requested_by = sanitizeRequester(requestedByRaw)

  const source = String(body.source || '').trim().toUpperCase().replace(/[\s-]+/g, '_')
  if (!LOCATION_SOURCES.has(source)) {
    return { error: 'source must be CURRENT or LAST_KNOWN' }
  }

  let accuracy = String(body.accuracy || '').trim().toUpperCase()
  let accuracy_meters = parseFiniteNumber(body.accuracy_meters)
  if (body.accuracy_meters !== undefined && accuracy_meters === null) {
    return { error: 'accuracy_meters must be a finite number' }
  }

  // New Android clients may send numeric accuracy in `accuracy`; old clients
  // used HIGH/MEDIUM/LOW. Accept both without changing the legacy column.
  if (body.accuracy !== undefined && !ACCURACY_LABELS.has(accuracy)) {
    const numericAccuracy = parseFiniteNumber(body.accuracy)
    if (numericAccuracy === null) {
      return { error: 'accuracy must be HIGH, MEDIUM, LOW, or a number of metres' }
    }
    if (accuracy_meters === null) accuracy_meters = numericAccuracy
    accuracy = 'LOW'
  }
  if (!ACCURACY_LABELS.has(accuracy)) accuracy = 'LOW'
  if (accuracy_meters !== null && accuracy_meters < 0) {
    return { error: 'accuracy_meters cannot be negative' }
  }

  let captured_at = null
  if (body.captured_at !== undefined && body.captured_at !== null && body.captured_at !== '') {
    captured_at = new Date(body.captured_at)
    if (Number.isNaN(captured_at.getTime())) {
      return { error: 'captured_at must be a valid date-time' }
    }
  }

  const provider = body.provider === undefined || body.provider === null
    ? null
    : String(body.provider).trim().slice(0, 32) || null

  return {
    value: {
      event_id,
      latitude,
      longitude,
      accuracy,
      accuracy_meters,
      requested_by,
      source,
      provider,
      captured_at,
    },
  }
}

function usagePayload(event, requestsUsed, monthlyLimit, idempotent, planMeta = {}) {
  return {
    event,
    idempotent,
    requests_used: requestsUsed,
    requests_remaining: monthlyLimit === null
      ? null
      : Math.max(0, monthlyLimit - requestsUsed),
    limit_reached: monthlyLimit !== null && requestsUsed >= monthlyLimit,
    plan_name: planMeta.plan_name || 'Free',
    monthly_limit: monthlyLimit,
    subscription_id: planMeta.subscription_id || null,
    status: planMeta.status || 'active',
  }
}

module.exports = {
  validateLocationEvent,
  usagePayload,
  sanitizeRequester,
}
