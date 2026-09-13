import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { listNotifications, markNotificationRead } from '../api/endpoints'
import { ApiError } from '../api/client'
import { Banner } from '../components/Banner'
import { Button } from '../components/Button'
import { Card } from '../components/Card'
import { EmptyState } from '../components/EmptyState'
import { Field } from '../components/Field'
import { Modal } from '../components/Modal'
import { PasswordStrength, scorePassword } from '../components/PasswordStrength'
import { Skeleton } from '../components/Skeleton'
import { StatusChip } from '../components/StatusChip'

function remainingCopy(subscription) {
  if (!subscription) return 'Loading your usage…'
  if (subscription.monthly_limit == null || subscription.plan_name === 'Premium') {
    return 'Unlimited locates this period'
  }
  const remaining = subscription.requests_remaining ?? '—'
  const limit = subscription.monthly_limit ?? 5
  return `${remaining} of ${limit} locates remaining`
}

function formatDate(value) {
  if (!value) return null
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return null
  return date.toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' })
}

function statusChip(status) {
  if (status === 'pending_payment') return { tone: 'warning', label: 'Waiting for receipt' }
  if (status === 'pending_approval') return { tone: 'warning', label: 'Under review' }
  if (status === 'active') return { tone: 'success', label: 'Active' }
  return { tone: 'neutral', label: status || 'Unknown' }
}

