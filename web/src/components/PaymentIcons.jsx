/** Simple JazzCash-style mark for payment UI */
export function JazzCashIcon({ size = 36 }) {
  return (
    <svg className="method-icon" width={size} height={size} viewBox="0 0 64 64" aria-hidden="true">
      <rect width="64" height="64" rx="14" fill="#E21836" />
      <text
        x="32"
        y="40"
        textAnchor="middle"
        fill="#fff"
        fontSize="22"
        fontFamily="Segoe UI, sans-serif"
        fontWeight="700"
      >
        JC
      </text>
    </svg>
  )
}

/** Simple EasyPaisa-style mark for payment UI */
export function EasyPaisaIcon({ size = 36 }) {
  return (
    <svg className="method-icon" width={size} height={size} viewBox="0 0 64 64" aria-hidden="true">
      <rect width="64" height="64" rx="14" fill="#00A651" />
      <text
        x="32"
        y="40"
        textAnchor="middle"
        fill="#fff"
        fontSize="22"
        fontFamily="Segoe UI, sans-serif"
        fontWeight="700"
      >
        EP
      </text>
    </svg>
  )
}
