const { DataTypes } = require('sequelize')
const sequelize = require('../config/database')

const LocationLog = sequelize.define('LocationLog', {
  id: {
    type: DataTypes.INTEGER,
    primaryKey: true,
    autoIncrement: true,
  },
  event_id: {
    type: DataTypes.UUID,
    allowNull: false,
    unique: true,
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
  accuracy_meters: {
    type: DataTypes.DECIMAL(8, 2),
    allowNull: true,
  },
  source: {
    type: DataTypes.ENUM('CURRENT', 'LAST_KNOWN'),
    allowNull: false,
    defaultValue: 'CURRENT',
  },
  provider: {
    type: DataTypes.STRING(32),
    allowNull: true,
  },
  captured_at: {
    type: DataTypes.DATE,
    allowNull: true,
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
