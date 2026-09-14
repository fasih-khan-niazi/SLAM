#!/usr/bin/env node
/**
 * Ensure primary + extra admin accounts exist.
 * Usage: node scripts/ensure-admins.js
 * Optional: RESET_EXTRA_ADMIN_PASSWORDS=1 to reset the three extra admins to Password123
 */
require('dotenv').config()
const { sequelize, syncDatabase } = require('../models')

async function main() {
  await syncDatabase()
  console.log('Admin ensure complete.')
  await sequelize.close()
}

main().catch(async (err) => {
  console.error(err)
  try { await sequelize.close() } catch (_) {}
  process.exit(1)
})
