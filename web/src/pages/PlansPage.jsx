import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { getPlans, subscribeToPlan } from '../api/endpoints'
import { ApiError } from '../api/client'
import { useAuth } from '../context/AuthContext'
import { useConfig } from '../context/ConfigContext'
import { Banner } from '../components/Banner'
import { Button } from '../components/Button'
import { Card } from '../components/Card'
import { Modal } from '../components/Modal'
import { Skeleton } from '../components/Skeleton'
import { StatusChip } from '../components/StatusChip'

function limitLabel(plan) {
  if (plan.monthly_limit == null) return 'Unlimited SMS locates each month'
  return `${plan.monthly_limit} SMS locates each month`
}

export function PlansPage() {
  const { user, token, subscription, refresh } = useAuth()
  const { paymentsEnabled, maintenance } = useConfig()
  const navigate = useNavigate()
  const [plans, setPlans] = useState(null)
  const [error, setError] = useState(null)
  const [busyId, setBusyId] = useState(null)

  useEffect(() => {
    getPlans()
      .then((res) => setPlans(res.data.plans || []))
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Unable to load plans'))
  }, [])

  const pending = subscription?.pending_upgrade
    || (subscription && ['pending_payment', 'pending_approval'].includes(subscription.status) ? subscription : null)
  const active = subscription?.pending_upgrade
    ? subscription
    : (subscription?.active_plan || (
      subscription && ['pending_payment', 'pending_approval'].includes(subscription.status)
        ? null
        : subscription
    ))
  const activeName = active?.plan_name

  async function choose(plan) {
    if (!user) {
      navigate('/login', { state: { from: '/plans' } })
      return
    }
    if (plan.price_pkr === 0) return
    setBusyId(plan.id)
    try {
      const res = await subscribeToPlan(token, plan.id)
      await refresh()
      navigate('/payments', {
        state: {
          subscription_id: res.data.subscription_id,
          plan_name: plan.name,
          amount_pkr: plan.price_pkr,
        },
      })
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not start the subscription')
    } finally {
      setBusyId(null)
    }
  }

  return (
    <main className="page">
      <h1>Plans</h1>
      <p className="lede">
        Start on Free. Upgrade when you need more locates or more trusted numbers on the phone app.
        Your live locate quota only changes after an admin approves payment.
      </p>
      {!paymentsEnabled || maintenance ? (
        <div style={{ marginTop: 16 }}>
          <Banner title="Paid upgrades paused" message="Free still works. Payments will open again when service resumes." />
        </div>
      ) : null}
      {pending ? (
        <div style={{ marginTop: 16 }}>
          <Banner
            title={`${pending.plan_name} payment in progress`}
            message="Your current plan and locate quota stay the same until an admin approves the receipt."
          />
        </div>
      ) : null}

      <div className="card-grid" style={{ marginTop: 24 }}>
        {!plans ? (
          <>
            <Skeleton height={200} />
            <Skeleton height={200} />
            <Skeleton height={200} />
          </>
        ) : (
          plans.map((plan) => {
            const current = activeName === plan.name
            const isPendingPlan = pending?.plan_name === plan.name
            return (
              <Card key={plan.id}>
                <p className="eyebrow">{plan.price_pkr === 0 ? 'Included' : 'Paid plan'}</p>
                <h2>{plan.name}</h2>
                <p className="plan-price">
                  {plan.price_pkr === 0 ? 'Rs 0' : `Rs ${plan.price_pkr} / month`}
                </p>
                <p className="lede">{plan.description}</p>
                <ul className="muted" style={{ paddingLeft: 18, margin: '16px 0 0' }}>
                  <li>{limitLabel(plan)}</li>
                  <li>{plan.max_contacts} trusted number{plan.max_contacts === 1 ? '' : 's'} on the phone</li>
                  <li>PIN and listening stay on Android</li>
                  {plan.emergency_enabled === false ? null : <li>Emergency updates when enabled on the phone</li>}
                </ul>
                {current ? (
                  <div style={{ marginTop: 16 }}>
                    <StatusChip tone="success">Current plan</StatusChip>
                  </div>
                ) : null}
                {isPendingPlan ? (
                  <div style={{ marginTop: 16 }}>
                    <StatusChip tone="warning">Payment in progress</StatusChip>
                    <div style={{ marginTop: 12 }}>
                      <Button onClick={() => navigate('/payments')} block>Continue payment</Button>
                    </div>
                  </div>
                ) : null}
                {user && plan.price_pkr > 0 && !current && !isPendingPlan && !pending && paymentsEnabled && !maintenance ? (
                  <div style={{ marginTop: 16 }}>
                    <Button onClick={() => choose(plan)} loading={busyId === plan.id} block>
                      Choose {plan.name}
                    </Button>
                  </div>
                ) : null}
                {!user && plan.price_pkr > 0 ? (
                  <div style={{ marginTop: 16 }}>
                    <Button onClick={() => choose(plan)} block>Sign in to choose</Button>
                  </div>
                ) : null}
              </Card>
            )
          })
        )}
      </div>

      <p className="muted" style={{ marginTop: 20 }}>
        After you choose a paid plan, send EasyPaisa or JazzCash from your phone, then upload the screenshot on{' '}
        <Link to="/payments">Payments</Link>.
      </p>

      {error ? (
        <Modal title="Plans" message={error} confirmLabel="OK" onConfirm={() => setError(null)} onDismiss={() => setError(null)} />
      ) : null}
    </main>
  )
}
