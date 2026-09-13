export function Button({
  children,
  onClick,
  type = 'button',
  loading = false,
  disabled = false,
  variant = 'primary',
  block = false,
}) {
  const className = [
    'btn',
    variant === 'ghost' ? 'btn-ghost' : '',
    variant === 'secondary' ? 'btn-secondary' : '',
    variant === 'danger' ? 'btn-danger' : '',
    block ? 'btn-block' : '',
  ].filter(Boolean).join(' ')

  return (
    <button type={type} className={className} onClick={onClick} disabled={disabled || loading}>
      {loading ? 'Please wait…' : children}
    </button>
  )
}
