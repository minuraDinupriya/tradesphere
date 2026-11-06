import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { ApiError, errorMessage } from '../../shared/api/http'
import { ErrorState, LoadingState } from '../../shared/components/Feedback'
import { useAuth } from '../identity/useAuth'
import { productApi } from './productApi'
import { formatLkr } from './sellingPriceApi'
import type { ProductFilters, ProductPage } from './productApi'

const initialFilters: ProductFilters = { q: '', category: '', active: '', page: 0, size: 20 }

export function ProductListPage() {
  const { user, refresh } = useAuth()
  const location = useLocation()
  const [filters, setFilters] = useState(initialFilters)
  const [q, setQ] = useState('')
  const [category, setCategory] = useState('')
  const [active, setActive] = useState('')
  const [categories, setCategories] = useState<string[]>([])
  const [data, setData] = useState<ProductPage | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [attempt, setAttempt] = useState(0)
  const [notice, setNotice] = useState<string | null>((location.state as { notice?: string } | null)?.notice ?? null)

  useEffect(() => {
    const controller = new AbortController()
    Promise.all([productApi.list(filters, controller.signal), productApi.categories(controller.signal)])
      .then(([page, options]) => { setData(page); setCategories(options) })
      .catch(failure => {
        if (controller.signal.aborted) return
        if (failure instanceof ApiError && failure.problem.status === 401) void refresh()
        else setError(errorMessage(failure))
      }).finally(() => { if (!controller.signal.aborted) setLoading(false) })
    return () => controller.abort()
  }, [filters, attempt, refresh])

  function search(event: FormEvent) {
    event.preventDefault()
    applyFilters({ ...filters, q: q.trim(), category, active, page: 0 })
    setNotice(null)
  }

  function clear() {
    setQ(''); setCategory(''); setActive(''); applyFilters({ ...initialFilters }); setNotice(null)
  }

  function applyFilters(next: ProductFilters) { setLoading(true); setError(null); setFilters(next) }

  return <>
    <div className="page-heading"><div><h1>Products</h1><p className="muted">Manage the products in your organization’s catalog.</p></div>
      {user?.canWriteCatalog && <Link className="button primary" to="/products/new"><span aria-hidden="true">＋</span> Add product</Link>}</div>
    {notice && <p className="success-panel" role="status">{notice}</p>}
    <form className="filter-bar" onSubmit={search} aria-label="Filter products">
      <div className="search-field"><label htmlFor="product-search">Search products</label>
        <input id="product-search" placeholder="Product name or SKU" maxLength={100} value={q} onChange={event => setQ(event.target.value)} /></div>
      <div><label htmlFor="category-filter">Category</label><select id="category-filter" value={category} onChange={event => setCategory(event.target.value)}>
        <option value="">All categories</option>{categories.map(value => <option key={value}>{value}</option>)}</select></div>
      <div><label htmlFor="status-filter">Status</label><select id="status-filter" value={active} onChange={event => setActive(event.target.value)}>
        <option value="">All statuses</option><option value="true">Active</option><option value="false">Inactive</option></select></div>
      <button className="button primary" type="submit">Search</button><button className="button secondary" type="button" onClick={clear}>Clear</button>
    </form>
    {loading ? <LoadingState /> : error ? <ErrorState message={error} onRetry={() => { setLoading(true); setError(null); setAttempt(attempt + 1) }} />
      : data && data.items.length === 0 ? <div className="state-panel"><h2>{filters.q || filters.category || filters.active ? 'No matching products' : 'Your catalog is ready for its first product'}</h2>
        <p className="muted">{filters.q || filters.category || filters.active ? 'Try a different search or clear your filters.' : user?.canWriteCatalog ? 'Add a product to start building your organization’s catalog.' : 'Ask a catalog editor to add the first product.'}</p>
        {(filters.q || filters.category || filters.active) && <button className="button secondary" onClick={clear}>Clear filters</button>}</div>
        : data && <div className="table-panel"><p className="table-hint">Scroll the table horizontally to see all columns.</p><div className="table-scroll"><table>
          <caption className="sr-only">Products in your organization</caption>
          <thead><tr><th scope="col">Product name</th><th scope="col">SKU</th><th scope="col">Category</th><th scope="col">Unit</th><th scope="col">Status</th><th scope="col">Selling price</th><th scope="col">Updated</th><th scope="col">Actions</th></tr></thead>
          <tbody>{data.items.map(product => <tr key={product.id}>
            <td><Link className="product-name" to={`/products/${product.id}`}>{product.name}</Link></td><td className="sku-cell">{product.sku}</td>
            <td>{product.category}</td><td>{product.unitOfMeasure}</td><td><span className={`status-badge ${product.active ? 'active' : 'inactive'}`}>{product.active ? 'Active' : 'Inactive'}</span></td>
            <td>{formatLkr(product.sellingPrice)}</td><td>{new Date(product.updatedAt).toLocaleDateString('en-GB')}</td><td>{user?.canWriteCatalog
              ? <Link className="text-link" to={`/products/${product.id}/edit`} aria-label={`Edit ${product.name}`}>Edit</Link>
              : <Link className="text-link" to={`/products/${product.id}`} aria-label={`View ${product.name}`}>View</Link>}</td>
          </tr>)}</tbody>
        </table></div>
          <div className="pagination"><span>{data.totalElements} {data.totalElements === 1 ? 'product' : 'products'} · Page {data.page + 1} of {Math.max(1, data.totalPages)}</span>
            <div className="pagination-controls"><label htmlFor="page-size">Per page</label><select id="page-size" value={filters.size} onChange={event => applyFilters({ ...filters, size: Number(event.target.value), page: 0 })}>
              <option value={20}>20</option><option value={50}>50</option><option value={100}>100</option></select>
              <button className="button secondary" disabled={data.page === 0} onClick={() => applyFilters({ ...filters, page: filters.page - 1 })}>Previous</button>
              <button className="button secondary" disabled={data.page + 1 >= data.totalPages} onClick={() => applyFilters({ ...filters, page: filters.page + 1 })}>Next</button></div>
          </div></div>}
  </>
}
