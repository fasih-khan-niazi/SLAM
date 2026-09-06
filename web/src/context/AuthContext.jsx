import { createContext, useContext, useEffect, useMemo, useState } from 'react'
import { getMe, loginAccount, registerAccount } from '../api/endpoints'

const AuthContext = createContext(null)
const TOKEN_KEY = 'slam_token'
const USER_KEY = 'slam_user'

export function AuthProvider({ children }) {
  const [token, setToken] = useState(() => localStorage.getItem(TOKEN_KEY) || '')
  const [user, setUser] = useState(() => {
    try {
      return JSON.parse(localStorage.getItem(USER_KEY) || 'null')
    } catch {
      return null
    }
  })
  const [subscription, setSubscription] = useState(null)
  const [ready, setReady] = useState(!localStorage.getItem(TOKEN_KEY))

  function persist(nextToken, nextUser, nextSubscription) {
    setToken(nextToken)
    setUser(nextUser)
    setSubscription(nextSubscription || null)
    if (nextToken) localStorage.setItem(TOKEN_KEY, nextToken)
    else localStorage.removeItem(TOKEN_KEY)
    if (nextUser) localStorage.setItem(USER_KEY, JSON.stringify(nextUser))
    else localStorage.removeItem(USER_KEY)
  }

  useEffect(() => {
    if (!token) {
      setReady(true)
      return
    }
    getMe(token)
      .then((res) => {
        persist(token, res.data.user, res.data.subscription)
      })
      .catch(() => {
        persist('', null, null)
      })
      .finally(() => setReady(true))
  }, [])

  async function login(email, password) {
    const res = await loginAccount({ email, password })
    persist(res.data.token, res.data.user, res.data.subscription)
    return res
  }

  async function register(payload) {
    const res = await registerAccount(payload)
    persist(res.data.token, res.data.user, res.data.subscription)
    return res
  }

  function logout() {
    persist('', null, null)
  }

  const value = useMemo(() => ({
    token,
    user,
    subscription,
    ready,
    login,
    register,
    logout,
  }), [token, user, subscription, ready])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider')
  return ctx
}
