import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useLocation, useParams } from 'react-router-dom'
import { ApiError, errorMessage } from '../../shared/api/http'
import { ErrorState, LoadingState } from '../../shared/components/Feedback'
import { useRemoteData } from '../../shared/hooks/useRemoteData'
import { formatLkr } from '../catalog/sellingPriceApi'
import { useAuth } from '../identity/useAuth'
import { orderApi } from './orderApi'
import type { OrderDetail } from './orderApi'

export function OrderDetailsPage() {
  const { id } = useParams()
  const { user, refresh } = useAuth()
  const location = useLocation()
  const load = useCallback((signal: AbortSignal) => orderApi.get(id!, signal), [id])
  const result = useRemoteData(load)
  const [confirmed, setConfirmed] = useState<OrderDetail | null>(null)
  const [pending, setPending] = useState(false)
  const [uncertain, setUncertain] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>((location.state as { notice?: string } | null)?.notice ?? null)
  const errorRef = useRef<HTMLDivElement>(null)
  useEffect(() => { if (error) errorRef.current?.focus() }, [error])

  async function confirm() {
    if (pending) return
    setPending(true); setError(null); setNotice(null)
    try {
      const response = await orderApi.confirm(id!)
      setConfirmed(response.order); setUncertain(false)
      setNotice(response.replayed ? 'This order was already confirmed. Stock was not reserved again.' : 'Order confirmed. All items were reserved successfully.')
    } catch (failure) {
      if (failure instanceof ApiError && failure.problem.status === 401) { await refresh(); return }
      if (uncertain || !(failure instanceof ApiError) || failure.problem.status >= 500) {
        setUncertain(true)
        setError(`${errorMessage(failure)} Confirmation may already have completed. Retry confirmation safely for this same order, or reload its latest status.`)
      } else setError(errorMessage(failure))
    } finally { setPending(false) }
  }

  function reload() { setConfirmed(null); setError(null); setNotice(null); setUncertain(false); result.reload() }
  if (result.loading) return <LoadingState label="Loading order…" />
  if (result.error) return <><Link className="back-link" to="/orders">← Back to orders</Link><ErrorState message={result.error} onRetry={result.reload} /></>
  const order = confirmed ?? result.data
  if (!order) return null

  return <>
    <Link className="back-link" to="/orders">← Back to orders</Link>
    <div className="page-heading"><div><h1>Order details</h1><p className="muted order-identifier">{order.id}</p></div>
      <span className={`status-badge ${order.status === 'CONFIRMED' ? 'active' : 'inactive'}`}>{order.status === 'CONFIRMED' ? 'Confirmed' : 'Pending'}</span></div>
    {notice && <p className="success-panel" role="status">{notice}</p>}
    <section className="form-card"><dl className="detail-grid">
      <div><dt>Customer</dt><dd>{order.customerName}</dd></div><div><dt>Warehouse</dt><dd>{order.warehouseName}<span className="cell-detail">{order.warehouseCode}</span></dd></div>
      <div><dt>Created</dt><dd>{new Date(order.createdAt).toLocaleString('en-GB')}</dd></div><div><dt>Confirmed</dt><dd>{order.confirmedAt ? new Date(order.confirmedAt).toLocaleString('en-GB') : 'Not confirmed'}</dd></div>
      <div><dt>Order total</dt><dd>{formatLkr(order.totalAmount)}</dd></div>
    </dl></section>
    <div className="table-panel"><p className="table-hint">Scroll the table horizontally to see all columns.</p><div className="table-scroll"><table>
      <caption className="sr-only">Order items and snapshotted prices</caption><thead><tr><th scope="col">Product</th><th scope="col">Quantity</th><th scope="col">Unit price</th><th scope="col">Line total</th></tr></thead>
      <tbody>{order.items.map(item => <tr key={item.productId}><td><strong>{item.productName}</strong><span className="cell-detail">{item.sku}</span></td><td>{item.quantity.toLocaleString()} {item.unitOfMeasure}</td><td>{formatLkr(item.unitPrice)}</td><td>{formatLkr(item.lineTotal)}</td></tr>)}</tbody>
    </table></div></div>
    <p className="field-hint">Prices were captured when this order was created. Later catalog price changes do not change these amounts.</p>
    {order.status === 'PENDING' && <section className="form-card"><h2>Reserve stock</h2><p>Confirming reserves every item together. If any item is unavailable, none are reserved. Physical on-hand stock does not decrease.</p>
      {user?.canWriteOrders ? <div className="inline-actions"><button className="button primary" disabled={pending} onClick={() => void confirm()}>{pending ? 'Confirming…' : uncertain ? 'Retry confirmation' : 'Confirm order'}</button>
        <button className="button secondary" disabled={pending} onClick={reload}>Reload latest order</button></div> : <p className="muted">Order write permission is required to confirm this order.</p>}</section>}
    {order.status === 'CONFIRMED' && <div className="inline-actions"><Link className="button secondary" to={`/inventory?warehouseId=${order.warehouseId}`}>View reserved inventory</Link></div>}
    {error && <div className="error-panel" role="alert" tabIndex={-1} ref={errorRef}>{error}</div>}
  </>
}
