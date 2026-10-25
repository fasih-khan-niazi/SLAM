const { Subscription } = require('../models')

const ACCOUNT_ACTIVE = 'active'
const ACCOUNT_SUSPENDED = 'suspended'
const ACCOUNT_DEACTIVATED = 'deactivated'

function normalizeAccountStatus(value) {
  const status = String(value || ACCOUNT_ACTIVE).toLowerCase()
  if (status === ACCOUNT_SUSPENDED || status === ACCOUNT_DEACTIVATED) return status
  return ACCOUNT_ACTIVE
}

function isAccountBlocked(user) {
  const status = normalizeAccountStatus(user && user.account_status)
  return status === ACCOUNT_SUSPENDED || status === ACCOUNT_DEACTIVATED
}

function accountBlockMessage(user) {
  const status = normalizeAccountStatus(user && user.account_status)
  if (status === ACCOUNT_SUSPENDED) {
    return 'This account is suspended. Contact support if you need access restored.'
  }
  if (status === ACCOUNT_DEACTIVATED) {
    return 'This account has been deactivated.'
  }
  return 'This account cannot sign in.'
}

/** 'YYYY-MM-DD' in UTC, written and read the same way everywhere so a DATEONLY round-trip is
 *  never ambiguous between a Date object's local calendar date and its UTC calendar date. */
function todayUtcDateOnly() {
  return new Date().toISOString().slice(0, 10)
}

async function pauseActiveSubscriptions(userId) {
  await Subscription.update(
    { status: 'paused', paused_at: todayUtcDateOnly() },
    { where: { user_id: userId, status: 'active' } }
  )
}

/**
 * Resuming shifts end_date forward by however long the subscription was paused, so a paying
 * user doesn't lose days they already paid for while suspended/deactivated. Then runs the normal
 * expiry check (not a blind set-to-active) in case the extended date is still in the past -
 * e.g. paused for longer than a full period - so it correctly drops to Free or renews instead of
 * sitting "active" with a stale end_date.
 */
async function resumePausedSubscriptions(userId) {
  const { addDays, rollUsagePeriodIfExpired } = require('./subscription')
  const DAY_MS = 24 * 60 * 60 * 1000
  const paused = await Subscription.findAll({ where: { user_id: userId, status: 'paused' } })

  const today = new Date(todayUtcDateOnly())
  for (const sub of paused) {
    let nextEndDate = sub.end_date
    if (sub.paused_at && sub.end_date) {
      const pausedAt = new Date(sub.paused_at)
      const pausedDays = Math.round((today.getTime() - pausedAt.getTime()) / DAY_MS)
      if (Number.isFinite(pausedDays) && pausedDays > 0) {
        nextEndDate = addDays(new Date(sub.end_date), pausedDays)
      }
    }
    await sub.update({ status: 'active', end_date: nextEndDate, paused_at: null })
  }

  for (const sub of paused) {
    await rollUsagePeriodIfExpired(sub)
  }
}

module.exports = {
  ACCOUNT_ACTIVE,
  ACCOUNT_SUSPENDED,
  ACCOUNT_DEACTIVATED,
  normalizeAccountStatus,
  isAccountBlocked,
  accountBlockMessage,
  pauseActiveSubscriptions,
  resumePausedSubscriptions,
}
