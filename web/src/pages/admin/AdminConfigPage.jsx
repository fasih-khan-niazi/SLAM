import { useEffect, useState } from 'react'
import { useAuth } from '../../context/AuthContext'
import { useConfig } from '../../context/ConfigContext'
import { adminGetConfig, adminUpdateConfig, getConfig } from '../../api/endpoints'
import { ApiError } from '../../api/client'
import { Banner } from '../../components/Banner'
import { Button } from '../../components/Button'
import { Field } from '../../components/Field'
import { Skeleton } from '../../components/Skeleton'

function Toggle({ label, hint, checked, onChange }) {
  return (
    <label className="admin-toggle-row">
      <span>
        <strong style={{ display: 'block' }}>{label}</strong>
        {hint ? <span className="muted" style={{ fontSize: '0.85rem' }}>{hint}</span> : null}
      </span>
      <input type="checkbox" checked={checked} onChange={(e) => onChange(e.target.checked)} />
    </label>
  )
}

const PIN_CAP_MIN = 1
const PIN_CAP_MAX = 5
const EMERGENCY_MIN = 5
const EMERGENCY_MAX = 1440

function rangeError(value, min, max, what) {
  const n = Number(value)
  if (value === '' || !Number.isInteger(n) || n < min || n > max) {
    return `${what} must be a whole number from ${min} to ${max}.`
  }
  return ''
}

