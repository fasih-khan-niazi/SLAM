const express = require('express')
const { Op } = require('sequelize')
const { protect } = require('../middleware/auth')
const {
  Subscription,
  SubscriptionPlan,
  LocationLog,
} = require('../models')

const router = express.Router()

async function getActivePlanInfo(user_id) {
  const subscription = await Subscription.findOne({
    where: { user_id, status: 'active' },
    include: [{ model: SubscriptionPlan, as: 'plan' }],
    order: [['createdAt', 'DESC']],
  })

  if (subscription) {
    return {
      subscription,
      plan: subscription.plan,
      monthly_limit: subscription.plan.monthly_limit,
      requests_used: subscription.requests_used,
      has_history: subscription.plan.has_history,
      plan_name: subscription.plan.name,
    }
  }

  const freePlan = await SubscriptionPlan.findOne({ where: { name: 'Free' } })
  const startOfMonth = new Date()
  startOfMonth.setDate(1)
  startOfMonth.setHours(0, 0, 0, 0)

  const requests_used = await LocationLog.count({
    where: {
      user_id,
      createdAt: { [Op.gte]: startOfMonth },
    },
  })

  return {
    subscription: null,
    plan: freePlan,
    monthly_limit: freePlan ? freePlan.monthly_limit : 5,
    requests_used,
    has_history: false,
    plan_name: 'Free',
  }
}

router.get('/location/can-request', protect, async (req, res) => {
  try {
    const info = await getActivePlanInfo(req.user.id)

    if (info.monthly_limit === null) {
      return res.status(200).json({
        success: true,
        message: 'Request allowed',
        data: {
          allowed: true,
          requests_used: info.requests_used,
          requests_remaining: null,
          plan_name: info.plan_name,
        },
      })
    }

    const allowed = info.requests_used < info.monthly_limit

    return res.status(200).json({
      success: true,
      message: allowed ? 'Request allowed' : 'Monthly limit reached',
      data: {
        allowed,
        requests_used: info.requests_used,
        requests_remaining: Math.max(0, info.monthly_limit - info.requests_used),
        plan_name: info.plan_name,
      },
    })
  } catch (err) {
    console.error('Can-request error:', err)
    return res.status(500).json({
      success: false,
      message: 'Unable to check request limit',
      data: null,
    })
  }
})

router.post('/location/log', protect, async (req, res) => {
  try {
    const { latitude, longitude, accuracy, requested_by } = req.body
    const user_id = req.user.id

    if (latitude === undefined || longitude === undefined || !requested_by) {
      return res.status(400).json({
        success: false,
        message: 'latitude, longitude and requested_by are required',
        data: null,
      })
    }

    const validAccuracy = ['HIGH', 'MEDIUM', 'LOW']
    const finalAccuracy = validAccuracy.includes(accuracy) ? accuracy : 'LOW'

    await LocationLog.create({
      user_id,
      latitude,
      longitude,
      accuracy: finalAccuracy,
      requested_by,
    })

    const info = await getActivePlanInfo(user_id)
    let requests_used

    if (info.subscription) {
      await info.subscription.increment('requests_used')
      requests_used = info.requests_used + 1
    } else {
      requests_used = info.requests_used
    }

    const monthly_limit = info.monthly_limit
    const requests_remaining = monthly_limit === null
      ? null
      : Math.max(0, monthly_limit - requests_used)
    const limit_reached = monthly_limit !== null && requests_used >= monthly_limit

    return res.status(200).json({
      success: true,
      message: limit_reached
        ? 'Location logged. Monthly limit reached.'
        : 'Location logged',
      data: {
        requests_used,
        requests_remaining,
        limit_reached,
      },
    })
  } catch (err) {
    console.error('Location log error:', err)
    return res.status(500).json({
      success: false,
      message: 'Unable to log location',
      data: null,
    })
  }
})

router.get('/location/history', protect, async (req, res) => {
  try {
    const info = await getActivePlanInfo(req.user.id)

    if (!info.has_history) {
      return res.status(403).json({
        success: false,
        message: 'Location history is available on the Premium plan.',
        data: null,
      })
    }

    const logs = await LocationLog.findAll({
      where: { user_id: req.user.id },
      order: [['createdAt', 'DESC']],
      limit: 100,
    })

    return res.status(200).json({
      success: true,
      message: 'Location history fetched',
      data: { logs },
    })
  } catch (err) {
    console.error('Location history error:', err)
    return res.status(500).json({
      success: false,
      message: 'Unable to load history',
      data: null,
    })
  }
})

module.exports = router
