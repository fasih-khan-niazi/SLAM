const { DataTypes } = require('sequelize')
const sequelize = require('../config/database')

const User = sequelize.define('User', {
  id: {
    type: DataTypes.INTEGER,
    primaryKey: true,
    autoIncrement: true,
  },
  name: {
    type: DataTypes.STRING,
    allowNull: false,
  },
  email: {
    type: DataTypes.STRING,
    allowNull: false,
    unique: true,
  },
  password_hash: {
    type: DataTypes.STRING,
    allowNull: false,
  },
  phone: {
    type: DataTypes.STRING,
    allowNull: false,
  },
  role: {
    type: DataTypes.ENUM('user', 'admin'),
    defaultValue: 'user',
  },
  account_status: {
    type: DataTypes.STRING(32),
    allowNull: false,
    defaultValue: 'active',
  },
  pin_salt: {
    type: DataTypes.STRING(64),
    allowNull: true,
  },
  pin_verifier: {
    type: DataTypes.STRING(128),
    allowNull: true,
  },
  // Tokens issued before this moment are rejected (set on every password change/reset).
  password_changed_at: {
    type: DataTypes.DATE,
    allowNull: true,
  },
  // Admins only: whether this account gets emailed on admin events (new payment, etc).
  // True for everyone by default; the seeded ADMIN_EMAIL account is the one deliberate exception.
  notify_admin_emails: {
    type: DataTypes.BOOLEAN,
    allowNull: false,
    defaultValue: true,
  },
}, {
  tableName: 'users',
  timestamps: true,
})

module.exports = User
