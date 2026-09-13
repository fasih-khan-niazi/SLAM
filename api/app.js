require('dotenv').config()

const express = require('express')
const cors = require('cors')
const helmet = require('helmet')
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

// AdminJS uses inline scripts; keep Helmet on, leave CSP off until Phase 12.
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

function isAdminPath(req) {
  const path = String(req.originalUrl || req.path || '').split('?')[0]
  return path === '/admin' || path.startsWith('/admin/')
}

app.use((req, res, next) => {
  // AdminJS is served from this same API. Reflect any Origin so
  // localhost vs 127.0.0.1 vs ::1 (and the Railway public host) can sign in.
  if (isAdminPath(req)) {
    return cors({ origin: true, credentials: true })(req, res, next)
  }

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

async function ensureAdminComponents() {
  const fs = require('fs')
  const path = require('path')
  const bundlePath = path.join(__dirname, '.adminjs', 'bundle.js')
  let needsBuild = true
  try {
    if (fs.existsSync(bundlePath)) {
      const text = fs.readFileSync(bundlePath, 'utf8')
      needsBuild = !text.includes('Welcome back') || !text.includes('Payments waiting')
    }
  } catch {
    needsBuild = true
  }
  if (!needsBuild) return
  console.log('Building AdminJS custom components…')
  await new Promise((resolve, reject) => {
    const { spawn } = require('child_process')
    const child = spawn(process.execPath, [path.join(__dirname, 'scripts', 'bundle-admin.js')], {
      cwd: __dirname,
      stdio: 'inherit',
      env: process.env,
    })
    child.on('exit', (code) => (code === 0 ? resolve() : reject(new Error(`admin bundle exit ${code}`))))
    child.on('error', reject)
  })
}

async function startServer() {
  await syncDatabase()
  await ensureAdminComponents()

  const { rateLimit } = require('./middleware/rateLimit')
  const { adminRouter } = require('./admin')

  // Components bundle must be public: it includes the Login override.
  // AdminJS auth would otherwise return the login HTML for this URL (chicken-and-egg).
  app.get('/admin/frontend/assets/components.bundle.js', (req, res) => {
    const bundlePath = path.join(__dirname, '.adminjs', 'bundle.js')
    res.type('application/javascript')
    res.set('Cache-Control', 'no-cache')
    return res.sendFile(bundlePath, (err) => {
      if (err) {
        console.error('Admin components bundle missing:', err.message)
        res.status(500).type('text/plain').send('Admin components bundle missing')
      }
    })
  })

  // In-memory limit for AdminJS form POST (not shared across Railway replicas).
  app.post(
    '/admin/login',
    rateLimit({
      windowMs: 15 * 60 * 1000,
      max: 20,
      message: 'Too many admin sign-in attempts. Try again in 15 minutes.',
    })
  )
  app.use('/admin', adminRouter)

  app.use(express.json({ limit: '1mb' }))
  app.use(express.urlencoded({ extended: true }))

  app.use('/api/auth', require('./routes/auth'))
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
    console.log(`Admin     http://localhost:${PORT}/admin`)
    console.log('Listening on 0.0.0.0 (LAN phones can use this PC’s IPv4)')
  })
}

startServer()
