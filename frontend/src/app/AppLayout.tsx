import { useState } from 'react'
import { NavLink, Outlet } from 'react-router-dom'
import { errorMessage } from '../shared/api/http'
import { useAuth } from '../features/identity/useAuth'

export function AppLayout() {
  const { user, signOut } = useAuth()
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const editableCapabilities = [user?.canWriteCatalog && 'Catalog', user?.canWriteInventory && 'Inventory',
    user?.canWriteCustomers && 'Customers', user?.canWritePrices && 'Prices'].filter(Boolean).join(', ')

  async function logout() {
    setPending(true); setError(null)
    try { await signOut() }
    catch (failure) { setError(errorMessage(failure)) }
    finally { setPending(false) }
  }

  return <div className="app-shell">
    <a className="skip-link" href="#main-content">Skip to content</a>
    <aside className="sidebar">
      <NavLink className="brand" to="/products" aria-label="TradeSphere home"><span className="brand-mark" aria-hidden="true">TS</span>TradeSphere</NavLink>
      <div className="organization-card"><span className="eyebrow">Workspace</span><strong>{user?.organizationName}</strong></div>
      <p className="nav-section-label">Workspace</p>
      <nav aria-label="Main navigation">
        <NavLink className="nav-link" to="/products"><span aria-hidden="true">◇</span> Products</NavLink>
        <NavLink className="nav-link" to="/customers"><span aria-hidden="true">♧</span> Customers</NavLink>
        <NavLink className="nav-link" to="/warehouses"><span aria-hidden="true">▤</span> Warehouses</NavLink>
        <NavLink className="nav-link" to="/inventory" end><span aria-hidden="true">▦</span> Inventory</NavLink>
        <NavLink className="nav-link" to="/inventory/movements"><span aria-hidden="true">↗</span> Stock movements</NavLink>
      </nav>
      <p className="sidebar-note">TradeSphere<br />Fictional B2B commerce platform</p>
    </aside>
    <div className="workspace"><header className="topbar"><div><span className="small muted">{user?.organizationName}</span><p className="topbar-title">Business workspace</p></div>
      <div className="account-menu"><div><strong>{user?.username}</strong><span className="small muted">{editableCapabilities ? `Can edit: ${editableCapabilities}` : 'Read-only access'}</span></div>
        <button className="button secondary" disabled={pending} onClick={() => void logout()}>{pending ? 'Signing out…' : 'Sign out'}</button></div></header>
      <main id="main-content" tabIndex={-1}>{error && <p role="alert" className="error-panel">{error}</p>}<Outlet /></main>
    </div>
  </div>
}
