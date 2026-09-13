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
}) {
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
        onChange={(event) => onChange(event.target.value)}
      />
      {error ? <span className="field-error">{error}</span> : null}
    </div>
  )
}
