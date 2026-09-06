import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { getPlans, subscribeToPlan } from '../api/endpoints'
import { ApiError } from '../api/client'
import { useAuth } from '../context/AuthContext'
import { useConfig } from '../context/ConfigContext'
import { Button } from '../components/Button'
import { Card } from '../components/Card'
import { Skeleton } from '../components/Skeleton'
import { Modal } from '../components/Modal'

function limitLabel(plan) {
  if (plan.monthly_limit == null) return 'Unlimited location requests'
  return `${plan.monthly_limit} location requests each month`
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

  async function choose(plan) {
    if (!user) {
      navigate('/login')
      return
    }
    if (plan.price_pkr === 0) return
    setBusyId(plan.id)
    try {
      const res = await subscribeToPlan(token, plan.id)
      await refresh()
      navigate('/payments', { state: { subscription_id: res.data.subscription_id } })
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not start the subscription')
    } finally {
      setBusyId(null)
    }
  }

  return (
    <main className="page">
      <h1>Plans</h1>
      <p className="lede">Start on Free. Upgrade when the family needs more requests or more trusted numbers.</p>
      {!paymentsEnabled || maintenance ? (
        <p className="lede">Paid upgrades are paused right now. Free still works.</p>
      ) : null}
      <div className="card-grid" style={{ marginTop: 24 }}>
        {!plans ? (
          <>
            <Skeleton height={180} />
            <Skeleton height={180} />
            <Skeleton height={180} />
          </>
        ) : (
          plans.map((plan) => {
            const current = subscription?.plan_name === plan.name
            return (
              <Card key={plan.id}>
                <p className="eyebrow">{plan.price_pkr === 0 ? 'Included' : `Rs ${plan.price_pkr} / month`}</p>
                <h2>{plan.name}</h2>
                <p className="lede">{plan.description}</p>
                <ul className="muted" style={{ paddingLeft: 18, margin: '16px 0 0' }}>
                  <li>{limitLabel(plan)}</li>
                  <li>{plan.max_contacts} trusted number{plan.max_contacts === 1 ? '' : 's'}</li>
                  <li>{plan.has_history ? 'Location history on the web' : 'On-device history only'}</li>
                </ul>
                {current ? <p className="badge" style={{ marginTop: 16 }}>Current plan</p> : null}
                {user && plan.price_pkr > 0 && !current && paymentsEnabled && !maintenance ? (
                  <div style={{ marginTop: 16 }}>
                    <Button onClick={() => choose(plan)} loading={busyId === plan.id} block>
                      Choose {plan.name}
                    </Button>
                  </div>
                ) : null}
              </Card>
            )
          })
        )}
      </div>
      <p className="muted" style={{ marginTop: 24 }}>
        {user ? (
          <Link to="/">Back to your account</Link>
        ) : (
          <>
            <Link to="/register">Create an account</Link> to activate Free automatically.
          </>
        )}
      </p>
      {error ? (
        <Modal title="Plans unavailable" message={error} confirmLabel="OK" onConfirm={() => setError(null)} onDismiss={() => setError(null)} />
      ) : null}
    </main>
  )
}
