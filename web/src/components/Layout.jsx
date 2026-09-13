import { Link, NavLink, useLocation } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { useTheme } from '../context/ThemeContext'
import { Logo } from './Logo'
import { Button } from './Button'
import { PageNav } from './PageNav'

export function Layout({ children, maintenance = false }) {
  const { user, logout } = useAuth()
  const { theme, toggleTheme } = useTheme()
  const location = useLocation()
  const hideChrome = ['/login', '/register', '/forgot-password', '/reset-password'].includes(location.pathname)

  if (hideChrome) {
    return (
      <div className="app-shell">
        {maintenance ? (
          <p className="maintenance">Service is paused. You can still sign in to review your account.</p>
        ) : null}
        {children}
      </div>
    )
  }

  return (
    <div className="app-shell">
      {maintenance ? (
        <p className="maintenance">Service is paused. You can still sign in to review your account.</p>
      ) : null}
      <header className="topbar">
        <div className="topbar-side topbar-side-start">
          <Link to={user ? '/' : '/plans'} className="brand">
            <span style={{ color: 'var(--primary)', display: 'inline-flex' }}><Logo /></span>
            <span>SLAM</span>
            <span className="brand-tag">Secure location by SMS</span>
          </Link>
        </div>

        <nav className="nav-center" aria-label="Main">
          {user ? (
            <>
              <NavLink to="/" end>Account</NavLink>
              <NavLink to="/plans">Plans</NavLink>
              <NavLink to="/payments">Payments</NavLink>
            </>
          ) : (
            <>
              <NavLink to="/plans">Plans</NavLink>
              <NavLink to="/terms">Terms</NavLink>
            </>
          )}
        </nav>

        <div className="topbar-side topbar-side-end">
          <nav className="nav-actions">
            <Button variant="ghost" onClick={toggleTheme}>
              {theme === 'dark' ? 'Light' : 'Dark'}
            </Button>
            {user ? (
              <Button variant="ghost" onClick={logout}>Sign out</Button>
            ) : (
              <NavLink to="/login">Sign in</NavLink>
            )}
          </nav>
        </div>
      </header>
      <PageNav />
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
