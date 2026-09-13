function parsePositiveInt(raw) {
  const trimmed = String(raw ?? '').trim()
  if (trimmed === '') return ''
  const n = Number(trimmed)
  if (!Number.isFinite(n)) return ''
  const rounded = Math.round(n)
  if (rounded < 1) return ''
  return String(rounded)
}

function parseNonNegativeInt(raw) {
  const trimmed = String(raw ?? '').trim()
  if (trimmed === '') return ''
  const n = Number(trimmed)
  if (!Number.isFinite(n)) return ''
  const rounded = Math.round(n)
  if (rounded < 0) return ''
  return String(rounded)
}

export function Field({
  id,
  label,
  type = 'text',
  value,
  onChange,
  autoComplete,
  required = false,
  error = '',
  placeholder = '',
  min,
  max,
  step,
  /** When type=number: 'positive' (>0), 'nonNegative' (>=0), or null (default browser). */
  integerMode = null,
}) {
  function handleChange(event) {
    const next = event.target.value
    if (type !== 'number' || !integerMode) {
      onChange(next)
      return
    }
    const parsed = integerMode === 'nonNegative' ? parseNonNegativeInt(next) : parsePositiveInt(next)
    onChange(parsed)
  }

  const numberProps = type === 'number'
    ? {
        min: min ?? (integerMode === 'nonNegative' ? 0 : integerMode === 'positive' ? 1 : undefined),
        max,
        step: step ?? (integerMode ? 1 : undefined),
        inputMode: 'numeric',
      }
    : {}

  return (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      <input
        id={id}
        type={type}
        value={value}
        required={required}
        autoComplete={autoComplete}
        placeholder={placeholder}
        onChange={handleChange}
        {...numberProps}
      />
      {error ? <span className="field-error">{error}</span> : null}
    </div>
  )
}
