import { useCallback, useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { ApiError, errorMessage } from '../../shared/api/http'
import { ErrorState, LoadingState } from '../../shared/components/Feedback'
import { FormField } from '../../shared/components/FormField'
import { productApi } from '../catalog/productApi'
import { useAuth } from '../identity/useAuth'
import { inventoryApi } from './inventoryApi'
import type { StockReceiptInput } from './inventoryApi'
import { useRemoteData } from '../../shared/hooks/useRemoteData'

export function StockReceivingPage() {
  const { user, refresh } = useAuth()
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const [warehouseId, setWarehouseId] = useState(params.get('warehouseId') ?? '')
  const [productId, setProductId] = useState('')
  const [search, setSearch] = useState('')
  const [query, setQuery] = useState('')
  const [quantity, setQuantity] = useState('')
  const [reference, setReference] = useState('')
  const [note, setNote] = useState('')
  const [requestId, setRequestId] = useState(() => crypto.randomUUID())
  const [pending, setPending] = useState(false)
  const [submitted, setSubmitted] = useState<StockReceiptInput | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const errorRef = useRef<HTMLDivElement>(null)
  const load = useCallback(async (signal: AbortSignal) => {
    const [warehouses, products] = await Promise.all([inventoryApi.warehouses({ active: true, size: 100 }, signal), productApi.list({ q: query, category: '', active: 'true', page: 0, size: 20 }, signal)])
    return { warehouses: warehouses.items, products: products.items }
  }, [query])
  const result = useRemoteData(load)
  useEffect(() => { if (error) errorRef.current?.focus() }, [error])
  if (!user?.canWriteInventory) return <ErrorState message="Inventory write permission is required to receive stock." />
  const locked = pending || submitted !== null
  function field(name: string, hint = false) {
    return { id: name, 'aria-invalid': Boolean(errors[name]),
      'aria-describedby': errors[name] ? `${name}-error` : hint ? `${name}-hint` : undefined }
  }
  async function receive(event: FormEvent) {
    event.preventDefault()
    if (pending) return
    const units = Number(quantity)
    if (!submitted && (!Number.isSafeInteger(units) || units <= 0)) {
      setErrors({ quantity: 'Enter a positive whole-unit quantity within the supported range.' })
      return
    }
    const payload: StockReceiptInput = submitted ?? { requestId, warehouseId, productId, quantity: units, reference: reference.trim() || null, note: note.trim() || null }
    setPending(true)
    setError(null)
    setErrors({})
    setSubmitted(payload)
    try {
      const response = await inventoryApi.receive(payload)
      navigate(`/inventory?warehouseId=${payload.warehouseId}`, { state: { notice: response.replayed ? 'This receipt was already recorded. Stock was not increased again.' : 'Stock received successfully.' } })
    } catch (failure) {
      if (failure instanceof ApiError && failure.problem.status === 401) { await refresh(); return }
      // A later rejection cannot prove that an earlier uncertain attempt did not commit.
      // Retain its original ID/payload until a successful replay resolves that uncertainty.
      if (submitted) {
        setError(`${errorMessage(failure)} The earlier receipt may already be saved. Keep this page open and retry the same receipt after resolving the error, or check movement history before recording another delivery.`)
      } else if (failure instanceof ApiError && failure.problem.status < 500) {
        setSubmitted(null); setRequestId(crypto.randomUUID()); setErrors(failure.problem.errors ?? {}); setError(errorMessage(failure))
      } else setError('We could not confirm whether this receipt was saved. Keep this page open and retry the same receipt safely; its details are locked to prevent double-counting. If you leave, check movement history before recording another delivery.')
    } finally { setPending(false) }
  }
  return <><Link className="back-link" to="/inventory">← Back to inventory</Link><div className="page-heading"><div><h1>Receive stock</h1><p className="muted">Record a physical delivery of one product into an active warehouse.</p></div></div>
    <form className="product-form" onSubmit={receive}>
      <section className="form-card"><h2>Receipt details</h2><p className="muted small">Review the warehouse, product, and quantity before receiving. Fields marked * are required.</p>
        {result.loading ? <LoadingState label="Loading active warehouses and products…" /> : result.error ? <ErrorState message={result.error} onRetry={result.reload} /> : <fieldset className="field-grid" disabled={locked}><legend className="sr-only">Stock receipt</legend>
          <FormField name="warehouseId" label="Warehouse" required error={errors.warehouseId}><select {...field('warehouseId')} required value={warehouseId} onChange={event => setWarehouseId(event.target.value)}><option value="">Select a warehouse</option>{result.data?.warehouses.map(warehouse => <option key={warehouse.id} value={warehouse.id}>{warehouse.name} ({warehouse.code})</option>)}</select></FormField>
          <div><label htmlFor="receipt-product-search">Find an active product</label><div className="inline-search"><input id="receipt-product-search" maxLength={100} value={search} placeholder="Name or SKU" onChange={event => setSearch(event.target.value)} /><button className="button secondary" type="button" onClick={() => { setProductId(''); result.begin(); if (query === search.trim()) result.reload(); else setQuery(search.trim()) }}>Find products</button></div></div>
          <FormField name="productId" label="Product" required error={errors.productId} hint="Showing up to 20 matching active products. Refine the search if needed."><select {...field('productId', true)} required value={productId} onChange={event => setProductId(event.target.value)}><option value="">Select a product</option>{result.data?.products.map(product => <option key={product.id} value={product.id}>{product.name} ({product.sku})</option>)}</select></FormField>
          <FormField name="quantity" label="Quantity received" required error={errors.quantity} hint="Positive whole units; use the product's catalog unit."><input {...field('quantity', true)} type="number" min={1} max={Number.MAX_SAFE_INTEGER} step={1} required value={quantity} onChange={event => setQuantity(event.target.value)} /></FormField>
          <FormField name="reference" label="Reference" error={errors.reference} hint="Optional delivery slip or internal reference."><input {...field('reference', true)} maxLength={120} value={reference} onChange={event => setReference(event.target.value)} /></FormField>
          <div className="span-two"><FormField name="note" label="Note" error={errors.note}><textarea {...field('note')} maxLength={500} rows={3} value={note} onChange={event => setNote(event.target.value)} /></FormField></div>
        </fieldset>}
        <p className="field-hint">Only active warehouses/products can receive new stock. Receipts create a read-only movement record; corrections are not available in this release.</p>
      </section>
      {error && <div className="error-panel" role="alert" ref={errorRef} tabIndex={-1}>{error}</div>}
      <div className="form-actions"><Link className="button secondary" to="/inventory/movements" onClick={event => { if (pending) event.preventDefault() }}>View stock movements</Link><button className="button primary" disabled={pending || result.loading || Boolean(result.error)}>{pending ? 'Receiving…' : submitted ? 'Retry same receipt' : 'Receive stock'}</button></div>
    </form></>
}
