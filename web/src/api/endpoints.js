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
