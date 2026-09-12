const express = require('express')
const { Op } = require('sequelize')
const { protect } = require('../middleware/auth')
const {
  sequelize,
  User,
  Subscription,
  SubscriptionPlan,
  LocationLog,
} = require('../models')
const { ok, fail } = require('../utils/http')
const { addDays, getActivePlanInfo, getFreePlan } = require('../utils/subscription')
const { validateLocationEvent, usagePayload } = require('../utils/locationEvent')

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

    return ok(res, allowed ? 'Request allowed' : 'Plan-period limit reached', {
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
  const validated = validateLocationEvent(req.body)
  if (validated.error) {
    return fail(res, 400, validated.error)
  }

  try {
    const user_id = req.user.id
    const result = await sequelize.transaction(async (transaction) => {
      // The user lock also serializes legacy accounts without a subscription.
      // Active subscriptions receive their own required quota-row lock.
      await User.findByPk(user_id, {
        transaction,
        lock: transaction.LOCK.UPDATE,
      })

      const subscription = await Subscription.findOne({
        where: { user_id, status: 'active' },
        order: [['createdAt', 'DESC']],
        transaction,
        lock: transaction.LOCK.UPDATE,
      })
      const plan = subscription
        ? await SubscriptionPlan.findByPk(subscription.plan_id, { transaction })
        : await getFreePlan(transaction)
      const monthlyLimit = plan ? plan.monthly_limit : 5
      const legacyPeriodStart = new Date()
      legacyPeriodStart.setDate(1)
      legacyPeriodStart.setHours(0, 0, 0, 0)
      const legacyUsageWhere = {
        user_id,
        createdAt: { [Op.gte]: legacyPeriodStart },
      }

      if (subscription && monthlyLimit !== null && subscription.end_date) {
        const end = new Date(subscription.end_date)
        if (!Number.isNaN(end.getTime()) && Date.now() > end.getTime()) {
          const today = new Date()
          await subscription.update({
            requests_used: 0,
            start_date: today,
            end_date: addDays(today, 30),
          }, { transaction })
        }
      }

      const existing = await LocationLog.findOne({
        where: { event_id: validated.value.event_id },
        transaction,
      })
      if (existing) {
        if (existing.user_id !== user_id) {
          const conflict = new Error('event_id is already used by another account')
          conflict.status = 409
          throw conflict
        }

        const requestsUsed = subscription
          ? subscription.requests_used
          : await LocationLog.count({ where: legacyUsageWhere, transaction })
        return usagePayload(existing, requestsUsed, monthlyLimit, true)
      }

      const requestsUsed = subscription
        ? subscription.requests_used
        : await LocationLog.count({ where: legacyUsageWhere, transaction })
      if (monthlyLimit !== null && requestsUsed >= monthlyLimit) {
        const limit = new Error('Plan-period limit reached. Upgrade to continue.')
        limit.status = 403
        throw limit
      }

      const event = await LocationLog.create({
        ...validated.value,
        user_id,
        captured_at: validated.value.captured_at || new Date(),
      }, { transaction })
      const nextRequestsUsed = requestsUsed + 1

      if (subscription) {
        await subscription.update(
          { requests_used: nextRequestsUsed },
          { transaction }
        )
      }

      return usagePayload(event, nextRequestsUsed, monthlyLimit, false)
    })

    return ok(
      res,
      result.idempotent
        ? 'Location event already logged'
        : result.limit_reached
          ? 'Location logged. Plan-period limit reached.'
          : 'Location logged',
      result
    )
  } catch (err) {
    console.error('Location log error:', err)
    if (err.status) return fail(res, err.status, err.message)
    if (err.name === 'SequelizeUniqueConstraintError') {
      return fail(res, 409, 'event_id has already been used')
    }
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

// Lightweight activity feed for all plans (restore after reinstall).
router.get('/location/activity', protect, async (req, res) => {
  try {
    const logs = await LocationLog.findAll({
      where: { user_id: req.user.id },
      order: [['createdAt', 'DESC']],
      limit: 40,
    })
    const normalized = logs.map((log) => {
      const row = typeof log.toJSON === 'function' ? log.toJSON() : log
      return {
        ...row,
        latitude: Number(row.latitude),
        longitude: Number(row.longitude),
        accuracy_meters:
          row.accuracy_meters == null || row.accuracy_meters === ''
            ? null
            : Number(row.accuracy_meters),
      }
    })
    return ok(res, 'Activity fetched', { logs: normalized })
  } catch (err) {
    console.error('Location activity error:', err)
    return fail(res, 500, 'Unable to load activity')
  }
})

module.exports = router
