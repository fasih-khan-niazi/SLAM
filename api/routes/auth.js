const express = require('express')
const bcrypt = require('bcryptjs')
const jwt = require('jsonwebtoken')
const { User } = require('../models')
const { protect } = require('../middleware/auth')

const router = express.Router()

function generateToken(userId) {
  return jwt.sign({ id: userId }, process.env.JWT_SECRET, { expiresIn: '7d' })
}

function publicUser(user) {
  return {
    id: user.id,
    name: user.name,
    email: user.email,
    phone: user.phone,
    role: user.role,
  }
}

router.post('/register', async (req, res) => {
  try {
    const { name, email, password, phone } = req.body

    if (!name || !email || !password || !phone) {
      return res.status(400).json({
        success: false,
        message: 'Name, email, password and phone are required',
        data: null,
      })
    }

    const existing = await User.findOne({ where: { email } })
    if (existing) {
      return res.status(400).json({
        success: false,
        message: 'An account with this email already exists',
        data: null,
      })
    }

    const password_hash = await bcrypt.hash(password, 10)
    const user = await User.create({ name, email, password_hash, phone })
    const token = generateToken(user.id)

    return res.status(201).json({
      success: true,
      message: 'Account created',
      data: { token, user: publicUser(user) },
    })
  } catch (err) {
    console.error('Register error:', err)
    return res.status(500).json({
      success: false,
      message: 'Unable to create account',
      data: null,
    })
  }
})

router.post('/login', async (req, res) => {
  try {
    const { email, password } = req.body

    if (!email || !password) {
      return res.status(400).json({
        success: false,
        message: 'Email and password are required',
        data: null,
      })
    }

    const user = await User.findOne({ where: { email } })
    if (!user) {
      return res.status(401).json({
        success: false,
        message: 'Invalid email or password',
        data: null,
      })
    }

    const isMatch = await bcrypt.compare(password, user.password_hash)
    if (!isMatch) {
      return res.status(401).json({
        success: false,
        message: 'Invalid email or password',
        data: null,
      })
    }

    const token = generateToken(user.id)

    return res.status(200).json({
      success: true,
      message: 'Signed in',
      data: { token, user: publicUser(user) },
    })
  } catch (err) {
    console.error('Login error:', err)
    return res.status(500).json({
      success: false,
      message: 'Unable to sign in',
      data: null,
    })
  }
})

router.get('/me', protect, async (req, res) => {
  return res.status(200).json({
    success: true,
    message: 'Profile fetched',
    data: publicUser(req.user),
  })
})

module.exports = router
