const express = require('express')
const { protect } = require('../middleware/auth')
const { SubscriptionPlan, Subscription } = require('../models')

const router = express.Router()

function addDays(date, days) {
  const next = new Date(date)
  next.setDate(next.getDate() + days)
  return next
}

router.get('/plans', async (req, res) => {
  try {
    const plans = await SubscriptionPlan.findAll({
      where: { is_active: true },
      order: [['price_pkr', 'ASC']],
    })

    return res.status(200).json({
      success: true,
      message: 'Plans fetched',
      data: { plans },
    })
  } catch (err) {
    console.error('Get plans error:', err)
    return res.status(500).json({
      success: false,
      message: 'Unable to load plans',
      data: null,
    })
  }
})

router.post('/subscribe', protect, async (req, res) => {
  try {
    const { plan_id } = req.body
    const user_id = req.user.id

    if (!plan_id) {
      return res.status(400).json({
        success: false,
        message: 'plan_id is required',
        data: null,
      })
    }

    const plan = await SubscriptionPlan.findByPk(plan_id)
    if (!plan) {
      return res.status(404).json({
        success: false,
        message: 'Plan not found',
        data: null,
      })
    }

    const existing = await Subscription.findOne({
      where: {
        user_id,
        status: ['active', 'pending_payment', 'pending_approval'],
      },
    })

    if (existing) {
      return res.status(400).json({
        success: false,
        message: 'You already have an active or pending subscription.',
        data: null,
      })
    }

    const isFree = plan.price_pkr === 0
    const today = new Date()
    const subscription = await Subscription.create({
      user_id,
      plan_id,
      status: isFree ? 'active' : 'pending_payment',
      requests_used: 0,
      start_date: isFree ? today : null,
      end_date: isFree ? addDays(today, 30) : null,
    })

    return res.status(201).json({
      success: true,
      message: isFree
        ? 'Free plan activated'
        : 'Subscription created. Complete payment to activate.',
      data: {
        subscription_id: subscription.id,
        plan_name: plan.name,
        amount_pkr: plan.price_pkr,
        status: subscription.status,
      },
    })
  } catch (err) {
    console.error('Subscribe error:', err)
    return res.status(500).json({
      success: false,
      message: 'Unable to create subscription',
      data: null,
    })
  }
})

router.get('/user/subscription', protect, async (req, res) => {
  try {
    const user_id = req.user.id

    const subscription = await Subscription.findOne({
      where: { user_id, status: ['active', 'pending_payment', 'pending_approval'] },
      include: [{ model: SubscriptionPlan, as: 'plan' }],
      order: [['createdAt', 'DESC']],
    })

    if (!subscription) {
      const freePlan = await SubscriptionPlan.findOne({ where: { name: 'Free' } })

      return res.status(200).json({
        success: true,
        message: 'Free plan active',
        data: {
          plan_name: 'Free',
          plan_id: freePlan ? freePlan.id : null,
          status: 'active',
          price_pkr: 0,
          monthly_limit: freePlan ? freePlan.monthly_limit : 5,
          requests_used: 0,
          requests_remaining: freePlan ? freePlan.monthly_limit : 5,
          max_contacts: freePlan ? freePlan.max_contacts : 1,
          has_history: false,
          start_date: null,
          end_date: null,
        },
      })
    }

    const plan = subscription.plan
    const monthly_limit = plan.monthly_limit
    const requests_used = subscription.requests_used
    const requests_remaining = monthly_limit === null
      ? null
      : Math.max(0, monthly_limit - requests_used)

    return res.status(200).json({
      success: true,
      message: 'Subscription fetched',
      data: {
        plan_name: plan.name,
        plan_id: plan.id,
        status: subscription.status,
        price_pkr: plan.price_pkr,
        monthly_limit,
        requests_used,
        requests_remaining,
        max_contacts: plan.max_contacts,
        has_history: plan.has_history,
        start_date: subscription.start_date,
        end_date: subscription.end_date,
      },
    })
  } catch (err) {
    console.error('Get subscription error:', err)
    return res.status(500).json({
      success: false,
      message: 'Unable to load subscription',
      data: null,
    })
  }
})

module.exports = router
