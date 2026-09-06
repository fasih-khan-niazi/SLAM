import { api } from './client'

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

export function getMe(token) {
  return api('/api/auth/me', { token })
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

// Phase 10 UI will POST multipart (screenshot + transaction_id) to /api/payments/submit.
