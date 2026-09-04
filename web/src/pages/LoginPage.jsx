import { useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { ApiError } from '../api/client'
import { Button } from '../components/Button'
import { Field } from '../components/Field'
import { Logo } from '../components/Logo'
import { Modal } from '../components/Modal'

export function LoginPage() {
  const { user, login } = useAuth()
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)

  if (user) return <Navigate to="/" replace />

  async function onSubmit(event) {
    event.preventDefault()
    setLoading(true)
    try {
      await login(email.trim(), password)
      navigate('/', { replace: true })
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not sign in')
    } finally {
      setLoading(false)
    }
  }

  return (
    <main className="page page-narrow">
      <div style={{ color: 'var(--primary)', marginBottom: 16 }}><Logo size={40} /></div>
      <h1>Welcome back</h1>
      <p className="lede">Sign in to manage your plan. Tracking still works over SMS without a browser.</p>
      <form className="stack-lg" style={{ marginTop: 28 }} onSubmit={onSubmit}>
        <Field id="email" label="Email" type="email" value={email} onChange={setEmail} autoComplete="email" required />
        <Field id="password" label="Password" type="password" value={password} onChange={setPassword} autoComplete="current-password" required />
        <Button type="submit" loading={loading} block>Sign in</Button>
      </form>
      <p className="muted" style={{ marginTop: 16 }}>
        New here? <Link to="/register">Create an account</Link>
      </p>
      {error ? (
        <Modal title="Sign in failed" message={error} confirmLabel="OK" onConfirm={() => setError(null)} onDismiss={() => setError(null)} />
      ) : null}
    </main>
  )
}
