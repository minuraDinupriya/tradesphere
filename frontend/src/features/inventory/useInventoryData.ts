import { useCallback, useEffect, useState } from 'react'
import { ApiError, errorMessage } from '../../shared/api/http'
import { useAuth } from '../identity/useAuth'

export function useInventoryData<T>(load: (signal: AbortSignal) => Promise<T>) {
  const { refresh } = useAuth()
  const [data, setData] = useState<T | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [attempt, setAttempt] = useState(0)
  const begin = useCallback(() => { setLoading(true); setError(null) }, [])
  const reload = useCallback(() => { begin(); setAttempt(value => value + 1) }, [begin])

  useEffect(() => {
    const controller = new AbortController()
    load(controller.signal).then(result => { if (!controller.signal.aborted) setData(result) })
      .catch(failure => {
        if (controller.signal.aborted) return
        if (failure instanceof ApiError && failure.problem.status === 401) void refresh()
        else setError(errorMessage(failure))
      }).finally(() => { if (!controller.signal.aborted) setLoading(false) })
    return () => controller.abort()
  }, [load, attempt, refresh])
  return { data, loading, error, begin, reload }
}
