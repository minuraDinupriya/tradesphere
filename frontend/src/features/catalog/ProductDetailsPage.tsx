import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ApiError, errorMessage } from '../../shared/api/http'
import { ErrorState, LoadingState } from '../../shared/components/Feedback'
import { useAuth } from '../identity/useAuth'
import { productApi } from './productApi'
import type { Product } from './productApi'
import { formatLkr } from './sellingPriceApi'

export function ProductDetailsPage() {
  const { id } = useParams()
  const { user, refresh } = useAuth()
  const [product, setProduct] = useState<Product | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    productApi.get(id!, controller.signal).then(setProduct).catch(failure => {
      if (controller.signal.aborted) return
      if (failure instanceof ApiError && failure.problem.status === 401) void refresh()
      else setError(errorMessage(failure))
    })
    return () => controller.abort()
  }, [id, attempt, refresh])

  if (error) return <><Link className="back-link" to="/products">← Back to products</Link><ErrorState message={error} onRetry={() => { setProduct(null); setError(null); setAttempt(attempt + 1) }} /></>
  if (!product) return <LoadingState label="Loading product…" />
  return <>
    <Link className="back-link" to="/products">← Back to products</Link>
    <div className="page-heading"><div><h1>{product.name}</h1><p className="muted">Product details</p></div>
      {user?.canWriteCatalog && <Link className="button primary" to={`/products/${product.id}/edit`}>Edit product</Link>}</div>
    <section className="form-card"><dl className="detail-grid">
      <div><dt>SKU</dt><dd>{product.sku}</dd></div><div><dt>Category</dt><dd>{product.category}</dd></div>
      <div><dt>Selling price</dt><dd>{formatLkr(product.sellingPrice)} {user?.canWritePrices && <Link className="text-link" to={`/products/${product.id}/selling-price`}>Manage selling price</Link>}</dd></div>
      <div><dt>Unit of measure</dt><dd>{product.unitOfMeasure}</dd></div><div><dt>Status</dt><dd><span className={`status-badge ${product.active ? 'active' : 'inactive'}`}>{product.active ? 'Active' : 'Inactive'}</span></dd></div>
      <div className="span-two"><dt>Description</dt><dd className="preserve-lines">{product.description || 'No description provided.'}</dd></div>
      <div><dt>Created</dt><dd>{new Date(product.createdAt).toLocaleString('en-GB')}</dd></div><div><dt>Updated</dt><dd>{new Date(product.updatedAt).toLocaleString('en-GB')}</dd></div>
    </dl></section>
  </>
}
