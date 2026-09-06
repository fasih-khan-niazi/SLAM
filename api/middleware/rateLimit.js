const { fail } = require('../utils/http')

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

    if (buckets.size > 2000) {
      for (const [bucketKey, times] of buckets) {
        const kept = times.filter((time) => time > cutoff)
        if (kept.length) buckets.set(bucketKey, kept)
        else buckets.delete(bucketKey)
      }
    }

    next()
  }
}

module.exports = { rateLimit }
