export function scorePassword(password) {
  const value = String(password || '')
  const checks = {
    length: value.length >= 8,
    lower: /[a-z]/.test(value),
    upper: /[A-Z]/.test(value),
    digit: /\d/.test(value),
    special: /[^A-Za-z0-9]/.test(value),
  }
  const passed = Object.values(checks).filter(Boolean).length
  let label = 'Weak'
  let percent = 25
  let color = 'var(--danger)'
  if (passed >= 5) {
    label = 'Strong'
    percent = 100
    color = 'var(--success)'
  } else if (passed >= 3) {
    label = 'Medium'
    percent = 60
    color = 'var(--warning)'
  }
  return {
    checks,
    label,
    percent,
    color,
    isAcceptable: checks.length && checks.lower && checks.upper && checks.digit && checks.special,
  }
}

export function PasswordStrength({ password }) {
  const result = scorePassword(password)
  if (!password) return null
  return (
    <div className="strength">
      <div className="strength-bar">
        <div
          className="strength-fill"
          style={{ width: `${result.percent}%`, background: result.color }}
        />
      </div>
      <span className="muted" style={{ fontSize: '0.85rem' }}>
        {result.label}. Use 8+ characters with upper, lower, a digit, and a symbol.
      </span>
    </div>
  )
}
