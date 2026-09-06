import { useEffect, useState } from 'react'
import { Link, Navigate, useLocation } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { ApiError } from '../api/client'
import { listPayments, submitPayment } from '../api/endpoints'
import { Button } from '../components/Button'
import { Card } from '../components/Card'
import { Field } from '../components/Field'
import { Modal } from '../components/Modal'
import { Skeleton } from '../components/Skeleton'

function formatDate(value) {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '—'
  return date.toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' })
}

export function PaymentsPage() {
  const { token, user, subscription, ready, refresh } = useAuth()
  const location = useLocation()
  const presetId = location.state?.subscription_id

  const [payments, setPayments] = useState(null)
  const [method, setMethod] = useState('jazzcash')
  const [transactionId, setTransactionId] = useState('')
  const [file, setFile] = useState(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)
  const [notice, setNotice] = useState(null)

  const waiting = subscription && ['pending_payment', 'pending_approval'].includes(subscription.status)
  const subscriptionId = presetId || subscription?.subscription_id

  useEffect(() => {
    if (!token) return
    listPayments(token)
      .then((res) => setPayments(res.data.payments || []))
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Unable to load payments'))
  }, [token])

  if (ready && !user) return <Navigate to="/login" replace />

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
      setNotice('Payment submitted. You keep Free until an admin approves it.')
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
      <p className="lede">Send JazzCash or EasyPaisa, then upload the receipt. Approval activates the plan for 30 days.</p>

      <div className="stack-lg" style={{ marginTop: 24 }}>
        <Card>
          <h2>Submit a receipt</h2>
          {waiting ? (
            <p className="lede">
              {subscription.plan_name} is {subscription.status.replace('_', ' ')}. Amount Rs {subscription.price_pkr}.
            </p>
          ) : (
            <p className="lede">
              Pick Basic or Premium on <Link to="/plans">Plans</Link>, then come back here with the transaction ID.
            </p>
          )}
          <form className="stack-lg" style={{ marginTop: 20 }} onSubmit={onSubmit}>
            <div className="field">
              <label htmlFor="method">Method</label>
              <select id="method" value={method} onChange={(event) => setMethod(event.target.value)}>
                <option value="jazzcash">JazzCash</option>
                <option value="easypaisa">EasyPaisa</option>
              </select>
            </div>
            <Field
              id="txn"
              label="Transaction ID"
              value={transactionId}
              onChange={setTransactionId}
              required
            />
            <div className="field">
              <label htmlFor="shot">Screenshot (JPG or PNG)</label>
              <input
                id="shot"
                type="file"
                accept="image/jpeg,image/png"
                onChange={(event) => setFile(event.target.files?.[0] || null)}
              />
            </div>
            <Button type="submit" loading={loading} block disabled={!waiting && !presetId}>
              Submit payment
            </Button>
          </form>
        </Card>

        <Card>
          <h2>History</h2>
          {!payments ? (
            <Skeleton height={88} />
          ) : payments.length === 0 ? (
            <p className="lede">No payments yet.</p>
          ) : (
            <ul className="muted" style={{ listStyle: 'none', padding: 0, margin: '16px 0 0' }}>
              {payments.map((item) => (
                <li key={item.id} style={{ padding: '12px 0', borderTop: '1px solid var(--border)' }}>
                  <strong>{item.plan_name}</strong> · Rs {item.amount_pkr} · {item.payment_method}
                  <br />
                  {item.transaction_id} · {item.status} · {formatDate(item.submitted_at)}
                </li>
              ))}
            </ul>
          )}
        </Card>
      </div>

      {error ? (
        <Modal title="Payment" message={error} confirmLabel="OK" onConfirm={() => setError(null)} onDismiss={() => setError(null)} />
      ) : null}
      {notice ? (
        <Modal title="Received" message={notice} confirmLabel="OK" onConfirm={() => setNotice(null)} onDismiss={() => setNotice(null)} />
      ) : null}
    </main>
  )
}
