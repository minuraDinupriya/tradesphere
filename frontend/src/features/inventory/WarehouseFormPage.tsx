import { useCallback, useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ApiError, errorMessage } from '../../shared/api/http'
import { ErrorState, LoadingState } from '../../shared/components/Feedback'
import { FormField } from '../../shared/components/FormField'
import { useAuth } from '../identity/useAuth'
import { inventoryApi } from './inventoryApi'
import type { Warehouse, WarehouseValues } from './inventoryApi'

export function WarehouseFormPage() {
  const { id } = useParams(); const { user, refresh } = useAuth(); const navigate = useNavigate()
  const [values, setValues] = useState<WarehouseValues>({ code: '', name: '', location: '', active: true })
  const [version, setVersion] = useState(0); const [loading, setLoading] = useState(Boolean(id))
  const [loadError, setLoadError] = useState<string | null>(null); const [saveError, setSaveError] = useState<string | null>(null)
  const [errors, setErrors] = useState<Record<string, string>>({}); const [pending, setPending] = useState(false)
  const [attempt, setAttempt] = useState(0); const errorRef = useRef<HTMLDivElement>(null)
  const fill = useCallback((warehouse: Warehouse) => { setValues(warehouse); setVersion(warehouse.version) }, [])
  useEffect(() => {
    if (!id || !user?.canWriteInventory) return
    const controller = new AbortController()
    inventoryApi.warehouse(id, controller.signal).then(fill).catch(failure => {
      if (!controller.signal.aborted) setLoadError(errorMessage(failure))
    }).finally(() => { if (!controller.signal.aborted) setLoading(false) })
    return () => controller.abort()
  }, [id, user?.canWriteInventory, attempt, fill])
  useEffect(() => { if (saveError) errorRef.current?.focus() }, [saveError])
  function reload() { setLoading(true); setLoadError(null); setSaveError(null); setErrors({}); setAttempt(value => value + 1) }

  if (!user?.canWriteInventory) return <ErrorState message="Inventory write permission is required to manage warehouses." />
  if (loading) return <LoadingState label="Loading warehouse…" />
  if (loadError) return <ErrorState message={loadError} onRetry={reload} />
  function field(name: string, key: string) { return { id: name, 'aria-invalid': Boolean(errors[key]), 'aria-describedby': errors[key] ? `${name}-error` : undefined } }
  async function save(event: FormEvent) {
    event.preventDefault(); if (pending) return
    setPending(true); setErrors({}); setSaveError(null)
    const payload: WarehouseValues = { code: values.code.trim(), name: values.name.trim(), location: values.location?.trim() || null, active: values.active }
    try {
      if (id) await inventoryApi.updateWarehouse(id, payload, version); else await inventoryApi.createWarehouse(payload)
      navigate('/warehouses', { state: { notice: id ? 'Warehouse updated successfully.' : 'Warehouse created successfully.' } })
    } catch (failure) {
      if (failure instanceof ApiError && failure.problem.status === 401) { await refresh(); return }
      setSaveError(errorMessage(failure)); if (failure instanceof ApiError) setErrors(failure.problem.errors ?? {})
    } finally { setPending(false) }
  }
  return <><Link className="back-link" to="/warehouses">← Back to warehouses</Link><div className="page-heading"><div><h1>{id ? 'Edit warehouse' : 'Add warehouse'}</h1><p className="muted">A warehouse code identifies a stock location within your organization.</p></div></div>
    <form className="product-form" onSubmit={save}><section className="form-card"><h2>Warehouse information</h2><p className="muted small">Fields marked * are required.</p><fieldset className="field-grid" disabled={pending}><legend className="sr-only">Warehouse details</legend>
      <FormField name="warehouse-name" label="Warehouse name" required error={errors.name}><input {...field('warehouse-name', 'name')} required maxLength={160} value={values.name} onChange={event => setValues({ ...values, name: event.target.value })} /></FormField>
      <FormField name="warehouse-code" label="Warehouse code" required error={errors.code}><input {...field('warehouse-code', 'code')} required maxLength={32} pattern="[A-Za-z0-9][A-Za-z0-9._\-]{0,31}" placeholder="For example: COL-01" value={values.code} onChange={event => setValues({ ...values, code: event.target.value })} /></FormField>
      <div className="span-two"><FormField name="warehouse-location" label="Location" error={errors.location}><input {...field('warehouse-location', 'location')} maxLength={240} value={values.location ?? ''} onChange={event => setValues({ ...values, location: event.target.value })} /></FormField></div>
      <label className="checkbox-label span-two"><input type="checkbox" checked={values.active} onChange={event => setValues({ ...values, active: event.target.checked })} />Active warehouse</label>
    </fieldset><p className="field-hint">Inactive warehouses retain their stock and history but cannot receive new stock.</p></section>
      {saveError && <div role="alert" className="error-panel" tabIndex={-1} ref={errorRef}><strong>Warehouse could not be saved</strong><p>{saveError}</p>{id && <button type="button" className="button secondary" onClick={reload}>Reload latest warehouse (discards edits)</button>}</div>}
      <div className="form-actions"><Link className="button secondary" to="/warehouses" onClick={event => { if (pending) event.preventDefault() }}>Cancel</Link><button className="button primary" disabled={pending}>{pending ? 'Saving…' : 'Save warehouse'}</button></div>
    </form></>
}
