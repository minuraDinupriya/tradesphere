import { Link } from 'react-router-dom'
import { ErrorState, LoadingState } from '../../shared/components/Feedback'
import { useRemoteData } from '../../shared/hooks/useRemoteData'
import { orderStatusLabels } from '../ordering/orderApi'
import { dashboardApi } from './dashboardApi'

export function DashboardPage() {
  const result = useRemoteData(dashboardApi.get)
  return <>
    <div className="page-heading"><div><h1>Dashboard</h1><p className="muted">Your organization's operational overview</p></div>
      <button className="button secondary" disabled={result.loading} onClick={result.reload}>Refresh dashboard</button></div>
    {result.loading ? <LoadingState label="Loading dashboard…" /> : result.error ? <ErrorState message={result.error} onRetry={result.reload} /> : result.data && <>
      <dl className="dashboard-metrics">
        {([
          ['Active products', result.data.counts.activeProducts, '/products'],
          ['Warehouses', result.data.counts.warehouses, '/warehouses'],
          ['Active customers', result.data.counts.activeCustomers, '/customers'],
          ['Total orders', result.data.counts.totalOrders, '/orders'],
          ['Recorded inventory balances', result.data.counts.recordedBalances, '/inventory'],
          ['Balances with no availability', result.data.counts.zeroAvailableBalances, '/inventory'],
        ] as const).map(([label, value, path]) => <div className="form-card" key={label}><dt><Link to={path}>{label}</Link></dt><dd className="metric-value">{value.toLocaleString()}</dd></div>)}
      </dl>
      <p className="field-hint">Inventory counts cover recorded warehouse/product balances, including inactive records. Unrecorded combinations are excluded. No low-stock threshold is configured; quantities in different units are not added together. Refresh to retrieve a new snapshot.</p>
      <section className="form-card"><h2>Orders by status</h2><dl className="detail-grid">
        {Object.entries(orderStatusLabels).map(([status,label]) => <div key={status}><dt><Link to={`/orders?status=${status}`}>{label}</Link></dt><dd>{(result.data!.ordersByStatus.find(row => row.status === status)?.total ?? 0).toLocaleString()}</dd></div>)}
      </dl></section>
      <section className="form-card"><h2>Recent orders</h2>
        {result.data.recentOrders.length === 0 ? <p>No orders yet. <Link to="/orders">Open orders</Link> to begin.</p> : <ul className="dashboard-orders">
          {result.data.recentOrders.map(order => <li key={order.id}><Link to={`/orders/${order.id}`}>{order.customerName} — {order.id}</Link>
            <span className="cell-detail">{orderStatusLabels[order.status]} · {new Date(order.createdAt).toLocaleString('en-GB')}</span></li>)}
        </ul>}
      </section>
    </>}
  </>
}
