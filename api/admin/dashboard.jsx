import React, { useEffect, useState } from 'react'
import { Box, Button, H2, H5, Text } from '@adminjs/design-system'
import { ApiClient } from 'adminjs'

const api = new ApiClient()

function Stat({ title, value, href, action }) {
  return (
    <Box variant="white" p="xl" flexGrow={1} style={{ minWidth: 220, borderTop: '4px solid #14b8a6' }}>
      <H5>{title}</H5>
      <Text mt="lg" style={{ fontSize: 32, fontWeight: 600 }}>{value ?? '—'}</Text>
      {href ? (
        <Box mt="xl">
          <Button as="a" href={href}>{action}</Button>
        </Box>
      ) : null}
    </Box>
  )
}

const Dashboard = () => {
  const [stats, setStats] = useState(null)

  useEffect(() => {
    api.getDashboard()
      .then((response) => setStats(response.data || {}))
      .catch(() => setStats({}))
  }, [])

  return (
    <Box p={['lg', 'xl']}>
      <Box variant="white" p="xl" mb="xl">
        <H2>SLAM Admin</H2>
        <Text mt="default">
          Operators only. Approve JazzCash and EasyPaisa receipts, manage accounts, and change
          product limits. Location SMS stays on the owner’s phone — this panel does not track anyone.
        </Text>
      </Box>
      <Box flex flexWrap="wrap" style={{ gap: 16 }}>
        <Stat
          title="Payments waiting"
          value={stats?.pendingPayments}
          href="/admin/resources/Payment?filters.status=pending"
          action="Review payments"
        />
        <Stat
          title="Accounts"
          value={stats?.users}
          href="/admin/resources/User"
          action="Open users"
        />
        <Stat
          title="Product settings"
          value={stats?.emergencyEnabled === false ? 'Emergency off' : 'Limits live'}
          href="/admin/resources/SystemConfig"
          action="Edit config"
        />
      </Box>
    </Box>
  )
}

export default Dashboard
