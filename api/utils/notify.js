const { Notification, User } = require('../models')

async function notifyUser(userId, title, body, kind = 'system') {
  if (!userId) return
  await Notification.create({ user_id: userId, title, body, kind })
}

async function notifyAdmins(title, body, kind = 'payment') {
  const admins = await User.findAll({ where: { role: 'admin' } })
  await Promise.all(admins.map((admin) => notifyUser(admin.id, title, body, kind)))
}

module.exports = { notifyUser, notifyAdmins }
