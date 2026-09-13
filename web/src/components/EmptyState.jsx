export function EmptyState({ title, message }) {
  return (
    <div className="empty">
      <strong style={{ color: 'var(--text)', display: 'block', marginBottom: 6 }}>{title}</strong>
      <span>{message}</span>
    </div>
  )
}
