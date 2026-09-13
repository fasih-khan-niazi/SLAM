import { useState } from 'react'
import { Link } from 'react-router-dom'
import { ApiError } from '../api/client'
import { requestPasswordReset } from '../api/endpoints'
import { Button } from '../components/Button'
import { Field } from '../components/Field'
import { Logo } from '../components/Logo'
import { Modal } from '../components/Modal'

export function ForgotPasswordPage() {
  const [email, setEmail] = useState('')
  const [loading, setLoading] = useState(false)
  const [notice, setNotice] = useState(null)
  const [error, setError] = useState(null)

  async function onSubmit(event) {
    event.preventDefault()
    setLoading(true)
    try {
      const res = await requestPasswordReset(email.trim())
      setNotice(res.message || 'If that email exists, reset instructions were sent.')
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not start password reset')
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
      <h1>Forgot password</h1>
      <p className="lede">Enter your account email. We will send a reset link when email is configured.</p>
      <form className="stack-lg" style={{ marginTop: 28 }} onSubmit={onSubmit}>
        <Field id="email" label="Email" type="email" value={email} onChange={setEmail} autoComplete="email" required />
        <Button type="submit" loading={loading} block>Send reset link</Button>
      </form>
      <p className="muted" style={{ marginTop: 16 }}>
        <Link to="/login">Back to sign in</Link>
      </p>
      {notice ? (
        <Modal title="Check your email" message={notice} confirmLabel="OK" onConfirm={() => setNotice(null)} onDismiss={() => setNotice(null)} />
      ) : null}
      {error ? (
        <Modal title="Reset failed" message={error} confirmLabel="OK" onConfirm={() => setError(null)} onDismiss={() => setError(null)} />
      ) : null}
    </main>
  )
}
