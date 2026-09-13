import { useMemo, useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { ApiError } from '../api/client'
import { Button } from '../components/Button'
import { Field } from '../components/Field'
import { Logo } from '../components/Logo'
import { Modal } from '../components/Modal'
import { PasswordStrength, scorePassword } from '../components/PasswordStrength'

const CONSENT_POINTS = [
  'You own this phone, or have clear permission to run SLAM on it.',
  'Trusted numbers can request location by SMS with your PIN while Listening is on.',
  'SLAM needs SMS, location (including background), and notifications on the phone app.',
  'Some phones pause background apps. Allow unrestricted battery use if listening stops.',
]

export function RegisterPage() {
  const { user, register } = useAuth()
  const navigate = useNavigate()
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [phone, setPhone] = useState('')
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [agreed, setAgreed] = useState(false)
  const [consentRead, setConsentRead] = useState(false)
  const [consentOpen, setConsentOpen] = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)
  const strength = useMemo(() => scorePassword(password), [password])

  if (user) return <Navigate to="/" replace />

  async function onSubmit(event) {
    event.preventDefault()
    if (!strength.isAcceptable) {
      setError('Choose a strong password before creating the account.')
      return
    }
    if (password !== confirm) {
      setError('Passwords do not match.')
      return
    }
    if (!agreed || !consentRead) {
      setError('Open consent, confirm you understand, then agree to continue.')
      return
    }
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
      <div className="auth-brand">
        <span style={{ color: 'var(--primary)' }}><Logo size={48} /></span>
        <div>
          <h2>SLAM</h2>
          <p>Secure location by SMS</p>
        </div>
      </div>
      <h1>Create account</h1>
      <form className="stack-lg" style={{ marginTop: 28 }} onSubmit={onSubmit}>
        <Field id="name" label="Full name" value={name} onChange={setName} autoComplete="name" required />
        <Field id="email" label="Email" type="email" value={email} onChange={setEmail} autoComplete="email" required />
        <Field id="phone" label="Phone" type="tel" value={phone} onChange={setPhone} autoComplete="tel" required />
        <Field id="password" label="Password" type="password" value={password} onChange={setPassword} autoComplete="new-password" required />
        <PasswordStrength password={password} />
        <Field id="confirm" label="Confirm password" type="password" value={confirm} onChange={setConfirm} autoComplete="new-password" required />
        <Button type="button" variant="secondary" block onClick={() => setConsentOpen(true)}>
          {consentRead ? 'Consent reviewed' : 'Open consent and disclosure'}
        </Button>
        <label className="muted" style={{ display: 'flex', gap: 10, alignItems: 'flex-start' }}>
          <input
            type="checkbox"
            checked={agreed}
            onChange={(event) => setAgreed(event.target.checked)}
            style={{ marginTop: 4 }}
          />
          <span>
            I agree to the <Link to="/terms">terms</Link> and understand how SLAM uses SMS location on the phone app.
          </span>
        </label>
        <Button
          type="submit"
          loading={loading}
          block
          disabled={!strength.isAcceptable || !agreed || !consentRead}
        >
          Create account
        </Button>
      </form>
      <p className="muted" style={{ marginTop: 16 }}>
        Already have an account? <Link to="/login">Sign in</Link>
      </p>

      {consentOpen ? (
        <Modal
          title="Consent and disclosure"
          confirmLabel="I understand"
          cancelLabel="Close"
          onConfirm={() => {
            setConsentRead(true)
            setConsentOpen(false)
          }}
          onDismiss={() => setConsentOpen(false)}
        >
          <p className="lede">Scroll to read every point before confirming.</p>
          <ul className="muted" style={{ paddingLeft: 18, marginTop: 12 }}>
            {CONSENT_POINTS.map((point) => (
              <li key={point} style={{ marginBottom: 10 }}>{point}</li>
            ))}
          </ul>
        </Modal>
      ) : null}

      {error ? (
        <Modal title="Could not register" message={error} confirmLabel="OK" onConfirm={() => setError(null)} onDismiss={() => setError(null)} />
      ) : null}
    </main>
  )
}
