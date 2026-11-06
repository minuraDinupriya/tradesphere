import { useCallback, useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { ErrorState, LoadingState } from '../../shared/components/Feedback'
import { useRemoteData } from '../../shared/hooks/useRemoteData'
import { useAuth } from '../identity/useAuth'
import { customerApi } from './customerApi'
import type { CustomerFilters } from './customerApi'

export function CustomerListPage() {
  const { user } = useAuth()
  const location = useLocation()
  const [q, setQ] = useState('')
  const [active, setActive] = useState('')
  const [filters, setFilters] = useState<CustomerFilters>({ q: '', active: '', page: 0, size: 20 })
  const load = useCallback((signal: AbortSignal) => customerApi.list(filters, signal), [filters])
  const result = useRemoteData(load)
  const notice = (location.state as { notice?: string } | null)?.notice

  function apply(next: CustomerFilters) { result.begin(); setFilters(next) }
  function search(event: FormEvent) {
    event.preventDefault()
    apply({ ...filters, q: q.trim(), active, page: 0 })
  }

  return <>
    <div className="page-heading"><div><h1>Customers</h1><p className="muted">Manage the businesses your organization sells to.</p></div>
      {user?.canWriteCustomers && <Link className="button primary" to="/customers/new">＋ Add customer</Link>}</div>
    {notice && <p className="success-panel" role="status">{notice}</p>}
    <form className="filter-bar" onSubmit={search} aria-label="Filter customers">
      <div className="search-field"><label htmlFor="customer-search">Search customers</label><input id="customer-search" maxLength={100} placeholder="Name, contact, email or phone" value={q} onChange={event => setQ(event.target.value)} /></div>
      <div><label htmlFor="customer-status">Status</label><select id="customer-status" value={active} onChange={event => setActive(event.target.value)}>
        <option value="">All statuses</option><option value="true">Active</option><option value="false">Inactive</option></select></div>
      <button className="button primary">Search</button><button className="button secondary" type="button" onClick={() => { setQ(''); setActive(''); apply({ ...filters, q: '', active: '', page: 0 }) }}>Clear</button>
    </form>
    {result.loading ? <LoadingState label="Loading customers…" /> : result.error ? <ErrorState message={result.error} onRetry={result.reload} />
      : result.data && result.data.items.length === 0 ? <div className="state-panel"><h2>No customers found</h2><p className="muted">{filters.q || filters.active ? 'Adjust the search or status filter.' : 'Customers will appear here after an authorized employee adds them.'}</p></div>
        : result.data && <div className="table-panel"><p className="table-hint">Scroll the table horizontally to see all columns.</p><div className="table-scroll"><table>
          <caption className="sr-only">Customers in your organization</caption>
          <thead><tr><th scope="col">Customer</th><th scope="col">Contact</th><th scope="col">Email</th><th scope="col">Phone</th><th scope="col">Status</th><th scope="col">Actions</th></tr></thead>
          <tbody>{result.data.items.map(customer => <tr key={customer.id}>
            <td><Link className="product-name" to={`/customers/${customer.id}`}>{customer.name}</Link></td><td>{customer.contactName || '—'}</td><td>{customer.email || '—'}</td><td>{customer.phone || '—'}</td>
            <td><span className={`status-badge ${customer.active ? 'active' : 'inactive'}`}>{customer.active ? 'Active' : 'Inactive'}</span></td>
            <td>{user?.canWriteCustomers ? <Link className="text-link" to={`/customers/${customer.id}/edit`} aria-label={`Edit ${customer.name}`}>Edit</Link> : <Link className="text-link" to={`/customers/${customer.id}`} aria-label={`View ${customer.name}`}>View</Link>}</td>
          </tr>)}</tbody>
        </table></div><div className="pagination"><span>{result.data.totalElements} results · Page {result.data.page + 1} of {Math.max(1, result.data.totalPages)}</span>
          <div className="pagination-controls"><button className="button secondary" disabled={filters.page === 0} onClick={() => apply({ ...filters, page: filters.page - 1 })}>Previous</button>
            <button className="button secondary" disabled={filters.page + 1 >= result.data.totalPages} onClick={() => apply({ ...filters, page: filters.page + 1 })}>Next</button></div></div></div>}
  </>
}
