const express = require('express')
const { protect } = require('../middleware/auth')
const { LocationLog } = require('../models')
const { ok, fail } = require('../utils/http')
const { getActivePlanInfo } = require('../utils/subscription')

const router = express.Router()

router.get('/location/can-request', protect, async (req, res) => {
  try {
    const info = await getActivePlanInfo(req.user.id)

    if (info.monthly_limit === null) {
      return ok(res, 'Request allowed', {
        allowed: true,
        requests_used: info.requests_used,
        requests_remaining: null,
        plan_name: info.plan_name,
      })
    }

    const allowed = info.requests_used < info.monthly_limit

    return ok(res, allowed ? 'Request allowed' : 'Monthly limit reached', {
      allowed,
      requests_used: info.requests_used,
      requests_remaining: Math.max(0, info.monthly_limit - info.requests_used),
      plan_name: info.plan_name,
    })
  } catch (err) {
    console.error('Can-request error:', err)
    return fail(res, 500, 'Unable to check request limit')
  }
})

router.post('/location/log', protect, async (req, res) => {
  try {
    const { latitude, longitude, accuracy, requested_by } = req.body
    const user_id = req.user.id

    if (latitude === undefined || longitude === undefined || !requested_by) {
      return fail(res, 400, 'latitude, longitude and requested_by are required')
    }

    const lat = Number(latitude)
    const lng = Number(longitude)
    if (Number.isNaN(lat) || Number.isNaN(lng)) {
      return fail(res, 400, 'latitude and longitude must be numbers')
    }

    const info = await getActivePlanInfo(user_id)
    if (info.monthly_limit !== null && info.requests_used >= info.monthly_limit) {
      return fail(res, 403, 'Monthly limit reached. Upgrade to continue.')
    }

    const validAccuracy = ['HIGH', 'MEDIUM', 'LOW']
    const finalAccuracy = validAccuracy.includes(accuracy) ? accuracy : 'LOW'

    await LocationLog.create({
      user_id,
      latitude: lat,
      longitude: lng,
      accuracy: finalAccuracy,
      requested_by: String(requested_by).trim(),
    })

    let requests_used
    if (info.subscription) {
      await info.subscription.increment('requests_used')
      requests_used = info.requests_used + 1
    } else {
      const refreshed = await getActivePlanInfo(user_id)
      requests_used = refreshed.requests_used
    }

    const monthly_limit = info.monthly_limit
    const requests_remaining = monthly_limit === null
      ? null
      : Math.max(0, monthly_limit - requests_used)
    const limit_reached = monthly_limit !== null && requests_used >= monthly_limit

    return ok(
      res,
      limit_reached ? 'Location logged. Monthly limit reached.' : 'Location logged',
      { requests_used, requests_remaining, limit_reached }
    )
  } catch (err) {
    console.error('Location log error:', err)
    return fail(res, 500, 'Unable to log location')
  }
})

router.get('/location/history', protect, async (req, res) => {
  try {
    const info = await getActivePlanInfo(req.user.id)

    if (!info.has_history) {
      return fail(res, 403, 'Location history is available on the Premium plan.')
    }

    const logs = await LocationLog.findAll({
      where: { user_id: req.user.id },
      order: [['createdAt', 'DESC']],
      limit: 100,
    })

    return ok(res, 'Location history fetched', { logs })
  } catch (err) {
    console.error('Location history error:', err)
    return fail(res, 500, 'Unable to load history')
  }
})

module.exports = router
