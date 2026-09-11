const express = require('express')
const bcrypt = require('bcryptjs')
const jwt = require('jsonwebtoken')
const { sequelize, User } = require('../models')
const { protect } = require('../middleware/auth')
const { rateLimit, loginAttemptGuard, recordFailedLogin, clearFailedLogins } = require('../middleware/rateLimit')
const { ok, fail } = require('../utils/http')
const {
  normalizeEmail,
  normalizePhone,
  validateRegister,
  validateLogin,
} = require('../utils/validation')
const {
  activateFreePlan,
  getCurrentSubscription,
  formatSubscription,
  getActivePlanInfo,
} = require('../utils/subscription')

const router = express.Router()

function generateToken(userId) {
  return jwt.sign(
    { id: userId },
    process.env.JWT_SECRET,
    { expiresIn: process.env.JWT_EXPIRES_IN || '7d' }
  )
}

const registerLimit = rateLimit({
  windowMs: 15 * 60 * 1000,
  max: 8,
  message: 'Too many accounts created from this network. Try again in a few minutes.',
})

function publicUser(user) {
  return {
    id: user.id,
    name: user.name,
    email: user.email,
    phone: user.phone,
    role: user.role,
  }
}

function trackingPinPayload(user) {
  if (!user.pin_salt || !user.pin_verifier) return null
  return {
    salt: user.pin_salt,
    verifier: user.pin_verifier,
  }
}

async function subscriptionPayload(userId) {
  const current = await getCurrentSubscription(userId)

  if (current && current.status !== 'active') {
    const activeInfo = await getActivePlanInfo(userId)
    return {
      ...formatSubscription(current, current.plan, current.requests_used),
      active_plan: formatSubscription(
        activeInfo.subscription,
        activeInfo.plan,
        activeInfo.requests_used
      ),
    }
  }

  if (current) {
    return formatSubscription(current, current.plan, current.requests_used)
  }

  const info = await getActivePlanInfo(userId)
  return formatSubscription(info.subscription, info.plan, info.requests_used)
}

router.post('/register', registerLimit, async (req, res) => {
  const error = validateRegister(req.body)
  if (error) return fail(res, 400, error)

  const name = String(req.body.name).trim()
  const email = normalizeEmail(req.body.email)
  const phone = normalizePhone(req.body.phone)
  const password = req.body.password

  const transaction = await sequelize.transaction()

  try {
    const existing = await User.findOne({ where: { email }, transaction })
    if (existing) {
      await transaction.rollback()
      return fail(res, 400, 'An account with this email already exists')
    }

    const user = await User.create(
      {
        name,
        email,
        password_hash: await bcrypt.hash(password, 10),
        phone,
      },
      { transaction }
    )

    await activateFreePlan(user.id, transaction)
    await transaction.commit()

    const subscription = await subscriptionPayload(user.id)

    return ok(
      res,
      'Account created',
      {
        token: generateToken(user.id),
        user: publicUser(user),
        subscription,
        tracking_pin: trackingPinPayload(user),
      },
      201
    )
  } catch (err) {
    await transaction.rollback()
    console.error('Register error:', err)
    return fail(res, 500, 'Unable to create account')
  }
})

router.post('/login', loginAttemptGuard, async (req, res) => {
  const error = validateLogin(req.body)
  if (error) return fail(res, 400, error)

  try {
    const email = normalizeEmail(req.body.email)
    const user = await User.findOne({ where: { email } })
    if (!user) {
      recordFailedLogin(req)
      return fail(res, 401, 'Invalid email or password')
    }

    const isMatch = await bcrypt.compare(req.body.password, user.password_hash)
    if (!isMatch) {
      recordFailedLogin(req)
      return fail(res, 401, 'Invalid email or password')
    }

    clearFailedLogins(req)
    const subscription = await subscriptionPayload(user.id)

    return ok(res, 'Signed in', {
      token: generateToken(user.id),
      user: publicUser(user),
      subscription,
      tracking_pin: trackingPinPayload(user),
    })
  } catch (err) {
    console.error('Login error:', err)
    return fail(res, 500, 'Unable to sign in')
  }
})

router.get('/me', protect, async (req, res) => {
  try {
    const subscription = await subscriptionPayload(req.user.id)
    return ok(res, 'Profile fetched', {
      user: publicUser(req.user),
      subscription,
      tracking_pin: trackingPinPayload(req.user),
    })
  } catch (err) {
    console.error('Profile error:', err)
    return fail(res, 500, 'Unable to load profile')
  }
})

router.put('/pin', protect, async (req, res) => {
  try {
    const salt = String(req.body.pin_salt || '').trim().toLowerCase()
    const verifier = String(req.body.pin_verifier || '').trim().toLowerCase()
    if (!/^[a-f0-9]{32}$/.test(salt) || !/^[a-f0-9]{64}$/.test(verifier)) {
      return fail(res, 400, 'Invalid PIN payload')
    }
    await User.update(
      { pin_salt: salt, pin_verifier: verifier },
      { where: { id: req.user.id } }
    )
    return ok(res, 'Tracking PIN saved', {
      tracking_pin: { salt, verifier },
    })
  } catch (err) {
    console.error('PIN save error:', err)
    return fail(res, 500, 'Unable to save tracking PIN')
  }
})

module.exports = router
