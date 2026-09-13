import { useCallback, useEffect, useState } from 'react'
import { useAuth } from '../../context/AuthContext'
import { adminListSubscriptions, adminUpdateSubscription } from '../../api/endpoints'
import { ApiError } from '../../api/client'
import { Banner } from '../../components/Banner'
import { Button } from '../../components/Button'
import { Modal } from '../../components/Modal'
import { Skeleton } from '../../components/Skeleton'
import { StatusChip } from '../../components/StatusChip'

const STATUSES = ['', 'active', 'pending_payment', 'pending_approval', 'expired', 'cancelled']

export function AdminSubscriptionsPage() {
  const { token } = useAuth()
  const [status, setStatus] = useState('')
  const [rows, setRows] = useState(null)
  const [error, setError] = useState(null)
  const [editing, setEditing] = useState(null)

  const load = useCallback(() => {
    return adminListSubscriptions(token, { status: status || undefined })
      .then((res) => setRows(res.data.subscriptions || []))
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Unable to load subscriptions'))
  }, [token, status])

  useEffect(() => {
    setRows(null)
    load()
  }, [load])

  async function save() {
    try {
      await adminUpdateSubscription(token, editing.id, {
        status: editing.status,
        end_date: editing.end_date || null,
      })
      setEditing(null)
      await load()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Unable to update')
      setEditing(null)
    }
  }

  return (
    <div className="admin-page">
      <p className="eyebrow">Billing</p>
      <h1>Subscriptions</h1>
      <p className="lede">Support view of plan assignments and status.</p>

      <div className="admin-toolbar">
        {STATUSES.map((value) => (
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

      {rows == null ? (
        <Skeleton height={200} />
      ) : (
        <div className="admin-table-wrap">
          <table className="admin-table">
            <thead>
              <tr>
                <th>ID</th>
                <th>User</th>
                <th>Plan</th>
                <th>Status</th>
                <th>Used</th>
                <th>Ends</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {rows.map((s) => (
                <tr key={s.id}>
                  <td>{s.id}</td>
                  <td>
                    <div>{s.user_name}</div>
                    <div className="muted" style={{ fontSize: '0.8rem' }}>{s.user_email}</div>
                  </td>
                  <td>{s.plan_name}</td>
                  <td><StatusChip tone={s.status === 'active' ? 'success' : 'warning'}>{s.status}</StatusChip></td>
                  <td>{s.requests_used}</td>
                  <td>{s.end_date ? String(s.end_date).slice(0, 10) : '—'}</td>
                  <td>
                    <Button variant="secondary" onClick={() => setEditing({
                      id: s.id,
                      status: s.status,
                      end_date: s.end_date ? String(s.end_date).slice(0, 10) : '',
                    })}
                    >
                      Edit
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {editing ? (
        <Modal
          title="Edit subscription"
          confirmLabel="Save"
          cancelLabel="Cancel"
          onConfirm={save}
          onDismiss={() => setEditing(null)}
        >
          <div className="stack-lg">
            <label className="field">
              <span>Status</span>
              <select
                value={editing.status}
                onChange={(e) => setEditing({ ...editing, status: e.target.value })}
                style={{ width: '100%', padding: '10px 12px', borderRadius: 8, border: '1px solid var(--border)', background: 'var(--surface)', color: 'var(--text)' }}
              >
                {STATUSES.filter(Boolean).map((s) => (
                  <option key={s} value={s}>{s}</option>
                ))}
              </select>
            </label>
            <label className="field">
              <span>End date</span>
              <input
                type="date"
                value={editing.end_date || ''}
                onChange={(e) => setEditing({ ...editing, end_date: e.target.value })}
              />
            </label>
          </div>
        </Modal>
      ) : null}
    </div>
  )
}
