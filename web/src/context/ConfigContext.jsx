import { createContext, useContext, useEffect, useMemo, useState } from 'react'
import { getConfig } from '../api/endpoints'

const ConfigContext = createContext(null)

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
  }), [config])

  return <ConfigContext.Provider value={value}>{children}</ConfigContext.Provider>
}

export function useConfig() {
  const ctx = useContext(ConfigContext)
  if (!ctx) throw new Error('useConfig must be used inside ConfigProvider')
  return ctx
}
