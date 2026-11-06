import { Link, Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { AppLayout } from './app/AppLayout'
import { ProductDetailsPage } from './features/catalog/ProductDetailsPage'
import { ProductFormPage } from './features/catalog/ProductFormPage'
import { ProductListPage } from './features/catalog/ProductListPage'
import { SignInPage } from './features/identity/SignInPage'
import { useAuth } from './features/identity/useAuth'
import { ErrorState, LoadingState } from './shared/components/Feedback'

function App() {
  const { user, loading, error, refresh } = useAuth()
  const location = useLocation()
  if (loading) return <LoadingState label="Opening your workspace…" />
  if (error) return <main><ErrorState message={error} onRetry={() => void refresh()} /></main>
  if (!user) return <SignInPage />

  return <Routes>
    <Route element={<AppLayout />}>
      <Route index element={<Navigate to="/products" replace />} />
      <Route path="products" element={<ProductListPage />} />
      <Route path="products/new" element={<ProductFormPage key="new" />} />
      <Route path="products/:id/edit" element={<ProductFormPage key={location.pathname} />} />
      <Route path="products/:id" element={<ProductDetailsPage key={location.pathname} />} />
      <Route path="*" element={<section className="state-panel"><h1>Page not found</h1>
        <p>The page you requested does not exist.</p><Link className="text-link" to="/products">Return to products</Link></section>} />
    </Route>
  </Routes>
}

export default App
