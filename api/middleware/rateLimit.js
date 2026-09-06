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

async function loginAttemptGuard(req, res, next) {
  try {
    const config = await getSystemConfig()
    const max = config.login_attempt_cap
    const windowMs = config.login_window_minutes * 60 * 1000
    if (loginFailures.blocked(req.ip, max, windowMs)) {
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
  loginFailures.record(req.ip, windowMs)
}

function clearFailedLogins(req) {
  loginFailures.clear(req.ip)
}

module.exports = {
  rateLimit,
  loginAttemptGuard,
  recordFailedLogin,
  clearFailedLogins,
}
