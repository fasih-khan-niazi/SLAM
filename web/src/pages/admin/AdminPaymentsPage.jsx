import { useCallback, useEffect, useState } from 'react'
import { useAuth } from '../../context/AuthContext'
import {
  adminApprovePayment,
  adminListPayments,
  adminRejectPayment,
} from '../../api/endpoints'
import { ApiError } from '../../api/client'
import { Banner } from '../../components/Banner'
import { Button } from '../../components/Button'
import { Modal } from '../../components/Modal'
import { Skeleton } from '../../components/Skeleton'
import { StatusChip } from '../../components/StatusChip'
import { EmptyState } from '../../components/EmptyState'

function tone(status) {
  if (status === 'approved') return 'success'
  if (status === 'rejected') return 'danger'
  return 'warning'
}

export function AdminPaymentsPage() {
  const { token } = useAuth()
  const [status, setStatus] = useState('pending')
  const [payments, setPayments] = useState(null)
  const [error, setError] = useState(null)
  const [notice, setNotice] = useState(null)
  const [confirm, setConfirm] = useState(null)
  const [busy, setBusy] = useState(false)

  const load = useCallback(() => {
    setError(null)
    return adminListPayments(token, { status: status || undefined })
      .then((res) => setPayments(res.data.payments || []))
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Unable to load payments'))
  }, [token, status])

  useEffect(() => {
    setPayments(null)
    load()
  }, [load])

  async function runAction() {
    if (!confirm) return
    setBusy(true)
    try {
      if (confirm.action === 'approve') {
        await adminApprovePayment(token, confirm.id)
        setNotice('Payment approved.')
      } else {
        await adminRejectPayment(token, confirm.id)
        setNotice('Payment rejected.')
      }
      setConfirm(null)
      await load()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Action failed')
      setConfirm(null)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="admin-page">
      <p className="eyebrow">Billing</p>
      <h1>Payments</h1>
      <p className="lede">Approve or reject JazzCash and EasyPaisa receipts.</p>

      <div className="admin-toolbar">
        {['pending', 'approved', 'rejected', ''].map((value) => (
          <Button
            key={value || 'all'}
            variant={status === value ? 'primary' : 'secondary'}
            onClick={() => setStatus(value)}
          >
            {value || 'All'}
          </Button>
        ))}
      </div>

      {error ? <Banner tone="danger" title="Error" message={error} /> : null}
      {notice ? <Banner tone="success" title="Done" message={notice} /> : null}

      {payments == null ? (
        <Skeleton height={220} />
      ) : payments.length === 0 ? (
        <EmptyState title="No payments" message="Nothing matches this filter." />
      ) : (
        <div className="admin-table-wrap">
          <table className="admin-table">
            <thead>
              <tr>
                <th>ID</th>
                <th>User</th>
                <th>Plan</th>
                <th>Amount</th>
                <th>Method</th>
                <th>Txn</th>
                <th>Status</th>
                <th>Screenshot</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {payments.map((p) => (
                <tr key={p.id}>
                  <td>{p.id}</td>
                  <td>
                    <div>{p.user_name}</div>
                    <div className="muted" style={{ fontSize: '0.8rem' }}>{p.user_email}</div>
                  </td>
                  <td>{p.plan_name}</td>
                  <td>Rs {p.amount_pkr}</td>
                  <td>{p.payment_method}</td>
                  <td style={{ maxWidth: 140, wordBreak: 'break-all' }}>{p.transaction_id}</td>
                  <td>
                    <StatusChip tone={tone(p.status)}>{p.status}</StatusChip>
                    {p.reviewed_by ? (
                      <div className="muted" style={{ fontSize: '0.75rem', marginTop: 4 }}>
                        by {p.reviewed_by}
                      </div>
                    ) : null}
                  </td>
                  <td>
                    {p.screenshot_url ? (
                      <a href={p.screenshot_url} target="_blank" rel="noreferrer">Open</a>
                    ) : '—'}
                  </td>
                  <td>
                    {p.status === 'pending' ? (
                      <div className="stack" style={{ gap: 6 }}>
                        <Button onClick={() => setConfirm({ action: 'approve', id: p.id })}>
                          Approve
                        </Button>
                        <Button
                          variant="danger"
                          onClick={() => setConfirm({ action: 'reject', id: p.id })}
                        >
                          Reject
                        </Button>
                      </div>
                    ) : null}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {confirm ? (
        <Modal
          title={confirm.action === 'approve' ? 'Approve payment?' : 'Reject payment?'}
          message={
            confirm.action === 'approve'
              ? 'This activates the subscription for 30 days.'
              : 'The user will need to submit again.'
          }
          confirmLabel={confirm.action === 'approve' ? 'Approve' : 'Reject'}
          danger={confirm.action === 'reject'}
          onConfirm={runAction}
          onDismiss={() => !busy && setConfirm(null)}
          cancelLabel="Cancel"
        />
      ) : null}
    </div>
  )
}
