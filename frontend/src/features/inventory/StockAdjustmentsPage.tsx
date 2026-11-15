import { useCallback, useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { ApiError, errorMessage } from '../../shared/api/http'
import { ErrorState, LoadingState } from '../../shared/components/Feedback'
import { FormField } from '../../shared/components/FormField'
import { useRemoteData } from '../../shared/hooks/useRemoteData'
import { useAuth } from '../identity/useAuth'
import { InventoryPagination } from './InventoryPagination'
import { inventoryApi } from './inventoryApi'
import type { StockAdjustmentInput } from './inventoryApi'

export function StockAdjustmentsPage() {
  const { user, refresh } = useAuth()
  const [params] = useSearchParams()
  const warehouseId = params.get('warehouseId') ?? ''
  const productId = params.get('productId') ?? ''
  const [page, setPage] = useState(0)
  const [count, setCount] = useState('')
  const [reason, setReason] = useState('')
  const [expectedOnHand, setExpectedOnHand] = useState<number | null>(null)
  const [submitted, setSubmitted] = useState<StockAdjustmentInput | null>(null)
  const [pending, setPending] = useState(false)
  const [notice, setNotice] = useState('')
  const [error, setError] = useState('')
  const [errors, setErrors] = useState<Record<string, string>>({})
  const errorRef = useRef<HTMLDivElement>(null)
  const load = useCallback((signal: AbortSignal) => inventoryApi.adjustments(warehouseId, productId, page, signal), [warehouseId, productId, page])
  const result = useRemoteData(load)
  const balance = result.data?.balance
  const history = result.data?.history
  const canAdjust = Boolean(user?.canWriteInventory && balance?.productActive && balance.warehouseActive)

  useEffect(() => { if (error) errorRef.current?.focus() }, [error])

  function showPage(next: number) {
    if (next === page) result.reload()
    else { result.begin(); setPage(next) }
  }

  async function adjust(event: FormEvent) {
    event.preventDefault()
    if (!balance || pending || !user?.canWriteInventory) return
    const countedOnHand = Number(count)
    const baseline = expectedOnHand ?? balance.onHand
    if (!submitted) {
      const invalid: Record<string, string> = {}
      if (!count.trim() || !Number.isSafeInteger(countedOnHand) || countedOnHand < 0) {
        invalid.countedOnHand = 'Enter a whole number between zero and the supported maximum.'
      } else if (countedOnHand < balance.reserved) {
        invalid.countedOnHand = 'The count cannot be below the reserved quantity.'
      } else if (countedOnHand === baseline) {
        invalid.countedOnHand = 'Enter a count that differs from the current on-hand quantity.'
      }
      if (!reason.trim()) invalid.reason = 'Enter a reason for this adjustment.'
      if (Object.keys(invalid).length) { setErrors(invalid); return }
    }

    const input = submitted ?? { warehouseId, productId, expectedOnHand: baseline, countedOnHand, reason: reason.trim(), requestId: crypto.randomUUID() }
    setSubmitted(input)
    setPending(true)
    setNotice('')
    setError('')
    setErrors({})
    try {
      const response = await inventoryApi.adjust(input)
      setSubmitted(null)
      setCount('')
      setExpectedOnHand(null)
      setReason('')
      setNotice(response.replayed ? 'This stock adjustment was already recorded.' : 'Stock adjustment recorded.')
      showPage(0)
    } catch (failure) {
      if (failure instanceof ApiError && failure.problem.status === 401) { await refresh(); return }
      if (submitted) {
        setError(`${errorMessage(failure)} The earlier adjustment may already be saved. Retry the same adjustment or check the history before recording another count.`)
      } else if (failure instanceof ApiError && failure.problem.status < 500) {
        setSubmitted(null)
        setErrors(failure.problem.errors ?? {})
        if (failure.problem.status === 409) {
          setCount('')
          setExpectedOnHand(null)
          setError(`${errorMessage(failure)} Review the latest balance before entering the count again.`)
          result.reload()
        } else setError(errorMessage(failure))
      } else {
        setError('We could not confirm whether the adjustment was saved. Retry the same adjustment or check the history before recording another count.')
      }
    } finally { setPending(false) }
  }

  return <>
    <Link className="back-link" to={warehouseId ? `/inventory?warehouseId=${encodeURIComponent(warehouseId)}` : '/inventory'}>← Back to inventory</Link>
    <div className="page-heading"><div><h1>Stock adjustments</h1>{balance && <p className="muted">{balance.productName} ({balance.sku}) · {balance.warehouseName}</p>}</div></div>
    {notice && <p className="success-panel" role="status">{notice}</p>}
    {error && <div className="error-panel" role="alert" ref={errorRef} tabIndex={-1}>{error}</div>}
    {result.loading ? <LoadingState label="Loading stock balance and adjustment history…" /> :
      result.error ? <ErrorState message={result.error} onRetry={result.reload} /> :
      balance && history && <>
        <section className="form-card">
          <h2>Current balance</h2>
          <dl className="detail-grid">
            <div><dt>On-hand</dt><dd>{balance.onHand.toLocaleString()} {balance.unitOfMeasure}</dd></div>
            <div><dt>Reserved</dt><dd>{balance.reserved.toLocaleString()} {balance.unitOfMeasure}</dd></div>
          </dl>
        </section>
        {(canAdjust || (user?.canWriteInventory && submitted)) && <form className="product-form" onSubmit={adjust}>
          <section className="form-card">
            <h2>Record a physical count</h2>
            <fieldset className="field-grid" disabled={pending || submitted !== null}>
              <legend className="sr-only">Stock adjustment</legend>
              <FormField name="countedOnHand" label="Counted on-hand" required error={errors.countedOnHand} hint="Enter the total physical count, including reserved units.">
                <input id="countedOnHand" type="number" min={0} max={Number.MAX_SAFE_INTEGER} step={1} required value={count}
                  aria-invalid={Boolean(errors.countedOnHand)} aria-describedby={errors.countedOnHand ? 'countedOnHand-error' : 'countedOnHand-hint'}
                  onChange={event => { setCount(event.target.value); setExpectedOnHand(expectedOnHand ?? balance.onHand) }} />
              </FormField>
              <FormField name="reason" label="Reason" required error={errors.reason}>
                <textarea id="reason" rows={2} maxLength={500} required value={reason} onChange={event => setReason(event.target.value)}
                  aria-invalid={Boolean(errors.reason)} aria-describedby={errors.reason ? 'reason-error' : undefined} />
              </FormField>
            </fieldset>
            <div className="form-actions"><button className="button primary" disabled={pending}>
              {pending ? 'Recording…' : submitted ? 'Retry same adjustment' : 'Record adjustment'}
            </button></div>
          </section>
        </form>}
        <section aria-labelledby="adjustment-history">
          <h2 id="adjustment-history">Adjustment history</h2>
          {history.items.length === 0 ? <p className="state-panel">No stock adjustments have been recorded for this balance.</p> :
            <div className="table-panel">
              <p className="table-hint">Scroll the table horizontally to see all columns.</p>
              <div className="table-scroll"><table aria-labelledby="adjustment-history">
                <thead><tr><th scope="col">Recorded</th><th scope="col">Previous on-hand</th><th scope="col">Counted on-hand</th><th scope="col">Reserved</th><th scope="col">Reason</th><th scope="col">Recorded by</th></tr></thead>
                <tbody>{history.items.map(entry => <tr key={entry.id}>
                  <td>{new Date(entry.occurredAt).toLocaleString('en-GB')}</td>
                  <td>{entry.previousOnHand.toLocaleString()}</td><td>{entry.countedOnHand.toLocaleString()}</td>
                  <td>{entry.reserved.toLocaleString()}</td><td className="preserve-lines">{entry.reason}</td><td>{entry.actorUsername}</td>
                </tr>)}</tbody>
              </table></div>
              <InventoryPagination page={history} onPage={showPage} />
            </div>}
        </section>
      </>}
  </>
}
