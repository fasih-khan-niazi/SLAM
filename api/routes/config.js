const express = require('express')
const { ok } = require('../utils/http')

const router = express.Router()

/**
 * Public product defaults. Week 2 SystemConfig will own these values.
 * Only safe fields — never secrets, SMTP, or Cloudinary credentials.
 */
router.get('/config', (req, res) => {
  return ok(res, 'Config fetched', {
    sms_prefix: 'SLAM',
    pin_min_length: 4,
    pin_max_length: 6,
    maintenance: false,
    payments_enabled: false,
    maps_enabled: false,
    email_enabled: Boolean(process.env.EMAIL_USER && process.env.EMAIL_PASS),
  })
})

module.exports = router
