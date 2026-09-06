import { Link, NavLink } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { useTheme } from '../context/ThemeContext'
import { Logo } from './Logo'
import { Button } from './Button'

export function Layout({ children, maintenance = false }) {
  const { user } = useAuth()
  const { theme, toggleTheme } = useTheme()

  return (
    <div className="app-shell">
      {maintenance ? <p className="maintenance">Service is paused. You can still sign in to review your account.</p> : null}
      <header className="topbar">
        <Link to={user ? '/' : '/plans'} className="brand">
          <span style={{ color: 'var(--primary)' }}><Logo /></span>
          SLAM
        </Link>
        <nav className="nav-links">
          <NavLink to="/plans">Plans</NavLink>
          {user ? <NavLink to="/payments">Payments</NavLink> : null}
          {user ? <NavLink to="/">Account</NavLink> : <NavLink to="/login">Sign in</NavLink>}
          <Button variant="ghost" onClick={toggleTheme}>
            {theme === 'dark' ? 'Light' : 'Dark'}
          </Button>
        </nav>
      </header>
      {children}
    </div>
  )
}
