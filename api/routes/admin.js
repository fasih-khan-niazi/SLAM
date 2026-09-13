const express = require('express')
const { Op } = require('sequelize')
const { protect, requireAdmin } = require('../middleware/auth')
const { ok, fail } = require('../utils/http')
const { approvePayment, rejectPayment } = require('../utils/paymentReview')
const {
  getSystemConfig,
  publicFields,
  normalizeMerchant,
  envMerchant,
  DEFAULTS,
} = require('../utils/config')
const {
  User,
  SubscriptionPlan,
  Subscription,
  Payment,
  LocationLog,
  SystemConfig,
} = require('../models')
const {
  ACCOUNT_ACTIVE,
  ACCOUNT_SUSPENDED,
  ACCOUNT_DEACTIVATED,
  normalizeAccountStatus,
  pauseActiveSubscriptions,
  resumePausedSubscriptions,
} = require('../utils/accountStatus')
const { ensureActiveSubscription } = require('../utils/subscription')

const router = express.Router()

router.use(protect, requireAdmin)

function publicAdminUser(user) {
  return {
    id: user.id,
    name: user.name,
    email: user.email,
    phone: user.phone,
    role: user.role,
    account_status: user.account_status || 'active',
    created_at: user.createdAt,
  }
}

function formatPlan(plan) {
  return {
    id: plan.id,
    name: plan.name,
    price_pkr: plan.price_pkr,
    monthly_limit: plan.monthly_limit,
    max_contacts: plan.max_contacts,
    has_history: Boolean(plan.has_history),
    description: plan.description,
    is_active: Boolean(plan.is_active),
    created_at: plan.createdAt,
    updated_at: plan.updatedAt,
  }
}

async function countFreeActivePlans(excludeId = null) {
  const where = { price_pkr: 0, is_active: true }
  if (excludeId != null) where.id = { [Op.ne]: excludeId }
  return SubscriptionPlan.count({ where })
}

router.get('/stats', async (req, res) => {
  try {
    const [pendingPayments, users, activePlans, config] = await Promise.all([
      Payment.count({ where: { status: 'pending' } }),
      User.count(),
      SubscriptionPlan.count({ where: { is_active: true } }),
      getSystemConfig(),
    ])
    return ok(res, 'Admin stats', {
      pendingPayments,
      users,
      activePlans,
      smsPrefix: config.sms_prefix,
      emergencyEnabled: config.emergency_enabled,
      maintenance: config.maintenance,
      paymentsEnabled: config.payments_enabled,
    })
  } catch (err) {
    console.error('Admin stats error:', err)
    return fail(res, 500, 'Unable to load admin stats')
  }
})

router.get('/payments', async (req, res) => {
  try {
    const where = {}
    if (req.query.status) where.status = String(req.query.status)
    const payments = await Payment.findAll({
      where,
      include: [
        { model: User, attributes: ['id', 'name', 'email'] },
        { model: SubscriptionPlan, as: 'plan', attributes: ['id', 'name', 'price_pkr'] },
      ],
      order: [['createdAt', 'DESC']],
      limit: Math.min(Number(req.query.limit) || 100, 200),
    })
    return ok(res, 'Payments fetched', {
      payments: payments.map((p) => ({
        id: p.id,
        user_id: p.user_id,
        user_name: p.User ? p.User.name : null,
        user_email: p.User ? p.User.email : null,
        plan_id: p.plan_id,
        plan_name: p.plan ? p.plan.name : null,
        subscription_id: p.subscription_id,
        amount_pkr: p.amount_pkr,
        payment_method: p.payment_method,
        transaction_id: p.transaction_id,
        screenshot_url: p.screenshot_url,
        status: p.status,
        reviewed_by: p.reviewed_by,
        approved_at: p.approved_at,
        created_at: p.createdAt,
      })),
    })
  } catch (err) {
    console.error('Admin payments list error:', err)
    return fail(res, 500, 'Unable to load payments')
  }
})

