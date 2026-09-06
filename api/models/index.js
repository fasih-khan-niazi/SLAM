const bcrypt = require('bcryptjs')
const { DataTypes } = require('sequelize')
const sequelize = require('../config/database')
const User = require('./User')
const SubscriptionPlan = require('./SubscriptionPlan')
const Subscription = require('./Subscription')
const Payment = require('./Payment')
const LocationLog = require('./LocationLog')
const SystemConfig = require('./SystemConfig')
const Notification = require('./Notification')

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

User.hasMany(Notification, { foreignKey: 'user_id' })
Notification.belongsTo(User, { foreignKey: 'user_id' })

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

async function ensureSystemConfigColumns() {
  const qi = sequelize.getQueryInterface()
  let table
  try {
    table = await qi.describeTable('system_config')
  } catch {
    return
  }

  const columns = [
    ['pin_window_minutes', 15],
    ['login_attempt_cap', 3],
    ['login_window_minutes', 15],
  ]
  for (const [name, defaultValue] of columns) {
    if (!table[name]) {
      await qi.addColumn('system_config', name, {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue,
      })
    }
  }

  await SystemConfig.update(
    { pin_attempt_cap: 3 },
    { where: { pin_attempt_cap: 8 } }
  )
}

async function seedSystemConfig() {
  const count = await SystemConfig.count()
  if (count > 0) return
  await SystemConfig.create({
    sms_prefix: 'SLAM',
    pin_min_length: 4,
    pin_max_length: 6,
    pin_attempt_cap: 3,
    pin_window_minutes: 15,
    login_attempt_cap: 3,
    login_window_minutes: 15,
    maintenance: false,
    payments_enabled: true,
    maps_enabled: false,
    email_enabled: true,
  })
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
    await ensureSystemConfigColumns()
    await seedPlans()
    await seedAdmin()
    await seedSystemConfig()
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
  SystemConfig,
  Notification,
}
