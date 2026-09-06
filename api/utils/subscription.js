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

async function activateFreePlan(userId, transaction) {
  const freePlan = await getFreePlan(transaction)
  if (!freePlan) {
    throw new Error('Free plan is not seeded')
  }

  const today = new Date()
  return Subscription.create({
    user_id: userId,
    plan_id: freePlan.id,
    status: 'active',
    requests_used: 0,
    start_date: today,
    end_date: addDays(today, 30),
  }, transaction ? { transaction } : undefined)
}

async function rollUsagePeriodIfExpired(subscription) {
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
  })
  return subscription
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

async function monthLocationCount(userId) {
  const startOfMonth = new Date()
  startOfMonth.setDate(1)
  startOfMonth.setHours(0, 0, 0, 0)

  return LocationLog.count({
    where: {
      user_id: userId,
      createdAt: { [Op.gte]: startOfMonth },
    },
  })
}

async function getActivePlanInfo(userId) {
  const subscription = await Subscription.findOne({
    where: { user_id: userId, status: 'active' },
    include: [{ model: SubscriptionPlan, as: 'plan' }],
    order: [['createdAt', 'DESC']],
  })

  if (subscription) {
    const current = await rollUsagePeriodIfExpired(subscription)
    return {
      subscription: current,
      plan: current.plan,
      monthly_limit: current.plan.monthly_limit,
      requests_used: current.requests_used,
      has_history: current.plan.has_history,
      plan_name: current.plan.name,
    }
  }

  const freePlan = await getFreePlan()
  const requests_used = await monthLocationCount(userId)

  return {
    subscription: null,
    plan: freePlan,
    monthly_limit: freePlan ? freePlan.monthly_limit : 5,
    requests_used,
    has_history: false,
    plan_name: 'Free',
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

module.exports = {
  addDays,
  formatSubscription,
  activateFreePlan,
  getFreePlan,
  getCurrentSubscription,
  getActivePlanInfo,
  cancelOtherActiveSubscriptions,
}
