const jwt = require('jsonwebtoken')
const { User } = require('../models')

function readCookieToken(req) {
  const header = req.headers.cookie || ''
  const parts = header.split(';')
  for (const part of parts) {
    const [rawKey, ...rest] = part.trim().split('=')
    if (rawKey === 'slam_token') {
      return decodeURIComponent(rest.join('=') || '')
    }
  }
  return ''
}

function extractToken(req) {
  const authHeader = req.headers.authorization
  if (authHeader && authHeader.startsWith('Bearer ')) {
    return authHeader.split(' ')[1]
  }
  return readCookieToken(req)
}

const protect = async (req, res, next) => {
  try {
    const token = extractToken(req)
    if (!token) {
      return res.status(401).json({
        success: false,
        message: 'No token provided. Please sign in first.',
        data: null,
      })
    }

    const decoded = jwt.verify(token, process.env.JWT_SECRET)
    const user = await User.findByPk(decoded.id)

    if (!user) {
      return res.status(401).json({
        success: false,
        message: 'Account no longer exists.',
        data: null,
      })
    }

    const { isAccountBlocked, accountBlockMessage } = require('../utils/accountStatus')
    if (isAccountBlocked(user)) {
      return res.status(403).json({
        success: false,
        message: accountBlockMessage(user),
        data: null,
      })
    }

    req.user = user
    next()
  } catch (err) {
    return res.status(401).json({
      success: false,
      message: 'Token is invalid or expired. Please sign in again.',
      data: null,
    })
  }
}

const requireAdmin = (req, res, next) => {
  if (!req.user || req.user.role !== 'admin') {
    return res.status(403).json({
      success: false,
      message: 'Admin access required.',
      data: null,
    })
  }
  next()
}

module.exports = { protect, requireAdmin, extractToken, readCookieToken }
