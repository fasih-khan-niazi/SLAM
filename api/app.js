require('dotenv').config()

const express = require('express')
const cors = require('cors')
const path = require('path')
const { syncDatabase, sequelize } = require('./models')

const requiredSecrets = ['JWT_SECRET', 'SESSION_SECRET', 'DB_HOST', 'DB_NAME', 'DB_USER']
const missing = requiredSecrets.filter((key) => !process.env[key])
if (missing.length) {
  console.error(`Missing required env: ${missing.join(', ')}`)
  process.exit(1)
}

const app = express()
const PORT = process.env.PORT || 3000

app.set('trust proxy', 1)

const allowedOrigins = [
  process.env.FRONTEND_URL,
  'http://localhost:5173',
  'http://localhost:3000',
].filter(Boolean)

app.use(cors({
  origin(origin, callback) {
    if (!origin) return callback(null, true)
    if (allowedOrigins.includes(origin)) return callback(null, true)
    return callback(new Error('Not allowed by CORS'))
  },
  credentials: true,
}))

app.use('/uploads', express.static(path.join(__dirname, 'uploads')))

app.get('/', (req, res) => {
  res.json({ success: true, message: 'SLAM API is running', version: '1.0.0' })
})

app.get('/health', async (req, res) => {
  try {
    await sequelize.authenticate()
    return res.json({ success: true, status: 'ok', database: 'connected' })
  } catch (err) {
    return res.status(503).json({
      success: false,
      status: 'degraded',
      database: 'disconnected',
      data: null,
    })
  }
})

async function startServer() {
  await syncDatabase()

  const { adminRouter } = require('./admin')
  app.use('/admin', adminRouter)

  app.use(express.json({ limit: '1mb' }))
  app.use(express.urlencoded({ extended: true }))

  app.use('/api/auth', require('./routes/auth'))
  app.use('/api', require('./routes/plans'))
  app.use('/api', require('./routes/payments'))
  app.use('/api', require('./routes/location'))

  app.use((err, req, res, next) => {
    if (err && err.message === 'Not allowed by CORS') {
      return res.status(403).json({
        success: false,
        message: 'Origin not allowed',
        data: null,
      })
    }
    if (err && err.name === 'MulterError') {
      return res.status(400).json({
        success: false,
        message: err.message,
        data: null,
      })
    }
    return next(err)
  })

  app.use((req, res) => {
    res.status(404).json({
      success: false,
      message: `Route ${req.method} ${req.originalUrl} not found`,
      data: null,
    })
  })

  app.listen(PORT, '0.0.0.0', () => {
    console.log(`SLAM API  http://localhost:${PORT}`)
    console.log(`LAN       http://192.168.100.7:${PORT}`)
    console.log(`Admin     http://localhost:${PORT}/admin`)
  })
}

startServer()
