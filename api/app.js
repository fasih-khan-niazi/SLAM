require('dotenv').config()

const express = require('express')
const cors = require('cors')
const path = require('path')
const { syncDatabase } = require('./models')

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
    if (!origin || allowedOrigins.includes(origin) || process.env.NODE_ENV !== 'production') {
      return callback(null, true)
    }
    return callback(new Error('Not allowed by CORS'))
  },
  credentials: true,
}))

app.use('/uploads', express.static(path.join(__dirname, 'uploads')))

app.get('/', (req, res) => {
  res.json({ success: true, message: 'SLAM API is running', version: '1.0.0' })
})

app.get('/health', (req, res) => {
  res.json({ success: true, status: 'ok' })
})

async function startServer() {
  await syncDatabase()

  const { adminRouter } = require('./admin')
  app.use('/admin', adminRouter)

  app.use(express.json())
  app.use(express.urlencoded({ extended: true }))

  app.use('/api/auth', require('./routes/auth'))
  app.use('/api', require('./routes/plans'))
  app.use('/api', require('./routes/payments'))
  app.use('/api', require('./routes/location'))

  app.use((req, res) => {
    res.status(404).json({
      success: false,
      message: `Route ${req.method} ${req.originalUrl} not found`,
      data: null,
    })
  })

  app.listen(PORT, () => {
    console.log(`SLAM API  http://localhost:${PORT}`)
    console.log(`Admin     http://localhost:${PORT}/admin`)
  })
}

startServer()
