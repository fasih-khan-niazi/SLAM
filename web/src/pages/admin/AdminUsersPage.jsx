import { useCallback, useEffect, useState } from 'react'
import { useAuth } from '../../context/AuthContext'
import { adminDeleteUser, adminListUsers, adminUpdateUser } from '../../api/endpoints'
import { ApiError } from '../../api/client'
import { Banner } from '../../components/Banner'
import { Button } from '../../components/Button'
import { Field } from '../../components/Field'
import { Modal } from '../../components/Modal'
import { Skeleton } from '../../components/Skeleton'
import { StatusChip } from '../../components/StatusChip'

export function AdminUsersPage() {
  const { token, user: me } = useAuth()
  const [q, setQ] = useState('')
  const [users, setUsers] = useState(null)
  const [error, setError] = useState(null)
  const [notice, setNotice] = useState(null)
  const [editing, setEditing] = useState(null)
  const [deleteId, setDeleteId] = useState(null)

  const load = useCallback(() => {
    return adminListUsers(token, { q: q.trim() || undefined })
      .then((res) => setUsers(res.data.users || []))
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Unable to load users'))
  }, [token, q])

  useEffect(() => {
    const t = setTimeout(() => {
      setUsers(null)
      load()
    }, 200)
    return () => clearTimeout(t)
  }, [load])

  async function save() {
    try {
      await adminUpdateUser(token, editing.id, {
        name: editing.name,
        email: editing.email,
        phone: editing.phone,
        role: editing.role,
      })
      setNotice('User updated.')
      setEditing(null)
      await load()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Unable to update user')
    }
  }

  async function remove() {
    try {
      await adminDeleteUser(token, deleteId)
      setNotice('User deleted.')
      setDeleteId(null)
      await load()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Unable to delete user')
      setDeleteId(null)
    }
  }

  return (
    <div className="admin-page">
      <p className="eyebrow">Operations</p>
      <h1>Users</h1>
      <p className="lede">Search accounts, change roles, and remove non-admin users.</p>

      <div className="admin-toolbar">
        <Field id="user-q" label="Search" value={q} onChange={setQ} placeholder="Name, email, phone" />
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
                <th />
              </tr>
            </thead>
            <tbody>
              {users.map((u) => (
                <tr key={u.id}>
                  <td>{u.name}</td>
                  <td>{u.email}</td>
                  <td>{u.phone}</td>
                  <td>
                    <StatusChip tone={u.role === 'admin' ? 'success' : 'neutral'}>{u.role}</StatusChip>
                  </td>
                  <td>
                    <div className="stack" style={{ gap: 6 }}>
                      <Button variant="secondary" onClick={() => setEditing({ ...u })}>Edit</Button>
                      {u.role !== 'admin' && u.id !== me?.id ? (
                        <Button variant="danger" onClick={() => setDeleteId(u.id)}>Delete</Button>
                      ) : null}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {editing ? (
        <Modal
          title="Edit user"
          confirmLabel="Save"
          cancelLabel="Cancel"
          onConfirm={save}
          onDismiss={() => setEditing(null)}
        >
          <div className="stack-lg">
            <Field id="u-name" label="Name" value={editing.name} onChange={(v) => setEditing({ ...editing, name: v })} />
            <Field id="u-email" label="Email" type="email" value={editing.email} onChange={(v) => setEditing({ ...editing, email: v })} />
            <Field id="u-phone" label="Phone" value={editing.phone} onChange={(v) => setEditing({ ...editing, phone: v })} />
            <label className="field">
              <span>Role</span>
              <select
                value={editing.role}
                onChange={(e) => setEditing({ ...editing, role: e.target.value })}
                style={{ width: '100%', padding: '10px 12px', borderRadius: 8, border: '1px solid var(--border)', background: 'var(--surface)', color: 'var(--text)' }}
              >
                <option value="user">user</option>
                <option value="admin">admin</option>
              </select>
            </label>
          </div>
        </Modal>
      ) : null}

      {deleteId ? (
        <Modal
          title="Delete user?"
          message="This cannot be undone."
          confirmLabel="Delete"
          cancelLabel="Cancel"
          danger
          onConfirm={remove}
          onDismiss={() => setDeleteId(null)}
        />
      ) : null}
    </div>
  )
}
