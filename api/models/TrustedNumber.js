const { DataTypes } = require('sequelize')
const sequelize = require('../config/database')

const TrustedNumber = sequelize.define('TrustedNumber', {
  id: {
    type: DataTypes.INTEGER,
    primaryKey: true,
    autoIncrement: true,
  },
  user_id: {
    type: DataTypes.INTEGER,
    allowNull: false,
  },
  label: {
    type: DataTypes.STRING(64),
    allowNull: false,
    defaultValue: '',
  },
  phone: {
    type: DataTypes.STRING(32),
    allowNull: false,
  },
  normalized: {
    type: DataTypes.STRING(32),
    allowNull: false,
  },
}, {
  tableName: 'trusted_numbers',
  timestamps: true,
  indexes: [
    { unique: true, fields: ['user_id', 'normalized'] },
  ],
})

module.exports = TrustedNumber