export function AdminConfigPage() {
  const { token } = useAuth()
  const { refreshConfig } = useConfig()
  const [form, setForm] = useState(null)
  const [error, setError] = useState(null)
  const [notice, setNotice] = useState(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    adminGetConfig(token)
      .then((res) => setForm(res.data.config || {}))
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Unable to load config'))
  }, [token])

  function set(key, value) {
    setForm((prev) => ({ ...prev, [key]: value }))
  }

  const pinCapError = form ? rangeError(form.pin_attempt_cap ?? 3, PIN_CAP_MIN, PIN_CAP_MAX, 'Max wrong attempts') : ''
  const emergencyValue = form
    ? (form.emergency_interval_minutes
        ?? (form.emergency_interval_hours != null ? form.emergency_interval_hours * 60 : 60))
    : 60
  const emergencyError = form ? rangeError(emergencyValue, EMERGENCY_MIN, EMERGENCY_MAX, 'Minutes between SMS') : ''
  const pinMinError = form ? rangeError(form.pin_min_length ?? 4, 4, 8, 'PIN min length') : ''
  const pinMaxError = form ? rangeError(form.pin_max_length ?? 6, 4, 8, 'PIN max length') : ''
  const pinWindowError = form ? rangeError(form.pin_window_minutes ?? 15, 1, 1440, 'PIN window') : ''
  const loginCapError = form ? rangeError(form.login_attempt_cap ?? 3, 1, 30, 'Max failed attempts') : ''
  const loginWindowError = form ? rangeError(form.login_window_minutes ?? 15, 1, 1440, 'Login window') : ''
  const prefixError = form && !/^[A-Za-z0-9]{2,12}$/.test(String(form.sms_prefix || '').trim())
    ? 'Prefix must be 2 to 12 letters or digits.'
    : ''
  const hasErrors = Boolean(
    pinCapError || emergencyError || pinMinError || pinMaxError ||
    pinWindowError || loginCapError || loginWindowError || prefixError
  )

  async function save() {
    if (hasErrors) return
    setBusy(true)
    setError(null)
    try {
      const res = await adminUpdateConfig(token, form)
      setForm(res.data.config)
      setNotice('Config saved. Clients pick this up on next refresh.')
      if (refreshConfig) await refreshConfig()
      else await getConfig().catch(() => {})
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Unable to save config')
    } finally {
      setBusy(false)
    }
  }

  if (!form && !error) {
    return (
      <div className="admin-page">
        <Skeleton height={280} />
      </div>
    )
  }

  return (
    <div className="admin-page">
      <p className="eyebrow">Product</p>
      <h1>Config</h1>
      <p className="lede">Settings that flow to the Android app and web portal via GET /api/config.</p>

      {error ? <Banner tone="danger" title="Error" message={error} /> : null}
      {notice ? <Banner tone="success" title="Saved" message={notice} /> : null}

      {form ? (
        <>
          <section className="admin-section">
            <h2>SMS and PIN</h2>
            <p className="section-lede">Command prefix and PIN length rules on the phone. Wrong-PIN attempts allowed is 1 to 5; a sender who reaches it is locked out for the window.</p>
            <div className="stack-lg">
              <Field id="sms_prefix" label="SMS prefix" error={prefixError} value={form.sms_prefix || ''} onChange={(v) => set('sms_prefix', v)} />
              <Field id="pin_min" label="PIN min length" type="number" integerMode="positive" min={4} max={8} error={pinMinError} value={String(form.pin_min_length ?? 4)} onChange={(v) => set('pin_min_length', v === '' ? '' : Number(v))} />
              <Field id="pin_max" label="PIN max length" type="number" integerMode="positive" min={4} max={8} error={pinMaxError} value={String(form.pin_max_length ?? 6)} onChange={(v) => set('pin_max_length', v === '' ? '' : Number(v))} />
              <Field id="pin_cap" label="SMS PIN — max wrong attempts" type="number" integerMode="positive" min={PIN_CAP_MIN} max={PIN_CAP_MAX} error={pinCapError} value={String(form.pin_attempt_cap ?? 3)} onChange={(v) => set('pin_attempt_cap', v === '' ? '' : Number(v))} />
              <Field id="pin_window" label="SMS PIN — window (minutes)" type="number" integerMode="positive" min={1} max={1440} error={pinWindowError} value={String(form.pin_window_minutes ?? 15)} onChange={(v) => set('pin_window_minutes', v === '' ? '' : Number(v))} />
            </div>
          </section>

          <section className="admin-section">
            <h2>Portal login limits</h2>
            <p className="section-lede">Failed email/password attempts per device on web and phone login.</p>
            <div className="stack-lg">
              <Field id="login_cap" label="Max failed attempts" type="number" integerMode="positive" min={1} max={30} error={loginCapError} value={String(form.login_attempt_cap ?? 3)} onChange={(v) => set('login_attempt_cap', v === '' ? '' : Number(v))} />
              <Field id="login_window" label="Window (minutes)" type="number" integerMode="positive" min={1} max={1440} error={loginWindowError} value={String(form.login_window_minutes ?? 15)} onChange={(v) => set('login_window_minutes', v === '' ? '' : Number(v))} />
            </div>
          </section>

          <section className="admin-section">
            <h2>Emergency</h2>
            <p className="section-lede">Periodic SMS from the phone while Emergency is on.</p>
            <Toggle
              label="Emergency available on phones"
              hint="When off, the Emergency toggle is hidden in the app."
              checked={form.emergency_enabled !== false}
              onChange={(v) => set('emergency_enabled', v)}
            />
            <Field
              id="emergency_minutes"
              label="Minutes between SMS"
              type="number"
              integerMode="positive"
              min={EMERGENCY_MIN}
              max={EMERGENCY_MAX}
              error={emergencyError}
              value={String(emergencyValue)}
              onChange={(v) => set('emergency_interval_minutes', v === '' ? '' : Number(v))}
            />
            <p className="muted" style={{ marginTop: 8, fontSize: '0.85rem' }}>
              Between 5 minutes and 1440 (one day). Phones schedule the next emergency SMS after each send. Default is 60.
            </p>
          </section>

          <section className="admin-section">
            <h2>Portal flags</h2>
            <Toggle
              label="Maintenance mode"
              hint="Blocks upgrades and payment submit; shows banners."
              checked={Boolean(form.maintenance)}
              onChange={(v) => set('maintenance', v)}
            />
            <Toggle
              label="Payments enabled"
              hint="When off, paid plan choose and payment submit are blocked."
              checked={form.payments_enabled !== false}
              onChange={(v) => set('payments_enabled', v)}
            />
            <Toggle
              label="Maps enabled (reserved)"
              hint="Reserved for a future maps UI."
              checked={Boolean(form.maps_enabled)}
              onChange={(v) => set('maps_enabled', v)}
            />
            <Toggle
              label="Transactional email"
              hint="When off, the API skips sending email."
              checked={form.email_enabled !== false}
              onChange={(v) => set('email_enabled', v)}
            />
          </section>

          <section className="admin-section">
            <h2>Payment accounts</h2>
            <p className="section-lede">Shown on the web payments page for JazzCash and EasyPaisa.</p>
            <div className="stack-lg">
              <Field
                id="jazzcash"
                label="JazzCash account"
                value={form.jazzcash_account || ''}
                onChange={(v) => set('jazzcash_account', v)}
              />
              <Field
                id="easypaisa"
                label="EasyPaisa account"
                value={form.easypaisa_account || ''}
                onChange={(v) => set('easypaisa_account', v)}
              />
            </div>
          </section>

          <div className="admin-toolbar">
            <Button onClick={save} loading={busy} disabled={busy || hasErrors}>Save config</Button>
          </div>
        </>
      ) : null}
    </div>
  )
}
