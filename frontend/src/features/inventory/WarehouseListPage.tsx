import { useCallback, useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { ErrorState, LoadingState } from '../../shared/components/Feedback'
import { useAuth } from '../identity/useAuth'
import { inventoryApi } from './inventoryApi'
import { InventoryPagination } from './InventoryPagination'
import { useInventoryData } from './useInventoryData'

export function WarehouseListPage() {
  const { user } = useAuth()
  const location = useLocation()
  const [q, setQ] = useState(''); const [active, setActive] = useState('')
  const [filters, setFilters] = useState({ q: '', active: undefined as boolean | undefined, page: 0, size: 20 })
  const load = useCallback((signal: AbortSignal) => inventoryApi.warehouses(filters, signal), [filters])
  const result = useInventoryData(load)
  function apply(next: typeof filters) { result.begin(); setFilters(next) }
  function search(event: FormEvent) { event.preventDefault(); apply({ ...filters, q: q.trim(), active: active === '' ? undefined : active === 'true', page: 0 }) }
  const notice = (location.state as { notice?: string } | null)?.notice

  return <>
    <div className="page-heading"><div><h1>Warehouses</h1><p className="muted">Manage the locations where your organization holds stock.</p></div>
      {user?.canWriteInventory && <Link className="button primary" to="/warehouses/new">＋ Add warehouse</Link>}</div>
    {notice && <p className="success-panel" role="status">{notice}</p>}
    <form className="filter-bar" onSubmit={search} aria-label="Filter warehouses">
      <div className="search-field"><label htmlFor="warehouse-search">Search warehouses</label><input id="warehouse-search" value={q} maxLength={100} placeholder="Name, code or location" onChange={event => setQ(event.target.value)} /></div>
      <div><label htmlFor="warehouse-status">Status</label><select id="warehouse-status" value={active} onChange={event => setActive(event.target.value)}><option value="">All statuses</option><option value="true">Active</option><option value="false">Inactive</option></select></div>
      <button className="button primary">Search</button><button className="button secondary" type="button" onClick={() => { setQ(''); setActive(''); apply({ ...filters, q: '', active: undefined, page: 0 }) }}>Clear</button>
    </form>
    {result.loading ? <LoadingState label="Loading warehouses…" /> : result.error ? <ErrorState message={result.error} onRetry={result.reload} />
      : result.data && result.data.items.length === 0 ? <div className="state-panel"><h2>No warehouses found</h2><p className="muted">{user?.canWriteInventory ? 'Add your first warehouse or adjust the filters.' : 'Ask an inventory editor to add a warehouse, or adjust the filters.'}</p></div>
        : result.data && <div className="table-panel"><p className="table-hint">Scroll the table horizontally to see all columns.</p><div className="table-scroll"><table>
          <caption className="sr-only">Organization warehouses</caption><thead><tr><th scope="col">Warehouse</th><th scope="col">Code</th><th scope="col">Location</th><th scope="col">Status</th><th scope="col">Actions</th></tr></thead>
          <tbody>{result.data.items.map(warehouse => <tr key={warehouse.id}><td><Link className="product-name" to={`/warehouses/${warehouse.id}`}>{warehouse.name}</Link></td><td>{warehouse.code}</td><td>{warehouse.location || 'Not specified'}</td>
            <td><span className={`status-badge ${warehouse.active ? 'active' : 'inactive'}`}>{warehouse.active ? 'Active' : 'Inactive'}</span></td><td>{user?.canWriteInventory ? <Link className="text-link" to={`/warehouses/${warehouse.id}/edit`} aria-label={`Edit ${warehouse.name}`}>Edit</Link> : <Link className="text-link" to={`/warehouses/${warehouse.id}`}>View</Link>}</td></tr>)}</tbody>
        </table></div><InventoryPagination page={result.data} onPage={page => apply({ ...filters, page })} /></div>}
  </>
}
