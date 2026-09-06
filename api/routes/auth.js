const express = require('express')
const bcrypt = require('bcryptjs')
const jwt = require('jsonwebtoken')
const { sequelize, User } = require('../models')
const { protect } = require('../middleware/auth')
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

router.post('/register', async (req, res) => {
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
      { token: generateToken(user.id), user: publicUser(user), subscription },
      201
    )
  } catch (err) {
    await transaction.rollback()
    console.error('Register error:', err)
    return fail(res, 500, 'Unable to create account')
  }
})

router.post('/login', async (req, res) => {
  const error = validateLogin(req.body)
  if (error) return fail(res, 400, error)

  try {
    const email = normalizeEmail(req.body.email)
    const user = await User.findOne({ where: { email } })
    if (!user) return fail(res, 401, 'Invalid email or password')

    const isMatch = await bcrypt.compare(req.body.password, user.password_hash)
    if (!isMatch) return fail(res, 401, 'Invalid email or password')

    const subscription = await subscriptionPayload(user.id)

    return ok(res, 'Signed in', {
      token: generateToken(user.id),
      user: publicUser(user),
      subscription,
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
    })
  } catch (err) {
    console.error('Profile error:', err)
    return fail(res, 500, 'Unable to load profile')
  }
})

module.exports = router
