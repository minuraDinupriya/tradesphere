import { Link, Route, Routes } from 'react-router-dom'
import { AppLayout } from './app/AppLayout'
import { DashboardPage } from './features/dashboard/DashboardPage'

function App() {
  return (
    <Routes>
      <Route element={<AppLayout />}>
        <Route index element={<DashboardPage />} />
        <Route
          path="*"
          element={
            <section className="welcome-panel">
              <p className="eyebrow">Page not found</p>
              <h1>Let’s get you back on track.</h1>
              <p>The page you requested does not exist.</p>
              <Link className="text-link" to="/">Return to overview</Link>
            </section>
          }
        />
      </Route>
    </Routes>
  )
}

export default App
