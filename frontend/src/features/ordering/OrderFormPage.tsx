import { useCallback, useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ApiError, errorMessage } from '../../shared/api/http'
import { ErrorState, LoadingState } from '../../shared/components/Feedback'
import { FormField } from '../../shared/components/FormField'
import { useRemoteData } from '../../shared/hooks/useRemoteData'
import { productApi } from '../catalog/productApi'
import type { Product } from '../catalog/productApi'
import { formatLkr } from '../catalog/sellingPriceApi'
import { customerApi } from '../customers/customerApi'
import { useAuth } from '../identity/useAuth'
import { inventoryApi } from '../inventory/inventoryApi'
import { orderApi } from './orderApi'
import type { OrderInput } from './orderApi'

interface DraftItem { product: Product; quantity: string }

export function OrderFormPage() {
  const { user, refresh } = useAuth()
  const navigate = useNavigate()
  const [customerId, setCustomerId] = useState('')
  const [warehouseId, setWarehouseId] = useState('')
  const [customerSearch, setCustomerSearch] = useState('')
  const [customerQuery, setCustomerQuery] = useState('')
  const [productSearch, setProductSearch] = useState('')
  const [productQuery, setProductQuery] = useState('')
  const [productId, setProductId] = useState('')
  const [items, setItems] = useState<DraftItem[]>([])
  const [requestId, setRequestId] = useState(() => crypto.randomUUID())
  const [submitted, setSubmitted] = useState<OrderInput | null>(null)
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const errorRef = useRef<HTMLDivElement>(null)
  const load = useCallback(async (signal: AbortSignal) => {
    const [customers, warehouses, products] = await Promise.all([
      customerApi.list({ q: customerQuery, active: 'true', page: 0, size: 100 }, signal),
      inventoryApi.warehouses({ active: true, size: 100 }, signal),
      productApi.list({ q: productQuery, category: '', active: 'true', page: 0, size: 20 }, signal),
    ])
    return { customers: customers.items, warehouses: warehouses.items, products: products.items }
  }, [customerQuery, productQuery])
  const result = useRemoteData(load)
  useEffect(() => { if (error) errorRef.current?.focus() }, [error])

  if (!user?.canWriteOrders) return <ErrorState message="Order write permission is required to create orders." />
  const locked = pending || submitted !== null
  function field(name: string, hint = false) {
    return { id: name, 'aria-invalid': Boolean(errors[name]),
      'aria-describedby': errors[name] ? `${name}-error` : hint ? `${name}-hint` : undefined }
  }

  function addProduct() {
    const product = result.data?.products.find(option => option.id === productId)
    if (!product) { setError('Select a product to add.'); return }
    if (product.sellingPrice === null) { setError('Set a selling price for this product before adding it to an order.'); return }
    if (items.some(item => item.product.id === product.id)) { setError('This product is already in the order. Change its quantity instead.'); return }
    if (items.length >= 100) { setError('An order can contain at most 100 different products.'); return }
    setItems(current => [...current, { product, quantity: '1' }])
    setProductId(''); setError(null); setErrors({})
  }

  function removeProduct(id: string) {
    setItems(current => current.filter(item => item.product.id !== id))
    // Server field errors use item indexes; removing a row invalidates those indexes.
    setErrors({}); setError(null)
  }

  async function create(event: FormEvent) {
    event.preventDefault()
    if (pending) return
    if (!submitted) {
      const validation: Record<string, string> = {}
      if (!customerId) validation.customerId = 'Select an active customer.'
      if (!warehouseId) validation.warehouseId = 'Select an active warehouse.'
      if (items.length === 0) validation.items = 'Add at least one product.'
      items.forEach((item, index) => {
        const quantity = Number(item.quantity)
        if (!Number.isSafeInteger(quantity) || quantity <= 0) validation[`items[${index}].quantity`] = 'Enter a positive whole-unit quantity within the supported range.'
      })
      if (Object.keys(validation).length) { setErrors(validation); setError('Check the highlighted order fields.'); return }
    }
    const payload: OrderInput = submitted ?? { requestId, customerId, warehouseId,
      items: items.map(item => ({ productId: item.product.id, quantity: Number(item.quantity) })) }
    setPending(true); setError(null); setErrors({}); setSubmitted(payload)
    try {
      const response = await orderApi.create(payload)
      navigate(`/orders/${response.order.id}`, { state: { notice: response.replayed ? 'This order was already created. A duplicate was not added.' : 'Pending order created. No stock is reserved yet.' } })
    } catch (failure) {
      if (failure instanceof ApiError && failure.problem.status === 401) { await refresh(); return }
      if (submitted) {
        setError(`${errorMessage(failure)} The earlier order may already exist. Keep this page open and retry the same order, or check the order list before creating another.`)
      } else if (failure instanceof ApiError && failure.problem.status < 500) {
        setSubmitted(null); setRequestId(crypto.randomUUID()); setErrors(failure.problem.errors ?? {}); setError(errorMessage(failure))
      } else setError('We could not confirm whether this order was saved. Keep this page open and retry the same order safely. If you leave, check the order list before creating another.')
    } finally { setPending(false) }
  }

  return <>
    <Link className="back-link" to="/orders">← Back to orders</Link>
    <div className="page-heading"><div><h1>Create order</h1><p className="muted">Create a pending order for one customer and warehouse. Stock is reserved only when you confirm it.</p></div></div>
    <form className="product-form" onSubmit={create} aria-label="Create pending order">
      <section className="form-card"><h2>Customer and warehouse</h2>
        {result.loading ? <LoadingState label="Loading active order choices…" /> : result.error ? <ErrorState message={result.error} onRetry={result.reload} /> : <fieldset className="field-grid" disabled={locked}><legend className="sr-only">Order choices</legend>
          <div><label htmlFor="order-customer-search">Find an active customer</label><div className="inline-search"><input id="order-customer-search" maxLength={100} value={customerSearch} onChange={event => setCustomerSearch(event.target.value)} placeholder="Name, contact, email or phone" />
            <button className="button secondary" type="button" onClick={() => { setCustomerId(''); result.begin(); if (customerQuery === customerSearch.trim()) result.reload(); else setCustomerQuery(customerSearch.trim()) }}>Find customers</button></div></div>
          <FormField name="customerId" label="Customer" required error={errors.customerId} hint="Showing up to 100 active matches. Refine the customer search if needed."><select {...field('customerId', true)} required value={customerId} onChange={event => setCustomerId(event.target.value)}>
            <option value="">Select a customer</option>{result.data?.customers.map(customer => <option key={customer.id} value={customer.id}>{customer.name}</option>)}</select></FormField>
          <FormField name="warehouseId" label="Warehouse" required error={errors.warehouseId}><select {...field('warehouseId')} required value={warehouseId} onChange={event => setWarehouseId(event.target.value)}>
            <option value="">Select a warehouse</option>{result.data?.warehouses.map(warehouse => <option key={warehouse.id} value={warehouse.id}>{warehouse.name} ({warehouse.code})</option>)}</select></FormField>
        </fieldset>}
      </section>
      <section className="form-card"><h2>Order items</h2><p className="muted small">Add 1–100 different products. Quantities are whole units; prices are captured by the server when the order is saved.</p>
        <fieldset className="field-grid" disabled={locked || result.loading || Boolean(result.error)}><legend className="sr-only">Add order products</legend>
          <div><label htmlFor="order-product-search">Find an active product</label><div className="inline-search"><input id="order-product-search" maxLength={100} value={productSearch} onChange={event => setProductSearch(event.target.value)} placeholder="Name or SKU" />
            <button className="button secondary" type="button" onClick={() => { setProductId(''); result.begin(); if (productQuery === productSearch.trim()) result.reload(); else setProductQuery(productSearch.trim()) }}>Find products</button></div></div>
          <FormField name="order-product" label="Product to add" hint="Showing up to 20 active matches. Products need a selling price before ordering."><select id="order-product" aria-describedby="order-product-hint" value={productId} onChange={event => setProductId(event.target.value)}>
            <option value="">Select a product</option>{result.data?.products.map(product => <option key={product.id} value={product.id} disabled={product.sellingPrice === null}>{product.name} ({product.sku}) — {formatLkr(product.sellingPrice)}</option>)}</select></FormField>
          <div><button className="button secondary" type="button" onClick={addProduct}>Add product to order</button></div>
        </fieldset>
        {errors.items && <p className="field-error" id="items-error">{errors.items}</p>}
        {items.length === 0 ? <p className="muted">No products added yet.</p> : <fieldset disabled={locked}><legend className="sr-only">Order item quantities</legend>
          {items.map((item, index) => <div className="order-item-row" key={item.product.id}>
            <div><strong>{item.product.name}</strong><span className="cell-detail">{item.product.sku} · Current catalog price: {formatLkr(item.product.sellingPrice)} per {item.product.unitOfMeasure}</span></div>
            <FormField name={`item-quantity-${index}`} label={`Quantity for ${item.product.name}`} required error={errors[`items[${index}].quantity`]}><input id={`item-quantity-${index}`} type="number" min={1} max={Number.MAX_SAFE_INTEGER} step={1} required value={item.quantity}
              aria-invalid={Boolean(errors[`items[${index}].quantity`])} aria-describedby={errors[`items[${index}].quantity`] ? `item-quantity-${index}-error` : undefined}
              onChange={event => setItems(current => current.map((entry, itemIndex) => itemIndex === index ? { ...entry, quantity: event.target.value } : entry))} /></FormField>
            <button className="button secondary" type="button" aria-label={`Remove ${item.product.name}`} onClick={() => removeProduct(item.product.id)}>Remove</button>
          </div>)}
        </fieldset>}
      </section>
      {error && <div className="error-panel" role="alert" tabIndex={-1} ref={errorRef}>{error}</div>}
      <div className="form-actions"><Link className="button secondary" to="/orders" onClick={event => { if (pending) event.preventDefault() }}>View orders</Link>
        <button className="button primary" disabled={pending || result.loading || Boolean(result.error)}>{pending ? 'Creating…' : submitted ? 'Retry same order' : 'Create pending order'}</button></div>
    </form>
  </>
}
