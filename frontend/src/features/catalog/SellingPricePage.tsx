import { useCallback, useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ApiError, errorMessage } from '../../shared/api/http'
import { ErrorState, LoadingState } from '../../shared/components/Feedback'
import { FormField } from '../../shared/components/FormField'
import { useRemoteData } from '../../shared/hooks/useRemoteData'
import { useAuth } from '../identity/useAuth'
import { productApi } from './productApi'
import { formatLkr, sellingPriceApi } from './sellingPriceApi'

export function SellingPricePage() {
  const { id } = useParams()
  const { user, refresh } = useAuth()
  const [amount, setAmount] = useState('')
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [fieldError, setFieldError] = useState<string | undefined>()
  const [notice, setNotice] = useState<string | null>(null)
  const errorRef = useRef<HTMLDivElement>(null)
  const load = useCallback(async (signal: AbortSignal) => {
    const [product, price] = await Promise.all([productApi.get(id!, signal), sellingPriceApi.get(id!, signal)])
    return { product, price }
  }, [id])
  const result = useRemoteData(load)
  useEffect(() => { if (error) errorRef.current?.focus() }, [error])

  async function save(event: FormEvent) {
    event.preventDefault()
    if (pending || !result.data) return
    setError(null); setFieldError(undefined); setNotice(null)
    const value = amount.trim()
    if (!/^[0-9]{1,12}(\.[0-9]{1,2})?$/.test(value)) {
      setFieldError('Use a nonnegative LKR amount with up to 12 whole digits and two decimal places.')
      setError('Check the highlighted selling price.')
      return
    }
    setPending(true)
    try {
      await sellingPriceApi.update(id!, value, result.data.price.version)
      setAmount(''); setNotice('Selling price updated successfully.'); result.reload()
    } catch (failure) {
      if (failure instanceof ApiError && failure.problem.status === 401) { await refresh(); return }
      setError(errorMessage(failure))
      if (failure instanceof ApiError) setFieldError(failure.problem.errors?.amount)
    } finally { setPending(false) }
  }

  function reload() { setAmount(''); setError(null); setFieldError(undefined); setNotice(null); result.reload() }
  if (result.loading) return <LoadingState label="Loading selling price…" />
  if (result.error) return <ErrorState message={result.error} onRetry={result.reload} />
  if (!result.data) return null
  const { product, price } = result.data

  return <>
    <Link className="back-link" to={`/products/${product.id}`}>← Back to product</Link>
    <div className="page-heading"><div><h1>Selling price</h1><p className="muted">{product.name} · {product.sku}</p></div></div>
    {notice && <p className="success-panel" role="status">{notice}</p>}
    <section className="form-card"><h2>Current price</h2><p>{formatLkr(price.amount)}{price.amount !== null && ` per ${product.unitOfMeasure}`}</p>
      <p className="muted small">One LKR selling price applies across your organization's warehouses.</p></section>
    {user?.canWritePrices ? <form className="product-form" onSubmit={save}>
      <section className="form-card"><h2>Set selling price</h2><fieldset className="field-grid" disabled={pending}><legend className="sr-only">Selling price details</legend>
        <FormField name="amount" label="New selling price (LKR)" required error={fieldError} hint="Zero is allowed. Use up to two decimal places; extra decimals are rejected.">
          <input id="amount" type="text" inputMode="decimal" maxLength={15} required value={amount} placeholder={price.amount ?? '0.00'}
            aria-invalid={Boolean(fieldError)} aria-describedby={fieldError ? 'amount-error' : 'amount-hint'} onChange={event => setAmount(event.target.value)} />
        </FormField></fieldset></section>
      {error && <div className="error-panel" role="alert" tabIndex={-1} ref={errorRef}><p>{error}</p><button className="button secondary" type="button" onClick={reload}>Reload latest price (discards entry)</button></div>}
      <div className="form-actions"><button className="button primary" disabled={pending}>{pending ? 'Saving…' : 'Save selling price'}</button></div>
    </form> : <p className="state-panel">Selling-price write permission is required to change this price.</p>}
  </>
}
