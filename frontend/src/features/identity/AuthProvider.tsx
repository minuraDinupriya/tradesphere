import { useCallback, useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import { ApiError, errorMessage, request, resetCsrf } from '../../shared/api/http'
import { AuthContext } from './useAuth'
import type { SessionUser } from './useAuth'

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<SessionUser | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const refresh = useCallback(async () => {
    setLoading(true)
    setError(null)
    try { setUser(await request<SessionUser>('/auth/session')) }
    catch (failure) {
      setUser(null)
      if (!(failure instanceof ApiError && failure.problem.status === 401)) setError(errorMessage(failure))
    } finally { setLoading(false) }
  }, [])

  useEffect(() => {
    let cancelled = false
    request<SessionUser>('/auth/session').then(result => { if (!cancelled) setUser(result) })
      .catch(failure => {
        if (!cancelled && !(failure instanceof ApiError && failure.problem.status === 401)) setError(errorMessage(failure))
      }).finally(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true }
  }, [])

  async function signIn(username: string, password: string) {
    resetCsrf()
    await request<void>('/auth/login', { method: 'POST', body: new URLSearchParams({ username, password }) })
    resetCsrf() // Spring rotates the CSRF token after successful authentication.
    setUser(await request<SessionUser>('/auth/session'))
  }

  async function signOut() {
    await request<void>('/auth/logout', { method: 'POST' })
    resetCsrf()
    setUser(null)
  }

  return <AuthContext.Provider value={{ user, loading, error, refresh, signIn, signOut }}>{children}</AuthContext.Provider>
}
