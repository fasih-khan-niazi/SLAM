import { Card } from '../components/Card'

export function TermsPage() {
  return (
    <main className="page page-narrow">
      <h1>Terms</h1>
      <p className="lede">How SLAM uses the phone and your account.</p>
      <div className="stack-lg" style={{ marginTop: 24 }}>
        <Card>
          <h2>Ownership</h2>
          <p className="lede">
            Only install SLAM on a phone you own or clearly have permission to locate.
          </p>
        </Card>
        <Card>
          <h2>Location over SMS</h2>
          <p className="lede">
            While Listening is on, trusted numbers can request location by SMS with your PIN.
            The Android app replies with coordinates. Listening, PIN, and trusted numbers are managed on the phone.
          </p>
        </Card>
        <Card>
          <h2>Permissions</h2>
          <p className="lede">
            The phone app needs SMS, location (including background), and notifications.
            Some devices pause background apps. Allow unrestricted battery use if listening stops unexpectedly.
          </p>
        </Card>
        <Card>
          <h2>Your account and payments</h2>
          <p className="lede">
            Registration stores your name, email, phone, and plan. Free activates automatically.
            Paid plans stay pending until you send EasyPaisa or JazzCash from your phone and an admin approves the receipt.
          </p>
        </Card>
        <Card>
          <h2>PIN attempts</h2>
          <p className="lede">
            Wrong PIN texts are ignored. After repeated failures from one sender, that sender is locked out for a short window.
          </p>
        </Card>
      </div>
    </main>
  )
}
