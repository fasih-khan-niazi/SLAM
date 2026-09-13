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

  const intColumns = [
    ['pin_window_minutes', 15],
    ['login_attempt_cap', 3],
    ['login_window_minutes', 15],
    ['emergency_interval_hours', 1],
    ['emergency_interval_minutes', 60],
  ]
  for (const [name, defaultValue] of intColumns) {
    if (!table[name]) {
      await qi.addColumn('system_config', name, {
        type: DataTypes.INTEGER,
        allowNull: name === 'emergency_interval_hours' ? true : false,
        defaultValue,
      })
    }
  }

  // One-time style migrate: if minutes still at default seed and hours is set, prefer hours*60.
  try {
    await sequelize.query(
      `UPDATE system_config
       SET emergency_interval_minutes = GREATEST(15, LEAST(1440, COALESCE(emergency_interval_hours, 1) * 60))
       WHERE emergency_interval_minutes IS NULL
          OR (emergency_interval_minutes = 60 AND emergency_interval_hours IS NOT NULL AND emergency_interval_hours <> 1)`
    )
  } catch (err) {
    console.warn('emergency minutes migrate:', err.message)
  }
  if (!table.emergency_enabled) {
    await qi.addColumn('system_config', 'emergency_enabled', {
      type: DataTypes.BOOLEAN,
      allowNull: false,
      defaultValue: true,
    })
  }

  const merchantDefault =
    process.env.EASYPAY_ACCOUNT ||
    process.env.JAZZCASH_ACCOUNT ||
    process.env.PAYMENT_ACCOUNT ||
    '03300490019'

  if (!table.easypaisa_account) {
    await qi.addColumn('system_config', 'easypaisa_account', {
      type: DataTypes.STRING(32),
      allowNull: true,
      defaultValue: merchantDefault,
    })
  }
  if (!table.jazzcash_account) {
    await qi.addColumn('system_config', 'jazzcash_account', {
      type: DataTypes.STRING(32),
      allowNull: true,
      defaultValue: merchantDefault,
    })
  }

  await SystemConfig.update(
    { pin_attempt_cap: 3 },
    { where: { pin_attempt_cap: 8 } }
  )

  // Backfill blank merchant fields so the portal never shows empty numbers.
  await SystemConfig.update(
    { easypaisa_account: merchantDefault },
    { where: { easypaisa_account: null } }
  )
  await SystemConfig.update(
    { jazzcash_account: merchantDefault },
    { where: { jazzcash_account: null } }
  )
}

async function ensurePaymentColumns() {
  const qi = sequelize.getQueryInterface()
  let table
  try {
    table = await qi.describeTable('payments')
  } catch {
    return
  }
  if (!table.reviewed_by) {
    await qi.addColumn('payments', 'reviewed_by', {
      type: DataTypes.STRING(255),
      allowNull: true,
    })
  }
}

async function ensureLocationLogColumns() {
  const qi = sequelize.getQueryInterface()
  let table
  try {
    table = await qi.describeTable('location_logs')
  } catch {
    return
  }
  const eventIdNeedsNotNull = !table.event_id || table.event_id.allowNull

  if (!table.event_id) {
    await qi.addColumn('location_logs', 'event_id', {
      type: DataTypes.UUID,
      allowNull: true,
    })
  }
  if (!table.accuracy_meters) {
    await qi.addColumn('location_logs', 'accuracy_meters', {
      type: DataTypes.DECIMAL(8, 2),
      allowNull: true,
    })
  }
  if (!table.source) {
    await qi.addColumn('location_logs', 'source', {
      type: DataTypes.ENUM('CURRENT', 'LAST_KNOWN'),
      allowNull: false,
      defaultValue: 'CURRENT',
    })
  }
  if (!table.provider) {
    await qi.addColumn('location_logs', 'provider', {
      type: DataTypes.STRING(32),
      allowNull: true,
    })
  }
  if (!table.captured_at) {
    await qi.addColumn('location_logs', 'captured_at', {
      type: DataTypes.DATE,
      allowNull: true,
    })
  }

  // Existing rows predate client event IDs. Give each one a unique UUID before
  // making the column mandatory, and retain its original capture approximation.
  await sequelize.query(
    'UPDATE `location_logs` SET `event_id` = UUID() WHERE `event_id` IS NULL'
  )
  await sequelize.query(
    'UPDATE `location_logs` SET `captured_at` = `createdAt` WHERE `captured_at` IS NULL'
  )
  if (eventIdNeedsNotNull) {
    await qi.changeColumn('location_logs', 'event_id', {
      type: DataTypes.UUID,
      allowNull: false,
    })
  }

  const indexes = await qi.showIndex('location_logs')
  const hasUniqueEventId = indexes.some((index) =>
    index.unique && index.fields.some((field) => field.attribute === 'event_id')
  )
  if (!hasUniqueEventId) {
    await qi.addIndex('location_logs', ['event_id'], {
      name: 'location_logs_event_id_unique',
      unique: true,
    })
  }
}

async function seedSystemConfig() {
  const count = await SystemConfig.count()
  if (count > 0) return
  const merchantDefault =
    process.env.EASYPAY_ACCOUNT ||
    process.env.JAZZCASH_ACCOUNT ||
    process.env.PAYMENT_ACCOUNT ||
    '03300490019'

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
    emergency_enabled: true,
    emergency_interval_minutes: 60,
    emergency_interval_hours: 1,
    easypaisa_account: merchantDefault,
    jazzcash_account: merchantDefault,
  })
}

async function seedAdmin() {
  const email = process.env.ADMIN_EMAIL
  const password = process.env.ADMIN_PASSWORD
  if (!email || !password) return

  let user = await User.findOne({ where: { email } })
  if (!user) {
    user = await User.create({
      name: 'Administrator',
      email,
      password_hash: await bcrypt.hash(password, 10),
      phone: '03000000000',
      role: 'admin',
    })
    console.log('Admin account seeded')
  }

  try {
    const { ensureActiveSubscription } = require('../utils/subscription')
    await ensureActiveSubscription(user.id)
  } catch (err) {
    console.error('Admin Free plan ensure failed:', err.message)
  }
}

async function ensureUserPinColumns() {
  const qi = sequelize.getQueryInterface()
  let table
  try {
    table = await qi.describeTable('users')
  } catch {
    return
  }
  if (!table.pin_salt) {
    await qi.addColumn('users', 'pin_salt', {
      type: DataTypes.STRING(64),
      allowNull: true,
    })
  }
  if (!table.pin_verifier) {
    await qi.addColumn('users', 'pin_verifier', {
      type: DataTypes.STRING(128),
      allowNull: true,
    })
  }
  if (!table.account_status) {
    await qi.addColumn('users', 'account_status', {
      type: DataTypes.STRING(32),
      allowNull: false,
      defaultValue: 'active',
    })
  }
}

async function ensureSubscriptionPausedStatus() {
  try {
    await sequelize.query(
      "ALTER TABLE `subscriptions` MODIFY COLUMN `status` ENUM(" +
        "'pending_payment','pending_approval','active','expired','cancelled','paused'" +
      ") NOT NULL DEFAULT 'pending_payment'"
    )
  } catch (err) {
    // Already applied, or table missing before sync — ignore benign failures.
    if (!/Duplicate|check that column|Can't FIND|Unknown table/i.test(err.message || '')) {
      console.warn('ensureSubscriptionPausedStatus:', err.message)
    }
  }
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
    await ensurePaymentColumns()
    await ensureLocationLogColumns()
    await ensureUserPinColumns()
    await ensureSubscriptionPausedStatus()
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
