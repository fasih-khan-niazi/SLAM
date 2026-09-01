const { Sequelize } = require('sequelize')

const options = {
  host: process.env.DB_HOST,
  port: process.env.DB_PORT || 3306,
  dialect: 'mysql',
  logging: false,
}

if (process.env.DB_SSL === 'true') {
  options.dialectOptions = { ssl: { rejectUnauthorized: false } }
}

const sequelize = new Sequelize(
  process.env.DB_NAME,
  process.env.DB_USER,
  process.env.DB_PASS,
  options
)

module.exports = sequelize
