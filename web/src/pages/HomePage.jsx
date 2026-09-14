import { useEffect, useMemo, useState, useCallback } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { cancelSubscription, listNotifications, markNotificationRead, resumeSubscription } from '../api/endpoints'
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
import { useIntervalRefresh } from '../hooks/useIntervalRefresh'

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

function pendingCtaLabel(status) {
  if (status === 'pending_approval') return 'Check payment status'
  return 'Continue to payment'
}

export function HomePage() {
  const { token, user, subscription, ready, changePassword, refresh } = useAuth()
  const [notes, setNotes] = useState(null)
  const [syncError, setSyncError] = useState(null)
  const [passwordOpen, setPasswordOpen] = useState(false)
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [passwordBusy, setPasswordBusy] = useState(false)
  const [passwordError, setPasswordError] = useState(null)
  const [passwordOk, setPasswordOk] = useState(null)
  const [refreshing, setRefreshing] = useState(false)
  const [cancelOpen, setCancelOpen] = useState(false)
  const [cancelBusy, setCancelBusy] = useState(false)
  const [cancelError, setCancelError] = useState(null)
  const strength = useMemo(() => scorePassword(newPassword), [newPassword])

  const loadNotes = useCallback(() => {
    if (!token && !user) return
    listNotifications(token)
      .then((res) => setNotes(res.data.notifications || []))
      .catch(() => {
        setNotes([])
        setSyncError('Could not refresh notifications. Showing what we have.')
      })
  }, [token, user])

  useEffect(() => {
    loadNotes()
  }, [loadNotes])

  useIntervalRefresh(() => {
    refresh().catch(() => {})
    loadNotes()
  }, 20000)

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
  const paidActive = active && Number(active.price_pkr || 0) > 0
  const cancelling = Boolean(active?.cancel_at_period_end)
  const endLabel = formatDate(active?.end_date)

  async function onConfirmCancel() {
    setCancelBusy(true)
    setCancelError(null)
    try {
      await cancelSubscription(token)
      await refresh()
      setCancelOpen(false)
    } catch (err) {
      setCancelError(err instanceof ApiError ? err.message : 'Could not schedule cancellation')
    } finally {
      setCancelBusy(false)
    }
  }

  async function onResume() {
    setCancelBusy(true)
    try {
      await resumeSubscription(token)
      await refresh()
    } catch (err) {
      setSyncError(err instanceof ApiError ? err.message : 'Could not undo cancellation')
    } finally {
      setCancelBusy(false)
    }
  }

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
        <Button
          variant="secondary"
          loading={refreshing}
          onClick={async () => {
            setRefreshing(true)
            setSyncError(null)
            try {
              await refresh()
              if (token) {
                const res = await listNotifications(token)
                setNotes(res.data.notifications || [])
              }
            } catch {
              setSyncError('Could not refresh account.')
            } finally {
              setRefreshing(false)
            }
          }}
        >
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
            {cancelling ? (
              <Banner
                title="Cancellation scheduled"
                message={`You keep ${(active || subscription).plan_name} until ${endLabel || 'the period end'}. After that you return to Free.`}
              />
            ) : null}
            {pending ? (
              <p style={{ marginTop: 16 }}>
                Upgrade to <strong>{pending.plan_name}</strong> is in progress.{' '}
                <Link to="/payments">{pendingCtaLabel(pending.status)}</Link>
              </p>
            ) : null}
            {paidActive && !cancelling ? (
              <div style={{ marginTop: 16 }}>
                <Button variant="secondary" onClick={() => setCancelOpen(true)}>Cancel plan</Button>
              </div>
            ) : null}
            {paidActive && cancelling ? (
              <div style={{ marginTop: 16 }}>
                <Button variant="secondary" loading={cancelBusy} onClick={onResume}>Keep my plan</Button>
              </div>
            ) : null}
          </Card>
        )}

        {cancelOpen ? (
          <Modal
            title="Cancel your plan?"
            message={
              `You will keep ${(active || {}).plan_name || 'this plan'} and its locate quota until ${endLabel || 'the renewal date'}. ` +
              'After that date you move to Free (fewer locates and trusted numbers). ' +
              'Listening, PIN, and trusted numbers stay on the phone.'
            }
            confirmLabel={cancelBusy ? 'Working…' : 'Cancel at period end'}
            onConfirm={onConfirmCancel}
            onDismiss={() => { if (!cancelBusy) setCancelOpen(false) }}
          />
        ) : null}
        {cancelError ? (
          <Modal title="Cancel plan" message={cancelError} confirmLabel="OK" onConfirm={() => setCancelError(null)} onDismiss={() => setCancelError(null)} />
        ) : null}

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
          </div>
          {formatDate(subscription?.end_date) ? (
            <p className="muted" style={{ marginTop: 12 }}>Period ends {formatDate(subscription.end_date)}</p>
          ) : null}
        </Card>
      </div>

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
