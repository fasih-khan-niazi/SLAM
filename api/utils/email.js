const nodemailer = require('nodemailer')

function createTransporter() {
  if (!process.env.EMAIL_USER || !process.env.EMAIL_PASS) {
    return null
  }

  return nodemailer.createTransport({
    service: 'gmail',
    auth: {
      user: process.env.EMAIL_USER,
      pass: process.env.EMAIL_PASS,
    },
  })
}

const transporter = createTransporter()

async function isEmailEnabled() {
  try {
    const { getSystemConfig } = require('./config')
    const config = await getSystemConfig()
    return config.email_enabled !== false
  } catch (_err) {
    return true
  }
}

/**
 * @param {string|string[]} to
 * @param {string} subject
 * @param {string} text
 * @param {string} [html]
 */
async function sendEmail(to, subject, text, html) {
  if (!(await isEmailEnabled())) {
    console.warn('Email skipped: disabled in system config')
    return { sent: false, reason: 'disabled' }
  }

  if (!transporter) {
    console.warn('Email skipped: EMAIL_USER or EMAIL_PASS is not set')
    return { sent: false, reason: 'missing_credentials' }
  }

  const recipients = (Array.isArray(to) ? to : [to])
    .map((item) => String(item || '').trim())
    .filter(Boolean)

  if (recipients.length === 0) {
    console.warn('Email skipped: no recipients')
    return { sent: false, reason: 'no_recipients' }
  }

  try {
    await transporter.sendMail({
      from: `"SLAM" <${process.env.EMAIL_USER}>`,
      to: recipients.join(', '),
      subject,
      text,
      ...(html ? { html } : {}),
    })
    return { sent: true, recipients }
  } catch (err) {
    console.error('Email send failed:', err.message)
    return { sent: false, reason: err.message }
  }
}

/**
 * Email every active admin account that opts in (role=admin, not suspended/deactivated,
 * notify_admin_emails=true). The default/owner admin seeded from ADMIN_EMAIL is excluded
 * by default (see seedAdmin in models/index.js); every other admin, including a future
 * promoted one, is included unless changed.
 */
async function sendEmailToAdmins(subject, text, html) {
  const { User } = require('../models')
  const { isAccountBlocked } = require('./accountStatus')
  const admins = await User.findAll({ where: { role: 'admin', notify_admin_emails: true } })
  const recipients = admins
    .filter((admin) => !isAccountBlocked(admin))
    .map((admin) => admin.email)
    .filter(Boolean)

  return sendEmail(recipients, subject, text, html)
}

module.exports = { sendEmail, sendEmailToAdmins }
