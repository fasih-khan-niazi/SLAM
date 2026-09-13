import { useEffect, useMemo, useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { useConfig } from '../context/ConfigContext'
import { ApiError } from '../api/client'
import { listPayments, submitPayment } from '../api/endpoints'
import { Banner } from '../components/Banner'
import { Button } from '../components/Button'
import { Card } from '../components/Card'
import { EmptyState } from '../components/EmptyState'
import { Field } from '../components/Field'
import { EasyPaisaIcon, JazzCashIcon } from '../components/PaymentIcons'
import { Modal } from '../components/Modal'
import { Skeleton } from '../components/Skeleton'
import { StatusChip } from '../components/StatusChip'
import { ProtectedRoute } from '../components/ProtectedRoute'

const MERCHANT = '03300490019'

function formatDate(value) {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '—'
  return date.toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' })
}

function paymentTone(status) {
  if (status === 'approved') return 'success'
  if (status === 'rejected') return 'danger'
  return 'warning'
}

function PaymentsContent() {
  const { token, subscription, refresh } = useAuth()
  const { paymentsEnabled, maintenance, easypaisaAccount, jazzcashAccount } = useConfig()
  const location = useLocation()
  const presetId = location.state?.subscription_id
  const presetPlan = location.state?.plan_name
  const presetAmount = location.state?.amount_pkr

  const [payments, setPayments] = useState(null)
  const [method, setMethod] = useState('jazzcash')
  const [transactionId, setTransactionId] = useState('')
  const [file, setFile] = useState(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)
  const [notice, setNotice] = useState(null)
  const [copied, setCopied] = useState(false)

  const waiting = subscription && ['pending_payment', 'pending_approval'].includes(subscription.status)
  const subscriptionId = presetId || subscription?.subscription_id
  const amount = presetAmount || subscription?.price_pkr
  const planName = presetPlan || subscription?.plan_name
  const accountNumber = method === 'easypaisa' ? (easypaisaAccount || MERCHANT) : (jazzcashAccount || MERCHANT)

  const timeline = useMemo(() => {
    const steps = [
      { id: 'chosen', label: 'Plan chosen', done: Boolean(subscriptionId) },
      { id: 'sent', label: 'Payment sent from your phone', done: Boolean(payments?.some((p) => p.status !== 'rejected')) || subscription?.status === 'pending_approval' },
      { id: 'review', label: 'Under admin review', done: subscription?.status === 'pending_approval' || payments?.some((p) => p.status === 'pending') },
      { id: 'result', label: 'Approved or rejected', done: payments?.some((p) => p.status === 'approved' || p.status === 'rejected') },
    ]
    return steps
  }, [subscriptionId, payments, subscription])

  useEffect(() => {
    listPayments(token)
      .then((res) => setPayments(res.data.payments || []))
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Unable to load payments'))
  }, [token])

  async function copyAccount() {
    try {
      await navigator.clipboard.writeText(accountNumber)
      setCopied(true)
      setTimeout(() => setCopied(false), 1600)
    } catch {
      setError('Could not copy the account number.')
    }
  }

  async function onSubmit(event) {
    event.preventDefault()
    if (!subscriptionId) {
      setError('Choose a paid plan first.')
      return
    }
    if (!file) {
      setError('Attach a JPG or PNG screenshot of the transfer.')
      return
    }
    if (!['image/jpeg', 'image/png', 'image/jpg'].includes(file.type) && !/\.(jpe?g|png)$/i.test(file.name)) {
      setError('Screenshot must be a JPG or PNG file.')
      return
    }
    setLoading(true)
    try {
      await submitPayment(token, {
        subscriptionId,
        paymentMethod: method,
        transactionId: transactionId.trim(),
        screenshot: file,
      })
      setTransactionId('')
      setFile(null)
      setNotice('Payment submitted. You stay on Free until an admin approves it.')
      const next = await listPayments(token)
      setPayments(next.data.payments || [])
      await refresh()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not submit the payment')
    } finally {
      setLoading(false)
    }
  }

  return (
    <main className="page">
      <h1>Payments</h1>
      <p className="lede">
        Send EasyPaisa or JazzCash from your phone banking app, then upload the transaction screenshot and ID for admin approval.
      </p>

      <div className="stack-lg" style={{ marginTop: 24 }}>
        {!paymentsEnabled || maintenance ? (
          <Banner title="Payments paused" message="Your history below is still available." />
        ) : null}

        <Card>
          <h2>Status</h2>
          <ul className="timeline" style={{ marginTop: 16 }}>
            {timeline.map((step, index) => (
              <li key={step.id} className={step.done ? 'done' : index === timeline.findIndex((s) => !s.done) ? 'current' : ''}>
                <span className="dot" />
                <span>{step.label}</span>
              </li>
            ))}
          </ul>
        </Card>

        {waiting || subscriptionId ? (
          <Card>
            <div className="row-between">
              <div>
                <p className="eyebrow">Pay for this plan</p>
                <h2>{planName || 'Paid plan'}</h2>
                <p className="plan-price">Rs {amount ?? '—'}</p>
              </div>
              <StatusChip tone={subscription?.status === 'pending_approval' ? 'warning' : 'warning'}>
                {subscription?.status === 'pending_approval' ? 'Under review' : 'Waiting for receipt'}
              </StatusChip>
            </div>

            <p className="lede" style={{ marginTop: 16 }}>
              1. Open EasyPaisa or JazzCash on your phone. 2. Send the exact amount to the account below.
              3. Put your SLAM email in the transfer note if asked. 4. Come back here with Transaction ID and screenshot.
            </p>

            <div className="method-grid" style={{ marginTop: 16 }}>
              <button
                type="button"
                className={`method-card ${method === 'jazzcash' ? 'active' : ''}`}
                onClick={() => setMethod('jazzcash')}
              >
                <JazzCashIcon />
                <strong>JazzCash</strong>
                <div className="muted" style={{ marginTop: 4 }}>Send from your JazzCash app</div>
              </button>
              <button
                type="button"
                className={`method-card ${method === 'easypaisa' ? 'active' : ''}`}
                onClick={() => setMethod('easypaisa')}
              >
                <EasyPaisaIcon />
                <strong>EasyPaisa</strong>
                <div className="muted" style={{ marginTop: 4 }}>Send from your EasyPaisa app</div>
              </button>
            </div>

            <p className="eyebrow" style={{ marginTop: 20 }}>Send to this account</p>
            <div className="pay-account">
              <span>{accountNumber}</span>
              <Button variant="secondary" onClick={copyAccount}>{copied ? 'Copied' : 'Copy'}</Button>
            </div>
            <p className="muted" style={{ marginTop: 8 }}>
              Same account number is used for EasyPaisa and JazzCash.
            </p>

            {paymentsEnabled && !maintenance ? (
              <form className="stack-lg" style={{ marginTop: 24 }} onSubmit={onSubmit}>
                <Field
                  id="txn"
                  label="Transaction ID"
                  value={transactionId}
                  onChange={setTransactionId}
                  required
                  placeholder="From your transfer receipt"
                />
                <div className="field">
                  <label htmlFor="shot">Transfer screenshot (JPG or PNG)</label>
                  <input
                    id="shot"
                    type="file"
                    accept="image/png,image/jpeg"
                    onChange={(event) => setFile(event.target.files?.[0] || null)}
                    required
                  />
                </div>
                <Button type="submit" loading={loading} block>Submit for approval</Button>
              </form>
            ) : null}
          </Card>
        ) : (
          <Card>
            <h2>No payment waiting</h2>
            <p className="lede">
              Pick Basic or Premium on <Link to="/plans">Plans</Link>, then return here after you send the transfer from your phone.
            </p>
          </Card>
        )}

        <Card>
          <h2>Payment history</h2>
          {!payments ? (
            <Skeleton height={120} />
          ) : payments.length === 0 ? (
            <EmptyState title="No payments yet" message="Submitted receipts will appear here." />
          ) : (
            <ul className="stack" style={{ listStyle: 'none', padding: 0, marginTop: 16 }}>
              {payments.map((payment) => (
                <li key={payment.id} className="card" style={{ padding: 14 }}>
                  <div className="row-between">
                    <div style={{ display: 'flex', gap: 10, alignItems: 'center' }}>
                      {payment.payment_method === 'easypaisa' ? <EasyPaisaIcon size={28} /> : <JazzCashIcon size={28} />}
                      <div>
                        <strong>{payment.payment_method === 'easypaisa' ? 'EasyPaisa' : 'JazzCash'}</strong>
                        <div className="muted">Rs {payment.amount_pkr} · {formatDate(payment.created_at || payment.createdAt)}</div>
                        <div className="muted">Txn {payment.transaction_id}</div>
                      </div>
                    </div>
                    <StatusChip tone={paymentTone(payment.status)}>{payment.status}</StatusChip>
                  </div>
                  {payment.screenshot_url ? (
                    <p style={{ marginTop: 10 }}>
                      <a href={payment.screenshot_url} target="_blank" rel="noreferrer">View receipt</a>
                    </p>
                  ) : null}
                </li>
              ))}
            </ul>
          )}
        </Card>
      </div>

      {notice ? (
        <Modal title="Submitted" message={notice} confirmLabel="OK" onConfirm={() => setNotice(null)} onDismiss={() => setNotice(null)} />
      ) : null}
      {error ? (
        <Modal title="Payments" message={error} confirmLabel="OK" onConfirm={() => setError(null)} onDismiss={() => setError(null)} />
      ) : null}
    </main>
  )
}

export function PaymentsPage() {
  return (
    <ProtectedRoute>
      <PaymentsContent />
    </ProtectedRoute>
  )
}
