import { useEffect, useState } from 'react'
import { useAuth } from '../../context/AuthContext'
import { adminListLocationLogs } from '../../api/endpoints'
import { ApiError } from '../../api/client'
import { Banner } from '../../components/Banner'
import { Skeleton } from '../../components/Skeleton'
import { EmptyState } from '../../components/EmptyState'

export function AdminLocationLogsPage() {
  const { token } = useAuth()
  const [logs, setLogs] = useState(null)
  const [error, setError] = useState(null)

  useEffect(() => {
    adminListLocationLogs(token)
      .then((res) => setLogs(res.data.logs || []))
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Unable to load logs'))
  }, [token])

  return (
    <div className="admin-page">
      <p className="eyebrow">Operations</p>
      <h1>Location logs</h1>
      <p className="lede">Read-only cloud history synced from phones.</p>

      {error ? <Banner tone="danger" title="Error" message={error} /> : null}

      {logs == null ? (
        <Skeleton height={200} />
      ) : logs.length === 0 ? (
        <EmptyState title="No logs" message="No location events have been synced yet." />
      ) : (
        <div className="admin-table-wrap">
          <table className="admin-table">
            <thead>
              <tr>
                <th>ID</th>
                <th>User</th>
                <th>Coords</th>
                <th>Accuracy</th>
                <th>Requested by</th>
                <th>Source</th>
                <th>When</th>
              </tr>
            </thead>
            <tbody>
              {logs.map((log) => (
                <tr key={log.id}>
                  <td>{log.id}</td>
                  <td>
                    <div>{log.user_name}</div>
                    <div className="muted" style={{ fontSize: '0.8rem' }}>{log.user_email}</div>
                  </td>
                  <td>{Number(log.latitude).toFixed(5)}, {Number(log.longitude).toFixed(5)}</td>
                  <td>{log.accuracy_meters != null ? `±${log.accuracy_meters}m` : (log.accuracy || '—')}</td>
                  <td>{log.requested_by || '—'}</td>
                  <td>{log.source || '—'}</td>
                  <td>{log.captured_at || log.created_at}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
