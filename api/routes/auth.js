const express = require('express')
const bcrypt = require('bcryptjs')
const crypto = require('crypto')
const jwt = require('jsonwebtoken')
const { sequelize, User } = require('../models')
const { protect } = require('../middleware/auth')
const { rateLimit, loginAttemptGuard, recordFailedLogin, clearFailedLogins } = require('../middleware/rateLimit')
const { ok, fail } = require('../utils/http')
const { sendEmail } = require('../utils/email')
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

function cookieOptions() {
  const secure = process.env.NODE_ENV === 'production' || String(process.env.COOKIE_SECURE || '') === '1'
  const maxAge = 7 * 24 * 60 * 60
  return [
    'Path=/',
    'HttpOnly',
    'SameSite=Lax',
    `Max-Age=${maxAge}`,
    secure ? 'Secure' : '',
  ].filter(Boolean).join('; ')
}

function setAuthCookie(res, token) {
  res.setHeader('Set-Cookie', `slam_token=${encodeURIComponent(token)}; ${cookieOptions()}`)
}

function clearAuthCookie(res) {
  const secure = process.env.NODE_ENV === 'production' || String(process.env.COOKIE_SECURE || '') === '1'
  res.setHeader(
    'Set-Cookie',
    `slam_token=; Path=/; HttpOnly; SameSite=Lax; Max-Age=0${secure ? '; Secure' : ''}`,
  )
}

const registerLimit = rateLimit({
  windowMs: 15 * 60 * 1000,
  max: 8,
  message: 'Too many accounts created from this network. Try again in a few minutes.',
})

const forgotLimit = rateLimit({
  windowMs: 15 * 60 * 1000,
  max: 5,
  message: 'Too many reset requests. Try again in a few minutes.',
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

function strongPasswordError(password) {
  if (!password || String(password).length < 8) return 'Password must be at least 8 characters'
  if (!/[A-Za-z]/.test(password) || !/\d/.test(password) || !/[^A-Za-z0-9]/.test(password)) {
    return 'Password needs a letter, a digit, and a special character'
  }
  if (!/[a-z]/.test(password) || !/[A-Z]/.test(password)) {
    return 'Password needs upper and lower case letters'
  }
  return null
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
    const token = generateToken(user.id)
    setAuthCookie(res, token)

    return ok(
      res,
      'Account created',
      {
        token,
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
    const token = generateToken(user.id)
    setAuthCookie(res, token)

    return ok(res, 'Signed in', {
      token,
      user: publicUser(user),
      subscription,
      tracking_pin: trackingPinPayload(user),
    })
  } catch (err) {
    console.error('Login error:', err)
    return fail(res, 500, 'Unable to sign in')
  }
})

router.post('/logout', async (_req, res) => {
  clearAuthCookie(res)
  return ok(res, 'Signed out', {})
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

router.post('/forgot-password', forgotLimit, async (req, res) => {
  try {
    const email = normalizeEmail(req.body.email)
    if (!email) return fail(res, 400, 'Email is required')

    const user = await User.findOne({ where: { email } })
    // Always return the same message to avoid account enumeration.
    const generic = 'If that email exists, reset instructions were sent.'
    if (!user) return ok(res, generic, {})

    const resetToken = jwt.sign(
      { id: user.id, purpose: 'password_reset', nonce: crypto.randomBytes(8).toString('hex') },
      process.env.JWT_SECRET,
      { expiresIn: '1h' },
    )
    const frontend = String(process.env.FRONTEND_URL || 'http://localhost:5173').replace(/\/$/, '')
    const link = `${frontend}/reset-password?token=${encodeURIComponent(resetToken)}`
    await sendEmail(
      user.email,
      'SLAM password reset',
      `Hi ${user.name},\n\nUse this link within one hour to reset your password:\n${link}\n\nIf you did not ask for this, ignore the email.\n`,
    )
    return ok(res, generic, {})
  } catch (err) {
    console.error('Forgot password error:', err)
    return fail(res, 500, 'Unable to start password reset')
  }
})

router.post('/reset-password', async (req, res) => {
  try {
    const token = String(req.body.token || '').trim()
    const password = req.body.password
    const passwordError = strongPasswordError(password)
    if (!token) return fail(res, 400, 'Reset token is required')
    if (passwordError) return fail(res, 400, passwordError)

    let decoded
    try {
      decoded = jwt.verify(token, process.env.JWT_SECRET)
    } catch {
      return fail(res, 400, 'Reset link is invalid or expired')
    }
    if (decoded.purpose !== 'password_reset' || !decoded.id) {
      return fail(res, 400, 'Reset link is invalid or expired')
    }

    const user = await User.findByPk(decoded.id)
    if (!user) return fail(res, 400, 'Reset link is invalid or expired')

    await user.update({ password_hash: await bcrypt.hash(password, 10) })
    clearAuthCookie(res)
    return ok(res, 'Password updated', {})
  } catch (err) {
    console.error('Reset password error:', err)
    return fail(res, 500, 'Unable to reset password')
  }
})

router.post('/change-password', protect, async (req, res) => {
  try {
    const currentPassword = req.body.current_password
    const newPassword = req.body.new_password
    if (!currentPassword || !newPassword) {
      return fail(res, 400, 'Current and new password are required')
    }
    const passwordError = strongPasswordError(newPassword)
    if (passwordError) return fail(res, 400, passwordError)

    const matches = await bcrypt.compare(currentPassword, req.user.password_hash)
    if (!matches) return fail(res, 401, 'Current password is incorrect')

    await req.user.update({ password_hash: await bcrypt.hash(newPassword, 10) })
    return ok(res, 'Password updated', {})
  } catch (err) {
    console.error('Change password error:', err)
    return fail(res, 500, 'Unable to change password')
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
