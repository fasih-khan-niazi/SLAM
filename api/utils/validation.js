const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

function normalizeEmail(value) {
  return String(value || '').trim().toLowerCase()
}

function normalizePhone(value) {
  return String(value || '').replace(/[\s\-()]/g, '').trim()
}

function validateRegister({ name, email, password, phone }) {
  if (!name || !String(name).trim()) {
    return 'Name is required'
  }
  if (String(name).trim().length < 2) {
    return 'Name must be at least 2 characters'
  }

  const normalizedEmail = normalizeEmail(email)
  if (!normalizedEmail) {
    return 'Email is required'
  }
  if (!EMAIL_RE.test(normalizedEmail)) {
    return 'Enter a valid email address'
  }

  if (!password) {
    return 'Password is required'
  }
  if (String(password).length < 8) {
    return 'Password must be at least 8 characters'
  }

  const normalizedPhone = normalizePhone(phone)
  if (!normalizedPhone) {
    return 'Phone is required'
  }
  if (!/^\+?\d{10,15}$/.test(normalizedPhone)) {
    return 'Enter a valid phone number (10–15 digits)'
  }

  return null
}

function validateLogin({ email, password }) {
  if (!normalizeEmail(email) || !password) {
    return 'Email and password are required'
  }
  return null
}

module.exports = {
  normalizeEmail,
  normalizePhone,
  validateRegister,
  validateLogin,
}
