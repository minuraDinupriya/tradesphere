import { useCallback } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ErrorState, LoadingState } from '../../shared/components/Feedback'
import { useRemoteData } from '../../shared/hooks/useRemoteData'
import { useAuth } from '../identity/useAuth'
import { customerApi } from './customerApi'

export function CustomerDetailsPage() {
  const { id } = useParams()
  const { user } = useAuth()
  const load = useCallback((signal: AbortSignal) => customerApi.get(id!, signal), [id])
  const { data: customer, loading, error, reload } = useRemoteData(load)
  if (loading) return <LoadingState label="Loading customer…" />
  if (error) return <><Link className="back-link" to="/customers">← Back to customers</Link><ErrorState message={error} onRetry={reload} /></>
  if (!customer) return null

  return <>
    <Link className="back-link" to="/customers">← Back to customers</Link>
    <div className="page-heading"><div><h1>{customer.name}</h1><p className="muted">Customer details</p></div>
      {user?.canWriteCustomers && <Link className="button primary" to={`/customers/${customer.id}/edit`}>Edit customer</Link>}</div>
    <section className="form-card"><dl className="detail-grid">
      <div><dt>Contact person</dt><dd>{customer.contactName || 'Not provided'}</dd></div><div><dt>Status</dt><dd>{customer.active ? 'Active' : 'Inactive'}</dd></div>
      <div><dt>Email</dt><dd>{customer.email || 'Not provided'}</dd></div><div><dt>Phone</dt><dd>{customer.phone || 'Not provided'}</dd></div>
      <div className="span-two"><dt>Address</dt><dd className="preserve-lines">{customer.address || 'Not provided'}</dd></div>
      <div><dt>Created</dt><dd>{new Date(customer.createdAt).toLocaleString('en-GB')}</dd></div><div><dt>Updated</dt><dd>{new Date(customer.updatedAt).toLocaleString('en-GB')}</dd></div>
    </dl></section>
  </>
}