export function HomePage() {
  const { token, user, subscription, ready, logout, changePassword, refresh } = useAuth()
  const [confirmOut, setConfirmOut] = useState(false)
  const [notes, setNotes] = useState(null)
  const [syncError, setSyncError] = useState(null)
  const [passwordOpen, setPasswordOpen] = useState(false)
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [passwordBusy, setPasswordBusy] = useState(false)
  const [passwordError, setPasswordError] = useState(null)
  const [passwordOk, setPasswordOk] = useState(null)
  const strength = useMemo(() => scorePassword(newPassword), [newPassword])

  useEffect(() => {
    if (!token && !user) return
    listNotifications(token)
      .then((res) => setNotes(res.data.notifications || []))
      .catch(() => {
        setNotes([])
        setSyncError('Could not refresh notifications. Showing what we have.')
      })
  }, [token, user])

  async function onRead(id) {
    try {
      await markNotificationRead(token, id)
      setNotes((prev) => (prev || []).map((item) => (
        item.id === id ? { ...item, read: true } : item
      )))
    } catch {
      // Keep list as-is.
    }
  }

  const firstName = user?.name?.split(' ')[0] || ''
  // Prefer nested pending_upgrade (new API). Fall back to old top-level pending shape.
  const pending = subscription?.pending_upgrade
    || (subscription && ['pending_payment', 'pending_approval'].includes(subscription.status) ? subscription : null)
  const active = subscription?.pending_upgrade
    ? subscription
    : (subscription?.active_plan || subscription)
  const chip = statusChip(active?.status || 'active')

  async function onChangePassword(event) {
    event.preventDefault()
    if (!strength.isAcceptable) {
      setPasswordError('Choose a strong new password.')
      return
    }
    setPasswordBusy(true)
    try {
      await changePassword(currentPassword, newPassword)
      setPasswordOk('Password updated.')
      setPasswordOpen(false)
      setCurrentPassword('')
      setNewPassword('')
    } catch (err) {
      setPasswordError(err instanceof ApiError ? err.message : 'Could not change password')
    } finally {
      setPasswordBusy(false)
    }
  }

  return (
    <main className="page">
      <div className="row-between">
        <div>
          <h1>Hello{firstName ? `, ${firstName}` : ''}</h1>
          <p className="lede">Account status for your SLAM plan and payments.</p>
        </div>
        <Button variant="secondary" onClick={() => refresh().catch(() => setSyncError('Could not refresh account.'))}>
          Refresh
        </Button>
      </div>

      <div className="stack-lg" style={{ marginTop: 24 }}>
        {syncError ? <Banner title="Sync issue" message={syncError} /> : null}

        {!ready || !subscription ? (
          <Skeleton height={140} />
        ) : (
          <Card>
            <div className="row-between">
              <p className="eyebrow">Current plan</p>
              <StatusChip tone={chip.tone}>{chip.label}</StatusChip>
            </div>
            <h2>{(active || subscription).plan_name || 'Free'}</h2>
            <p className="lede">{remainingCopy(active || subscription)}</p>
            {pending ? (
              <p style={{ marginTop: 16 }}>
                Upgrade to <strong>{pending.plan_name}</strong> is in progress.{' '}
                <Link to="/payments">Continue on Payments</Link>
              </p>
            ) : null}
          </Card>
        )}

        <Card>
          <h2>Use the Android app for Tracking</h2>
          <p className="lede">
            Listening, PIN, trusted numbers, and SMS replies run on the phone. This portal is for account, plans, and payments only.
          </p>
        </Card>

        <Card>
          <h2>Notifications</h2>
          {!notes ? (
            <Skeleton height={88} />
          ) : notes.length === 0 ? (
            <EmptyState title="No alerts yet" message="Payment updates will show here." />
          ) : (
            <ul className="muted" style={{ listStyle: 'none', padding: 0, margin: '16px 0 0' }}>
              {notes.slice(0, 8).map((item) => (
                <li key={item.id} style={{ padding: '12px 0', borderTop: '1px solid var(--border)' }}>
                  <strong style={{ color: 'var(--text)' }}>{item.title}</strong>
                  {item.read ? null : <span className="badge" style={{ marginLeft: 8 }}>New</span>}
                  <br />
                  {item.body}
                  {item.read ? null : (
                    <div style={{ marginTop: 8 }}>
                      <Button variant="ghost" onClick={() => onRead(item.id)}>Mark read</Button>
                    </div>
                  )}
                </li>
              ))}
            </ul>
          )}
        </Card>

        <Card>
          <h2>Plans and payments</h2>
          <p className="lede">
            Free includes a limited number of locates. Upgrade with EasyPaisa or JazzCash, then upload the transfer screenshot for admin approval.
          </p>
          <div style={{ marginTop: 16, display: 'flex', gap: 12, flexWrap: 'wrap' }}>
            <Link to="/plans"><Button>View plans</Button></Link>
            <Link to="/payments"><Button variant="secondary">Payments</Button></Link>
          </div>
        </Card>

        <Card>
          <h2>Account</h2>
          <p className="lede">{user?.email}</p>
          <div style={{ marginTop: 16, display: 'flex', gap: 12, flexWrap: 'wrap' }}>
            <Button variant="secondary" onClick={() => setPasswordOpen(true)}>Change password</Button>
            <Button variant="ghost" onClick={() => setConfirmOut(true)}>Sign out</Button>
          </div>
          {formatDate(subscription?.end_date) ? (
            <p className="muted" style={{ marginTop: 12 }}>Period ends {formatDate(subscription.end_date)}</p>
          ) : null}
        </Card>
      </div>

      {confirmOut ? (
        <Modal
          title="Sign out?"
          message="SMS tracking on the phone keeps working after you sign out of the portal."
          confirmLabel="Sign out"
          cancelLabel="Stay signed in"
          danger
          onConfirm={() => {
            setConfirmOut(false)
            logout()
          }}
          onDismiss={() => setConfirmOut(false)}
        />
      ) : null}

      {passwordOpen ? (
        <Modal
          title="Change password"
          cancelLabel="Cancel"
          onDismiss={() => setPasswordOpen(false)}
        >
          <form className="stack-lg" style={{ marginTop: 12 }} onSubmit={onChangePassword}>
            <Field id="current" label="Current password" type="password" value={currentPassword} onChange={setCurrentPassword} required />
            <Field id="next" label="New password" type="password" value={newPassword} onChange={setNewPassword} required />
            <PasswordStrength password={newPassword} />
            <Button type="submit" loading={passwordBusy} block disabled={!strength.isAcceptable}>Save password</Button>
          </form>
        </Modal>
      ) : null}

      {passwordError ? (
        <Modal title="Could not change password" message={passwordError} confirmLabel="OK" onConfirm={() => setPasswordError(null)} onDismiss={() => setPasswordError(null)} />
      ) : null}
      {passwordOk ? (
        <Modal title="Done" message={passwordOk} confirmLabel="OK" onConfirm={() => setPasswordOk(null)} onDismiss={() => setPasswordOk(null)} />
      ) : null}
    </main>
  )
}
