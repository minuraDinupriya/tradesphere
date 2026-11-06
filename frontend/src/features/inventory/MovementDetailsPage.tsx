import { useCallback } from 'react'
import { Link, useParams } from 'react-router-dom'
import { request } from '../../shared/api/http'
import { ErrorState, LoadingState } from '../../shared/components/Feedback'
import type { StockMovement } from './inventoryApi'
import { useRemoteData } from '../../shared/hooks/useRemoteData'

export function MovementDetailsPage() {
  const { id } = useParams()
  const load = useCallback((signal: AbortSignal) => request<StockMovement>(`/inventory/movements/${encodeURIComponent(id!)}`, { signal }), [id])
  const { data, loading, error, reload } = useRemoteData(load)
  if (loading) return <LoadingState label="Loading stock movement…" />
  if (error) return <ErrorState message={error} onRetry={reload} />
  if (!data) return null
  return <><Link className="back-link" to="/inventory/movements">← Back to stock movements</Link><div className="page-heading"><div><h1>Stock receipt</h1><p className="muted">A recorded increase in physical stock.</p></div></div>
    <section className="form-card"><dl className="detail-grid">
      <div><dt>Product</dt><dd>{data.productName}<span className="cell-detail">{data.sku}</span></dd></div><div><dt>Warehouse</dt><dd>{data.warehouseName}<span className="cell-detail">{data.warehouseCode}</span></dd></div>
      <div><dt>Quantity received</dt><dd>+{data.quantity.toLocaleString()} {data.unitOfMeasure}</dd></div><div><dt>Recorded by</dt><dd>{data.receivedByUsername}</dd></div>
      <div><dt>Received at</dt><dd>{new Date(data.receivedAt).toLocaleString('en-GB')}</dd></div><div><dt>Reference</dt><dd>{data.reference || 'Not provided'}</dd></div>
      <div className="span-two"><dt>Note</dt><dd className="preserve-lines">{data.note || 'No note provided.'}</dd></div><div><dt>Receipt ID</dt><dd>{data.id}</dd></div><div><dt>Request ID</dt><dd>{data.requestId}</dd></div>
    </dl></section><p className="muted small">History is read-only. Stock corrections and order movements are outside this release.</p></>
}
