import { useCallback, useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { ErrorState, LoadingState } from '../../shared/components/Feedback'
import { useRemoteData } from '../../shared/hooks/useRemoteData'
import { formatLkr } from '../catalog/sellingPriceApi'
import { useAuth } from '../identity/useAuth'
import { orderApi, orderStatusLabels } from './orderApi'
import type { OrderFilters } from './orderApi'

export function OrderListPage() {
  const { user } = useAuth()
  const [parameters] = useSearchParams()
  const initialStatus = parameters.get('status') ?? ''
  const validStatus = Object.hasOwn(orderStatusLabels, initialStatus) ? initialStatus : ''
  const [q, setQ] = useState('')
  const [status, setStatus] = useState(validStatus)
  const [filters, setFilters] = useState<OrderFilters>({ q: '', status: validStatus, page: 0, size: 20 })
  const load = useCallback((signal: AbortSignal) => orderApi.list(filters, signal), [filters])
  const result = useRemoteData(load)

  function apply(next: OrderFilters) { result.begin(); setFilters(next) }
  function search(event: FormEvent) {
    event.preventDefault()
    apply({ ...filters, q: q.trim(), status, page: 0 })
  }

  return <>
    <div className="page-heading"><div><h1>Orders</h1><p className="muted">Create customer orders and reserve stock in one warehouse.</p></div>
      {user?.canWriteOrders && <Link className="button primary" to="/orders/new"><span aria-hidden="true">＋</span> Create order</Link>}</div>
    <form className="filter-bar" onSubmit={search} aria-label="Filter orders">
      <div className="search-field"><label htmlFor="order-search">Search orders</label><input id="order-search" maxLength={100} placeholder="Customer or order ID" value={q} onChange={event => setQ(event.target.value)} /></div>
      <div><label htmlFor="order-status">Status</label><select id="order-status" value={status} onChange={event => setStatus(event.target.value)}>
        <option value="">All statuses</option>{Object.entries(orderStatusLabels).map(([value,label]) => <option key={value} value={value}>{label}</option>)}</select></div>
      <button className="button primary">Search</button><button className="button secondary" type="button" onClick={() => { setQ(''); setStatus(''); apply({ ...filters, q: '', status: '', page: 0 }) }}>Clear</button>
    </form>
    {result.loading ? <LoadingState label="Loading orders…" /> : result.error ? <ErrorState message={result.error} onRetry={result.reload} />
      : result.data && result.data.items.length === 0 ? <div className="state-panel"><h2>No orders found</h2><p className="muted">{filters.q || filters.status ? 'Adjust the search or status filter.' : 'Your organization’s orders will appear here after an authorized employee creates one.'}</p></div>
        : result.data && <div className="table-panel"><p className="table-hint">Scroll the table horizontally to see all columns.</p><div className="table-scroll"><table>
          <caption className="sr-only">Orders in your organization</caption><thead><tr><th scope="col">Order</th><th scope="col">Customer</th><th scope="col">Warehouse</th><th scope="col">Status</th><th scope="col">Total</th><th scope="col">Created</th></tr></thead>
          <tbody>{result.data.items.map(order => <tr key={order.id}>
            <td><Link className="product-name" to={`/orders/${order.id}`} aria-label={`View order ${order.id}`}>{order.id}</Link></td><td>{order.customerName}</td><td>{order.warehouseName}</td>
            <td><span className={`status-badge ${order.status === 'PENDING' || order.status === 'CANCELLED' ? 'inactive' : 'active'}`}>{orderStatusLabels[order.status]}</span></td>
            <td>{formatLkr(order.totalAmount)}</td><td>{new Date(order.createdAt).toLocaleString('en-GB')}</td>
          </tr>)}</tbody>
        </table></div><div className="pagination"><span>{result.data.totalElements} results · Page {result.data.page + 1} of {Math.max(1, result.data.totalPages)}</span>
          <div className="pagination-controls"><button className="button secondary" disabled={filters.page === 0} onClick={() => apply({ ...filters, page: filters.page - 1 })}>Previous</button>
            <button className="button secondary" disabled={filters.page + 1 >= result.data.totalPages} onClick={() => apply({ ...filters, page: filters.page + 1 })}>Next</button></div></div></div>}
  </>
}
