import { useState } from 'react'
import { Link, Navigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { Button } from '../components/Button'
import { Card } from '../components/Card'
import { Modal } from '../components/Modal'
import { Skeleton } from '../components/Skeleton'

function remainingCopy(subscription) {
  if (!subscription) return 'Loading your usage…'
  if (subscription.monthly_limit == null || subscription.plan_name === 'Premium') {
    return 'Unlimited location requests this period'
  }
  const remaining = subscription.requests_remaining ?? '—'
  const limit = subscription.monthly_limit ?? 5
  return `${remaining} of ${limit} requests remaining this period`
}

function formatDate(value) {
  if (!value) return null
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return null
  return date.toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' })
}

function statusLabel(status) {
  if (status === 'pending_payment') return 'Waiting for your receipt'
  if (status === 'pending_approval') return 'Waiting for approval'
  return status
}

export function HomePage() {
  const { user, subscription, ready, logout } = useAuth()
  const [confirmOut, setConfirmOut] = useState(false)

  if (ready && !user) return <Navigate to="/login" replace />

  const firstName = user?.name?.split(' ')[0] || ''
  const starts = formatDate(subscription?.start_date)
  const ends = formatDate(subscription?.end_date)
  const pending = subscription && ['pending_payment', 'pending_approval'].includes(subscription.status)
  const active = subscription?.active_plan

  return (
    <main className="page">
      <h1>Hello{firstName ? `, ${firstName}` : ''}</h1>
      <p className="lede">Your account is linked to the phone app. SMS tracking does not need this page to stay open.</p>

      <div className="stack-lg" style={{ marginTop: 24 }}>
        {!ready || !subscription ? (
          <Skeleton height={140} />
        ) : (
          <Card>
            <p className="eyebrow">Current plan</p>
            <h2>{(active || subscription).plan_name || 'Free'}</h2>
            <p className="lede">{remainingCopy(active || subscription)}</p>
            {starts || ends ? (
              <p className="muted" style={{ marginTop: 8 }}>
                {starts ? `Started ${starts}` : ''}
                {starts && ends ? ' · ' : ''}
                {ends ? `Resets ${ends}` : ''}
              </p>
            ) : null}
            {pending ? (
              <p className="badge" style={{ marginTop: 16 }}>
                {subscription.plan_name}: {statusLabel(subscription.status)}
              </p>
            ) : null}
          </Card>
        )}

        <Card>
          <h2>Upgrade or pay</h2>
          <p className="lede">
            Free includes 5 locates each 30-day period. Choose Basic or Premium, send JazzCash or EasyPaisa, then upload the receipt.
          </p>
          <p style={{ marginTop: 16 }}>
            <Link to="/plans">View plans</Link>
            {' · '}
            <Link to="/payments">Payments</Link>
          </p>
        </Card>

        <Card>
          <h2>On the phone</h2>
          <p className="lede">
            Install the SLAM app, set a PIN, then ask a friend to text{' '}
            <strong>SLAM [PIN] LOCATE</strong>. Settings on the phone control trusted numbers.
          </p>
        </Card>

        <Button variant="ghost" onClick={() => setConfirmOut(true)}>Sign out</Button>
      </div>

      {confirmOut ? (
        <Modal
          title="Sign out?"
          message="You can still receive SMS location requests on the phone after signing out here."
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
    </main>
  )
}
