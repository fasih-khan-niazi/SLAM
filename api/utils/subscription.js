const { Subscription, SubscriptionPlan, LocationLog } = require('../models')
const { Op } = require('sequelize')

function addDays(date, days) {
  const next = new Date(date)
  next.setDate(next.getDate() + days)
  return next
}

function remainingFor(monthlyLimit, used) {
  if (monthlyLimit === null) return null
  return Math.max(0, monthlyLimit - used)
}

function formatSubscription(subscription, plan, requestsUsed) {
  const monthly_limit = plan ? plan.monthly_limit : 5
  const requests_used = requestsUsed ?? (subscription ? subscription.requests_used : 0)

  return {
    subscription_id: subscription ? subscription.id : null,
    plan_name: plan ? plan.name : 'Free',
    plan_id: plan ? plan.id : null,
    status: subscription ? subscription.status : 'active',
    price_pkr: plan ? plan.price_pkr : 0,
    monthly_limit,
    requests_used,
    requests_remaining: remainingFor(monthly_limit, requests_used),
    max_contacts: plan ? plan.max_contacts : 1,
    has_history: plan ? Boolean(plan.has_history) : false,
    start_date: subscription ? subscription.start_date : null,
    end_date: subscription ? subscription.end_date : null,
  }
}

async function getFreePlan(transaction) {
  return SubscriptionPlan.findOne({
    where: { name: 'Free' },
    ...(transaction ? { transaction } : {}),
  })
}

async function countLogsSince(userId, since, transaction) {
  const where = { user_id: userId }
  if (since) {
    where.createdAt = { [Op.gte]: since }
  }
  return LocationLog.count({
    where,
    ...(transaction ? { transaction } : {}),
  })
}

async function activateFreePlan(userId, transaction) {
  const freePlan = await getFreePlan(transaction)
  if (!freePlan) {
    throw new Error('Free plan is not seeded')
  }

  const today = new Date()
  // Backfill from recent logs so reinstall / missing Free rows keep server usage.
  const lookback = addDays(today, -30)
  const used = await countLogsSince(userId, lookback, transaction)
  const capped = freePlan.monthly_limit == null
    ? used
    : Math.min(used, freePlan.monthly_limit)

  return Subscription.create({
    user_id: userId,
    plan_id: freePlan.id,
    status: 'active',
    requests_used: capped,
    start_date: today,
    end_date: addDays(today, 30),
  }, transaction ? { transaction } : undefined)
}

async function rollUsagePeriodIfExpired(subscription, transaction) {
  if (!subscription || subscription.status !== 'active' || !subscription.plan) {
    return subscription
  }
  if (subscription.plan.monthly_limit == null) return subscription
  if (!subscription.end_date) return subscription

  const end = new Date(subscription.end_date)
  if (Number.isNaN(end.getTime()) || Date.now() <= end.getTime()) {
    return subscription
  }

  const today = new Date()
  await subscription.update({
    requests_used: 0,
    start_date: today,
    end_date: addDays(today, 30),
  }, transaction ? { transaction } : undefined)
  return subscription
}

/**
 * Every account must have exactly one live active subscription row.
 * Locates always increment that row so reinstall/login restores the same remaining.
 */
async function ensureActiveSubscription(userId, transaction) {
  const { User } = require('../models')
  const { isAccountBlocked } = require('./accountStatus')
  const user = await User.findByPk(userId, transaction ? { transaction } : undefined)
  if (user && isAccountBlocked(user)) {
    const paused = await Subscription.findOne({
      where: { user_id: userId, status: 'paused' },
      include: [{ model: SubscriptionPlan, as: 'plan' }],
      order: [['createdAt', 'DESC']],
      ...(transaction ? { transaction } : {}),
    })
    if (paused) return paused
    throw new Error('Account is suspended or deactivated')
  }

  const findOpts = {
    where: { user_id: userId, status: 'active' },
    order: [['createdAt', 'DESC']],
    ...(transaction ? { transaction, lock: transaction.LOCK.UPDATE } : {}),
  }

  let subscription = await Subscription.findOne(findOpts)

  if (!subscription) {
    subscription = await activateFreePlan(userId, transaction)
  }

  if (!subscription.plan) {
    subscription = await Subscription.findByPk(subscription.id, {
      include: [{ model: SubscriptionPlan, as: 'plan' }],
      ...(transaction ? { transaction } : {}),
    })
  }

  return rollUsagePeriodIfExpired(subscription, transaction)
}

