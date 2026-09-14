const express = require('express')
const { protect } = require('../middleware/auth')
const { TrustedNumber } = require('../models')
const { ok, fail } = require('../utils/http')
const { normalizePhone } = require('../utils/validation')
const { getActivePlanInfo } = require('../utils/subscription')

const router = express.Router()

function digitsOnly(value) {
  return String(value || '').replace(/\D/g, '')
}

function formatRow(row) {
  return {
    id: row.id,
    label: row.label || '',
    phone: row.phone,
    normalized: row.normalized,
  }
}

router.get('/trusted-numbers', protect, async (req, res) => {
  try {
    const rows = await TrustedNumber.findAll({
      where: { user_id: req.user.id },
      order: [['createdAt', 'ASC']],
    })
    return ok(res, 'Trusted numbers fetched', { numbers: rows.map(formatRow) })
  } catch (err) {
    console.error('Get trusted numbers error:', err)
    return fail(res, 500, 'Unable to load trusted numbers')
  }
})

router.put('/trusted-numbers', protect, async (req, res) => {
  try {
    const list = Array.isArray(req.body?.numbers) ? req.body.numbers : null
    if (!list) return fail(res, 400, 'numbers array is required')

    const info = await getActivePlanInfo(req.user.id)
    const cap = Math.max(1, info.plan?.max_contacts || info.max_contacts || 1)
    if (list.length > cap) {
      return fail(res, 400, `Your plan allows up to ${cap} trusted number${cap === 1 ? '' : 's'}`)
    }

    const cleaned = []
    const seen = new Set()
    for (const item of list) {
      const phone = normalizePhone(item.phone || item.number)
      const normalized = digitsOnly(phone)
      if (!normalized || normalized.length < 10) {
        return fail(res, 400, 'Each trusted number must be a valid phone number')
      }
      if (seen.has(normalized)) continue
      seen.add(normalized)
      cleaned.push({
        user_id: req.user.id,
        label: String(item.label || '').trim().slice(0, 64),
        phone,
        normalized,
      })
    }

    await TrustedNumber.destroy({ where: { user_id: req.user.id } })
    if (cleaned.length) {
      await TrustedNumber.bulkCreate(cleaned)
    }

    const rows = await TrustedNumber.findAll({
      where: { user_id: req.user.id },
      order: [['createdAt', 'ASC']],
    })
    return ok(res, 'Trusted numbers saved', { numbers: rows.map(formatRow) })
  } catch (err) {
    console.error('Put trusted numbers error:', err)
    return fail(res, 500, 'Unable to save trusted numbers')
  }
})

module.exports = router
