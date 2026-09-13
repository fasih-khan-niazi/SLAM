import { useCallback, useEffect, useState } from 'react'
import { useAuth } from '../../context/AuthContext'
import {
  adminDeactivateUser,
  adminListUsers,
  adminReactivateUser,
  adminSuspendUser,
  adminUnsuspendUser,
} from '../../api/endpoints'
import { ApiError } from '../../api/client'
import { Banner } from '../../components/Banner'
import { Button } from '../../components/Button'
import { Field } from '../../components/Field'
import { Modal } from '../../components/Modal'
import { Skeleton } from '../../components/Skeleton'
import { StatusChip } from '../../components/StatusChip'

const STATUS_FILTERS = [
  { value: '', label: 'All' },
  { value: 'active', label: 'Active' },
  { value: 'suspended', label: 'Suspended' },
  { value: 'deactivated', label: 'Deactivated' },
]

function statusTone(status) {
  if (status === 'suspended') return 'warning'
  if (status === 'deactivated') return 'danger'
  return 'success'
}

export function AdminUsersPage() {
  const { token, user: me } = useAuth()
  const [q, setQ] = useState('')
  const [statusFilter, setStatusFilter] = useState('')
  const [users, setUsers] = useState(null)
  const [error, setError] = useState(null)
  const [notice, setNotice] = useState(null)
  const [confirm, setConfirm] = useState(null)
  const [busy, setBusy] = useState(false)

  const load = useCallback(() => {
    return adminListUsers(token, {
      q: q.trim() || undefined,
      accountStatus: statusFilter || undefined,
    })
      .then((res) => setUsers(res.data.users || []))
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Unable to load users'))
  }, [token, q, statusFilter])

  useEffect(() => {
    const t = setTimeout(() => {
      setUsers(null)
      load()
    }, 200)
    return () => clearTimeout(t)
  }, [load])

  function canManage(u) {
    return u.role !== 'admin' && u.id !== me?.id
  }

  async function runAction() {
    if (!confirm) return
    setBusy(true)
    setError(null)
    try {
      let res
      if (confirm.action === 'suspend') res = await adminSuspendUser(token, confirm.user.id)
      else if (confirm.action === 'unsuspend') res = await adminUnsuspendUser(token, confirm.user.id)
      else if (confirm.action === 'deactivate') res = await adminDeactivateUser(token, confirm.user.id)
      else if (confirm.action === 'reactivate') res = await adminReactivateUser(token, confirm.user.id)
      setNotice(res?.message || 'Account updated.')
      setConfirm(null)
      await load()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Unable to update account')
      setConfirm(null)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="admin-page">
      <p className="eyebrow">Operations</p>
      <h1>Users</h1>
      <p className="lede">
        Search accounts and suspend or deactivate them. Profile fields cannot be edited by admins.
      </p>

      <div className="admin-toolbar">
        <Field id="user-q" label="Search" value={q} onChange={setQ} placeholder="Name, email, phone" />
        {STATUS_FILTERS.map((f) => (
          <Button
            key={f.value || 'all'}
            variant={statusFilter === f.value ? 'primary' : 'secondary'}
            onClick={() => setStatusFilter(f.value)}
          >
            {f.label}
          </Button>
        ))}
      </div>

      {error ? <Banner tone="danger" title="Error" message={error} /> : null}
      {notice ? <Banner tone="success" title="Done" message={notice} /> : null}

      {users == null ? (
        <Skeleton height={200} />
      ) : (
        <div className="admin-table-wrap">
          <table className="admin-table">
            <thead>
              <tr>
                <th>Name</th>
                <th>Email</th>
                <th>Phone</th>
                <th>Role</th>
                <th>Status</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {users.map((u) => {
                const status = u.account_status || 'active'
                return (
                  <tr key={u.id}>
                    <td>{u.name}</td>
                    <td>{u.email}</td>
                    <td>{u.phone}</td>
                    <td>
                      <StatusChip tone={u.role === 'admin' ? 'success' : 'neutral'}>{u.role}</StatusChip>
                    </td>
                    <td>
                      <StatusChip tone={statusTone(status)}>{status}</StatusChip>
                    </td>
                    <td>
                      {canManage(u) ? (
                        <div className="stack" style={{ gap: 6 }}>
                          {status === 'active' ? (
                            <Button
                              variant="secondary"
                              onClick={() => setConfirm({
                                action: 'suspend',
                                user: u,
                                title: 'Suspend account?',
                                message: `Suspend ${u.email}? They cannot sign in or use SLAM until unsuspended. Active subscriptions will pause.`,
                                confirmLabel: 'Suspend',
                              })}
                            >
                              Suspend
                            </Button>
                          ) : null}
                          {status === 'suspended' ? (
                            <Button
                              variant="secondary"
                              onClick={() => setConfirm({
                                action: 'unsuspend',
                                user: u,
                                title: 'Unsuspend account?',
                                message: `Restore access for ${u.email}? Paused subscriptions will resume.`,
                                confirmLabel: 'Unsuspend',
                              })}
                            >
                              Unsuspend
                            </Button>
                          ) : null}
                          {status !== 'deactivated' ? (
                            <Button
                              variant="danger"
                              onClick={() => setConfirm({
                                action: 'deactivate',
                                user: u,
                                title: 'Deactivate account?',
                                message: `Soft-deactivate ${u.email}? Login and usage are blocked. You can reactivate later.`,
                                confirmLabel: 'Deactivate',
                                danger: true,
                              })}
                            >
                              Deactivate
                            </Button>
                          ) : (
                            <Button
                              variant="secondary"
                              onClick={() => setConfirm({
                                action: 'reactivate',
                                user: u,
                                title: 'Reactivate account?',
                                message: `Restore ${u.email} to active? Paused subscriptions will resume.`,
                                confirmLabel: 'Reactivate',
                              })}
                            >
                              Reactivate
                            </Button>
                          )}
                        </div>
                      ) : (
                        <span className="muted">—</span>
                      )}
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}

      {confirm ? (
        <Modal
          title={confirm.title}
          message={confirm.message}
          confirmLabel={busy ? 'Working…' : confirm.confirmLabel}
          cancelLabel="Cancel"
          danger={Boolean(confirm.danger)}
          onConfirm={busy ? undefined : runAction}
          onDismiss={busy ? undefined : () => setConfirm(null)}
        />
      ) : null}
    </div>
  )
}
