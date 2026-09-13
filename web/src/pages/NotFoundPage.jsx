export function NotFoundPage() {
  return (
    <main className="page page-narrow">
      <h1>Page not found</h1>
      <p className="lede">That link does not exist in the SLAM portal.</p>
      <p className="muted" style={{ marginTop: 16 }}>
        <a href="/plans">Go to plans</a>
        {' · '}
        <a href="/">Account</a>
      </p>
    </main>
  )
}
