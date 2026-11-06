import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ApiError, errorMessage } from '../../shared/api/http'
import { ErrorState, LoadingState } from '../../shared/components/Feedback'
import { FormField } from '../../shared/components/FormField'
import { useAuth } from '../identity/useAuth'
import { productApi } from './productApi'
import type { Product, ProductValues } from './productApi'

const emptyValues: ProductValues = { name: '', sku: '', description: '', category: '', unitOfMeasure: 'Each', active: true }

export function ProductFormPage() {
  const { id } = useParams()
  const { user, refresh } = useAuth()
  const navigate = useNavigate()
  const [values, setValues] = useState<ProductValues>(emptyValues)
  const [product, setProduct] = useState<Product | null>(null)
  const [loading, setLoading] = useState(Boolean(id))
  const [pending, setPending] = useState(false)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [saveError, setSaveError] = useState<string | null>(null)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [attempt, setAttempt] = useState(0)
  const errorRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!id || !user?.canWriteCatalog) return
    const controller = new AbortController()
    productApi.get(id, controller.signal).then(result => { setProduct(result); setValues(result) })
      .catch(failure => {
        if (controller.signal.aborted) return
        if (failure instanceof ApiError && failure.problem.status === 401) void refresh()
        else setLoadError(errorMessage(failure))
      }).finally(() => { if (!controller.signal.aborted) setLoading(false) })
    return () => controller.abort()
  }, [id, attempt, user?.canWriteCatalog, refresh])

  useEffect(() => { if (saveError) errorRef.current?.focus() }, [saveError])

  if (!user?.canWriteCatalog) return <ErrorState message="You have read-only catalog access. You can view products but cannot create or edit them." />
  if (loading) return <LoadingState label="Loading product…" />
  if (loadError) return <ErrorState message={loadError} onRetry={() => { setLoading(true); setLoadError(null); setAttempt(attempt + 1) }} />

  function field(name: string, hint = false) {
    return { id: name, 'aria-invalid': Boolean(errors[name]),
      'aria-describedby': errors[name] ? `${name}-error` : hint ? `${name}-hint` : undefined }
  }

  function change(name: keyof ProductValues, value: string | boolean) { setValues(current => ({ ...current, [name]: value })) }

  async function save(event: FormEvent) {
    event.preventDefault()
    if (pending) return
    setPending(true); setErrors({}); setSaveError(null)
    // Select only editable fields; never send response metadata or an organization identifier.
    const payload: ProductValues = { sku: values.sku.trim(), name: values.name.trim(), description: values.description?.trim() || null,
      category: values.category.trim(), unitOfMeasure: values.unitOfMeasure.trim(), active: values.active }
    try {
      if (id && product) await productApi.update(id, payload, product.version)
      else await productApi.create(payload)
      navigate('/products', { state: { notice: id ? 'Product updated successfully.' : 'Product created successfully.' } })
    } catch (failure) {
      if (failure instanceof ApiError && failure.problem.status === 401) { await refresh(); return }
      setSaveError(errorMessage(failure))
      if (failure instanceof ApiError) setErrors(failure.problem.errors ?? {})
    } finally { setPending(false) }
  }

  return <>
    <Link className="back-link" to="/products">← Back to products</Link>
    <div className="page-heading"><div><h1>{id ? 'Edit product' : 'Add product'}</h1><p className="muted">{id ? 'Update the information used to identify this product.' : 'Create a new item in your product catalog.'}</p></div></div>
    <form onSubmit={save} className="product-form">
      <section className="form-card" aria-labelledby="product-information"><div className="card-heading"><span className="section-number" aria-hidden="true">1</span>
        <div><h2 id="product-information">Product information</h2><p className="muted small">Basic details used to identify this product. Fields marked * are required.</p></div></div>
        <fieldset disabled={pending} className="field-grid"><legend className="sr-only">Product details</legend>
          <FormField name="name" label="Product name" required error={errors.name}><input {...field('name')} required maxLength={160} value={values.name} onChange={event => change('name', event.target.value)} /></FormField>
          <FormField name="sku" label="SKU" required error={errors.sku} hint="Unique in your organization; letters, numbers, dots, hyphens and underscores.">
            <input {...field('sku', true)} required maxLength={64} pattern="[A-Za-z0-9][A-Za-z0-9._\-]{0,63}" value={values.sku} onChange={event => change('sku', event.target.value)} /></FormField>
          <FormField name="category" label="Category" required error={errors.category}><input {...field('category')} required maxLength={80} value={values.category} onChange={event => change('category', event.target.value)} /></FormField>
          <FormField name="unitOfMeasure" label="Unit of measure" required error={errors.unitOfMeasure} hint="For example: Each, Box, Case or Set.">
            <input {...field('unitOfMeasure', true)} required maxLength={32} value={values.unitOfMeasure} onChange={event => change('unitOfMeasure', event.target.value)} /></FormField>
          <div className="span-two"><FormField name="description" label="Description" error={errors.description} hint="Optional. Maximum 2,000 characters.">
            <textarea {...field('description', true)} rows={5} maxLength={2000} value={values.description ?? ''} onChange={event => change('description', event.target.value)} /></FormField></div>
        </fieldset>
      </section>
      <section className="form-card" aria-labelledby="product-status"><div className="card-heading"><span className="section-number" aria-hidden="true">2</span>
        <div><h2 id="product-status">Catalog status</h2><p className="muted small">Mark whether this product is active in your catalog.</p></div></div>
        <label className="checkbox-label"><input type="checkbox" checked={values.active} disabled={pending} onChange={event => change('active', event.target.checked)} />Active product</label>
      </section>
      {saveError && <div ref={errorRef} tabIndex={-1} className="error-panel" role="alert"><strong>Product could not be saved</strong><p>{saveError}</p>
        {id && <button className="button secondary" type="button" onClick={() => { setLoading(true); setAttempt(attempt + 1); setSaveError(null); setErrors({}) }}>Reload latest product (discards edits)</button>}</div>}
      <div className="form-actions"><Link className="button secondary" to="/products" aria-disabled={pending} onClick={event => { if (pending) event.preventDefault() }}>Cancel</Link>
        <button className="button primary" disabled={pending}>{pending ? 'Saving…' : 'Save product'}</button></div>
    </form>
  </>
}
