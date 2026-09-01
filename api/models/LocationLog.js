const { DataTypes } = require('sequelize')
const sequelize = require('../config/database')

const LocationLog = sequelize.define('LocationLog', {
  id: {
    type: DataTypes.INTEGER,
    primaryKey: true,
    autoIncrement: true,
  },
  user_id: {
    type: DataTypes.INTEGER,
    allowNull: false,
  },
  latitude: {
    type: DataTypes.DECIMAL(10, 7),
    allowNull: false,
  },
  longitude: {
    type: DataTypes.DECIMAL(10, 7),
    allowNull: false,
  },
  accuracy: {
    type: DataTypes.ENUM('HIGH', 'MEDIUM', 'LOW'),
    defaultValue: 'LOW',
  },
  requested_by: {
    type: DataTypes.STRING,
    allowNull: false,
  },
}, {
  tableName: 'location_logs',
  timestamps: true,
})

module.exports = LocationLog
