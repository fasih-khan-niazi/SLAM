const express = require('express')
const { protect } = require('../middleware/auth')
const { Notification } = require('../models')
const { ok, fail } = require('../utils/http')

const router = express.Router()

router.get('/notifications', protect, async (req, res) => {
  try {
    const items = await Notification.findAll({
      where: { user_id: req.user.id },
      order: [['createdAt', 'DESC']],
      limit: 50,
    })
    return ok(res, 'Notifications fetched', {
      notifications: items.map((item) => ({
        id: item.id,
        title: item.title,
        body: item.body,
        kind: item.kind,
        read: Boolean(item.read_at),
        created_at: item.createdAt,
      })),
    })
  } catch (err) {
    console.error('Notifications error:', err)
    return fail(res, 500, 'Unable to load notifications')
  }
})

router.patch('/notifications/:id/read', protect, async (req, res) => {
  try {
    const item = await Notification.findOne({
      where: { id: req.params.id, user_id: req.user.id },
    })
    if (!item) return fail(res, 404, 'Notification not found')
    if (!item.read_at) await item.update({ read_at: new Date() })
    return ok(res, 'Notification updated', { id: item.id, read: true })
  } catch (err) {
    console.error('Notification read error:', err)
    return fail(res, 500, 'Unable to update notification')
  }
})

module.exports = router
