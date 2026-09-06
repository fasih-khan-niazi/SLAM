import { Navigate, Route, Routes } from 'react-router-dom'
import { useConfig } from './context/ConfigContext'
import { Layout } from './components/Layout'
import { HomePage } from './pages/HomePage'
import { LoginPage } from './pages/LoginPage'
import { PaymentsPage } from './pages/PaymentsPage'
import { PlansPage } from './pages/PlansPage'
import { RegisterPage } from './pages/RegisterPage'
import { TermsPage } from './pages/TermsPage'

export default function App() {
  const { maintenance } = useConfig()

  return (
    <Layout maintenance={maintenance}>
      <Routes>
        <Route path="/" element={<HomePage />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/plans" element={<PlansPage />} />
        <Route path="/payments" element={<PaymentsPage />} />
        <Route path="/terms" element={<TermsPage />} />
        <Route path="*" element={<Navigate to="/plans" replace />} />
      </Routes>
    </Layout>
  )
}
