import { useCallback, useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useLocation, useSearchParams } from 'react-router-dom'
import { ErrorState, LoadingState } from '../../shared/components/Feedback'
import { useAuth } from '../identity/useAuth'
import { inventoryApi } from './inventoryApi'
import type { InventoryBalance, InventoryFilters, StockMovement } from './inventoryApi'
import { InventoryPagination } from './InventoryPagination'
import { useRemoteData } from '../../shared/hooks/useRemoteData'

export function InventoryPage({ history = false }: { history?: boolean }) {
  const { user } = useAuth(); const [params] = useSearchParams(); const location = useLocation()
  const [filters, setFilters] = useState<InventoryFilters>({ q: '', warehouseId: params.get('warehouseId') ?? '', page: 0, size: 20 })
  const [q, setQ] = useState(''); const [warehouseId, setWarehouseId] = useState(filters.warehouseId)
  const load = useCallback(async (signal: AbortSignal) => {
    const [page, options] = await Promise.all([
      history ? inventoryApi.movements(filters, signal) : inventoryApi.balances(filters, signal),
      inventoryApi.warehouses({ size: 100 }, signal),
    ])
    return { page, warehouses: options.items }
  }, [filters, history])
  const result = useRemoteData(load)
  function apply(next: InventoryFilters) { result.begin(); setFilters(next) }
  function search(event: FormEvent) { event.preventDefault(); apply({ ...filters, q: q.trim(), warehouseId, page: 0 }) }
  const notice = (location.state as { notice?: string } | null)?.notice
  const title = history ? 'Stock movements' : 'Inventory'
  return <>
    <div className="page-heading"><div><h1>{title}</h1><p className="muted">{history ? 'Trace stock receipts, quantities, and the employee who recorded them.' : 'On-hand, reserved, and available stock at each warehouse.'}</p></div>
      {user?.canWriteInventory && <Link className="button primary" to="/inventory/receive">＋ Receive stock</Link>}</div>
    {notice && <p className="success-panel" role="status">{notice}</p>}
    {!history && <p className="inventory-formula"><strong>Available = on-hand − reserved.</strong></p>}
    <form className="filter-bar" onSubmit={search} aria-label={`Filter ${title.toLowerCase()}`}>
      <div className="search-field"><label htmlFor="inventory-search">{history ? 'Search movements' : 'Search inventory'}</label><input id="inventory-search" maxLength={100} placeholder={history ? 'Product, SKU, warehouse code or reference' : 'Product, SKU or warehouse'} value={q} onChange={event => setQ(event.target.value)} /></div>
      <div><label htmlFor="inventory-warehouse">Warehouse</label><select id="inventory-warehouse" value={warehouseId} onChange={event => setWarehouseId(event.target.value)}><option value="">All warehouses</option>{result.data?.warehouses.map(warehouse => <option key={warehouse.id} value={warehouse.id}>{warehouse.name}{warehouse.active ? '' : ' (inactive)'}</option>)}</select></div>
      <button className="button primary">Search</button><button type="button" className="button secondary" onClick={() => { setQ(''); setWarehouseId(''); apply({ ...filters, q: '', warehouseId: '', page: 0 }) }}>Clear</button>
    </form>
    {result.loading ? <LoadingState label={`Loading ${title.toLowerCase()}…`} /> : result.error ? <ErrorState message={result.error} onRetry={result.reload} />
      : result.data && result.data.page.items.length === 0 ? <div className="state-panel"><h2>{history ? 'No stock movements found' : 'No stock recorded'}</h2><p className="muted">{filters.q || filters.warehouseId ? 'Adjust the search or warehouse filter.' : 'Stock receipts will appear here once inventory is received.'}</p></div>
        : result.data && <div className="table-panel"><p className="table-hint">Scroll the table horizontally to see all columns.</p><div className="table-scroll"><table>
          <caption className="sr-only">{title} in your organization</caption>
          {history ? <><thead><tr><th scope="col">Received</th><th scope="col">Product</th><th scope="col">Warehouse</th><th scope="col">Quantity</th><th scope="col">Reference</th><th scope="col">Recorded by</th><th scope="col">Actions</th></tr></thead>
            <tbody>{(result.data.page.items as StockMovement[]).map(movement => <tr key={movement.id}><td>{new Date(movement.receivedAt).toLocaleString('en-GB')}</td><td><strong>{movement.productName}</strong><span className="cell-detail">{movement.sku}</span></td><td>{movement.warehouseName}</td><td>+{movement.quantity.toLocaleString()} {movement.unitOfMeasure}</td><td>{movement.reference || '—'}</td><td>{movement.receivedByUsername}</td><td><Link className="text-link" to={`/inventory/movements/${movement.id}`} aria-label={`View receipt ${movement.reference || movement.sku}`}>View</Link></td></tr>)}</tbody></>
            : <><thead><tr><th scope="col">Product</th><th scope="col">Warehouse</th><th scope="col">Unit</th><th scope="col">On-hand</th><th scope="col">Reserved</th><th scope="col">Available</th></tr></thead>
              <tbody>{(result.data.page.items as InventoryBalance[]).map(balance => <tr key={`${balance.warehouseId}-${balance.productId}`}><td><Link className="product-name" to={`/products/${balance.productId}`}>{balance.productName}</Link><span className="cell-detail">{balance.sku}{balance.productActive ? '' : ' · Inactive product'}</span></td><td><Link to={`/warehouses/${balance.warehouseId}`}>{balance.warehouseName}</Link><span className="cell-detail">{balance.warehouseCode}{balance.warehouseActive ? '' : ' · Inactive warehouse'}</span></td><td>{balance.unitOfMeasure}</td><td>{balance.onHand.toLocaleString()}</td><td>{balance.reserved.toLocaleString()}</td><td className="quantity-available">{balance.available.toLocaleString()}</td></tr>)}</tbody></>}
        </table></div><InventoryPagination page={result.data.page} onPage={page => apply({ ...filters, page })} /></div>}
  </>
}
