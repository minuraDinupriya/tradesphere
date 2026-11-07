import { Link, Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { AppLayout } from './app/AppLayout'
import { DashboardPage } from './features/dashboard/DashboardPage'
import { ProductDetailsPage } from './features/catalog/ProductDetailsPage'
import { ProductFormPage } from './features/catalog/ProductFormPage'
import { ProductListPage } from './features/catalog/ProductListPage'
import { SignInPage } from './features/identity/SignInPage'
import { useAuth } from './features/identity/useAuth'
import { ErrorState, LoadingState } from './shared/components/Feedback'
import { WarehouseListPage } from './features/inventory/WarehouseListPage'
import { WarehouseFormPage } from './features/inventory/WarehouseFormPage'
import { WarehouseDetailsPage } from './features/inventory/WarehouseDetailsPage'
import { InventoryPage } from './features/inventory/InventoryPage'
import { StockReceivingPage } from './features/inventory/StockReceivingPage'
import { MovementDetailsPage } from './features/inventory/MovementDetailsPage'
import { CustomerListPage } from './features/customers/CustomerListPage'
import { CustomerFormPage } from './features/customers/CustomerFormPage'
import { CustomerDetailsPage } from './features/customers/CustomerDetailsPage'
import { SellingPricePage } from './features/catalog/SellingPricePage'
import { OrderListPage } from './features/ordering/OrderListPage'
import { OrderFormPage } from './features/ordering/OrderFormPage'
import { OrderDetailsPage } from './features/ordering/OrderDetailsPage'

function App() {
  const { user, loading, error, refresh } = useAuth()
  const location = useLocation()
  if (loading) return <LoadingState label="Opening your workspace…" />
  if (error) return <main><ErrorState message={error} onRetry={() => void refresh()} /></main>
  if (!user) return <SignInPage />

  return <Routes>
    <Route element={<AppLayout />}>
      <Route index element={<Navigate to="/dashboard" replace />} />
      <Route path="dashboard" element={<DashboardPage />} />
      <Route path="products" element={<ProductListPage />} />
      <Route path="products/new" element={<ProductFormPage key="new" />} />
      <Route path="products/:id/edit" element={<ProductFormPage key={location.pathname} />} />
      <Route path="products/:id" element={<ProductDetailsPage key={location.pathname} />} />
      <Route path="products/:id/selling-price" element={<SellingPricePage key={location.pathname} />} />
      <Route path="customers" element={<CustomerListPage />} />
      <Route path="customers/new" element={<CustomerFormPage key="new-customer" />} />
      <Route path="customers/:id/edit" element={<CustomerFormPage key={location.pathname} />} />
      <Route path="customers/:id" element={<CustomerDetailsPage key={location.pathname} />} />
      <Route path="orders" element={<OrderListPage />} />
      <Route path="orders/new" element={<OrderFormPage key="new-order" />} />
      <Route path="orders/:id" element={<OrderDetailsPage key={location.pathname} />} />
      <Route path="warehouses" element={<WarehouseListPage />} />
      <Route path="warehouses/new" element={<WarehouseFormPage key="new-warehouse" />} />
      <Route path="warehouses/:id/edit" element={<WarehouseFormPage key={location.pathname} />} />
      <Route path="warehouses/:id" element={<WarehouseDetailsPage key={location.pathname} />} />
      <Route path="inventory" element={<InventoryPage key={location.pathname + location.search} />} />
      <Route path="inventory/receive" element={<StockReceivingPage key={location.pathname + location.search} />} />
      <Route path="inventory/movements" element={<InventoryPage history key={location.pathname + location.search} />} />
      <Route path="inventory/movements/:id" element={<MovementDetailsPage key={location.pathname} />} />
      <Route path="*" element={<section className="state-panel"><h1>Page not found</h1>
        <p>The page you requested does not exist.</p><Link className="text-link" to="/products">Return to products</Link></section>} />
    </Route>
  </Routes>
}

export default App
