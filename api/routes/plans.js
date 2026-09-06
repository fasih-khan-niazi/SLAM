const express = require('express')
const { protect } = require('../middleware/auth')
const { SubscriptionPlan, Subscription } = require('../models')
const { ok, fail } = require('../utils/http')
const {
  addDays,
  formatSubscription,
  getCurrentSubscription,
  getActivePlanInfo,
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

    const plan = await SubscriptionPlan.findByPk(plan_id)
    if (!plan || !plan.is_active) {
      return fail(res, 404, 'Plan not found')
    }

    const pending = await Subscription.findOne({
      where: {
        user_id,
        status: ['pending_payment', 'pending_approval'],
      },
    })

    if (pending) {
      return fail(res, 400, 'You already have a subscription waiting for payment.')
    }

    const active = await Subscription.findOne({
      where: { user_id, status: 'active' },
      include: [{ model: SubscriptionPlan, as: 'plan' }],
    })

    const isFree = plan.price_pkr === 0

    if (isFree) {
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

    if (active && active.plan && active.plan.price_pkr > 0) {
      return fail(res, 400, 'You already have an active paid plan.')
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
      'Subscription created. Complete payment to activate.',
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

router.get('/user/subscription', protect, async (req, res) => {
  try {
    const current = await getCurrentSubscription(req.user.id)

    if (current && current.status !== 'active') {
      const activeInfo = await getActivePlanInfo(req.user.id)
      return ok(res, 'Subscription fetched', {
        ...formatSubscription(current, current.plan, current.requests_used),
        active_plan: formatSubscription(
          activeInfo.subscription,
          activeInfo.plan,
          activeInfo.requests_used
        ),
      })
    }

    if (current) {
      return ok(
        res,
        'Subscription fetched',
        formatSubscription(current, current.plan, current.requests_used)
      )
    }

    const info = await getActivePlanInfo(req.user.id)
    return ok(
      res,
      'Free plan active',
      formatSubscription(info.subscription, info.plan, info.requests_used)
    )
  } catch (err) {
    console.error('Get subscription error:', err)
    return fail(res, 500, 'Unable to load subscription')
  }
})

module.exports = router
