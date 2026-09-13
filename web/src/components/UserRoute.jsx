import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { Skeleton } from './Skeleton'

/** User-portal routes: admins are sent to the operator shell. */
export function UserRoute({ children }) {
  const { user, ready, isAdmin } = useAuth()
  const location = useLocation()

  if (!ready) {
    return (
      <main className="page">
        <Skeleton height={160} />
      </main>
    )
  }

  if (!user) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }

  if (isAdmin) {
    return <Navigate to="/admin" replace />
  }

  return children
}
