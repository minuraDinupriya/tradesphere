import { useState } from 'react'
import type { FormEvent } from 'react'
import { errorMessage } from '../../shared/api/http'
import { useAuth } from './useAuth'

export function SignInPage() {
  const { signIn } = useAuth()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function submit(event: FormEvent) {
    event.preventDefault()
    setPending(true)
    setError(null)
    try { await signIn(username, password) }
    catch (failure) { setError(errorMessage(failure)); setPassword('') }
    finally { setPending(false) }
  }

  return <main className="signin-shell">
    <section className="signin-card" aria-labelledby="signin-title">
      <div className="brand"><span className="brand-mark" aria-hidden="true">TS</span>TradeSphere</div>
      <p className="eyebrow">Your business, connected</p>
      <h1 id="signin-title">Welcome back.</h1>
      <p className="muted">Sign in to your organization’s product workspace.</p>
      <form onSubmit={submit}>
        <div className="form-field"><label htmlFor="username">Username</label>
          <input id="username" autoComplete="username" required maxLength={80} value={username}
            onChange={event => setUsername(event.target.value)} disabled={pending} /></div>
        <div className="form-field"><label htmlFor="password">Password</label>
          <input id="password" type="password" autoComplete="current-password" required value={password}
            onChange={event => setPassword(event.target.value)} disabled={pending} /></div>
        {error && <p className="error-panel" role="alert">{error}</p>}
        <button className="button primary full-width" disabled={pending}>{pending ? 'Signing in…' : 'Sign in'}</button>
      </form>
      <p className="small muted">Access is provisioned for your organization. Contact the project owner if you need an account.</p>
    </section>
  </main>
}
