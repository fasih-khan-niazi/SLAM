export function Banner({ title, message, tone = 'warning' }) {
  return (
    <div className={`banner banner-${tone}`}>
      <h3>{title}</h3>
      {message ? <p>{message}</p> : null}
    </div>
  )
}
