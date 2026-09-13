import React from 'react'

function LogoMark() {
  return (
    <svg width="40" height="40" viewBox="0 0 108 108" aria-hidden="true">
      <path
        fill="currentColor"
        d="M54 22c-12.7 0-23 10.3-23 23 0 17.3 23 41 23 41s23-23.7 23-41c0-12.7-10.3-23-23-23zm0 33.5c-5.8 0-10.5-4.7-10.5-10.5S48.2 34.5 54 34.5 64.5 39.2 64.5 45 59.8 55.5 54 55.5z"
      />
    </svg>
  )
}

const inputStyle = {
  width: '100%',
  boxSizing: 'border-box',
  marginBottom: 16,
  padding: '12px 14px',
  background: '#ffffff',
  color: '#171717',
  WebkitTextFillColor: '#171717',
  caretColor: '#171717',
  border: '1px solid #b0b0b0',
  borderRadius: 8,
  fontSize: 16,
  fontFamily: '"Segoe UI", "IBM Plex Sans", system-ui, sans-serif',
}

const Login = () => {
  const props = typeof window !== 'undefined' ? window.__APP_STATE__ : {}
  const { action, errorMessage: message } = props || {}

  return (
    <div className="slam-login" style={{
      minHeight: '100vh',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      padding: 32,
      background: '#f5f5f5',
      color: '#171717',
      fontFamily: '"Segoe UI", "IBM Plex Sans", system-ui, sans-serif',
    }}>
      <div className="slam-login__card" style={{
        width: '100%',
        maxWidth: 420,
        background: '#ffffff',
        border: '1px solid #b0b0b0',
        borderRadius: 16,
        padding: '36px 32px',
        boxShadow: '0 12px 32px rgba(23, 23, 23, 0.08)',
      }}>
        <div className="slam-login__brand" style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 20 }}>
          <span style={{ color: '#0f766e', display: 'inline-flex' }}><LogoMark /></span>
          <div className="slam-login__brand-text">
            <h1 style={{ margin: 0, fontSize: '1.35rem', fontWeight: 700, color: '#171717' }}>SLAM</h1>
            <p style={{ margin: '2px 0 0', color: '#737373', fontSize: '0.9rem' }}>Operator console</p>
          </div>
        </div>
        <h2 className="slam-login__heading" style={{ margin: '0 0 6px', fontSize: '1.75rem', fontWeight: 700, color: '#171717' }}>
          Welcome back
        </h2>
        <p className="slam-login__lede" style={{ margin: '0 0 28px', color: '#737373' }}>
          Sign in to review payments, manage plans, and change product settings.
        </p>
        {message ? (
          <div className="slam-login__error" role="alert" style={{
            background: 'rgba(220, 38, 38, 0.1)',
            border: '1px solid rgba(220, 38, 38, 0.35)',
            color: '#dc2626',
            borderRadius: 8,
            padding: '10px 12px',
            marginBottom: 16,
          }}>
            {message}
          </div>
        ) : null}
        <form action={action} method="POST">
          <label htmlFor="email" style={{ display: 'block', fontSize: '0.875rem', fontWeight: 500, marginBottom: 6, color: '#737373' }}>
            Email
          </label>
          <input
            id="email"
            name="email"
            type="email"
            autoComplete="username"
            required
            style={inputStyle}
          />
          <label htmlFor="password" style={{ display: 'block', fontSize: '0.875rem', fontWeight: 500, marginBottom: 6, color: '#737373' }}>
            Password
          </label>
          <input
            id="password"
            name="password"
            type="password"
            autoComplete="current-password"
            required
            style={inputStyle}
          />
          <button
            type="submit"
            className="slam-login__submit"
            style={{
              width: '100%',
              marginTop: 8,
              padding: '12px 16px',
              border: 'none',
              borderRadius: 999,
              background: '#0f766e',
              color: '#ffffff',
              fontWeight: 600,
              fontSize: '1rem',
              cursor: 'pointer',
              fontFamily: 'inherit',
            }}
          >
            Sign in
          </button>
        </form>
        <p className="slam-login__hint" style={{ marginTop: 20, textAlign: 'center', color: '#737373', fontSize: '0.85rem' }}>
          Operators only — location SMS stays on the phone.
        </p>
      </div>
    </div>
  )
}

export default Login
