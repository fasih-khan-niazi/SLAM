import { useEffect, useState, useCallback } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../../context/AuthContext'
import { adminStats } from '../../api/endpoints'
import { ApiError } from '../../api/client'
import { Banner } from '../../components/Banner'
import { Skeleton } from '../../components/Skeleton'
import { StatusChip } from '../../components/StatusChip'
import { useIntervalRefresh } from '../../hooks/useIntervalRefresh'

export function AdminDashboardPage() {
  const { token } = useAuth()
  const [stats, setStats] = useState(null)
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(true)

  const load = useCallback(() => {
    adminStats(token)
      .then((res) => setStats(res.data || {}))
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Unable to load stats'))
      .finally(() => setLoading(false))
  }, [token])

  useEffect(() => {
    setLoading(true)
    load()
  }, [load])

  useIntervalRefresh(load, 20000)

  return (
    <div className="admin-page">
      <p className="eyebrow">Operator</p>
      <h1>SLAM Admin</h1>
      <p className="lede">
        Review payments, manage plans and accounts, and tune product settings. Location SMS stays on the phone.
      </p>

      {error ? <Banner tone="danger" title="Error" message={error} /> : null}

      <div className="admin-flags">
        <StatusChip tone={stats?.maintenance ? 'warning' : 'success'}>
          {stats?.maintenance ? 'Maintenance on' : 'Portal live'}
        </StatusChip>
        <StatusChip tone={stats?.paymentsEnabled === false ? 'warning' : 'success'}>
          {stats?.paymentsEnabled === false ? 'Payments off' : 'Payments on'}
        </StatusChip>
        <StatusChip tone={stats?.emergencyEnabled === false ? 'warning' : 'success'}>
          {stats?.emergencyEnabled === false ? 'Emergency off' : 'Emergency available'}
        </StatusChip>
      </div>

      {loading ? (
        <div className="admin-grid" style={{ marginTop: 24 }}>
          <Skeleton height={140} />
          <Skeleton height={140} />
          <Skeleton height={140} />
        </div>
      ) : (
        <div className="admin-grid" style={{ marginTop: 24 }}>
          <div className="admin-card">
            <h3>Payments waiting</h3>
            <p className="stat">{stats?.pendingPayments ?? '—'}</p>
            <Link to="/admin/payments" className="btn">Review payments</Link>
          </div>
          <div className="admin-card">
            <h3>Accounts</h3>
            <p className="stat">{stats?.users ?? '—'}</p>
            <Link to="/admin/users" className="btn">Open users</Link>
          </div>
          <div className="admin-card">
            <h3>Active plans</h3>
            <p className="stat">{stats?.activePlans ?? '—'}</p>
            <Link to="/admin/plans" className="btn">Manage plans</Link>
          </div>
        </div>
      )}
    </div>
  )
}