router.patch('/payments/:id/approve', async (req, res) => {
  try {
    const data = await approvePayment(req.params.id, req.user.email)
    return ok(res, 'Payment approved and subscription activated', data)
  } catch (err) {
    console.error('Admin approve error:', err)
    return fail(res, err.status || 500, err.message || 'Unable to approve payment')
  }
})

router.patch('/payments/:id/reject', async (req, res) => {
  try {
    const data = await rejectPayment(req.params.id, req.user.email)
    return ok(res, 'Payment rejected', data)
  } catch (err) {
    console.error('Admin reject error:', err)
    return fail(res, err.status || 500, err.message || 'Unable to reject payment')
  }
})

router.get('/plans', async (req, res) => {
  try {
    const plans = await SubscriptionPlan.findAll({ order: [['price_pkr', 'ASC'], ['id', 'ASC']] })
    return ok(res, 'Plans fetched', { plans: plans.map(formatPlan) })
  } catch (err) {
    console.error('Admin plans list error:', err)
    return fail(res, 500, 'Unable to load plans')
  }
})

router.post('/plans', async (req, res) => {
  try {
    const {
      name,
      price_pkr,
      monthly_limit,
      max_contacts,
      has_history,
      description,
      is_active,
    } = req.body || {}

    if (!name || String(name).trim() === '') {
      return fail(res, 400, 'Plan name is required')
    }

    let limit = monthly_limit
    if (limit === '' || limit === 'null' || limit === undefined) limit = null
    else if (limit != null) limit = Number(limit)

    const plan = await SubscriptionPlan.create({
      name: String(name).trim(),
      price_pkr: Number(price_pkr) || 0,
      monthly_limit: limit,
      max_contacts: Math.max(1, Number(max_contacts) || 1),
      has_history: Boolean(has_history),
      description: description ? String(description).trim() : null,
      is_active: is_active !== false && is_active !== 'false',
    })
    return ok(res, 'Plan created', { plan: formatPlan(plan) }, 201)
  } catch (err) {
    console.error('Admin plan create error:', err)
    return fail(res, 500, 'Unable to create plan')
  }
})

router.patch('/plans/:id', async (req, res) => {
  try {
    const plan = await SubscriptionPlan.findByPk(req.params.id)
    if (!plan) return fail(res, 404, 'Plan not found')

    const payload = { ...req.body }
    if (payload.monthly_limit === '' || payload.monthly_limit === 'null') {
      payload.monthly_limit = null
    }

    const nextActive = payload.is_active === undefined
      ? plan.is_active
      : payload.is_active !== false && payload.is_active !== 'false' && payload.is_active !== 0
    const nextPrice = payload.price_pkr != null ? Number(payload.price_pkr) : Number(plan.price_pkr)
    const wasFree = Number(plan.price_pkr) === 0 && plan.is_active
    const deactivatingFree = wasFree && !nextActive
    const removingFreePrice = wasFree && nextPrice !== 0

    if (deactivatingFree || removingFreePrice) {
      const others = await countFreeActivePlans(plan.id)
      if (others === 0) {
        return fail(
          res,
          400,
          'Cannot deactivate or remove the last Free (price 0) plan. Create another free tier first.'
        )
      }
    }

    const fields = {}
    if (payload.name != null) fields.name = String(payload.name).trim()
    if (payload.price_pkr != null) fields.price_pkr = Number(payload.price_pkr)
    if (payload.monthly_limit !== undefined) fields.monthly_limit = payload.monthly_limit
    if (payload.max_contacts != null) fields.max_contacts = Math.max(1, Number(payload.max_contacts))
    if (payload.has_history !== undefined) fields.has_history = Boolean(payload.has_history)
    if (payload.description !== undefined) {
      fields.description = payload.description ? String(payload.description).trim() : null
    }
    if (payload.is_active !== undefined) fields.is_active = nextActive

    await plan.update(fields)
    return ok(res, 'Plan updated', { plan: formatPlan(plan) })
  } catch (err) {
    console.error('Admin plan update error:', err)
    return fail(res, 500, 'Unable to update plan')
  }
})

