import { useCallback } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ErrorState, LoadingState } from '../../shared/components/Feedback'
import { useAuth } from '../identity/useAuth'
import { inventoryApi } from './inventoryApi'
import { useRemoteData } from '../../shared/hooks/useRemoteData'

export function WarehouseDetailsPage() {
  const { id } = useParams(); const { user } = useAuth()
  const load = useCallback((signal: AbortSignal) => inventoryApi.warehouse(id!, signal), [id])
  const { data: warehouse, loading, error, reload } = useRemoteData(load)
  if (loading) return <LoadingState label="Loading warehouse…" />
  if (error) return <><Link className="back-link" to="/warehouses">← Back to warehouses</Link><ErrorState message={error} onRetry={reload} /></>
  if (!warehouse) return null
  return <><Link className="back-link" to="/warehouses">← Back to warehouses</Link><div className="page-heading"><div><h1>{warehouse.name}</h1><p className="muted">Warehouse details</p></div>
    {user?.canWriteInventory && <Link className="button primary" to={`/warehouses/${warehouse.id}/edit`}>Edit warehouse</Link>}</div>
    <section className="form-card"><dl className="detail-grid"><div><dt>Code</dt><dd>{warehouse.code}</dd></div><div><dt>Status</dt><dd>{warehouse.active ? 'Active' : 'Inactive'}</dd></div><div className="span-two"><dt>Location</dt><dd>{warehouse.location || 'Not specified'}</dd></div></dl></section>
    <div className="inline-actions"><Link className="button secondary" to={`/inventory?warehouseId=${warehouse.id}`}>View inventory</Link><Link className="button secondary" to={`/inventory/movements?warehouseId=${warehouse.id}`}>View stock movements</Link>
      {user?.canWriteInventory && warehouse.active && <Link className="button primary" to={`/inventory/receive?warehouseId=${warehouse.id}`}>Receive stock</Link>}</div></>
}
