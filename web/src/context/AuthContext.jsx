import { createContext, useContext, useEffect, useMemo, useState } from 'react'
import { setUnauthorizedHandler } from '../api/client'
import {
  changePassword as changePasswordRequest,
  getMe,
  loginAccount,
  logoutAccount,
  registerAccount,
} from '../api/endpoints'

const AuthContext = createContext(null)
const TOKEN_KEY = 'slam_token'
const USER_KEY = 'slam_user'

function readStoredToken() {
  return sessionStorage.getItem(TOKEN_KEY) || ''
}

export function homePathForUser(user) {
  return user?.role === 'admin' ? '/admin' : '/'
}

export function AuthProvider({ children }) {
  const [token, setToken] = useState(() => readStoredToken())
  const [user, setUser] = useState(() => {
    try {
      return JSON.parse(sessionStorage.getItem(USER_KEY) || 'null')
    } catch {
      return null
    }
  })
  const [subscription, setSubscription] = useState(null)
  const [ready, setReady] = useState(false)
  const [sessionNotice, setSessionNotice] = useState('')

  function persist(nextToken, nextUser, nextSubscription) {
    setToken(nextToken || '')
    setUser(nextUser)
    setSubscription(nextSubscription || null)
    if (nextToken) sessionStorage.setItem(TOKEN_KEY, nextToken)
    else sessionStorage.removeItem(TOKEN_KEY)
    if (nextUser) sessionStorage.setItem(USER_KEY, JSON.stringify(nextUser))
    else sessionStorage.removeItem(USER_KEY)
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
  }

  // A rejected token (expired, or the password changed elsewhere) signs the user out cleanly.
  useEffect(() => {
    setUnauthorizedHandler(() => {
      persist('', null, null)
      setSessionNotice('Your session ended. Please sign in again.')
    })
    return () => setUnauthorizedHandler(null)
  }, [])

  useEffect(() => {
    getMe(token || undefined)
      .then((res) => {
        persist(token || res.data?.token || '', res.data.user, res.data.subscription)
      })
      .catch(() => {
        persist('', null, null)
      })
      .finally(() => setReady(true))
  }, [])

  async function login(email, password) {
    const res = await loginAccount({ email, password })
    setSessionNotice('')
    persist(res.data.token, res.data.user, res.data.subscription)
    return res
  }

  async function register(payload) {
    const res = await registerAccount(payload)
    persist(res.data.token, res.data.user, res.data.subscription)
    return res
  }

  async function logout() {
    try {
      await logoutAccount(token || undefined)
    } catch {
      // Always clear local session.
    }
    persist('', null, null)
  }

  async function refresh() {
    const res = await getMe(token || undefined)
    persist(token || res.data?.token || '', res.data.user, res.data.subscription)
    return res.data.subscription
  }

  async function changePassword(currentPassword, newPassword) {
    const res = await changePasswordRequest(token, {
      current_password: currentPassword,
      new_password: newPassword,
    })
    // Other devices are signed out by the server; this one gets a fresh token.
    if (res?.data?.token) persist(res.data.token, user, subscription)
  }

  function clearSessionNotice() {
    setSessionNotice('')
  }

  const isAdmin = user?.role === 'admin'

  const value = useMemo(() => ({
    token,
    user,
    subscription,
    ready,
    isAdmin,
    sessionNotice,
    clearSessionNotice,
    login,
    register,
    logout,
    refresh,
    changePassword,
  }), [token, user, subscription, ready, isAdmin, sessionNotice])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider')
  return ctx
}
