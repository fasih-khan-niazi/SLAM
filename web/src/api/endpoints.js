import { api, apiUpload } from './client'

export function getConfig() {
  return api('/api/config')
}

export function getPlans() {
  return api('/api/plans')
}

export function registerAccount(payload) {
  return api('/api/auth/register', { method: 'POST', body: payload })
}

export function loginAccount(payload) {
  return api('/api/auth/login', { method: 'POST', body: payload })
}

export function logoutAccount(token) {
  return api('/api/auth/logout', { method: 'POST', token })
}

export function getMe(token) {
  return api('/api/auth/me', { token })
}

export function requestPasswordReset(email) {
  return api('/api/auth/forgot-password', { method: 'POST', body: { email } })
}

export function resetPassword(payload) {
  return api('/api/auth/reset-password', { method: 'POST', body: payload })
}

export function changePassword(token, payload) {
  return api('/api/auth/change-password', { method: 'POST', body: payload, token })
}

export function getSubscription(token) {
  return api('/api/user/subscription', { token })
}

export function subscribeToPlan(token, planId) {
  return api('/api/subscribe', { method: 'POST', body: { plan_id: planId }, token })
}

export function listPayments(token) {
  return api('/api/payments/my', { token })
}

export function listNotifications(token) {
  return api('/api/notifications', { token })
}

export function markNotificationRead(token, id) {
  return api(`/api/notifications/${id}/read`, { method: 'PATCH', token })
}

export function submitPayment(token, { subscriptionId, paymentMethod, transactionId, screenshot }) {
  return apiUpload('/api/payments/submit', {
    token,
    fields: {
      subscription_id: subscriptionId,
      payment_method: paymentMethod,
      transaction_id: transactionId,
    },
    file: screenshot,
  })
}

/* ——— Admin (role === admin) ——— */

export function adminStats(token) {
  return api('/api/admin/stats', { token })
}

export function adminListPayments(token, { status } = {}) {
  const q = status ? `?status=${encodeURIComponent(status)}` : ''
  return api(`/api/admin/payments${q}`, { token })
}

export function adminApprovePayment(token, id) {
  return api(`/api/admin/payments/${id}/approve`, { method: 'PATCH', token })
}

export function adminRejectPayment(token, id) {
  return api(`/api/admin/payments/${id}/reject`, { method: 'PATCH', token })
}

export function adminListPlans(token) {
  return api('/api/admin/plans', { token })
}

export function adminCreatePlan(token, body) {
  return api('/api/admin/plans', { method: 'POST', body, token })
}

export function adminUpdatePlan(token, id, body) {
  return api(`/api/admin/plans/${id}`, { method: 'PATCH', body, token })
}

export function adminDeletePlan(token, id) {
  return api(`/api/admin/plans/${id}`, { method: 'DELETE', token })
}

export function adminGetConfig(token) {
  return api('/api/admin/config', { token })
}

export function adminUpdateConfig(token, body) {
  return api('/api/admin/config', { method: 'PATCH', body, token })
}

export function adminListUsers(token, { q, role } = {}) {
  const params = new URLSearchParams()
  if (q) params.set('q', q)
  if (role) params.set('role', role)
  const qs = params.toString()
  return api(`/api/admin/users${qs ? `?${qs}` : ''}`, { token })
}

export function adminUpdateUser(token, id, body) {
  return api(`/api/admin/users/${id}`, { method: 'PATCH', body, token })
}

export function adminDeleteUser(token, id) {
  return api(`/api/admin/users/${id}`, { method: 'DELETE', token })
}

export function adminListSubscriptions(token, { status, userId } = {}) {
  const params = new URLSearchParams()
  if (status) params.set('status', status)
  if (userId) params.set('user_id', String(userId))
  const qs = params.toString()
  return api(`/api/admin/subscriptions${qs ? `?${qs}` : ''}`, { token })
}

export function adminUpdateSubscription(token, id, body) {
  return api(`/api/admin/subscriptions/${id}`, { method: 'PATCH', body, token })
}

export function adminListLocationLogs(token, { userId } = {}) {
  const q = userId ? `?user_id=${encodeURIComponent(userId)}` : ''
  return api(`/api/admin/location-logs${q}`, { token })
}
