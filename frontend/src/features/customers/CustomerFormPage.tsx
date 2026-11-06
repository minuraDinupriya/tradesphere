import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ApiError, errorMessage } from '../../shared/api/http'
import { ErrorState, LoadingState } from '../../shared/components/Feedback'
import { FormField } from '../../shared/components/FormField'
import { useAuth } from '../identity/useAuth'
import { customerApi } from './customerApi'
import type { CustomerValues } from './customerApi'

const empty: CustomerValues = { name: '', contactName: '', email: '', phone: '', address: '', active: true }

export function CustomerFormPage() {
  const { id } = useParams()
  const { user, refresh } = useAuth()
  const navigate = useNavigate()
  const [values, setValues] = useState<CustomerValues>(empty)
  const [version, setVersion] = useState(0)
  const [loading, setLoading] = useState(Boolean(id))
  const [loadError, setLoadError] = useState<string | null>(null)
  const [saveError, setSaveError] = useState<string | null>(null)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [pending, setPending] = useState(false)
  const [attempt, setAttempt] = useState(0)
  const errorRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!id || !user?.canWriteCustomers) return
    const controller = new AbortController()
    customerApi.get(id, controller.signal).then(customer => {
      if (!controller.signal.aborted) { setValues(customer); setVersion(customer.version) }
    }).catch(failure => {
      if (controller.signal.aborted) return
      if (failure instanceof ApiError && failure.problem.status === 401) void refresh()
      else setLoadError(errorMessage(failure))
    }).finally(() => { if (!controller.signal.aborted) setLoading(false) })
    return () => controller.abort()
  }, [id, attempt, user?.canWriteCustomers, refresh])
  useEffect(() => { if (saveError) errorRef.current?.focus() }, [saveError])

  if (!user?.canWriteCustomers) return <ErrorState message="Customer write permission is required to manage customers." />
  if (loading) return <LoadingState label="Loading customer…" />
  if (loadError) return <ErrorState message={loadError} onRetry={reload} />

  function reload() {
    setLoading(true); setLoadError(null); setSaveError(null); setErrors({}); setAttempt(value => value + 1)
  }
  function field(name: string) {
    return { id: name, 'aria-invalid': Boolean(errors[name]), 'aria-describedby': errors[name] ? `${name}-error` : undefined }
  }
  function change(name: keyof CustomerValues, value: string | boolean) { setValues(current => ({ ...current, [name]: value })) }
  async function save(event: FormEvent) {
    event.preventDefault()
    if (pending) return
    setPending(true); setSaveError(null); setErrors({})
    const payload: CustomerValues = { name: values.name.trim(), contactName: values.contactName?.trim() || null,
      email: values.email?.trim() || null, phone: values.phone?.trim() || null, address: values.address?.trim() || null, active: values.active }
    try {
      if (id) await customerApi.update(id, payload, version)
      else await customerApi.create(payload)
      navigate('/customers', { state: { notice: id ? 'Customer updated successfully.' : 'Customer created successfully.' } })
    } catch (failure) {
      if (failure instanceof ApiError && failure.problem.status === 401) { await refresh(); return }
      setSaveError(errorMessage(failure))
      if (failure instanceof ApiError) setErrors(failure.problem.errors ?? {})
    } finally { setPending(false) }
  }

  return <>
    <Link className="back-link" to="/customers">← Back to customers</Link>
    <div className="page-heading"><div><h1>{id ? 'Edit customer' : 'Add customer'}</h1><p className="muted">Keep the information your employees use to identify and contact this business.</p></div></div>
    <form className="product-form" onSubmit={save}>
      <section className="form-card"><h2>Customer information</h2><p className="muted small">Fields marked * are required.</p>
        <fieldset className="field-grid" disabled={pending}><legend className="sr-only">Customer details</legend>
          <FormField name="name" label="Customer name" required error={errors.name}><input {...field('name')} required maxLength={160} value={values.name} onChange={event => change('name', event.target.value)} /></FormField>
          <FormField name="contactName" label="Contact person" error={errors.contactName}><input {...field('contactName')} maxLength={160} value={values.contactName ?? ''} onChange={event => change('contactName', event.target.value)} /></FormField>
          <FormField name="email" label="Email" error={errors.email}><input {...field('email')} type="email" maxLength={254} value={values.email ?? ''} onChange={event => change('email', event.target.value)} /></FormField>
          <FormField name="phone" label="Phone" error={errors.phone}><input {...field('phone')} type="tel" maxLength={40} value={values.phone ?? ''} onChange={event => change('phone', event.target.value)} /></FormField>
          <div className="span-two"><FormField name="address" label="Address" error={errors.address}><textarea {...field('address')} rows={3} maxLength={1000} value={values.address ?? ''} onChange={event => change('address', event.target.value)} /></FormField></div>
          <label className="checkbox-label span-two"><input type="checkbox" checked={values.active} onChange={event => change('active', event.target.checked)} />Active customer</label>
        </fieldset></section>
      {saveError && <div className="error-panel" role="alert" tabIndex={-1} ref={errorRef}><strong>Customer could not be saved</strong><p>{saveError}</p>
        {id && <button className="button secondary" type="button" onClick={reload}>Reload latest customer (discards edits)</button>}</div>}
      <div className="form-actions"><Link className="button secondary" to="/customers" onClick={event => { if (pending) event.preventDefault() }}>Cancel</Link>
        <button className="button primary" disabled={pending}>{pending ? 'Saving…' : 'Save customer'}</button></div>
    </form>
  </>
}
