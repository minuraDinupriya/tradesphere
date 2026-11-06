import { NavLink, Outlet } from 'react-router-dom'

export function AppLayout() {
  return (
    <div className="app-shell">
      <a className="skip-link" href="#main-content">Skip to content</a>
      <aside className="sidebar">
        <NavLink className="brand" to="/" aria-label="TradeSphere home">
          <span className="brand-mark" aria-hidden="true">T</span>
          TradeSphere
        </NavLink>
        <p className="workspace-label">Lanka Distribution Group</p>
        <nav aria-label="Main navigation">
          <NavLink className="nav-link" to="/" end>Overview</NavLink>
        </nav>
        <p className="sidebar-note">Fictional business · Demo workspace</p>
      </aside>
      <div className="workspace">
        <header className="topbar">
          <span>Business workspace</span>
          <span className="status-label">In development</span>
        </header>
        <main id="main-content" tabIndex={-1}>
          <Outlet />
        </main>
        <footer>TradeSphere · A solo software engineering portfolio project</footer>
      </div>
    </div>
  )
}