async function getCurrentSubscription(userId) {
  const current = await Subscription.findOne({
    where: {
      user_id: userId,
      status: ['active', 'pending_payment', 'pending_approval'],
    },
    include: [{ model: SubscriptionPlan, as: 'plan' }],
    order: [['createdAt', 'DESC']],
  })
  return rollUsagePeriodIfExpired(current)
}

async function getPendingUpgrade(userId) {
  return Subscription.findOne({
    where: {
      user_id: userId,
      status: ['pending_payment', 'pending_approval'],
    },
    include: [{ model: SubscriptionPlan, as: 'plan' }],
    order: [['createdAt', 'DESC']],
  })
}

/**
 * Entitlements are always the active plan at the top level.
 * A paid upgrade in flight is nested as pending_upgrade so clients never
 * treat Basic/Premium limits as live before admin approval.
 */
async function buildSubscriptionPayload(userId) {
  const activeInfo = await getActivePlanInfo(userId)
  const payload = formatSubscription(
    activeInfo.subscription,
    activeInfo.plan,
    activeInfo.requests_used
  )

  const pending = await getPendingUpgrade(userId)
  if (pending) {
    payload.pending_upgrade = formatSubscription(pending, pending.plan, pending.requests_used)
  }

  return payload
}

async function monthLocationCount(userId) {
  const startOfMonth = new Date()
  startOfMonth.setDate(1)
  startOfMonth.setHours(0, 0, 0, 0)
  return countLogsSince(userId, startOfMonth)
}

async function getActivePlanInfo(userId, transaction) {
  const subscription = await ensureActiveSubscription(userId, transaction)
  const plan = subscription.plan || await SubscriptionPlan.findByPk(subscription.plan_id, {
    ...(transaction ? { transaction } : {}),
  })

  return {
    subscription,
    plan,
    monthly_limit: plan ? plan.monthly_limit : 5,
    requests_used: subscription.requests_used,
    has_history: plan ? Boolean(plan.has_history) : false,
    plan_name: plan ? plan.name : 'Free',
  }
}

async function cancelOtherActiveSubscriptions(userId, keepId, transaction) {
  const where = { user_id: userId, status: 'active' }
  if (keepId) where.id = { [Op.ne]: keepId }

  await Subscription.update(
    { status: 'cancelled' },
    { where, ...(transaction ? { transaction } : {}) }
  )
}

/**
 * Force an account onto Free: cancel paid/pending rows and sync requests_used from logs.
 */
async function resetUserToFree(userId, transaction) {
  const freePlan = await getFreePlan(transaction)
  if (!freePlan) throw new Error('Free plan is not seeded')

  await Subscription.update(
    { status: 'cancelled' },
    {
      where: {
        user_id: userId,
        status: ['active', 'pending_payment', 'pending_approval'],
      },
      ...(transaction ? { transaction } : {}),
    }
  )

  const today = new Date()
  // Explicit admin/QA reset starts a clean Free period.
  return Subscription.create({
    user_id: userId,
    plan_id: freePlan.id,
    status: 'active',
    requests_used: 0,
    start_date: today,
    end_date: addDays(today, 30),
  }, transaction ? { transaction } : undefined)
}

module.exports = {
  addDays,
  formatSubscription,
  activateFreePlan,
  getFreePlan,
  getCurrentSubscription,
  getPendingUpgrade,
  getActivePlanInfo,
  ensureActiveSubscription,
  buildSubscriptionPayload,
  cancelOtherActiveSubscriptions,
  resetUserToFree,
  countLogsSince,
  monthLocationCount,
}
