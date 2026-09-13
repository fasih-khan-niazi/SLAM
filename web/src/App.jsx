import { lazy, Suspense } from 'react'
import { Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { useAuth, homePathForUser } from './context/AuthContext'
import { useConfig } from './context/ConfigContext'
import { Layout } from './components/Layout'
import { AdminLayout } from './components/AdminLayout'
import { AdminRoute } from './components/AdminRoute'
import { UserRoute } from './components/UserRoute'
import { Skeleton } from './components/Skeleton'
import { HomePage } from './pages/HomePage'
import { LoginPage } from './pages/LoginPage'
import { RegisterPage } from './pages/RegisterPage'
import { ForgotPasswordPage } from './pages/ForgotPasswordPage'
import { ResetPasswordPage } from './pages/ResetPasswordPage'
import { NotFoundPage } from './pages/NotFoundPage'
import { AdminDashboardPage } from './pages/admin/AdminDashboardPage'
import { AdminPaymentsPage } from './pages/admin/AdminPaymentsPage'
import { AdminPlansPage } from './pages/admin/AdminPlansPage'
import { AdminConfigPage } from './pages/admin/AdminConfigPage'
import { AdminUsersPage } from './pages/admin/AdminUsersPage'
import { AdminSubscriptionsPage } from './pages/admin/AdminSubscriptionsPage'
import { AdminLocationLogsPage } from './pages/admin/AdminLocationLogsPage'

const PlansPage = lazy(() => import('./pages/PlansPage').then((m) => ({ default: m.PlansPage })))
const PaymentsPage = lazy(() => import('./pages/PaymentsPage').then((m) => ({ default: m.PaymentsPage })))
const TermsPage = lazy(() => import('./pages/TermsPage').then((m) => ({ default: m.TermsPage })))

function LazyFallback() {
  return (
    <main className="page">
      <Skeleton height={180} />
    </main>
  )
}

function AdminRedirect() {
  const { user, ready } = useAuth()
  if (!ready) return <Skeleton height={120} />
  return <Navigate to={homePathForUser(user)} replace />
}

export default function App() {
  const { maintenance } = useConfig()
  const location = useLocation()
  const isAdminPath = location.pathname.startsWith('/admin')

  if (isAdminPath) {
    return (
      <Suspense fallback={<LazyFallback />}>
        <Routes>
          <Route
            path="/admin"
            element={(
              <AdminRoute>
                <AdminLayout />
              </AdminRoute>
            )}
          >
            <Route index element={<AdminDashboardPage />} />
            <Route path="payments" element={<AdminPaymentsPage />} />
            <Route path="plans" element={<AdminPlansPage />} />
            <Route path="config" element={<AdminConfigPage />} />
            <Route path="users" element={<AdminUsersPage />} />
            <Route path="subscriptions" element={<AdminSubscriptionsPage />} />
            <Route path="location-logs" element={<AdminLocationLogsPage />} />
          </Route>
          <Route path="*" element={<NotFoundPage />} />
        </Routes>
      </Suspense>
    )
  }

  return (
    <Layout maintenance={maintenance}>
      <Suspense fallback={<LazyFallback />}>
        <Routes>
          <Route path="/" element={<UserRoute><HomePage /></UserRoute>} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/forgot-password" element={<ForgotPasswordPage />} />
          <Route path="/reset-password" element={<ResetPasswordPage />} />
          <Route path="/plans" element={<PlansPage />} />
          <Route path="/payments" element={<UserRoute><PaymentsPage /></UserRoute>} />
          <Route path="/terms" element={<TermsPage />} />
          <Route path="/admin/*" element={<AdminRedirect />} />
          <Route path="*" element={<NotFoundPage />} />
        </Routes>
      </Suspense>
    </Layout>
  )
}
