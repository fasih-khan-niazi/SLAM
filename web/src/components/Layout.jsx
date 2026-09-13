import { Link, NavLink } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { useTheme } from '../context/ThemeContext'
import { Logo } from './Logo'
import { Button } from './Button'

export function Layout({ children, maintenance = false }) {
  const { user, logout } = useAuth()
  const { theme, toggleTheme } = useTheme()

  return (
    <div className="app-shell">
      {maintenance ? (
        <p className="maintenance">Service is paused. You can still sign in to review your account.</p>
      ) : null}
      <header className="topbar">
        <Link to={user ? '/' : '/plans'} className="brand">
          <span style={{ color: 'var(--primary)', display: 'inline-flex' }}><Logo /></span>
          <span>SLAM</span>
          <span className="brand-tag">Secure location by SMS</span>
        </Link>
        <nav className="nav-links">
          <NavLink to="/plans">Plans</NavLink>
          {user ? <NavLink to="/payments">Payments</NavLink> : null}
          {user ? <NavLink to="/">Account</NavLink> : <NavLink to="/login">Sign in</NavLink>}
          <Button variant="ghost" onClick={toggleTheme}>
            {theme === 'dark' ? 'Light' : 'Dark'}
          </Button>
          {user ? (
            <Button variant="ghost" onClick={logout}>Sign out</Button>
          ) : null}
        </nav>
      </header>
      {children}
      <footer className="footer">
        <div className="footer-inner">
          <p style={{ margin: 0 }}>
            Listening, PIN, and trusted numbers live in the Android app. This portal is for account, plans, and payments.
          </p>
          <p style={{ margin: 0 }}>
            <Link to="/terms">Terms</Link>
            {' · '}
            <Link to="/plans">Plans</Link>
          </p>
        </div>
      </footer>
    </div>
  )
}
