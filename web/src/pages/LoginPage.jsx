import { useState } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { useAuth, homePathForUser } from '../context/AuthContext'
import { ApiError } from '../api/client'
import { Button } from '../components/Button'
import { Field } from '../components/Field'
import { Logo } from '../components/Logo'
import { Modal } from '../components/Modal'

export function LoginPage() {
  const { user, login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)

  if (user) {
    const dest = location.state?.from?.startsWith('/admin') && user.role === 'admin'
      ? location.state.from
      : homePathForUser(user)
    return <Navigate to={dest} replace />
  }

  async function onSubmit(event) {
    event.preventDefault()
    setLoading(true)
    try {
      const res = await login(email.trim(), password)
      const nextUser = res.data.user
      const from = location.state?.from
      if (from && (nextUser.role === 'admin' ? from.startsWith('/admin') : !from.startsWith('/admin'))) {
        navigate(from, { replace: true })
      } else {
        navigate(homePathForUser(nextUser), { replace: true })
      }
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not sign in')
    } finally {
      setLoading(false)
    }
  }

  return (
    <main className="page page-narrow">
      <div className="auth-brand">
        <span style={{ color: 'var(--primary)' }}><Logo size={48} /></span>
        <div>
          <h2>SLAM</h2>
          <p>Secure location by SMS</p>
        </div>
      </div>
      <h1>Welcome back</h1>
      <p className="lede">Sign in to manage your plan and payments.</p>
      <form className="stack-lg" style={{ marginTop: 28 }} onSubmit={onSubmit}>
        <Field id="email" label="Email" type="email" value={email} onChange={setEmail} autoComplete="email" required />
        <Field id="password" label="Password" type="password" value={password} onChange={setPassword} autoComplete="current-password" required />
        <Button type="submit" loading={loading} block>Sign in</Button>
      </form>
      <p className="muted" style={{ marginTop: 16 }}>
        <Link to="/forgot-password">Forgot password?</Link>
      </p>
      <p className="muted">
        New here? <Link to="/register">Create an account</Link>
        {' · '}
        <Link to="/terms">Terms</Link>
      </p>
      {error ? (
        <Modal title="Sign in failed" message={error} confirmLabel="OK" onConfirm={() => setError(null)} onDismiss={() => setError(null)} />
      ) : null}
    </main>
  )
}
