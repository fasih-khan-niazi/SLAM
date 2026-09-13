require('dotenv').config()

const express = require('express')
const cors = require('cors')
const helmet = require('helmet')
const path = require('path')
const { syncDatabase, sequelize } = require('./models')

const requiredSecrets = ['JWT_SECRET', 'DB_HOST', 'DB_NAME', 'DB_USER']
const missing = requiredSecrets.filter((key) => !process.env[key])
if (missing.length) {
  console.error(`Missing required env: ${missing.join(', ')}`)
  process.exit(1)
}

const app = express()
const PORT = process.env.PORT || 3000

app.set('trust proxy', 1)

app.use(helmet({
  contentSecurityPolicy: false,
  crossOriginEmbedderPolicy: false,
}))

function originValue(value) {
  return String(value || '').trim().replace(/\/$/, '')
}

function hostnameOf(value) {
  try {
    const raw = String(value || '').trim()
    if (!raw) return ''
    const url = raw.includes('://') ? new URL(raw) : new URL(`https://${raw}`)
    return url.hostname.toLowerCase()
  } catch {
    return ''
  }
}

const extraOrigins = String(process.env.CORS_ORIGINS || '')
  .split(',')
  .map(originValue)
  .filter(Boolean)

const allowedOrigins = [
  originValue(process.env.FRONTEND_URL),
  originValue(process.env.API_PUBLIC_URL),
  ...extraOrigins,
  'http://localhost:5173',
  'http://localhost:4173',
  'http://localhost:3000',
].filter(Boolean)

const allowedHosts = new Set(allowedOrigins.map(hostnameOf).filter(Boolean))

function isLoopbackHost(host) {
  return host === 'localhost' || host === '127.0.0.1' || host === '::1'
}

app.use((req, res, next) => {
  cors({
    origin(origin, callback) {
      if (!origin) return callback(null, true)
      const originHost = hostnameOf(origin)
      const requestHost = hostnameOf(req.get('host'))
      if (originHost && requestHost && originHost === requestHost) {
        return callback(null, true)
      }
      if (isLoopbackHost(originHost) && isLoopbackHost(requestHost)) {
        return callback(null, true)
      }
      if (allowedHosts.has(originHost)) return callback(null, true)
      return callback(new Error('Not allowed by CORS'))
    },
    credentials: true,
  })(req, res, next)
})

app.use('/uploads', express.static(path.join(__dirname, 'uploads')))
app.use(express.static(path.join(__dirname, 'public')))

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

  app.use(express.json({ limit: '1mb' }))
  app.use(express.urlencoded({ extended: true }))

  app.use('/api/auth', require('./routes/auth'))
  app.use('/api/admin', require('./routes/admin'))
  app.use('/api', require('./routes/plans'))
  app.use('/api', require('./routes/payments'))
  app.use('/api', require('./routes/location'))
  app.use('/api', require('./routes/config'))
  app.use('/api', require('./routes/notifications'))

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
    console.log('Listening on 0.0.0.0 (LAN phones can use this PC’s IPv4)')
  })
}

startServer()