router.delete('/plans/:id', async (req, res) => {
  try {
    const plan = await SubscriptionPlan.findByPk(req.params.id)
    if (!plan) return fail(res, 404, 'Plan not found')

    const [subs, payments] = await Promise.all([
      Subscription.count({ where: { plan_id: plan.id } }),
      Payment.count({ where: { plan_id: plan.id } }),
    ])
    if (subs > 0 || payments > 0) {
      return fail(
        res,
        400,
        'Cannot delete a plan that has subscriptions or payments. Set is_active to false instead.'
      )
    }

    if (Number(plan.price_pkr) === 0 && plan.is_active) {
      const others = await countFreeActivePlans(plan.id)
      if (others === 0) {
        return fail(res, 400, 'Cannot delete the last Free plan.')
      }
    }

    await plan.destroy()
    return ok(res, 'Plan deleted', { id: Number(req.params.id) })
  } catch (err) {
    console.error('Admin plan delete error:', err)
    return fail(res, 500, 'Unable to delete plan')
  }
})

router.get('/config', async (req, res) => {
  try {
    const config = await getSystemConfig()
    return ok(res, 'Config fetched', { config })
  } catch (err) {
    console.error('Admin config get error:', err)
    return fail(res, 500, 'Unable to load config')
  }
})

router.patch('/config', async (req, res) => {
  try {
    let row = await SystemConfig.findOne({ order: [['id', 'ASC']] })
    if (!row) {
      row = await SystemConfig.create({
        ...DEFAULTS,
        easypaisa_account: envMerchant('EASYPAY_ACCOUNT', 'PAYMENT_ACCOUNT'),
        jazzcash_account: envMerchant('JAZZCASH_ACCOUNT', 'PAYMENT_ACCOUNT'),
      })
    }

    const body = req.body || {}
    const fields = {}

    if (body.sms_prefix != null) {
      fields.sms_prefix = String(body.sms_prefix).trim().toUpperCase() || 'SLAM'
    }
    if (body.pin_min_length != null) fields.pin_min_length = Number(body.pin_min_length)
    if (body.pin_max_length != null) fields.pin_max_length = Number(body.pin_max_length)
    if (body.pin_attempt_cap != null) fields.pin_attempt_cap = Number(body.pin_attempt_cap)
    if (body.pin_window_minutes != null) fields.pin_window_minutes = Number(body.pin_window_minutes)
    if (body.login_attempt_cap != null) fields.login_attempt_cap = Number(body.login_attempt_cap)
    if (body.login_window_minutes != null) fields.login_window_minutes = Number(body.login_window_minutes)
    if (body.emergency_interval_minutes != null) {
      fields.emergency_interval_minutes = Number(body.emergency_interval_minutes)
    } else if (body.emergency_interval_hours != null) {
      // Back-compat for older admin clients
      fields.emergency_interval_minutes = Number(body.emergency_interval_hours) * 60
    }
    for (const key of [
      'maintenance',
      'payments_enabled',
      'maps_enabled',
      'email_enabled',
      'emergency_enabled',
    ]) {
      if (body[key] !== undefined) fields[key] = Boolean(body[key])
    }

    if ('easypaisa_account' in body) {
      const cleaned = normalizeMerchant(body.easypaisa_account, '')
      fields.easypaisa_account = cleaned || envMerchant('EASYPAY_ACCOUNT', 'PAYMENT_ACCOUNT')
    }
    if ('jazzcash_account' in body) {
      const cleaned = normalizeMerchant(body.jazzcash_account, '')
      fields.jazzcash_account = cleaned || envMerchant('JAZZCASH_ACCOUNT', 'PAYMENT_ACCOUNT')
    }

    const nextMin = fields.pin_min_length != null ? fields.pin_min_length : row.pin_min_length
    const nextMax = fields.pin_max_length != null ? fields.pin_max_length : row.pin_max_length
    if (Number(nextMax) < Number(nextMin)) {
      fields.pin_max_length = Number(nextMin)
    }

    await row.update(fields)
    await row.reload()
    return ok(res, 'Config updated', { config: publicFields(row) })
  } catch (err) {
    console.error('Admin config patch error:', err)
    return fail(res, 500, 'Unable to update config')
  }
})

