import { createContext, useContext, useEffect, useMemo, useState } from 'react'
import { getConfig } from '../api/endpoints'

const ConfigContext = createContext(null)
const MERCHANT_FALLBACK = '03300490019'

export function ConfigProvider({ children }) {
  const [config, setConfig] = useState(null)

  useEffect(() => {
    getConfig()
      .then((res) => setConfig(res.data || null))
      .catch(() => {})
  }, [])

  const value = useMemo(() => ({
    config,
    maintenance: Boolean(config?.maintenance),
    paymentsEnabled: config?.payments_enabled !== false,
    easypaisaAccount: config?.easypaisa_account || MERCHANT_FALLBACK,
    jazzcashAccount: config?.jazzcash_account || MERCHANT_FALLBACK,
  }), [config])

  return <ConfigContext.Provider value={value}>{children}</ConfigContext.Provider>
}

export function useConfig() {
  const ctx = useContext(ConfigContext)
  if (!ctx) throw new Error('useConfig must be used inside ConfigProvider')
  return ctx
}
