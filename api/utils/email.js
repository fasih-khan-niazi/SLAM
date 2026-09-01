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

async function sendEmail(to, subject, text) {
  if (!transporter) {
    console.warn('Email skipped: EMAIL_USER or EMAIL_PASS is not set')
    return
  }

  try {
    await transporter.sendMail({
      from: `"SLAM" <${process.env.EMAIL_USER}>`,
      to,
      subject,
      text,
    })
  } catch (err) {
    console.error('Email send failed:', err.message)
  }
}

module.exports = { sendEmail }
