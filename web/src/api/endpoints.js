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

// Week 2: subscribe(), submitPayment(), listPayments() land here.
