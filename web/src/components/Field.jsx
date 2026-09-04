export function Field({
  id,
  label,
  type = 'text',
  value,
  onChange,
  autoComplete,
  required = false,
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
        onChange={(event) => onChange(event.target.value)}
      />
    </div>
  )
}
