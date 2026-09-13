const { DataTypes } = require('sequelize')
const sequelize = require('../config/database')

const SystemConfig = sequelize.define('SystemConfig', {
  id: {
    type: DataTypes.INTEGER,
    primaryKey: true,
    autoIncrement: true,
  },
  sms_prefix: {
    type: DataTypes.STRING,
    allowNull: false,
    defaultValue: 'SLAM',
  },
  pin_min_length: {
    type: DataTypes.INTEGER,
    allowNull: false,
    defaultValue: 4,
  },
  pin_max_length: {
    type: DataTypes.INTEGER,
    allowNull: false,
    defaultValue: 6,
  },
  pin_attempt_cap: {
    type: DataTypes.INTEGER,
    allowNull: false,
    defaultValue: 3,
  },
  pin_window_minutes: {
    type: DataTypes.INTEGER,
    allowNull: false,
    defaultValue: 15,
  },
  login_attempt_cap: {
    type: DataTypes.INTEGER,
    allowNull: false,
    defaultValue: 3,
  },
  login_window_minutes: {
    type: DataTypes.INTEGER,
    allowNull: false,
    defaultValue: 15,
  },
  maintenance: {
    type: DataTypes.BOOLEAN,
    allowNull: false,
    defaultValue: false,
  },
  payments_enabled: {
    type: DataTypes.BOOLEAN,
    allowNull: false,
    defaultValue: true,
  },
  maps_enabled: {
    type: DataTypes.BOOLEAN,
    allowNull: false,
    defaultValue: false,
  },
  email_enabled: {
    type: DataTypes.BOOLEAN,
    allowNull: false,
    defaultValue: true,
  },
  emergency_enabled: {
    type: DataTypes.BOOLEAN,
    allowNull: false,
    defaultValue: true,
  },
  emergency_interval_minutes: {
    type: DataTypes.INTEGER,
    allowNull: false,
    defaultValue: 60,
  },
  // Legacy; kept so existing DBs can migrate. Prefer emergency_interval_minutes.
  emergency_interval_hours: {
    type: DataTypes.INTEGER,
    allowNull: true,
    defaultValue: 1,
  },
  easypaisa_account: {
    type: DataTypes.STRING(32),
    allowNull: true,
  },
  jazzcash_account: {
    type: DataTypes.STRING(32),
    allowNull: true,
  },
}, {
  tableName: 'system_config',
  timestamps: true,
})

module.exports = SystemConfig
