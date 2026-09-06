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
    defaultValue: 8,
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
}, {
  tableName: 'system_config',
  timestamps: true,
})

module.exports = SystemConfig
