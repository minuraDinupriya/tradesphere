import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useLocation, useParams } from 'react-router-dom'
import { ApiError, errorMessage } from '../../shared/api/http'
import { ErrorState, LoadingState } from '../../shared/components/Feedback'
import { useRemoteData } from '../../shared/hooks/useRemoteData'
import { formatLkr } from '../catalog/sellingPriceApi'
import { useAuth } from '../identity/useAuth'
import { orderApi, orderStatusLabels, orderActionLabels } from './orderApi'
import type { OrderAction, OrderDetail, OrderHistory } from './orderApi'
import { OrderActionDialog } from './OrderActionDialog'

export function OrderDetailsPage() {
  const { id } = useParams()
  const { user, refresh } = useAuth()
  const location = useLocation()
  const load = useCallback((signal: AbortSignal) => orderApi.get(id!, signal), [id])
  const loadHistory = useCallback((signal: AbortSignal) => orderApi.history(id!, signal), [id])
  const result = useRemoteData(load)
  const history = useRemoteData<OrderHistory>(loadHistory)
  const [updated, setUpdated] = useState<OrderDetail | null>(null)
  const [pending, setPending] = useState(false)
  const [uncertain, setUncertain] = useState<OrderAction | null>(null)
  const [chosen, setChosen] = useState<OrderAction | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>((location.state as { notice?: string } | null)?.notice ?? null)
  const errorRef = useRef<HTMLDivElement>(null)
  useEffect(() => { if (error) errorRef.current?.focus() }, [error])

  async function act(action: OrderAction) {
    if (pending) return
    setChosen(null); setPending(true); setError(null); setNotice(null)
    try {
      const response = action === 'confirm' ? await orderApi.confirm(id!) : await orderApi.act(id!, action)
      setUpdated(response.order); setUncertain(null); history.reload()
      if (action === 'confirm') setNotice(response.replayed ? 'This order was already confirmed. Stock was not reserved again.' : 'Order confirmed. All items were reserved successfully.')
      else setNotice(response.replayed ? `This action was already recorded. Stock was not changed again. Current status: ${orderStatusLabels[response.order.status]}.` : action === 'process' ? 'Processing started. Stock remains reserved; cancellation is now unavailable.' : `${orderStatusLabels[response.order.status]} successfully. Order history and any stock changes were saved together.`)
    } catch (failure) {
      if (failure instanceof ApiError && failure.problem.status === 401) { await refresh(); return }
      if (uncertain || !(failure instanceof ApiError) || failure.problem.status >= 500) {
        setUncertain(action)
        setError(`${errorMessage(failure)} ${action === 'confirm' ? 'Confirmation' : 'The action'} may already have completed. Retry safely for this same order, or reload its latest status.`)
      } else setError(errorMessage(failure))
    } finally { setPending(false) }
  }

  function reload() { setUpdated(null); setError(null); setNotice(null); setUncertain(null); result.reload(); history.reload() }
  if (result.loading) return <LoadingState label="Loading order…" />
  if (result.error) return <><Link className="back-link" to="/orders">← Back to orders</Link><ErrorState message={result.error} onRetry={result.reload} /></>
  const order = updated ?? result.data
  if (!order) return null
  const actions: OrderAction[] = order.status === 'PENDING' ? ['confirm','cancel'] : order.status === 'CONFIRMED' ? ['process','cancel'] : order.status === 'PROCESSING' ? ['fulfill'] : []

  return <>
    <Link className="back-link" to="/orders">← Back to orders</Link>
    <div className="page-heading"><div><h1>Order details</h1><p className="muted order-identifier">{order.id}</p></div>
      <span className={`status-badge ${order.status === 'CANCELLED' || order.status === 'PENDING' ? 'inactive' : 'active'}`}>{orderStatusLabels[order.status]}</span></div>
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
    <section className="form-card"><h2>Order actions</h2>
      {order.status === 'PENDING' && <p>Confirmation reserves every item together. If any item is unavailable, none are reserved. On-hand stock does not decrease.</p>}
      {order.status === 'CONFIRMED' && <p>Stock is reserved. Start processing to prepare fulfillment, or cancel to release all reservations.</p>}
      {order.status === 'PROCESSING' && <p>Processing has started. Cancellation is no longer available; fulfill the entire order when ready.</p>}
      {order.status === 'FULFILLED' && <p>This order is complete. Stock was deducted once and its reservations were consumed.</p>}
      {order.status === 'CANCELLED' && <p>This order is cancelled. Any existing reservations were released; it cannot be reopened.</p>}
      {user?.canWriteOrders ? <div className="inline-actions">
        {uncertain ? <button className="button primary" disabled={pending} onClick={() => void act(uncertain)}>{pending ? 'Updating…' : uncertain === 'confirm' ? 'Retry confirmation' : 'Retry same action'}</button>
          : actions.map(action => <button key={action} className="button primary" disabled={pending} onClick={() => setChosen(action)}>{orderActionLabels[action]}</button>)}
        <button className="button secondary" disabled={pending} onClick={reload}>Reload latest order</button>
      </div> : <p className="muted">Order write permission is required to change this order.</p>}
      {order.status !== 'PENDING' && <Link className="text-link" to={`/inventory?warehouseId=${order.warehouseId}`}>{order.status === 'CONFIRMED' || order.status === 'PROCESSING' ? 'View reserved inventory' : 'View warehouse inventory'}</Link>}
    </section>
    {error && <div className="error-panel" role="alert" tabIndex={-1} ref={errorRef}>{error}</div>}
    <section className="form-card"><h2>Order history</h2>
      {history.loading ? <LoadingState label="Loading order history…" /> : history.error ? <ErrorState message={history.error} onRetry={history.reload} /> : <>
        {history.data?.transitions.length === 0 ? <p className="muted">No transition history is available.</p> : <ol className="order-history">{history.data?.transitions.map(event => <li key={event.id}>
          <strong>{event.fromStatus ? `${orderStatusLabels[event.fromStatus]} → ` : ''}{orderStatusLabels[event.toStatus]}</strong> · {event.actorUsername} · {new Date(event.occurredAt).toLocaleString('en-GB')}
          {event.backfilled && <span className="cell-detail">Reconstructed from the existing order record.</span>}
        </li>)}</ol>}
        <h3>Reservation release and fulfillment movements</h3>
        {history.data?.movements.length === 0 ? <p className="muted">No release or fulfillment movements recorded.</p> : <div className="table-scroll"><table><caption className="sr-only">Order inventory movements</caption>
          <thead><tr><th scope="col">Product</th><th scope="col">Operation</th><th scope="col">On-hand change</th><th scope="col">Reserved change</th><th scope="col">Recorded by</th><th scope="col">Time</th></tr></thead>
          <tbody>{history.data?.movements.map(movement => <tr key={movement.id}><td>{movement.productName}<span className="cell-detail">{movement.sku}</span></td><td>{movement.kind === 'FULFILL' ? 'Fulfillment' : 'Reservation release'}</td>
            <td>{movement.onHandDelta.toLocaleString()}</td><td>{movement.reservedDelta.toLocaleString()}</td><td>{movement.actorUsername}</td><td>{new Date(movement.occurredAt).toLocaleString('en-GB')}</td></tr>)}</tbody>
        </table></div>}
      </>}
    </section>
    {chosen && <OrderActionDialog action={chosen} status={order.status} onDismiss={() => setChosen(null)} onConfirm={() => void act(chosen)} />}
  </>
}
