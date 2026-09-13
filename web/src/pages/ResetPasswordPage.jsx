import { useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { ApiError } from '../api/client'
import { resetPassword } from '../api/endpoints'
import { Button } from '../components/Button'
import { Field } from '../components/Field'
import { Logo } from '../components/Logo'
import { Modal } from '../components/Modal'
import { PasswordStrength, scorePassword } from '../components/PasswordStrength'

export function ResetPasswordPage() {
  const [params] = useSearchParams()
  const token = params.get('token') || ''
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [loading, setLoading] = useState(false)
  const [notice, setNotice] = useState(null)
  const [error, setError] = useState(null)
  const strength = useMemo(() => scorePassword(password), [password])

  async function onSubmit(event) {
    event.preventDefault()
    if (!token) {
      setError('This reset link is missing a token.')
      return
    }
    if (!strength.isAcceptable) {
      setError('Choose a strong password.')
      return
    }
    if (password !== confirm) {
      setError('Passwords do not match.')
      return
    }
    setLoading(true)
    try {
      await resetPassword({ token, password })
      setNotice('Password updated. You can sign in now.')
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not reset password')
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
      <h1>Reset password</h1>
      <form className="stack-lg" style={{ marginTop: 28 }} onSubmit={onSubmit}>
        <Field id="password" label="New password" type="password" value={password} onChange={setPassword} autoComplete="new-password" required />
        <PasswordStrength password={password} />
        <Field id="confirm" label="Confirm password" type="password" value={confirm} onChange={setConfirm} autoComplete="new-password" required />
        <Button type="submit" loading={loading} block disabled={!strength.isAcceptable}>Update password</Button>
      </form>
      <p className="muted" style={{ marginTop: 16 }}>
        <Link to="/login">Back to sign in</Link>
      </p>
      {notice ? (
        <Modal
          title="Password updated"
          message={notice}
          confirmLabel="Sign in"
          onConfirm={() => { window.location.href = '/login' }}
          onDismiss={() => { window.location.href = '/login' }}
        />
      ) : null}
      {error ? (
        <Modal title="Reset failed" message={error} confirmLabel="OK" onConfirm={() => setError(null)} onDismiss={() => setError(null)} />
      ) : null}
    </main>
  )
}
