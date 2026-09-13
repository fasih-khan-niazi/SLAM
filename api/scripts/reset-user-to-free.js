#!/usr/bin/env node
/**
 * Reset an account to Free and cancel pending upgrades/payments.
 * Usage: node scripts/reset-user-to-free.js admin@slam.com
 */
require('dotenv').config()
const { sequelize, User, Payment, Subscription, SubscriptionPlan } = require('../models')
const { resetUserToFree, formatSubscription } = require('../utils/subscription')

async function main() {
  const email = String(process.argv[2] || '').trim().toLowerCase()
  if (!email) {
    console.error('Usage: node scripts/reset-user-to-free.js <email>')
    process.exit(1)
  }

  await sequelize.authenticate()

  const user = await User.findOne({ where: { email } })
  if (!user) {
    console.error(`No user found for ${email}`)
    process.exit(1)
  }

  const before = await Subscription.findAll({
    where: { user_id: user.id },
    include: [{ model: SubscriptionPlan, as: 'plan' }],
    order: [['createdAt', 'DESC']],
  })

  console.log(`User #${user.id} ${user.email}`)
  console.log('Subscriptions before:')
  for (const row of before) {
    console.log(
      `  #${row.id} ${row.plan ? row.plan.name : '?'} status=${row.status} used=${row.requests_used}`
    )
  }

  const result = await sequelize.transaction(async (transaction) => {
    await Payment.update(
      { status: 'rejected' },
      {
        where: { user_id: user.id, status: 'pending' },
        transaction,
      }
    )

    const free = await resetUserToFree(user.id, transaction)
    const withPlan = await Subscription.findByPk(free.id, {
      include: [{ model: SubscriptionPlan, as: 'plan' }],
      transaction,
    })
    return withPlan
  })

  console.log('Reset complete:')
  console.log(
    formatSubscription(result, result.plan, result.requests_used)
  )

  await sequelize.close()
}

main().catch(async (err) => {
  console.error(err)
  try {
    await sequelize.close()
  } catch (_) {
    // ignore
  }
  process.exit(1)
})
