import { Link, useLocation } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'

const COPY = {
  '/': {
    backTo: '/plans',
    backLabel: 'Browse plans',
    hint: 'Upgrade or renew from Plans. Finish a receipt on Payments.',
  },
  '/plans': {
    backTo: '/',
    backLabel: 'Back to account',
    guestBackTo: '/login',
    guestBackLabel: 'Sign in',
    hint: 'Choosing a paid plan does not change your live locate quota until payment is approved.',
  },
  '/payments': {
    backTo: '/plans',
    backLabel: 'Back to plans',
    secondaryTo: '/',
    secondaryLabel: 'Account',
    hint: 'Send EasyPaisa or JazzCash from your phone, then upload the screenshot here.',
  },
  '/terms': {
    backTo: '/',
    backLabel: 'Back to account',
    guestBackTo: '/plans',
    guestBackLabel: 'Back to plans',
  },
}

export function PageNav() {
  const { user } = useAuth()
  const { pathname } = useLocation()
  const entry = COPY[pathname]
  if (!entry) return null

  const backTo = user ? entry.backTo : (entry.guestBackTo || entry.backTo)
  const backLabel = user ? entry.backLabel : (entry.guestBackLabel || entry.backLabel)
  if (!backTo || !backLabel) return null

  return (
    <div className="page-nav">
      <div className="page-nav-inner">
        <div className="page-nav-links">
          <Link to={backTo} className="page-nav-back">{backLabel}</Link>
          {user && entry.secondaryTo ? (
            <Link to={entry.secondaryTo}>{entry.secondaryLabel}</Link>
          ) : null}
        </div>
        {entry.hint ? <p className="page-nav-hint">{entry.hint}</p> : null}
      </div>
    </div>
  )
}
