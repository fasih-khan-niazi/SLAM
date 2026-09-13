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

async function pauseActiveSubscriptions(userId) {
  await Subscription.update(
    { status: 'paused' },
    { where: { user_id: userId, status: 'active' } }
  )
}

async function resumePausedSubscriptions(userId) {
  await Subscription.update(
    { status: 'active' },
    { where: { user_id: userId, status: 'paused' } }
  )
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
