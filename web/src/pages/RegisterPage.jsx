import { useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { ApiError } from '../api/client'
import { Button } from '../components/Button'
import { Field } from '../components/Field'
import { Modal } from '../components/Modal'

export function RegisterPage() {
  const { user, register } = useAuth()
  const navigate = useNavigate()
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [phone, setPhone] = useState('')
  const [password, setPassword] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)

  if (user) return <Navigate to="/" replace />

  async function onSubmit(event) {
    event.preventDefault()
    setLoading(true)
    try {
      await register({
        name: name.trim(),
        email: email.trim(),
        phone: phone.trim(),
        password,
      })
      navigate('/', { replace: true })
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not create the account')
    } finally {
      setLoading(false)
    }
  }

  return (
    <main className="page page-narrow">
      <h1>Create account</h1>
      <p className="lede">Your phone stays findable over SMS. This account is for plans and usage.</p>
      <form className="stack-lg" style={{ marginTop: 28 }} onSubmit={onSubmit}>
        <Field id="name" label="Full name" value={name} onChange={setName} autoComplete="name" required />
        <Field id="email" label="Email" type="email" value={email} onChange={setEmail} autoComplete="email" required />
        <Field id="phone" label="Phone" type="tel" value={phone} onChange={setPhone} autoComplete="tel" required />
        <Field id="password" label="Password (8+ characters)" type="password" value={password} onChange={setPassword} autoComplete="new-password" required />
        <Button type="submit" loading={loading} block>Create account</Button>
      </form>
      <p className="muted" style={{ marginTop: 16 }}>
        Already have an account? <Link to="/login">Sign in</Link>
      </p>
      <p className="muted">
        By continuing you agree to the <Link to="/terms">terms</Link>.
      </p>
      {error ? (
        <Modal title="Could not register" message={error} confirmLabel="OK" onConfirm={() => setError(null)} onDismiss={() => setError(null)} />
      ) : null}
    </main>
  )
}
