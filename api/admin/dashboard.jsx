import React from 'react'
import { Box, Button, H2, H5, Text } from '@adminjs/design-system'
import { ApiClient } from 'adminjs'

const api = new ApiClient()

const surface = {
  background: '#ffffff',
  color: '#171717',
  borderRadius: 12,
  boxShadow: '0 12px 32px rgba(23, 23, 23, 0.08)',
  border: '1px solid #b0b0b0',
}

function Flag({ on, onLabel, offLabel }) {
  return (
    <span className={`slam-dash__flag ${on ? 'slam-dash__flag--ok' : 'slam-dash__flag--warn'}`}>
      {on ? onLabel : offLabel}
    </span>
  )
}

function Stat({ title, value, href, action, loading }) {
  return (
    <Box p="xl" flexGrow={1} className="slam-dash__stat" style={{ ...surface, minWidth: 220, borderTop: '4px solid #0f766e' }}>
      <H5 style={{ color: '#737373', margin: 0 }}>{title}</H5>
      <Text mt="lg" style={{ fontSize: 32, fontWeight: 700, color: '#171717', opacity: loading ? 0.35 : 1 }}>
        {loading ? '…' : (value ?? '—')}
      </Text>
      {href ? (
        <Box mt="xl">
          <Button as="a" href={href} variant="primary">
            {action}
          </Button>
        </Box>
      ) : null}
    </Box>
  )
}

const Dashboard = () => {
  const [stats, setStats] = React.useState(null)
  const [loading, setLoading] = React.useState(true)

  React.useEffect(() => {
    let cancelled = false
    api.getDashboard()
      .then((response) => {
        if (!cancelled) setStats(response.data || {})
      })
      .catch(() => {
        if (!cancelled) setStats({})
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => { cancelled = true }
  }, [])

  return (
    <Box p={['lg', 'xl']} style={{ background: '#f5f5f5', minHeight: '100%', color: '#171717' }}>
      <Box p="xl" mb="xl" className="slam-dash__hero" style={{ ...surface, borderTop: '4px solid #0f766e' }}>
        <H2 style={{ color: '#171717', marginTop: 0 }}>SLAM Admin</H2>
        <Text mt="default" style={{ color: '#737373' }}>
          Operators only. Approve JazzCash and EasyPaisa receipts, manage accounts and plans,
          and tune product limits. Location SMS stays on the owner&apos;s phone — this panel
          does not track anyone.
        </Text>
        <Box mt="xl" flex flexWrap="wrap" style={{ gap: 8 }}>
          <Flag
            on={!stats?.maintenance}
            onLabel="Portal live"
            offLabel="Maintenance on"
          />
          <Flag
            on={stats?.paymentsEnabled !== false}
            onLabel="Payments on"
            offLabel="Payments off"
          />
          <Flag
            on={stats?.emergencyEnabled !== false}
            onLabel="Emergency available"
            offLabel="Emergency off"
          />
        </Box>
      </Box>
      <Box flex flexWrap="wrap" style={{ gap: 16 }}>
        <Stat
          title="Payments waiting"
          value={stats?.pendingPayments}
          href="/admin/resources/Payment?filters.status=pending"
          action="Review payments"
          loading={loading}
        />
        <Stat
          title="Accounts"
          value={stats?.users}
          href="/admin/resources/User"
          action="Open users"
          loading={loading}
        />
        <Stat
          title="Active plans"
          value={stats?.activePlans}
          href="/admin/resources/SubscriptionPlan"
          action="Manage plans"
          loading={loading}
        />
        <Stat
          title="Product config"
          value={stats?.smsPrefix ? `SMS ${stats.smsPrefix}` : 'Limits'}
          href="/admin/resources/SystemConfig"
          action="Open config"
          loading={loading}
        />
      </Box>
    </Box>
  )
}

export default Dashboard
