export function StatusChip({ tone = 'neutral', children }) {
  return <span className={`chip chip-${tone}`}>{children}</span>
}
