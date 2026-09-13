#!/usr/bin/env node
/**
 * Ensure every user has an active subscription row (Free if missing).
 * Usage: node scripts/ensure-active-subscriptions.js
 */
require('dotenv').config()
const { sequelize, User } = require('../models')
const { ensureActiveSubscription } = require('../utils/subscription')

async function main() {
  await sequelize.authenticate()
  const users = await User.findAll({ attributes: ['id', 'email'] })
  let fixed = 0
  for (const user of users) {
    const before = await sequelize.models.Subscription.count({
      where: { user_id: user.id, status: 'active' },
    })
    await ensureActiveSubscription(user.id)
    if (before === 0) {
      fixed += 1
      console.log(`Ensured Free for #${user.id} ${user.email}`)
    }
  }
  console.log(`Done. Created active plans for ${fixed} user(s).`)
  await sequelize.close()
}

main().catch(async (err) => {
  console.error(err)
  try { await sequelize.close() } catch (_) {}
  process.exit(1)
})