router.get('/users', async (req, res) => {
  try {
    const where = {}
    if (req.query.role) where.role = String(req.query.role)
    if (req.query.account_status) {
      where.account_status = normalizeAccountStatus(req.query.account_status)
    }
    if (req.query.q) {
      const q = `%${String(req.query.q).trim()}%`
      where[Op.or] = [
        { name: { [Op.like]: q } },
        { email: { [Op.like]: q } },
        { phone: { [Op.like]: q } },
      ]
    }
    const users = await User.findAll({
      where,
      order: [['createdAt', 'DESC']],
      limit: Math.min(Number(req.query.limit) || 100, 200),
    })
    return ok(res, 'Users fetched', { users: users.map(publicAdminUser) })
  } catch (err) {
    console.error('Admin users list error:', err)
    return fail(res, 500, 'Unable to load users')
  }
})

function assertMutableTarget(actor, target) {
  if (!target) {
    const err = new Error('User not found')
    err.status = 404
    throw err
  }
  if (target.role === 'admin') {
    const err = new Error('Cannot change admin accounts this way')
    err.status = 400
    throw err
  }
  if (target.id === actor.id) {
    const err = new Error('Cannot change your own account status')
    err.status = 400
    throw err
  }
}

router.post('/users/:id/suspend', async (req, res) => {
  try {
    const user = await User.findByPk(req.params.id)
    assertMutableTarget(req.user, user)
    if (normalizeAccountStatus(user.account_status) === ACCOUNT_DEACTIVATED) {
      return fail(res, 400, 'Reactivate this account before suspending it')
    }
    if (normalizeAccountStatus(user.account_status) === ACCOUNT_SUSPENDED) {
      return ok(res, 'Account already suspended', { user: publicAdminUser(user) })
    }
    await user.update({ account_status: ACCOUNT_SUSPENDED })
    await pauseActiveSubscriptions(user.id)
    return ok(res, 'Account suspended. Login and live subscriptions are paused.', {
      user: publicAdminUser(user),
    })
  } catch (err) {
    console.error('Admin suspend error:', err)
    return fail(res, err.status || 500, err.message || 'Unable to suspend user')
  }
})

router.post('/users/:id/unsuspend', async (req, res) => {
  try {
    const user = await User.findByPk(req.params.id)
    assertMutableTarget(req.user, user)
    if (normalizeAccountStatus(user.account_status) !== ACCOUNT_SUSPENDED) {
      return fail(res, 400, 'Account is not suspended')
    }
    await user.update({ account_status: ACCOUNT_ACTIVE })
    await resumePausedSubscriptions(user.id)
    try {
      await ensureActiveSubscription(user.id)
    } catch (err) {
      console.warn('ensureActiveSubscription after unsuspend:', err.message)
    }
    await user.reload()
    return ok(res, 'Account unsuspended. Subscriptions resumed where paused.', {
      user: publicAdminUser(user),
    })
  } catch (err) {
    console.error('Admin unsuspend error:', err)
    return fail(res, err.status || 500, err.message || 'Unable to unsuspend user')
  }
})

router.post('/users/:id/deactivate', async (req, res) => {
  try {
    const user = await User.findByPk(req.params.id)
    assertMutableTarget(req.user, user)
    if (normalizeAccountStatus(user.account_status) === ACCOUNT_DEACTIVATED) {
      return ok(res, 'Account already deactivated', { user: publicAdminUser(user) })
    }
    await user.update({ account_status: ACCOUNT_DEACTIVATED })
    await pauseActiveSubscriptions(user.id)
    return ok(res, 'Account deactivated (soft). Login and usage are blocked.', {
      user: publicAdminUser(user),
    })
  } catch (err) {
    console.error('Admin deactivate error:', err)
    return fail(res, err.status || 500, err.message || 'Unable to deactivate user')
  }
})

