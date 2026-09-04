import { Card } from '../components/Card'

export function TermsPage() {
  return (
    <main className="page page-narrow">
      <h1>Terms</h1>
      <p className="lede">How SLAM uses the phone and your account.</p>
      <div className="stack-lg" style={{ marginTop: 24 }}>
        <Card>
          <h2>Location over SMS</h2>
          <p className="lede">
            The Android app replies to a locate command with coordinates. Anyone who knows the PIN
            can request a location unless you restrict the trusted-number list on the device.
          </p>
        </Card>
        <Card>
          <h2>Your account</h2>
          <p className="lede">
            Registration stores your name, email, phone, and plan. The Free plan activates
            automatically. Paid plans are approved after payment review.
          </p>
        </Card>
        <Card>
          <h2>Consent</h2>
          <p className="lede">
            Only install SLAM on a phone you own or have permission to locate. SMS and location
            access are required for tracking.
          </p>
        </Card>
      </div>
    </main>
  )
}
