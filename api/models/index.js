const bcrypt = require('bcryptjs')
const sequelize = require('../config/database')
const User = require('./User')
const SubscriptionPlan = require('./SubscriptionPlan')
const Subscription = require('./Subscription')
const Payment = require('./Payment')
const LocationLog = require('./LocationLog')

User.hasMany(Subscription, { foreignKey: 'user_id' })
Subscription.belongsTo(User, { foreignKey: 'user_id' })

SubscriptionPlan.hasMany(Subscription, { foreignKey: 'plan_id' })
Subscription.belongsTo(SubscriptionPlan, { foreignKey: 'plan_id', as: 'plan' })

User.hasMany(Payment, { foreignKey: 'user_id' })
Payment.belongsTo(User, { foreignKey: 'user_id' })

SubscriptionPlan.hasMany(Payment, { foreignKey: 'plan_id' })
Payment.belongsTo(SubscriptionPlan, { foreignKey: 'plan_id', as: 'plan' })

Subscription.hasMany(Payment, { foreignKey: 'subscription_id' })
Payment.belongsTo(Subscription, { foreignKey: 'subscription_id' })

User.hasMany(LocationLog, { foreignKey: 'user_id' })
LocationLog.belongsTo(User, { foreignKey: 'user_id' })

async function seedPlans() {
  const count = await SubscriptionPlan.count()
  if (count > 0) return

  await SubscriptionPlan.bulkCreate([
    {
      name: 'Free',
      price_pkr: 0,
      monthly_limit: 5,
      max_contacts: 1,
      has_history: false,
      description: 'Essential SMS location tracking',
    },
    {
      name: 'Basic',
      price_pkr: 299,
      monthly_limit: 50,
      max_contacts: 5,
      has_history: false,
      description: 'Reliable tracking for families',
    },
    {
      name: 'Premium',
      price_pkr: 699,
      monthly_limit: null,
      max_contacts: 20,
      has_history: true,
      description: 'Unlimited requests and full location history',
    },
  ])
  console.log('Subscription plans seeded')
}

async function seedAdmin() {
  const email = process.env.ADMIN_EMAIL
  const password = process.env.ADMIN_PASSWORD
  if (!email || !password) return

  const existing = await User.findOne({ where: { email } })
  if (existing) return

  await User.create({
    name: 'Administrator',
    email,
    password_hash: await bcrypt.hash(password, 10),
    phone: '03000000000',
    role: 'admin',
  })
  console.log('Admin account seeded')
}

async function syncDatabase() {
  try {
    await sequelize.authenticate()
    console.log('Database connected')
    // Do not use { alter: true }: Sequelize re-adds unique indexes on every
    // restart until MySQL hits the 64-key limit (users.email, payments.transaction_id).
    await sequelize.sync()
    console.log('Tables synced')
    await seedPlans()
    await seedAdmin()
  } catch (err) {
    console.error('Database connection failed:', err.message)
    process.exit(1)
  }
}

module.exports = {
  sequelize,
  syncDatabase,
  User,
  SubscriptionPlan,
  Subscription,
  Payment,
  LocationLog,
}
