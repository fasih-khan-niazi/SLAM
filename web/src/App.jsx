import { lazy, Suspense } from 'react'
import { Route, Routes } from 'react-router-dom'
import { useConfig } from './context/ConfigContext'
import { Layout } from './components/Layout'
import { ProtectedRoute } from './components/ProtectedRoute'
import { Skeleton } from './components/Skeleton'
import { HomePage } from './pages/HomePage'
import { LoginPage } from './pages/LoginPage'
import { RegisterPage } from './pages/RegisterPage'
import { ForgotPasswordPage } from './pages/ForgotPasswordPage'
import { ResetPasswordPage } from './pages/ResetPasswordPage'
import { NotFoundPage } from './pages/NotFoundPage'

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

export default function App() {
  const { maintenance } = useConfig()

  return (
    <Layout maintenance={maintenance}>
      <Suspense fallback={<LazyFallback />}>
        <Routes>
          <Route path="/" element={<ProtectedRoute><HomePage /></ProtectedRoute>} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/forgot-password" element={<ForgotPasswordPage />} />
          <Route path="/reset-password" element={<ResetPasswordPage />} />
          <Route path="/plans" element={<PlansPage />} />
          <Route path="/payments" element={<PaymentsPage />} />
          <Route path="/terms" element={<TermsPage />} />
          <Route path="*" element={<NotFoundPage />} />
        </Routes>
      </Suspense>
    </Layout>
  )
}