router.post('/users/:id/reactivate', async (req, res) => {
  try {
    const user = await User.findByPk(req.params.id)
    assertMutableTarget(req.user, user)
    if (normalizeAccountStatus(user.account_status) !== ACCOUNT_DEACTIVATED) {
      return fail(res, 400, 'Account is not deactivated')
    }
    await user.update({ account_status: ACCOUNT_ACTIVE })
    await resumePausedSubscriptions(user.id)
    try {
      await ensureActiveSubscription(user.id)
    } catch (err) {
      console.warn('ensureActiveSubscription after reactivate:', err.message)
    }
    await user.reload()
    return ok(res, 'Account reactivated.', { user: publicAdminUser(user) })
  } catch (err) {
    console.error('Admin reactivate error:', err)
    return fail(res, err.status || 500, err.message || 'Unable to reactivate user')
  }
})

router.get('/subscriptions', async (req, res) => {
  try {
    const where = {}
    if (req.query.status) where.status = String(req.query.status)
    if (req.query.user_id) where.user_id = Number(req.query.user_id)
    const rows = await Subscription.findAll({
      where,
      include: [
        { model: User, attributes: ['id', 'name', 'email'] },
        { model: SubscriptionPlan, as: 'plan', attributes: ['id', 'name', 'price_pkr'] },
      ],
      order: [['updatedAt', 'DESC']],
      limit: Math.min(Number(req.query.limit) || 100, 200),
    })
    return ok(res, 'Subscriptions fetched', {
      subscriptions: rows.map((s) => ({
        id: s.id,
        user_id: s.user_id,
        user_name: s.User ? s.User.name : null,
        user_email: s.User ? s.User.email : null,
        plan_id: s.plan_id,
        plan_name: s.plan ? s.plan.name : null,
        status: s.status,
        requests_used: s.requests_used,
        start_date: s.start_date,
        end_date: s.end_date,
        created_at: s.createdAt,
      })),
    })
  } catch (err) {
    console.error('Admin subscriptions list error:', err)
    return fail(res, 500, 'Unable to load subscriptions')
  }
})

router.patch('/subscriptions/:id', async (req, res) => {
  try {
    const sub = await Subscription.findByPk(req.params.id)
    if (!sub) return fail(res, 404, 'Subscription not found')

    const body = req.body || {}
    const fields = {}
    if (body.status != null) {
      const allowed = [
        'pending_payment',
        'pending_approval',
        'active',
        'paused',
        'expired',
        'cancelled',
      ]
      if (!allowed.includes(body.status)) {
        return fail(res, 400, 'Invalid subscription status')
      }
      fields.status = body.status
    }
    if (body.end_date !== undefined) {
      fields.end_date = body.end_date ? new Date(body.end_date) : null
    }
    await sub.update(fields)
    return ok(res, 'Subscription updated', {
      subscription: {
        id: sub.id,
        status: sub.status,
        end_date: sub.end_date,
      },
    })
  } catch (err) {
    console.error('Admin subscription patch error:', err)
    return fail(res, 500, 'Unable to update subscription')
  }
})

router.get('/location-logs', async (req, res) => {
  try {
    const where = {}
    if (req.query.user_id) where.user_id = Number(req.query.user_id)
    const logs = await LocationLog.findAll({
      where,
      include: [{ model: User, attributes: ['id', 'name', 'email'] }],
      order: [['createdAt', 'DESC']],
      limit: Math.min(Number(req.query.limit) || 100, 200),
    })
    return ok(res, 'Location logs fetched', {
      logs: logs.map((log) => ({
        id: log.id,
        user_id: log.user_id,
        user_name: log.User ? log.User.name : null,
        user_email: log.User ? log.User.email : null,
        latitude: log.latitude,
        longitude: log.longitude,
        accuracy: log.accuracy,
        accuracy_meters: log.accuracy_meters,
        requested_by: log.requested_by,
        source: log.source,
        event_id: log.event_id,
        captured_at: log.captured_at,
        created_at: log.createdAt,
      })),
    })
  } catch (err) {
    console.error('Admin location logs error:', err)
    return fail(res, 500, 'Unable to load location logs')
  }
})

module.exports = router
