const { DataTypes } = require('sequelize')
const sequelize = require('../config/database')

const SubscriptionPlan = sequelize.define('SubscriptionPlan', {
  id: {
    type: DataTypes.INTEGER,
    primaryKey: true,
    autoIncrement: true,
  },
  name: {
    type: DataTypes.STRING,
    allowNull: false,
  },
  price_pkr: {
    type: DataTypes.INTEGER,
    allowNull: false,
    defaultValue: 0,
  },
  monthly_limit: {
    type: DataTypes.INTEGER,
    allowNull: true,
    defaultValue: 5,
  },
  max_contacts: {
    type: DataTypes.INTEGER,
    allowNull: false,
    defaultValue: 1,
  },
  has_history: {
    type: DataTypes.BOOLEAN,
    defaultValue: false,
  },
  description: {
    type: DataTypes.STRING,
    allowNull: true,
  },
  is_active: {
    type: DataTypes.BOOLEAN,
    defaultValue: true,
  },
}, {
  tableName: 'subscription_plans',
  timestamps: true,
})

module.exports = SubscriptionPlan
