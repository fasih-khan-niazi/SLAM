import { useState } from 'react'
import { Link, NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { useTheme } from '../context/ThemeContext'
import { Logo } from './Logo'
import { Button } from './Button'
import { Modal } from './Modal'
import { SignOutIcon, ThemeIcon } from './Icons'

const NAV = [
  { to: '/admin', end: true, label: 'Dashboard' },
  { to: '/admin/payments', label: 'Payments' },
  { to: '/admin/plans', label: 'Plans' },
  { to: '/admin/config', label: 'Config' },
  { to: '/admin/users', label: 'Users' },
  { to: '/admin/subscriptions', label: 'Subscriptions' },
  { to: '/admin/location-logs', label: 'Location logs' },
]

export function AdminLayout() {
  const { user, logout } = useAuth()
  const { theme, toggleTheme } = useTheme()
  const [confirmOut, setConfirmOut] = useState(false)

  return (
    <div className="admin-shell">
      <aside className="admin-sidebar" aria-label="Admin">
        <Link to="/admin" className="admin-brand">
          <span style={{ color: 'var(--primary)', display: 'inline-flex' }}><Logo size={28} /></span>
          <span className="admin-brand-text">
            <strong>SLAM</strong>
            <span>Operator</span>
          </span>
        </Link>
        <nav className="admin-nav">
          {NAV.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              className={({ isActive }) => `admin-nav-link${isActive ? ' active' : ''}`}
            >
              {item.label}
            </NavLink>
          ))}
        </nav>
        <div className="admin-sidebar-foot">
          <p className="muted" style={{ margin: '0 0 8px', fontSize: '0.8rem' }}>
            {user?.email}
          </p>
          <Button variant="secondary" onClick={toggleTheme}>
            <span className="btn-with-icon">
              <ThemeIcon dark={theme === 'dark'} />
              {theme === 'dark' ? 'Light' : 'Dark'}
            </span>
          </Button>
          <Button variant="secondary" onClick={() => setConfirmOut(true)}>
            <span className="btn-with-icon">
              <SignOutIcon />
              Sign out
            </span>
          </Button>
        </div>
      </aside>
      <div className="admin-main">
        <Outlet />
      </div>

      {confirmOut ? (
        <Modal
          title="Sign out?"
          message="You will leave the operator console. SMS tracking on phones is not affected."
          confirmLabel="Sign out"
          cancelLabel="Stay signed in"
          danger
          onConfirm={() => {
            setConfirmOut(false)
            logout()
          }}
          onDismiss={() => setConfirmOut(false)}
        />
      ) : null}
    </div>
  )
}
