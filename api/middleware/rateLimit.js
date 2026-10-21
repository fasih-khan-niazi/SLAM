const { fail } = require('../utils/http')
const { getSystemConfig } = require('../utils/config')

function rateLimit({ windowMs, max, message }) {
  const buckets = new Map()

  return (req, res, next) => {
    const key = `${req.ip}|${req.path}`
    const now = Date.now()
    const cutoff = now - windowMs
    const stamps = (buckets.get(key) || []).filter((time) => time > cutoff)

    if (stamps.length >= max) {
      return fail(res, 429, message)
    }

    stamps.push(now)
    buckets.set(key, stamps)
    next()
  }
}

function createFailureStore() {
  const buckets = new Map()

  function prune(key, windowMs) {
    const cutoff = Date.now() - windowMs
    const stamps = (buckets.get(key) || []).filter((time) => time > cutoff)
    if (stamps.length) buckets.set(key, stamps)
    else buckets.delete(key)
    return stamps
  }

  return {
    blocked(key, max, windowMs) {
      return prune(key, windowMs).length >= max
    },
    record(key, windowMs) {
      const stamps = prune(key, windowMs)
      stamps.push(Date.now())
      buckets.set(key, stamps)
    },
    clear(key) {
      buckets.delete(key)
    },
  }
}

const loginFailures = createFailureStore()

/**
 * Failed sign-ins are counted per device, so people who share a network (a mobile carrier,
 * a home, an office) never lock each other out. The app and the web portal send a random
 * X-Device-Id. A client that sends none is counted by IP at the same cap, and every client
 * also faces a much higher per-IP ceiling so rotating device ids does not help an attacker.
 */
const IP_CEILING_MULTIPLIER = 10

function deviceIdFrom(req) {
  const raw = String((req.get && req.get('x-device-id')) || '').trim()
  return /^[A-Za-z0-9_-]{8,64}$/.test(raw) ? raw : ''
}

function loginKeys(req) {
  const device = deviceIdFrom(req)
  return {
    device: device ? `device:${device}` : '',
    ip: `ip:${req.ip}`,
  }
}

async function loginAttemptGuard(req, res, next) {
  try {
    const config = await getSystemConfig()
    const max = config.login_attempt_cap
    const windowMs = config.login_window_minutes * 60 * 1000
    const keys = loginKeys(req)
    const blocked = keys.device
      ? loginFailures.blocked(keys.device, max, windowMs) ||
        loginFailures.blocked(keys.ip, max * IP_CEILING_MULTIPLIER, windowMs)
      : loginFailures.blocked(keys.ip, max, windowMs)
    if (blocked) {
      return fail(
        res,
        429,
        `Too many sign-in attempts. Try again in ${config.login_window_minutes} minutes.`
      )
    }
    req.loginLimit = { max, windowMs }
    return next()
  } catch (err) {
    console.error('Login limit error:', err)
    return next()
  }
}

function recordFailedLogin(req) {
  const windowMs = req.loginLimit?.windowMs || 15 * 60 * 1000
  const keys = loginKeys(req)
  if (keys.device) loginFailures.record(keys.device, windowMs)
  loginFailures.record(keys.ip, windowMs)
}

function clearFailedLogins(req) {
  const keys = loginKeys(req)
  // A successful sign-in clears that device only. The shared per-IP ceiling is left to expire
  // so one valid login cannot reset it for an attacker on the same network.
  if (keys.device) loginFailures.clear(keys.device)
  else loginFailures.clear(keys.ip)
}

module.exports = {
  deviceIdFrom,
  loginKeys,
  rateLimit,
  loginAttemptGuard,
  recordFailedLogin,
  clearFailedLogins,
}
