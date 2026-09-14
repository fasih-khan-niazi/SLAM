import { useEffect, useRef } from 'react'

/**
 * Call `onTick` on an interval while the document tab is focused.
 */
export function useIntervalRefresh(onTick, ms = 20000) {
  const saved = useRef(onTick)
  useEffect(() => {
    saved.current = onTick
  }, [onTick])

  useEffect(() => {
    if (!ms || ms < 1000) return undefined
    const tick = () => {
      if (typeof document !== 'undefined' && document.visibilityState === 'hidden') return
      saved.current?.()
    }
    const id = setInterval(tick, ms)
    const onVisible = () => {
      if (document.visibilityState === 'visible') tick()
    }
    document.addEventListener('visibilitychange', onVisible)
    return () => {
      clearInterval(id)
      document.removeEventListener('visibilitychange', onVisible)
    }
  }, [ms])
}
