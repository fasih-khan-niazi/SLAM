const express = require('express')
const { protect } = require('../middleware/auth')
const { SubscriptionPlan, Subscription } = require('../models')
const { getSystemConfig } = require('../utils/config')
const { ok, fail } = require('../utils/http')
const {
  addDays,
  buildSubscriptionPayload,
  ensureActiveSubscription,
} = require('../utils/subscription')

const router = express.Router()

router.get('/plans', async (req, res) => {
  try {
    const plans = await SubscriptionPlan.findAll({
      where: { is_active: true },
      order: [['price_pkr', 'ASC']],
    })

    return ok(res, 'Plans fetched', { plans })
  } catch (err) {
    console.error('Get plans error:', err)
    return fail(res, 500, 'Unable to load plans')
  }
})

router.post('/subscribe', protect, async (req, res) => {
  try {
    const { plan_id } = req.body
    const user_id = req.user.id

    if (!plan_id) return fail(res, 400, 'plan_id is required')

    const config = await getSystemConfig()
    if (config.maintenance) {
      return fail(res, 503, 'Service is paused for maintenance')
    }

    const plan = await SubscriptionPlan.findByPk(plan_id)
    if (!plan || !plan.is_active) {
      return fail(res, 404, 'Plan not found')
    }

    const pending = await Subscription.findOne({
      where: {
        user_id,
        status: ['pending_payment', 'pending_approval'],
      },
      include: [{ model: SubscriptionPlan, as: 'plan' }],
      order: [['createdAt', 'DESC']],
    })

    const active = await ensureActiveSubscription(user_id)
    const isFree = plan.price_pkr === 0
    const activePrice = active?.plan ? active.plan.price_pkr : 0

    if (isFree) {
      if (active && active.plan && active.plan.price_pkr === 0) {
        return fail(res, 400, 'You already have an active Free plan.')
      }
      if (active) {
        return fail(res, 400, 'You already have an active plan.')
      }

      const today = new Date()
      const subscription = await Subscription.create({
        user_id,
        plan_id: plan.id,
        status: 'active',
        requests_used: 0,
        start_date: today,
        end_date: addDays(today, 30),
      })

      return ok(
        res,
        'Free plan activated',
        {
          subscription_id: subscription.id,
          plan_name: plan.name,
          amount_pkr: 0,
          status: subscription.status,
        },
        201
      )
    }

    if (!config.payments_enabled) {
      return fail(res, 503, 'Payments are paused right now')
    }

    if (active && active.plan_id === plan.id) {
      return fail(res, 400, 'This plan is already active on your account.')
    }

    // Allow upgrade to a higher paid plan while keeping current until approval.
    if (active && activePrice > 0) {
      if (plan.price_pkr <= activePrice) {
        return fail(
          res,
          400,
          'Cancel your current plan at period end, or choose a higher plan to upgrade.'
        )
      }
    }

    if (pending) {
      if (pending.plan_id === plan.id) {
        return ok(res, 'Continue payment for this plan', {
          subscription_id: pending.id,
          plan_name: pending.plan ? pending.plan.name : plan.name,
          amount_pkr: pending.plan ? pending.plan.price_pkr : plan.price_pkr,
          status: pending.status,
        })
      }
      return fail(
        res,
        400,
        `Finish or wait out your pending ${pending.plan ? pending.plan.name : 'plan'} payment before choosing another plan.`
      )
    }

    const subscription = await Subscription.create({
      user_id,
      plan_id: plan.id,
      status: 'pending_payment',
      requests_used: 0,
      start_date: null,
      end_date: null,
    })

    return ok(
      res,
      'Subscription created. Complete payment to activate. Your current plan stays active until an admin approves.',
      {
        subscription_id: subscription.id,
        plan_name: plan.name,
        amount_pkr: plan.price_pkr,
        status: subscription.status,
      },
      201
    )
  } catch (err) {
    console.error('Subscribe error:', err)
    return fail(res, 500, 'Unable to create subscription')
  }
})

router.post('/subscribe/cancel', protect, async (req, res) => {
  try {
    const active = await ensureActiveSubscription(req.user.id)
    if (!active || !active.plan) {
      return fail(res, 400, 'No active subscription to cancel')
    }
    if (active.plan.price_pkr === 0) {
      return fail(res, 400, 'The Free plan cannot be cancelled')
    }

    await active.update({ cancel_at_period_end: true })
    const payload = await buildSubscriptionPayload(req.user.id)
    return ok(res, 'Your plan will end on the renewal date. You keep access until then.', payload)
  } catch (err) {
    console.error('Cancel subscription error:', err)
    return fail(res, 500, 'Unable to cancel subscription')
  }
})

router.post('/subscribe/resume', protect, async (req, res) => {
  try {
    const active = await ensureActiveSubscription(req.user.id)
    if (!active || !active.cancel_at_period_end) {
      return fail(res, 400, 'No scheduled cancellation to undo')
    }
    await active.update({ cancel_at_period_end: false })
    const payload = await buildSubscriptionPayload(req.user.id)
    return ok(res, 'Cancellation undone. Your plan will renew as usual.', payload)
  } catch (err) {
    console.error('Resume subscription error:', err)
    return fail(res, 500, 'Unable to resume subscription')
  }
})

router.get('/user/subscription', protect, async (req, res) => {
  try {
    const payload = await buildSubscriptionPayload(req.user.id)
    return ok(res, 'Subscription fetched', payload)
  } catch (err) {
    console.error('Get subscription error:', err)
    return fail(res, 500, 'Unable to load subscription')
  }
})

module.exports = router
