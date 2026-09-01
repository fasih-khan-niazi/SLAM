const { DataTypes } = require('sequelize')
const sequelize = require('../config/database')

const Subscription = sequelize.define('Subscription', {
  id: {
    type: DataTypes.INTEGER,
    primaryKey: true,
    autoIncrement: true,
  },
  user_id: {
    type: DataTypes.INTEGER,
    allowNull: false,
  },
  plan_id: {
    type: DataTypes.INTEGER,
    allowNull: false,
  },
  status: {
    type: DataTypes.ENUM(
      'pending_payment',
      'pending_approval',
      'active',
      'expired',
      'cancelled'
    ),
    defaultValue: 'pending_payment',
  },
  start_date: {
    type: DataTypes.DATEONLY,
    allowNull: true,
  },
  end_date: {
    type: DataTypes.DATEONLY,
    allowNull: true,
  },
  requests_used: {
    type: DataTypes.INTEGER,
    defaultValue: 0,
  },
}, {
  tableName: 'subscriptions',
  timestamps: true,
})

module.exports = Subscription
